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


/**
 * OllamaActivity 类继承自 AppCompatActivity，主要用于展示消息列表和发送消息。
 * 它包含了UI初始化、消息发送逻辑和富文本输入的实现。
 */
class OllamaActivity : AppCompatActivity(){
    // 声明视图绑定变量，用于操作Activity中的UI元素
    private lateinit var binding: ActivityOllamaBinding
    // 声声明消息适配器，用于RecyclerView展示消息列表
    private lateinit var messageAdapter: MessageAdapter
    // 声明消息列表，存储消息数据
    private lateinit var messageList: MutableList<String>

    /**
     * Activity 创建时调用的方法。
     * 用于初始化UI、设置RecyclerView适配器和布局管理器、配置按钮点击事件和实现富文本输入。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 使用视图绑定 inflate 布局，并设置为Activity的内容视图
        binding = ActivityOllamaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化消息列表和适配器，并将适配器设置给RecyclerView
        messageList = mutableListOf()
        messageAdapter = MessageAdapter(messageList)
        binding.messageRecyclerView.adapter = messageAdapter
        binding.messageRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        // 配置发送按钮的点击事件，用于发送消息
        binding.ollamaSendButton.setOnClickListener {
            val message = binding.ollamaInput.text.toString()
            // 当输入的消息非空时，添加到消息列表中，并更新RecyclerView
            if (message.isNotEmpty()) {
                try {
                    messageList.add("You: $message")
                    messageAdapter.notifyItemInserted(messageList.size - 1)
                    binding.messageRecyclerView.scrollToPosition(messageList.size - 1)
                    binding.ollamaInput.text.clear()
                } catch (e: Exception) {
                    // 如果发送消息失败，显示Toast提示
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 实现富文本输入，当输入以"**"结尾时，将其替换为HTML加粗标签"<b>"
        binding.ollamaInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Do nothing
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Do nothing
            }

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
     * 将HTML字符串转换为Spanned对象。
     * 这个方法是为了兼容不同版本的Android系统。
     *
     * @param html HTML字符串
     * @return 转换后的Spanned对象
     */
    private fun fromHtml(html: String): Spanned {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(html)
        }
    }
}
