package com.vidadomu.nexus.assistant

internal fun conversationRelativeDate(at:Long,now:Long=System.currentTimeMillis(),
    zone:java.time.ZoneId=java.time.ZoneId.systemDefault(),locale:java.util.Locale=java.util.Locale.getDefault()):String {
    if(at<=0) return ""
    val date=java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today=java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when(val days=java.time.temporal.ChronoUnit.DAYS.between(date,today)) {
        0L -> "Hoy"
        1L -> "Ayer"
        in 2L..6L -> "Hace $days d"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern(if(date.year==today.year) "d MMM" else "d MMM yyyy",locale))
    }
}

/** Presentation only: filtering and pinning never rewrite or remove a saved conversation. */
internal fun drawerConversations(
    conversations: List<AssistantChatEntry>,
    closed: Set<String>,
    pinned: Set<String>,
    showClosed: Boolean,
    query: String,
): List<AssistantChatEntry> {
    val terms = normalizeConversationSearch(query).split(Regex("\\s+")).filter(String::isNotBlank)
    return conversations.filter { chat ->
        if (!showClosed && chat.id in closed) false
        else if (terms.isEmpty()) true
        else {
            // Full message text is normalized by the store's index on a background dispatcher.
            val text = normalizeConversationSearch("${chat.title} ${chat.preview}") + " " + chat.searchText
            terms.all { it in text }
        }
    }.sortedByDescending { it.id in pinned }
}
