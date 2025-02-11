package com.robotemi.sdk.sample
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
        // 消息文本视图，用于展示消息内容
        val messageText: TextView = itemView.findViewById(R.id.message_text)
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
        // 获取当前位置的消息内容
        val message = messageList[position]
        // 将消息内容转换为 HTML 格式并设置到视图上
        holder.messageText.text = Html.fromHtml(message.content, Html.FROM_HTML_MODE_LEGACY)
    }
    fun updateMessages(newMessage: Message) {
        messageList.add(newMessage)
        notifyItemInserted(messageList.size - 1)
    }
    fun updateMessages(newMessage: String){
        val newMessageObject = Message("assistant", newMessage)
        updateMessages(newMessageObject)
    }
    /**
     * 获取消息列表大小，用于确定RecyclerView中消息项的数量
     * @return 消息列表的大小
     */
    override fun getItemCount(): Int {
        return messageList.size
    }

    /**
     * 从 HTML 字符串中创建 Spanned 对象，考虑到不同版本的 Android 系统使用不同的方法
     * @param html HTML 字符串
     * @return Spanned 对象
     */
    private fun fromHtml(html: String): Spanned {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // 对于 Android N 及以上版本，使用 FROM_HTML_MODE_LEGACY 模式
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            // 对于旧版本，直接使用 fromHtml 方法
            Html.fromHtml(html)
        }
    }

}
