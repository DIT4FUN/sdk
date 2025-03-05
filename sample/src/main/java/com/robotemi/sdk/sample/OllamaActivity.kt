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
import androidx.recyclerview.widget.DiffUtil
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
import java.util.regex.Pattern

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
        val layoutManager = binding.messageRecyclerView.layoutManager as LinearLayoutManager
        val totalItemCount = messageAdapter.itemCount
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

        // 智能滚动策略
        if (totalItemCount - lastVisiblePosition <= 3) {
            binding.messageRecyclerView.postDelayed({
                layoutManager.scrollToPositionWithOffset(totalItemCount - 1, 0)
            }, 100) // 添加微小延迟确保布局完成
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
        binding.messageRecyclerView.setItemViewCacheSize(20) // 增大缓存
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
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
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
        var holeResponse = StringBuilder() // 改用StringBuilder提升性能
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
                            if (nonNullLine.contains("\"content\":\"")) {
                                // 实时提取内容片段
                                val content = extractContent(nonNullLine)
                                holeResponse.append(content)

                                // 实时分段更新
                                runOnUiThread {
                                    if (messageList.last().role == "assistant") {
                                        // 更新最后一条消息
                                        messageList.last().content = "assistant: $holeResponse"
                                        messageAdapter.notifyItemChanged(messageList.lastIndex)
                                    } else {
                                        // 新建助理消息
                                        val newMessage = Message("assistant", "assistant: $holeResponse")
                                        messageList.add(newMessage)
                                        messageAdapter.notifyItemInserted(messageList.lastIndex)
                                    }
                                    scrollToBottom()
                                }
                            }
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

    private fun extractContent(line: String): String {
        val pattern = Pattern.compile("\"content\":\"(.*?)(?=\")")
        val matcher = pattern.matcher(line)
        return if (matcher.find()) matcher.group(1) else ""
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

// 添加DiffUtil处理增量更新
class MessageDiffUtil(
    private val oldList: List<Message>,
    private val newList: List<Message>
) : DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size
    override fun areItemsTheSame(oldPos: Int, newPos: Int) =
        oldList[oldPos].content.hashCode() == newList[newPos].content.hashCode()
    override fun areContentsTheSame(oldPos: Int, newPos: Int) =
        oldList[oldPos] == newList[newPos]
}



