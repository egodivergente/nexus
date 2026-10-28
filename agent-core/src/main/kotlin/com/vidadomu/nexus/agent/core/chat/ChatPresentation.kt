package com.vidadomu.nexus.agent.core.chat

/** One presentation contract for live text, dictation and saved replies. Never rewrites history. */
object ChatPresentation {
    fun blocks(text: String, streaming: Boolean = false): List<MdBlock> = normalize(ChatMarkdown.parse(text, streaming))

    private fun normalize(blocks: List<MdBlock>): List<MdBlock> = blocks.flatMap { block ->
        when (block) {
            is MdBlock.Callout -> {
                val children = normalize(block.blocks)
                if (block.kind in setOf(CalloutKind.TLDR, CalloutKind.TIP, CalloutKind.DECISION)) children
                else listOf(block.copy(blocks = children))
            }
            else -> listOf(block)
        }
    }
}
