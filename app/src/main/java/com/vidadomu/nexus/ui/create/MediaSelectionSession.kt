package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.MediaSlotType

/** Main-thread tokens: asynchronous copies must still belong to the current form/slot. */
internal class MediaSelectionSession {
    data class Token(val epoch: Long, val slot: MediaSlotType, val revision: Long, val fieldKey: String? = null)
    private var epoch = 0L
    private val revisions = mutableMapOf<Pair<MediaSlotType, String?>, Long>()
    fun reset() { epoch++; revisions.clear() }
    fun invalidate(slot: MediaSlotType, fieldKey: String? = null) { val key = slot to fieldKey; revisions[key] = (revisions[key] ?: 0L) + 1L }
    fun begin(slot: MediaSlotType, append: Boolean = false, fieldKey: String? = null): Token {
        if (!append) invalidate(slot, fieldKey)
        return Token(epoch, slot, revisions[slot to fieldKey] ?: 0L, fieldKey)
    }
    fun accepts(token: Token): Boolean = token.epoch == epoch && token.revision == (revisions[token.slot to token.fieldKey] ?: 0L)
}
