package com.vidadomu.nexus.assistant

internal data class ConversationMetadata(val title:String,val preview:String,val searchText:String)

internal fun conversationPreview(text:String):String = text
    .replace(Regex("(?m)^\\s*:::[A-Za-z_-]*[ \\t]*"),"")
    .replace("**","").replace("__","").replace("`","")
    .replace(Regex("\\s+")," ").trim().take(160)

internal fun normalizeConversationSearch(text:String):String = java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT)

/** Derived locally off main. The original conversation files remain the source of truth. */
internal fun conversationMetadata(items:List<ChatItem>):ConversationMetadata {
    val messages=items.mapNotNull {item -> when(item) {
        is ChatItem.User -> item.text
        is ChatItem.Assistant -> item.text
        else -> null
    }}.filter {it.isNotBlank()}
    val first=items.filterIsInstance<ChatItem.User>().firstOrNull {it.text.isNotBlank()}?.text
    return ConversationMetadata(first?.replace(Regex("\\s+")," ")?.take(180) ?: "Nueva conversación",
        messages.lastOrNull()?.let(::conversationPreview).orEmpty(),normalizeConversationSearch(messages.joinToString("\n")))
}
