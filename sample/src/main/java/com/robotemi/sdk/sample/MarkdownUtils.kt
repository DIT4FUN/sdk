package com.robotemi.sdk.sample
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.TextContentRenderer
object MarkdownUtils {
    private val parser: Parser = Parser.builder()
        .extensions(listOf(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create()
        ))
        .build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(listOf(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create()
        ))
        .build()

    fun parseMarkdown(markdown: String): Spanned {
        val document: Node = parser.parse(markdown)
        val html: String = renderer.render(document)
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    }

    fun setMarkdown(textView: TextView, markdown: String) {
        val spanned: Spanned = parseMarkdown(markdown)
        textView.text = spanned
        textView.movementMethod = LinkMovementMethod.getInstance() // 确保链接可点击
    }

    public  fun convertMarkdownToHtml(markdown: String): String {
        val document: Node = parser.parse(markdown)
        return renderer.render(document)
    }
}
