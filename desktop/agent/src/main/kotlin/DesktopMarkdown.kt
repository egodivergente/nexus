import com.vidadomu.nexus.agent.core.chat.ChatMarkdown
import com.vidadomu.nexus.agent.core.chat.ListItem
import com.vidadomu.nexus.agent.core.chat.MdBlock
import com.vidadomu.nexus.agent.core.chat.MdSpan
import java.net.URI

/**
 * HTML for the Windows chat bubble. Everything is escaped; the only markup produced is a fixed tag set, and the only
 * attribute carrying model text is an http(s) href. Other link schemes (javascript:, file:, data:...) stay plain text.
 */
internal object DesktopMarkdown {
    private val bareUrl = Regex("https?://[^\\s<>\"'`]+", RegexOption.IGNORE_CASE)

    fun html(text: String): String = ChatMarkdown.parse(text).joinToString("") { block ->
        when (block) {
            is MdBlock.Heading -> "<h3>${spans(block.spans)}</h3>"
            is MdBlock.Paragraph -> "<p>${spans(block.spans)}</p>"
            is MdBlock.Quote -> "<blockquote>${spans(block.spans)}</blockquote>"
            is MdBlock.Code -> "<pre><code>${escape(block.code)}</code></pre>"
            is MdBlock.ListBlock -> list(block.items)
            is MdBlock.Table -> "<div class=agent-table><table><thead><tr>" + block.header.joinToString("") { "<th>${spans(it)}</th>" } +
                "</tr></thead><tbody>" + block.rows.joinToString("") { row -> "<tr>" + row.joinToString("") { "<td>${spans(it)}</td>" } + "</tr>" } +
                "</tbody></table></div>"
            MdBlock.Rule -> "<hr>"
        }
    }

    fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    /** Returns the normalized URL when it is an absolute http(s) URL with a host, otherwise null. */
    fun safeHref(raw: String): String? {
        val candidate = raw.trim().removePrefix("<").removeSuffix(">").substringBefore(' ')
        if (candidate.any { it.isISOControl() }) return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        return uri.toASCIIString()
    }

    private fun anchor(href: String, label: String) =
        "<a href=\"${escape(href)}\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">$label</a>"

    private fun styled(span: MdSpan, body: String): String {
        var text = body
        if (span.code) text = "<code>$text</code>"
        if (span.bold) text = "<strong>$text</strong>"
        if (span.italic) text = "<em>$text</em>"
        if (span.strike) text = "<s>$text</s>"
        return text
    }

    private fun autolinked(text: String): String {
        val out = StringBuilder()
        var last = 0
        for (match in bareUrl.findAll(text)) {
            val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
            val href = safeHref(url) ?: continue
            out.append(escape(text.substring(last, match.range.first)))
            out.append(anchor(href, escape(url)))
            last = match.range.first + url.length
        }
        return out.append(escape(text.substring(last))).toString()
    }

    private fun spans(list: List<MdSpan>): String {
        val out = StringBuilder()
        var i = 0
        while (i < list.size) {
            val span = list[i]
            val href = span.link?.let(::safeHref)
            if (href == null) {
                out.append(styled(span, if (span.code || span.link != null) escape(span.text) else autolinked(span.text)))
                i++; continue
            }
            // Consecutive spans of the same link (bold part inside the label) share one anchor.
            val label = StringBuilder()
            while (i < list.size && list[i].link == span.link) { label.append(styled(list[i], escape(list[i].text))); i++ }
            out.append(anchor(href, label.toString()))
        }
        return out.toString()
    }

    private fun list(items: List<ListItem>): String {
        if (items.isEmpty()) return ""
        val out = StringBuilder()
        // Open lists, innermost last: (depth, ordered).
        val open = ArrayDeque<Pair<Int, Boolean>>()
        fun close() { val (_, ordered) = open.removeLast(); out.append(if (ordered) "</li></ol>" else "</li></ul>") }
        fun openList(depth: Int, item: ListItem) {
            val ordered = item.number != null
            val start = item.number?.toIntOrNull()?.takeIf { it != 1 }
            out.append(if (ordered) (if (start != null) "<ol start=\"$start\">" else "<ol>") else "<ul>")
            open.addLast(depth to ordered)
        }
        for (item in items) {
            val ordered = item.number != null
            while (open.isNotEmpty() && open.last().first > item.depth) close()
            val top = open.lastOrNull()
            when {
                top == null -> openList(item.depth, item)
                top.first == item.depth && top.second == ordered -> out.append("</li>")
                top.first == item.depth -> { close(); openList(item.depth, item) }
                else -> openList(item.depth, item) // deeper: nested inside the still-open <li>
            }
            out.append("<li>").append(spans(item.spans))
        }
        while (open.isNotEmpty()) close()
        return out.toString()
    }
}
