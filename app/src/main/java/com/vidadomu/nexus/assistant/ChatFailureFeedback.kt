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
