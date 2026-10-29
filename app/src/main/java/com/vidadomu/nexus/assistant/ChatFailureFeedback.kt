package com.vidadomu.nexus.assistant

/** Restored history is content, while a new error during the current visit needs immediate feedback. */
internal fun shouldAnnounceChatFailure(failure: ChatItem.Notice, enteredAt: Long, shownId: String?): Boolean =
    failure.error && failure.at > enteredAt && failure.id != shownId
