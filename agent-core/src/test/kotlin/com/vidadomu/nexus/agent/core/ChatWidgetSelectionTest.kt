package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.chat.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ChatWidgetSelectionTest {
    private val options = listOf(WidgetOption("k", "Kling"), WidgetOption("pro", "Kling Pro"), WidgetOption("max", "Kling Pro Max"))
    private fun poll(multi: Boolean = false) = WidgetSpec.Poll("models", "¿Modelo?", options, multi)
    private val compare = WidgetSpec.Compare("models", "Comparar", options)

    @Test fun `long labels never restore shorter labels in polls or comparisons`() {
        assertEquals(setOf("pro"), ChatWidgets.selectedIds(poll(), "Kling Pro"))
        assertEquals(setOf("max"), ChatWidgets.selectedIds(poll(), "Kling Pro Max"))
        assertEquals(setOf("pro"), ChatWidgets.selectedIds(compare, "Elijo: Kling Pro"))
        assertEquals(setOf("k"), ChatWidgets.selectedIds(compare, "Elijo: Kling"))
        assertEquals(emptySet(), ChatWidgets.selectedIds(compare, "No elijo: Kling Pro"))
        assertEquals(emptySet(), ChatWidgets.selectedIds(poll(), "Prefiero Kling Pro para luego"))
    }

    @Test fun `multiple selections restore old comma replies and recent newline replies`() {
        for (text in listOf("Kling, Kling Pro Max", "Kling\nKling Pro Max")) {
            assertEquals(setOf("k", "max"), ChatWidgets.selectedIds(poll(true), text))
            assertEquals(emptySet(), ChatWidgets.selectedIds(poll(), text))
            assertEquals(emptySet(), ChatWidgets.selectedIds(compare, text))
        }
    }

    @Test fun `every supported subset round trips with explicit ids`() {
        for (mask in 1..7) {
            val ids = options.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.map { it.id }.toSet()
            val reply = assertNotNull(ChatWidgets.reply(poll(true), ids))
            assertEquals(ids, ChatWidgets.selectedIds(poll(true), reply.text, reply.answer))
            assertEquals(ids, ChatWidgets.selectedIds(poll(true), reply.text))
        }
    }

    @Test fun `comma and newline labels use ids and ambiguous legacy answers stay unselected`() {
        for (separator in listOf(", ", "\n")) {
            val p = poll(true).copy(options = listOf(WidgetOption("a", "Kling"), WidgetOption("b", "Pro"), WidgetOption("ab", "Kling${separator}Pro")))
            for (ids in listOf(setOf("a", "b"), setOf("ab"))) {
                val reply = assertNotNull(ChatWidgets.reply(p, ids))
                assertEquals(ids, ChatWidgets.selectedIds(p, reply.text, reply.answer))
            }
            assertEquals(emptySet(), ChatWidgets.selectedIds(p, "Kling${separator}Pro"))
        }
    }

    @Test fun `explicit answers are scoped by panel and source message without text fallback`() {
        val answer = WidgetAnswer("models", setOf("pro"), "assistant-2")
        assertEquals(setOf("pro"), ChatWidgets.selectedIds(poll(), "Kling", answer, "assistant-2"))
        assertEquals(emptySet(), ChatWidgets.selectedIds(poll(), "Kling Pro", answer, "assistant-1"))
        assertEquals(emptySet(), ChatWidgets.selectedIds(poll().copy(id = "other"), "Kling Pro", answer, "assistant-2"))
        assertEquals(emptySet(), ChatWidgets.selectedIds(poll(), "Kling Pro", answer.copy(optionIds = setOf("unknown")), "assistant-2"))
        assertEquals(emptySet(), ChatWidgets.selectedIds(poll(), "Kling Pro", answer.copy(optionIds = setOf("k", "pro")), "assistant-2"))
    }

    @Test fun `duplicate option ids are repaired deterministically without stealing reserved ids`() {
        val raw = """{"type":"poll","question":"Modelo","options":[{"id":"same","label":"Kling"},{"id":"same","label":"Kling Pro"},{"id":"same_1","label":"Kling Pro Max"},"Pro Kling"]}"""
        val p = assertIs<WidgetSpec.Poll>(ChatWidgets.parse(raw))
        assertEquals(4, p.options.map { it.id }.toSet().size)
        assertEquals("same_1", p.options[2].id)
        assertEquals(p, ChatWidgets.parse(raw))
        p.options.forEach { option ->
            val reply = assertNotNull(ChatWidgets.reply(p, setOf(option.id)))
            assertEquals(setOf(option.id), ChatWidgets.selectedIds(p, reply.text, reply.answer))
        }
    }

    @Test fun `repeated widget ids including nested callouts get separate identities`() {
        val panel = "```nexus-ui\n{\"type\":\"compare\",\"id\":\"same\",\"options\":[\"Kling\",\"Kling Pro\"]}\n```"
        val md = "$panel\n\n:::tip\n$panel\n:::\n\n$panel"
        val blocks = ChatMarkdown.parse(md)
        val specs = listOf((blocks[0] as MdBlock.Widget).spec, ((blocks[1] as MdBlock.Callout).blocks.single() as MdBlock.Widget).spec,
            (blocks[2] as MdBlock.Widget).spec)
        assertEquals(3, specs.map { it.id }.toSet().size)
        val reply = assertNotNull(ChatWidgets.reply(specs[1], setOf("o1")))
        assertEquals(emptySet(), ChatWidgets.selectedIds(specs[0], reply.text, reply.answer))
        assertEquals(setOf("o1"), ChatWidgets.selectedIds(specs[1], reply.text, reply.answer))
        assertEquals(blocks, ChatMarkdown.parse(md))
    }

    @Test fun `invalid selections and steps cannot produce a reply`() {
        assertNull(ChatWidgets.reply(poll(), emptySet()))
        assertNull(ChatWidgets.reply(poll(), setOf("unknown")))
        assertNull(ChatWidgets.reply(poll(), setOf("k", "pro")))
        assertNull(ChatWidgets.reply(compare, setOf("k", "pro")))
        assertNull(ChatWidgets.reply(WidgetSpec.Steps("s", null, listOf("Paso")), setOf("k")))
    }

    @Test fun `reply metadata survives serialization while ids hints and action fields stay out of reply text`() {
        val p = assertIs<WidgetSpec.Compare>(ChatWidgets.parse("""{"type":"compare","id":"p","action":"generate","options":[{"id":"execute","label":"Kling","hint":"spend credits","action":"submit"},{"id":"other","label":"Kling Pro"}]}"""))
        val reply = assertNotNull(ChatWidgets.reply(p, setOf("execute")))
        assertEquals("Elijo: Kling", reply.text)
        val answer = reply.answer.copy(messageId = "assistant-1")
        assertEquals(answer, Json.decodeFromString<WidgetAnswer>(Json.encodeToString(answer)))
        assertEquals(setOf("execute"), ChatWidgets.selectedIds(p, reply.text, answer, "assistant-1"))
    }
}
