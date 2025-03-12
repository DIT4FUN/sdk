package com.robotemi.sdk.sample
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView



/**
 * 消息适配器，用于在RecyclerView中展示消息列表
 * @param messages 消息列表，包含要展示的消息文本
 */
class MessageAdapter(private val messageList: MutableList<Message>) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    /**
     * 消息视图持有者，用于在RecyclerView中展示单个消息项
     */
    class MessageViewHolder(itemView: View):RecyclerView.ViewHolder(itemView) {
        // 消息文本WEB视图，用于展示消息内容
        val messageWebView: WebView = itemView.findViewById(R.id.message_webview)
    }

    /**
     * 创建消息项视图
     * @param parent 视图的父容器
     * @param viewType 视图类型
     * @return 返回初始化后的MessageViewHolder对象
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        // 通过布局 inflater 将 message_item 布局转化为视图
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_item, parent, false)
        return MessageViewHolder(view)
    }

    /**
     * 绑定消息项视图，将消息内容设置到视图上
     * @param holder 消息视图持有者
     * @param position 消息在列表中的位置
     */
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        // 修改Adapter的onBindViewHolder
        val processedContent = processMarkdown(messageList[position].content)
        Log.d("MessageAdapter", "Processed Content: $processedContent")
        setHtmlContent(holder.messageWebView, processedContent)

    }

    private fun processMarkdown(content: String): String {
        return content
            .replace("\\u003c","<")
            .replace("\\u003e", ">")
            .replace("<think>.*?</think>".toRegex(), "") // 过滤思考内容
            .replace("\\n{2,}".toRegex(), "<br><br>") // 多个换行优先替换
            .replace("\\n", "<br>")                   // 单个换行
            // 其他Markdown元素处理
            .replace("\\*\\*(.*?)\\*\\*".toRegex(), "<strong>$1</strong>")  // 加粗
            .replace("\\*(.*?)\\*".toRegex(), "<em>$1</em>")                // 斜体
            .replace("\\[(.*?)\\]\\((.*?)\\)".toRegex(), "<a href=\"$2\">$1</a>") // 链接
            .replace("`([^`]+)`".toRegex(), "<code>$1</code>")               // 行内代码
            // 标题处理
            .replace("^#{1,6}\\s+(.*?)(\\s+#+)?$".toRegex(RegexOption.MULTILINE)) { match ->
                val level = match.value.takeWhile { it == '#' }.length.coerceIn(1..6)
                val text = match.groupValues[1].trim()
                "<h$level>$text</h$level>"
            }
            // 列表处理
            .replace("^\\*\\s+(.*)$".toRegex(RegexOption.MULTILINE), "<ul><li>$1</li></ul>")
            .replace("^\\d+\\.\\s+(.*)$".toRegex(RegexOption.MULTILINE), "<ol><li>$1</li></ol>")
    }


    private fun setHtmlContent(webView: WebView, markdownContent: String) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true // 根据需要启用或禁用JavaScript
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        val htmlContent = MarkdownUtils.convertMarkdownToHtml(markdownContent)

        val html = """
            <html>
            <head>
                <style>
                    body {
                        font-size: 24px;
                        padding: 16px;
                        color: #000000;
                        font-family: sans-serif-condensed;
                        line-height: 1.2;
                        line-spacing: 4px;
                    }
                    a {
                        color: #80CBC4;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: #000000;
                        font-weight: bold;
                        }
                    pre {
                        background: #f4f4f4;
                        padding: 15px;
                        border-radius: 5px;
                        overflow-x: auto;
                        margin: 10px 0;
                    }
                    code {
                        font-family: 'Courier New', monospace;
                        font-size: 0.9em;
                    }
                    ul, ol {
                        padding-left: 30px;
                        margin: 10px 0;
                    }
                    li {
                        margin: 5px 0;
                    }
                </style>
            </head>
            <body>
                $htmlContent            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
    fun updateMessages(newMessage: Message) {
        when (newMessage.role) {
            "user" -> {
                messageList.add(newMessage)
                notifyItemInserted(messageList.lastIndex)
            }
            "assistant" -> {
                messageList.add(newMessage)
                notifyItemInserted(messageList.lastIndex)
            }
        }
    }

    /**
     * 获取消息列表大小，用于确定RecyclerView中消息项的数量
     * @return 消息列表的大小
     */
    override fun getItemCount(): Int {
        return messageList.size
    }
}

