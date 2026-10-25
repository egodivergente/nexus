import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopMarkdownTest {
    @Test fun markdownLinksBecomeSafeAnchors() {
        val html = DesktopMarkdown.html("Mira [la guía](https://example.com/a?b=1&c=2) ya.")
        assertEquals("<p>Mira <a href=\"https://example.com/a?b=1&amp;c=2\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">la guía</a> ya.</p>", html)
    }

    @Test fun formattedLabelSharesOneAnchor() {
        val html = DesktopMarkdown.html("[**Atlas** docs](http://atlas.test/x)")
        assertEquals(1, Regex("<a ").findAll(html).count())
        assertTrue(html.contains("><strong>Atlas</strong> docs</a>"))
    }

    @Test fun dangerousSchemesStayPlainText() {
        for (url in listOf("javascript:alert(1)", "JAVASCRIPT:alert(1)", "data:text/html,<b>x</b>", "file:///C:/Windows/win.ini", "//evil.test/x", "https://user:pw@evil.test/")) {
            val html = DesktopMarkdown.html("[clic]($url)")
            assertFalse(html.contains("<a "), url)
            assertTrue(html.contains("clic"), url)
        }
        assertNull(DesktopMarkdown.safeHref("https://"))
        assertNull(DesktopMarkdown.safeHref("https://ex.test/\u0000"))
    }

    @Test fun attributeInjectionIsEscaped() {
        val html = DesktopMarkdown.html("[x](https://ex.test/\"onmouseover=\"alert(1))")
        assertFalse(html.contains("\"onmouseover"))
        assertFalse(DesktopMarkdown.html("<script>alert(1)</script> **<img src=x onerror=1>**").contains("<script>"))
        assertFalse(DesktopMarkdown.html("<img src=x onerror=1>").contains("<img"))
    }

    @Test fun bareUrlsAreLinkedWithoutTrailingPunctuation() {
        val html = DesktopMarkdown.html("Ver https://ex.test/path, y luego (https://ex.test/b).")
        assertTrue(html.contains("<a href=\"https://ex.test/path\""))
        assertTrue(html.contains(">https://ex.test/path</a>, y"))
        assertTrue(html.contains("<a href=\"https://ex.test/b\""))
        assertFalse(DesktopMarkdown.html("`https://ex.test/code`").contains("<a "), "code stays literal")
    }

    @Test fun orderedListsKeepNumberingAndNesting() {
        assertEquals("<ol><li>uno</li><li>dos</li></ol>", DesktopMarkdown.html("1. uno\n2. dos"))
        assertEquals("<ol start=\"3\"><li>tres</li></ol>", DesktopMarkdown.html("3. tres"))
        assertEquals("<ul><li>a</li></ul><ol><li>b</li></ol>", DesktopMarkdown.html("- a\n1. b"))
        assertEquals("<ol><li>uno<ul><li>sub</li></ul></li><li>dos</li></ol>", DesktopMarkdown.html("1. uno\n  - sub\n2. dos"))
        assertEquals("<ul><li>a<ul><li>b<ul><li>c</li></ul></li></ul></li><li>d</li></ul>", DesktopMarkdown.html("- a\n  - b\n    - c\n- d"))
    }

    @Test fun existingBlocksAreUnchanged() {
        assertEquals("<h3>T</h3><p><strong>b</strong> <em>i</em> <code>c</code> <s>s</s></p><hr><pre><code>&lt;x&gt;</code></pre>",
            DesktopMarkdown.html("# T\n**b** *i* `c` ~~s~~\n\n---\n```\n<x>\n```"))
        assertTrue(DesktopMarkdown.html("| a | b |\n|---|---|\n| 1 | 2 |").startsWith("<div class=agent-table><table>"))
    }
}

class VideoSummaryTest {
    @Test fun measuredDurationAndNoAudioLabel() {
        val summary = videoSummary(buildJsonObject { put("duration_ms", 12_345); put("audio", false); put("frames", JsonArray(emptyList())) })
        assertEquals("Duración medida del vídeo: 12345 ms. Marcas de extracción aproximadas. SIN audio.", summary)
    }

    @Test fun detectedAudioIsReported() {
        assertEquals("Duración medida del vídeo: 900 ms. Marcas de extracción aproximadas. Audio detectado.",
            videoSummary(buildJsonObject { put("duration_ms", 900); put("audio", true) }))
    }

    @Test fun olderHostWithoutMetadataDoesNotInferSilence() {
        val summary = videoSummary(buildJsonObject { put("frames", JsonArray(emptyList())) })
        assertEquals("Duración del vídeo no medida. Marcas de extracción aproximadas. Audio no medido.", summary)
        assertFalse(summary.contains("null"))
        assertFalse(summary.contains("SIN audio"))
        assertEquals(summary, videoSummary(buildJsonObject { put("audio", JsonNull); put("duration_ms", "x") }))
    }
}
