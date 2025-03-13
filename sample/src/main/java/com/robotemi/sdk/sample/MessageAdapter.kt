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
import android.webkit.WebViewClient
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
        val message = messageList[position]
        val processedContent = when (message.role) {
            "user" -> "<b>You:</b> ${message.content}"
            "assistant" -> "<b>Assistant:</b> ${message.content}"
            else -> message.content
        }
        
        // 添加样式以减少消息间距
        val htmlContent = """
            <div style="margin: 4px 0; padding: 4px; border-radius: 8px;">
                ${MarkdownUtils.convertMarkdownToHtml(processedContent)}
            </div>
        """.trimIndent()
        
        setHtmlContent(holder.messageWebView, htmlContent)

        // 确保WebView内容加载完成后再计算高度
        holder.messageWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.post {
                    view.evaluateJavascript("""
                        Math.max(document.body.scrollHeight, document.body.offsetHeight, 
                                 document.documentElement.clientHeight, document.documentElement.scrollHeight, 
                                 document.documentElement.offsetHeight);
                    """.trimIndent()) { height ->
                        if (height != null && height != "null") {
                            val params = view.layoutParams
                            params.height = height.toInt()
                            view.layoutParams = params
                            (holder.itemView.context as? OllamaActivity)?.scrollToBottom()
                        }
                    }
                }
            }
        }
    }

    private fun setHtmlContent(webView: WebView, markdownContent: String) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = false // 根据需要启用或禁用JavaScript
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        val htmlContent = MarkdownUtils.convertMarkdownToHtml(markdownContent)

        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }


    /**
     * 获取消息列表大小，用于确定RecyclerView中消息项的数量
     * @return 消息列表的大小
     */
    override fun getItemCount(): Int {
        return messageList.size
    }

    /**
     * 更新消息列表并通知适配器
     * @param newList 新的消息列表
     */
    fun updateList(newList: List<Message>) {
        messageList.clear()
        messageList.addAll(newList)
        notifyDataSetChanged()
    }
}

