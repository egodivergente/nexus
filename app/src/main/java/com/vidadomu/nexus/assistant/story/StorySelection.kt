package com.vidadomu.nexus.assistant.story

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** An explicit selection, never inferred from the last visible screen or generation. */
data class StorySelection(
    val projectId: String,
    val sceneId: String? = null,
    val shotId: String? = null,
    val blockId: String? = null,
    val mode: String = "proposal",
    val preserveIds: Set<String> = emptySet(),
)

object StorySelectionStore {
    private val mutable = MutableStateFlow<StorySelection?>(null)
    val selection = mutable.asStateFlow()
    fun select(value: StorySelection) { mutable.value = value }
    fun select(projectId: String, sceneId: String? = null, shotId: String? = null, blockId: String? = null,
        mode: String = "proposal", preserveIds: Set<String> = emptySet()) =
        select(StorySelection(projectId, sceneId, shotId, blockId, mode, preserveIds))
    fun clear() { mutable.value = null }
}

enum class StoryDestination { GENERATE, FLOW }
object StoryNavigation {
    private val channel = Channel<StoryDestination>(Channel.BUFFERED)
    val requests = channel.receiveAsFlow()
    fun request(destination: StoryDestination) { channel.trySend(destination) }
}
