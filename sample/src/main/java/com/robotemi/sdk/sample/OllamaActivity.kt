package com.robotemi.sdk.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.robotemi.sdk.sample.databinding.ActivityOllamaBinding
import android.text.Html
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.Editable
import android.text.Html.fromHtml
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.io.InputStream
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet


class OllamaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOllamaBinding
    private lateinit var ollamaApi: OllamaApi
    private val messageList = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOllamaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 启用硬件加速
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        binding.chatWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Initialize WebView settings
        binding.chatWebView.settings.apply {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            // 新增：启用文本选择功能
            setDefaultTextEncodingName("utf-8")
            setSupportMultipleWindows(true)
        }

        // Ensure WebView is ready to execute JavaScript
        binding.chatWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // WebView is ready to execute JavaScript
                updateChatWebView()
            }
        }

        // Initialize Retrofit and OllamaApi
        val customGson: Gson = GsonBuilder().setLenient().create()
        val loggingInterceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val retryInterceptor = RetryInterceptor(maxRetries = 3)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Increase read timeout
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://169.254.1.2:11434")
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .client(okHttpClient)
            .build()
        ollamaApi = retrofit.create(OllamaApi::class.java)

        // 修改初始HTML结构添加文本选择样式
        binding.chatWebView.loadDataWithBaseURL(
            null,
            """
                <style>
                    body, div {
                        -webkit-user-select: text !important;
                        user-select: text !important;
                    }
                </style>
                <div id='chatContainer'></div>
            """.trimIndent(),
            "text/html",
            "UTF-8",
            null
        )  // 初始化基础HTML结构

        binding.ollamaSendButton.setOnClickListener {
            val message = binding.ollamaInput.text.toString()
            if (message.isNotEmpty()) {
                messageList.add(Message(role = "user", content = message))
                updateChatWebView()
                sendMessageToOllama(message)
                binding.ollamaInput.text.clear()
            }
        }
    }

    // 修改updateChatWebView方法的JavaScript执行逻辑，替换innerHTML而非追加
    private fun updateChatWebView(messages: List<Message> = messageList) {
        val htmlContent = messages.joinToString("") { message ->
            val processedContent = when (message.role) {
                "user" -> "<b>user:</b> ${convertMarkdownToHtml(message.content).trim()}"
                "assistant" -> "<b>Assistant:</b> ${convertMarkdownToHtml(message.content).trim()}"
                else -> convertMarkdownToHtml(message.content).trim()
            }
            """
                <div style='margin: 4px 0; padding: 4px; border-radius: 8px;'>$processedContent</div>
            """.trimIndent()
        }.trim() // 新增整体trim去除多余换行

        Log.d("OllamaActivity", "updateChatWebView called with htmlContent: $htmlContent")
        // 使用双引号包裹并转义双引号，修复单引号转义问题
        binding.chatWebView.evaluateJavascript(
            "javascript:document.getElementById('chatContainer').innerHTML = \"${htmlContent.replace("\"", "&quot;").replace("\n", " ")}\";",
            null
        )
    }

    private fun convertMarkdownToHtml(content: String): String {
        var parser = Parser.builder().build()
        var renderer = HtmlRenderer.builder().build()
        val html = renderer.render(parser.parse(content)).trim() // 新增trim去除多余空白
        Log.d("MarkdownDebug", """
        Original: $content
        Converted: $html
    """.trimMargin())
        return html
    }

    // 新增请求与消息位置的映射表
    private val pendingRequests = mutableMapOf<Call<ResponseBody>, Int>()

    private fun sendMessageToOllama(message: String) {
        Log.d("OllamaActivity", "sendMessageToOllama called with message: $message")
        val userMessage = messageList.last { it.role == "user" && it.content == message }
        val assistantIndex = messageList.size
        messageList.add(Message(role = "assistant", content = ""))
        updateChatWebView()
        
        // 新增：构造API请求参数
        val request = ChatRequest(
            model = "qwq:latest", // 根据实际模型名称修改
            messages = listOf(userMessage)
        )
        
        val call = ollamaApi.sendMessage(request)
        pendingRequests[call] = assistantIndex

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    response.body()?.let { responseBody ->
                        val source = responseBody.source()
                        try {
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line() ?: break
                                val chatResponse = Gson().fromJson(line, ChatResponse::class.java)
                                runOnUiThread {
                                    val position = pendingRequests[call]
                                    if (position != null) {
                                        val assistantMessage = messageList[position]
                                        assistantMessage.content += chatResponse.message.content
                                        if (chatResponse.done) {
                                            pendingRequests.remove(call)
                                            updateChatWebView()
                                        }
                                    }
                                }
                                // Increase read timeout time
                                source.timeout().deadlineNanoTime(System.nanoTime() + TimeUnit.SECONDS.toNanos(30))
                            }
                        } catch (e: Exception) {
                            Log.e("OllamaActivity", "Error processing response", e)
                            runOnUiThread {
                                Toast.makeText(this@OllamaActivity, "Error processing response", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            source.close()
                        }
                    }
                } else {
                    Log.d("OllamaActivity", "Failed to send message, response code: ${response.code()}")
                    runOnUiThread {
                        Toast.makeText(this@OllamaActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.d("OllamaActivity", "Failed to send message, error: ${t.message}")
                runOnUiThread {
                    Toast.makeText(this@OllamaActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
                pendingRequests.remove(call)
            }
        })
    }
}