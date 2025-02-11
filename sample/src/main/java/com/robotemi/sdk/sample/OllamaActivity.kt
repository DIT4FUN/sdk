package com.robotemi.sdk.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.robotemi.sdk.sample.databinding.ActivityOllamaBinding
import android.text.Html
import android.text.Spanned
import android.text.TextWatcher
import android.text.Editable
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * OllamaActivity 类继承自 AppCompatActivity，主要用于展示和处理与 Ollama 相关的消息交互
 */
class OllamaActivity : AppCompatActivity() {
    // 延迟初始化的 Activity 绑定对象
    private lateinit var binding: ActivityOllamaBinding
    // 消息适配器，用于 RecyclerView 显示消息列表
    private lateinit var messageAdapter: MessageAdapter
    // 消息列表，存储消息数据
    private lateinit var messageList: MutableList<Message>
    // Ollama API 接口的实现，用于与服务器交互
    private lateinit var ollamaApi: OllamaApi

    /**
     * Activity 创建时调用的方法
     * 初始化 UI、设置适配器、配置网络请求，并设置点击和文本变化监听器
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOllamaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        messageList = mutableListOf()
        messageAdapter = MessageAdapter(messageList) // 初始化适配器
        binding.messageRecyclerView.adapter = messageAdapter
        binding.messageRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        // 创建自定义的 Gson 实例，并设置为宽容模式
        val customGson: Gson = GsonBuilder()
            .setLenient()
            .create()
        //创建重试拦截器
        val retryInterceptor = object:Interceptor{
            private val MAX_RETRIES = 10
            private var retryCount = 0
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()
                var response: okhttp3.Response? = null

                while (retryCount < MAX_RETRIES) {
                    try {
                        response = chain.proceed(request)
                        if (response.isSuccessful) {
                            return response
                        }
                    } catch (e: Exception) {
                        retryCount++
                        Log.d("ollamaActivity","Exception Request failed ,retrying....$retryCount")
                    }
                }

                //如果所有重试都失败了,则抛出最后一个异常
                throw IOException("Request failed after $MAX_RETRIES retries")
            }
        }
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://172.16.24.134:11434")
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .client(okHttpClient)
            .build()
        ollamaApi = retrofit.create(OllamaApi::class.java)

        binding.ollamaSendButton.setOnClickListener {
            val message = binding.ollamaInput.text.toString()
            if (message.isNotEmpty()) {
                sendMessageToOllama(message)
            }
        }

        binding.ollamaInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                var text = s.toString()
                if (text.endsWith("**")) {
                    val formattedText = text.replace("**", "<b>")
                    binding.ollamaInput.setText(fromHtml(formattedText))
                    binding.ollamaInput.setSelection(formattedText.length)
                }
            }
        })

    }

    /**
     * 发送消息到 Ollama
     * @param message 用户输入的消息文本
     */
    private fun sendMessageToOllama(message: String) {
        Log.d("OllamaActivity", "sendMessageToOllama called with message: $message")

        // 添加用户消息到 messageList
        messageList.add(Message(role = "user", content = message))
        messageAdapter.notifyItemInserted(messageList.size - 1) // 通知适配器数据已更改
        binding.messageRecyclerView.scrollToPosition(messageList.size - 1)

        val request = ChatRequest(
            model = "deepseek-r1:70b",
            messages = listOf(Message(role = "user", content = message))
        )
        ollamaApi.sendMessage(request).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.d("OllamaActivity", "sendMessageToOllama response: ${response.body()}")
                Log.d("OllamaActivity", "sendMessageToOllama response.isSuccessful: ${response.isSuccessful}")
                if (response.isSuccessful) {
                    response.body()?.let { responseBody ->
                        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            runOnUiThread {
                                try {
                                    val responseMessage = Gson().fromJson(line, ChatResponse::class.java)
                                    responseMessage.message?.let {
                                        messageList.add(Message(role = "assistant", content = it.content))
                                        messageAdapter.notifyItemInserted(messageList.size - 1) // 通知适配器数据已更改
                                        binding.messageRecyclerView.scrollToPosition(messageList.size - 1)
                                    }
                                } catch (e: Exception) {
                                    Log.e("OllamaActivity", "Error parsing JSON: ${e.message}")
                                }
                            }
                        }
                        reader.close()
                        binding.ollamaInput.text.clear()
                    }
                } else {
                    Log.d("OllamaActivity", "Failed to send message,response code: ${response.code()}")
                    Toast.makeText(this@OllamaActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }


            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.d("OllamaActivity", "Failed to send message,error: ${t.message}")
                Toast.makeText(this@OllamaActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
        })
    }



    /**
     * 将 HTML 字符串转换为 Spanned 对象
     * @param html HTML 字符串
     * @return 转换后的 Spanned 对象
     */
    private fun fromHtml(html: String): Spanned {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(html)
        }
    }
}


