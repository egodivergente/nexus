package com.vidadomu.nexus.cut.engine

/** Remember transport intent across a drag, including cancelled/multitouch gestures. */
internal class PreviewScrubState {
    var active = false
        private set
    private var resume = false
    fun begin(playing: Boolean) {
        if (active) return
        active = true
        resume = playing
    }
    fun finish(): Boolean = (active && resume).also { cancel() }
    fun cancel() { active = false; resume = false }
}
