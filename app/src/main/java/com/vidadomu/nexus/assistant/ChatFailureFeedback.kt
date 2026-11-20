package com.vidadomu.nexus.assistant

/**
 * Restored history is content, while a new error during the current visit needs immediate feedback. The feedback is
 * a tap in the hand and, if the card is out of sight, a mark on the way down: the card itself is the only message.
 */
internal fun shouldAnnounceChatFailure(failure: ChatItem.Notice, enteredAt: Long, shownId: String?): Boolean =
    failure.error && failure.at > enteredAt && failure.id != shownId

/**
 * The failure the conversation is still about: the last error notice, until the user sends something after it. That
 * one keeps its whole card; the earlier ones, and this one once the conversation moves on, fold to a line.
 */
internal fun currentFailureId(items: List<ChatItem>): String? {
    val at = items.indexOfLast { it is ChatItem.Notice && it.error }
    if (at < 0) return null
    return items[at].id.takeIf { (at + 1 until items.size).none { later -> items[later] is ChatItem.User } }
}

/**
 * What the app says when the answer that was working in another conversation is over. A turn that ended in a failure
 * did not finish: its card waits in that conversation, and until it is opened this line is the only word about it.
 */
internal fun backgroundTurnMessage(title: String, items: List<ChatItem>): String {
    val failed = items.takeLastWhile { it !is ChatItem.User }.any { it is ChatItem.Notice && it.error }
    return (if (failed) "Nexus no ha podido terminar en «$title»." else "Nexus ha terminado en «$title».") + " Ábrela en Conversaciones."
}
