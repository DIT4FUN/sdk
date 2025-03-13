package com.robotemi.sdk.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.message_item.message_webview
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

/**
 * OllamaActivity 类继承自 AppCompatActivity，主要用于展示和处理与 Ollama 相关的消息交互
 */
class OllamaActivity() : AppCompatActivity(), Parcelable {
    // 延迟初始化的 Activity 绑定对象
    private lateinit var binding: ActivityOllamaBinding
    // 消息适配器，用于 RecyclerView 显示消息列表
    private lateinit var messageAdapter: MessageAdapter
    // 消息列表，存储消息数据
    private lateinit var messageList: MutableList<Message>
    // Ollama API 接口的实现，用于与服务器交互
    private lateinit var ollamaApi: OllamaApi

    constructor(parcel: Parcel) : this() {

    }


    fun scrollToBottom() {
        binding.messageRecyclerView.post {
            (binding.messageRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                messageList.size - 1,
                0
            )
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
        // 在RecyclerView初始化时添加
        binding.messageRecyclerView.setItemViewCacheSize(200) // 增大缓存
        binding.messageRecyclerView.setHasFixedSize(false)   // 允许动态高度
        binding.messageRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val lastVisibleItemPosition = (recyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).findLastVisibleItemPosition()
                val itemCount = recyclerView.adapter?.itemCount ?: 0
                if (lastVisibleItemPosition == itemCount -1) {}
            }
        })

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
            .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS) //禁用全局ReadTimeout
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                response.newBuilder()
                    .body(response.body?.let { it } ?: ResponseBody.create(null, ""))
                    .build()
            }
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://172.16.25.33:11434")
            .addConverterFactory(GsonConverterFactory.create(customGson))
            .client(okHttpClient)
            .build()
        ollamaApi = retrofit.create(OllamaApi::class.java)

        binding.ollamaSendButton.setOnClickListener {
            val message = binding.ollamaInput.text.toString()
            if (message.isNotEmpty()) {
                // 修改消息格式，去掉多余的 user: 前缀
                val userMessage = Message(role = "user", content = message)
                updateMessages(userMessage)
                scrollToBottom()
                sendMessageToOllama(message)
                binding.ollamaInput.text.clear()
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
            messages = messageList + Message(role = "user", content = message)
        )
        ollamaApi.sendMessage(request).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    response.body()?.let { responseBody ->
                        val source = responseBody.source()
                        try {
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line() ?: break
                                val chatResponse = Gson().fromJson(line, ChatResponse::class.java)
                                runOnUiThread {
                                    // 确保消息内容不为空
                                    if (chatResponse.message.content.isNotEmpty()) {
                                        updateMessages(chatResponse.message)
                                    }
                                }
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
                if (call.isCanceled.not()) {
                    call.clone().enqueue(this)
                }
            }
        })
    }

    private fun extractContent(line: String): String {
        val chatResponse = Gson().fromJson(line, ChatResponse::class.java)
        return chatResponse.message.content
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {

    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<OllamaActivity> {
        override fun createFromParcel(parcel: Parcel): OllamaActivity {
            return OllamaActivity(parcel)
        }

        override fun newArray(size: Int): Array<OllamaActivity?> {
            return arrayOfNulls(size)
        }
    }

    private fun updateMessages(newMessage: Message) {
        runOnUiThread {
            if (newMessage.role == "user") {
                // 用户消息直接添加到列表中
                messageList.add(newMessage)
                messageAdapter.notifyItemInserted(messageList.size - 1)
                scrollToBottom()
            } else {
                // 机器人消息流式拼接显示
                if (messageList.isNotEmpty() && messageList.last().role == "assistant") {
                    // 如果最后一条消息是机器人的，则拼接内容
                    val lastMessage = messageList.last()
                    lastMessage.content += newMessage.content
                    messageAdapter.notifyItemChanged(messageList.size - 1)
                } else {
                    // 如果最后一条消息不是机器人的，则添加新消息
                    messageList.add(newMessage)
                    messageAdapter.notifyItemInserted(messageList.size - 1)
                }
                scrollToBottom()
            }
        }
    }
}