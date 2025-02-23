package com.robotemi.sdk.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.robotemi.sdk.sample.databinding.ActivityOllamaBinding
import okhttp3.internal.notify
import android.text.Html
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.Editable
import android.text.Html.fromHtml
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.message_item.message_text
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



    private fun scrollToBottom() {
        binding.messageRecyclerView.post {
            val lastPosition = messageList.size - 1
            if (lastPosition >= 0) {
                binding.messageRecyclerView.smoothScrollToPosition(lastPosition)

                // 添加布局完成监听确保滚动生效
                binding.messageRecyclerView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: View?,
                        left: Int,
                        top: Int,
                        right: Int,
                        bottom: Int,
                        oldLeft: Int,
                        oldTop: Int,
                        oldRight: Int,
                        oldBottom: Int
                    ) {
                        binding.messageRecyclerView.removeOnLayoutChangeListener(this)
                        (binding.messageRecyclerView.layoutManager as LinearLayoutManager)
                            .scrollToPositionWithOffset(lastPosition, 0)
                    }
                })
            }
        }
    }

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

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val retryInterceptor = RetryInterceptor(maxRetries = 3)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://172.16.24.187:11434")
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .client(okHttpClient)
            .build()
        ollamaApi = retrofit.create(OllamaApi::class.java)

        binding.ollamaSendButton.setOnClickListener {
            val message = binding.ollamaInput.text.toString()
            if (message.isNotEmpty()) {
                var userStringHead = "user: $message"
                // 添加用户消息到消息列表
                val userMessage = Message(role = "user", content = userStringHead)
                messageAdapter.updateMessages(userMessage)
                scrollToBottom()
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
                    val lines = mutableListOf<String>()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { nonNullLine ->
                            lines.add(nonNullLine)
                        }
                    }
                    //创建拼接数据
                    var HoleResponse = "assistant:"

                    runOnUiThread {
                        try {
                            for (line in lines) {
                                val responseMessage = Gson().fromJson(line, ChatResponse::class.java)
                                responseMessage.message?.let {
                                    val assistantMessage= Message(
                                            role = "assistant",
                                            content = it.content
                                        )
                                    Log.d("OllamaActivity", "sendMessageToOllama responseMessage: ${responseMessage.message}")

                                    HoleResponse += it.content

                                }
                                if ( line.contains("\"done\":true")){
                                    messageAdapter.updateMessages(HoleResponse) // 通知适配器数据已更改
                                    //处理对话结束
                                    //reader.close() // 确保在所有数据读取完毕后关闭 reader
                                    binding.ollamaInput.text.clear()
                                    //binding.messageRecyclerView.smoothScrollToPosition(messageList.lastIndex)
                                    scrollToBottom()
                                    break
                                }
                            }

                        } catch (e: Exception) {
                            Log.e("OllamaActivity", "Error parsing JSON: ${e.message}")
                        }
                    }
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


