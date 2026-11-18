package com.vidadomu.nexus.cut.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.cut.media.LutStore
import com.vidadomu.nexus.cut.timeline.render.CubeLut
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.CompositionPlayer
import com.vidadomu.nexus.cut.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PreviewLevel(val label: String, val factor: Float) {
    AUTO("Auto", 0f), FULL("Completa", 1f), HALF("1/2", 0.5f), QUARTER("1/4", 0.25f),
}

/**
 * Preview of the timeline. Must be used from the main thread.
 *
 * Two paths, because of a Media3 1.11.1 limitation measured on device (PreviewProbeTest): a
 * multitrack [CompositionPlayer] (MultipleInputVideoGraph) never renders after a paused seek and
 * then stays in BUFFERING, so it cannot even start playing again. It does play correctly when it
 * is prepared at 0 and runs continuously.
 *  - Paused: [still] holds a frame composed by [StillFrameRenderer] (exact frame, same layer
 *    maths as the export).
 *  - Playing: a fresh player gets a composition that *starts at the playhead*
 *    (CompositionFactory fromUs) and plays it from 0. Pausing releases it.
 *  - Pre-roll: preparing that player is what makes Play slow (~1.5 s measured on a Pixel 8 Pro).
 *    Once the editor has been still for [PREROLL_DELAY_MS], the player for the current playhead is
 *    built and prepared *paused at 0* (no seek, so the limitation above does not apply). Play then
 *    only has to call play(). Any edit, seek or surface change makes it stale: it is never used for
 *    another key, and it is released before any other player touches the surface.
 */
@UnstableApi
@androidx.annotation.OptIn(markerClass = [ExperimentalApi::class]) // CompositionPlayer is experimental in Media3 1.11
class PreviewController(private val context: Context, private val scope: CoroutineScope) {
    private val scrub = PreviewScrubState()
    private var player: CompositionPlayer? = null
    private var playOriginUs = 0L
    private var surface: Surface? = null
    private var surfaceSize = Size(0, 0)
    private var pendingRestart: Job? = null
    private var ticker: Job? = null
    private var lastPlayKey: Any? = null
    private var preroll: CompositionPlayer? = null
    private var prerollKey: Any? = null
    private var prerollInfo = ""
    private var prerollJob: Job? = null
    private var prerollFailedKey: Any? = null
    private var prerollStartedMs = 0L
    private var foreground = true
    private var exporting = false
    private var touching = false
    private var playRequestedMs = 0L
    private var playWarm = false
    private val prerollAllowed = !context.getSystemService(ActivityManager::class.java).isLowRamDevice

    private val _positionUs = MutableStateFlow(0L)
    val positionUs: StateFlow<Long> = _positionUs.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _info = MutableStateFlow("")
    /** Human readable preview resolution, e.g. "960×540 (1/2)". */
    val info: StateFlow<String> = _info.asStateFlow()
    private val _buffering = MutableStateFlow(false)
    /** True while playback is starting (the still frame is still on screen). */
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    private val _still = MutableStateFlow<StillFrameRenderer.Result?>(null)
    /** Frame to show while not playing (null: nothing to show yet). */
    val still: StateFlow<StillFrameRenderer.Result?> = _still.asStateFlow()
    private val _showStill = MutableStateFlow(true)
    /** Whether the UI must draw [still] over the player surface. */
    val showStill: StateFlow<Boolean> = _showStill.asStateFlow()

    private var durationUs = 0L
    private var project: Project? = null
    private var isOnline: (String) -> Boolean = { true }
    private var offline: Set<String> = emptySet()
    private var lutJob: Job? = null
    private var level = PreviewLevel.AUTO

    // Still frames: one worker thread owns the renderer; requests are conflated (latest wins).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val stillWorker = Dispatchers.IO.limitedParallelism(1)
    private val renderer = StillFrameRenderer(context)
    private val luts: (String) -> CubeLut? = { LutStore.get(context, it) }
    private class StillRequest(val project: Project, val isOnline: (String) -> Boolean, val timeUs: Long, val w: Int, val h: Int, val exact: Boolean)
    private val stillRequests = MutableStateFlow<StillRequest?>(null)
    private val stillJob = scope.launch(stillWorker) {
        stillRequests.collect { r ->
            if (r == null) return@collect
            val result = renderer.render(r.project, r.isOnline, r.timeUs, r.w, r.h, r.exact, luts)
            withContext(Dispatchers.Main) { if (stillRequests.value === r || !r.exact) _still.value = result }
            // Arrastrando se enseña el fotograma clave más cercano (rápido). Si mientras se pintaba nadie ha
            // pedido otro instante (el dedo se ha parado o va despacio), se afina al fotograma exacto sin
            // esperar a que se levante el dedo.
            if (!r.exact && stillRequests.value === r) {
                val refined = renderer.render(r.project, r.isOnline, r.timeUs, r.w, r.h, true, luts)
                withContext(Dispatchers.Main) { if (stillRequests.value === r) _still.value = refined }
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Deferred: releasing the player from inside its own callback is re-entrant.
            if (playbackState == Player.STATE_ENDED) scope.launch { stopPlayback(atUs = durationUs) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _error.value = "Error de reproducción: ${error.errorCodeName}${error.cause?.message?.let { " ($it)" } ?: ""}"
            scope.launch { stopPlayback(atUs = _positionUs.value) }
        }
    }

    /** Listener of the pre-rolled player while it waits: a failure only drops it (Play falls back to a cold start). */
    private val prerollListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) Log.i(TAG, "pre-roll ready in ${SystemClock.uptimeMillis() - prerollStartedMs} ms")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "pre-roll failed: ${error.errorCodeName}", error)
            val key = prerollKey
            scope.launch { if (prerollKey === key) { prerollFailedKey = key; dropPreroll() } }
        }
    }

    fun attachSurface(s: Surface, width: Int, height: Int) {
        // A pre-rolled player was set up for the old surface/size: never two producers on one surface.
        dropPreroll()
        surface = s
        surfaceSize = Size(width, height)
        player?.setVideoSurface(s, surfaceSize)
        requestStill(exact = true)
        schedulePreroll()
    }

    fun detachSurface(expected: Surface? = null) {
        val s = surface ?: return
        if (expected != null && expected !== s) return
        dropPreroll()
        surface = null
        // CompositionPlayer only accepts clearing the exact output that was set.
        player?.clearVideoSurface(s)
    }

    fun update(project: Project, offline: Set<String>, level: PreviewLevel) {
        val changed = project !== this.project || level != this.level || offline != this.offline
        this.project = project
        if (offline != this.offline) {
            // A new predicate object on every change: the still renderer keys its plan on it.
            this.offline = offline
            isOnline = { it !in offline }
        }
        this.level = level
        durationUs = project.timeline.durationUs
        if (!changed) return
        preloadLuts(project)
        if (_playing.value) {
            // Keep playing the edited timeline from where we are (debounced: drags emit a lot).
            if (playKey(project) == lastPlayKey) return
            pendingRestart?.cancel()
            pendingRestart = scope.launch { delay(250); if (_playing.value) startPlayback(currentPosition()) }
        } else {
            if (_positionUs.value > durationUs) _positionUs.value = durationUs
            requestStill(exact = true)
            schedulePreroll()
        }
    }

    /**
     * LUT files are read on IO before they are needed; playback (built on the main thread) only
     * looks at the cache, and the frame is redrawn once a LUT arrives.
     */
    private fun preloadLuts(p: Project) {
        val missing = p.timeline.allTracks.asSequence().flatMap { it.clips }.mapNotNull { it.video.color.lutUri }.distinct()
            .filter { LutStore.peek(it) == null && LutStore.errorOf(it) == null }.toList()
        if (missing.isEmpty()) return
        lutJob?.cancel()
        lutJob = scope.launch {
            withContext(Dispatchers.IO) { missing.forEach { LutStore.load(context, it) } }
            if (_playing.value) startPlayback(currentPosition()) else {
                requestStill(exact = true)
                // Same key, different picture: a player pre-rolled before the LUT arrived would play without it.
                dropPreroll()
                schedulePreroll()
            }
        }
    }

    private fun previewSize(p: Project): Pair<Int, Int> {
        val pw = p.settings.width
        val ph = p.settings.height
        val factor = when (level) {
            PreviewLevel.AUTO -> {
                val am = context.getSystemService(ActivityManager::class.java)
                val cap = if (am.isLowRamDevice || am.memoryClass < 256) 960f else 1280f
                val surfaceCap = if (surfaceSize.width > 0) maxOf(surfaceSize.width, surfaceSize.height).toFloat() else cap
                minOf(1f, minOf(cap, surfaceCap) / maxOf(pw, ph))
            }
            else -> level.factor
        }
        fun even(v: Float) = (v.toInt() / 2 * 2).coerceAtLeast(16)
        return even(pw * factor) to even(ph * factor)
    }

    private fun requestStill(exact: Boolean) {
        val p = project ?: return
        if (p.timeline.durationUs <= 0) { _still.value = null; return }
        val (w, h) = previewSize(p)
        stillRequests.value = StillRequest(p, isOnline, _positionUs.value.coerceIn(0, maxOf(0, durationUs - 1)), w, h, exact)
    }

    fun togglePlay() {
        if (_playing.value) pause() else play()
    }

    fun play() {
        val p = project ?: return
        if (p.timeline.durationUs <= 0 || _playing.value) return
        val from = playFrom()
        _positionUs.value = from
        _playing.value = true
        playRequestedMs = SystemClock.uptimeMillis()
        val warm = takePreroll(p, from)
        playWarm = warm != null
        if (warm != null) startPrerolled(warm, p, from) else startPlayback(from)
    }

    private fun playFrom(): Long = if (_positionUs.value >= durationUs - 20_000) 0L else _positionUs.value

    private fun playKey(p: Project) = listOf(p.timeline, p.media, p.settings, level, offline)

    private fun prerollKeyFor(p: Project, fromUs: Long): List<Any?> {
        val (w, h) = previewSize(p)
        return listOf(playKey(p), fromUs, w, h, surface, surfaceSize)
    }

    /**
     * Prepares, paused, the player Play would build right now. Debounced: while the user keeps
     * scrubbing or editing nothing is built (and the stale one is released only when this fires).
     * Building and releasing cost the main thread (up to ~70 and ~160 ms measured on a Pixel 8 Pro):
     * never while a finger is on the editor ([setTouching]), so a drag or a pinch is not interrupted.
     */
    private fun schedulePreroll() {
        prerollJob?.cancel()
        prerollJob = null
        if (!prerollAllowed || !foreground || exporting || touching || _playing.value || surface == null || scrub.active) return
        val p = project ?: return
        if (p.timeline.durationUs <= 0) return
        prerollJob = scope.launch {
            delay(PREROLL_DELAY_MS)
            prerollJob = null
            buildPreroll()
        }
    }

    private fun buildPreroll() {
        val p = project ?: return
        val s = surface ?: return
        if (_playing.value || scrub.active || touching || !foreground || exporting || p.timeline.durationUs <= 0) return
        val from = playFrom()
        val key = prerollKeyFor(p, from)
        if (preroll != null && key == prerollKey) return
        dropPreroll()
        if (key == prerollFailedKey) return
        val (w, h) = previewSize(p)
        val built = try {
            CompositionFactory.build(p, isOnline, w, h, null, from) { LutStore.peek(it) }
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) {
            null
        } ?: return
        prerollStartedMs = SystemClock.uptimeMillis()
        val pl = CompositionPlayer.Builder(context)
            .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
            .build()
        pl.addListener(prerollListener)
        pl.setVideoSurface(s, surfaceSize)
        pl.setComposition(built.composition)
        pl.prepare()
        preroll = pl
        prerollKey = key
        Log.i(TAG, "pre-roll built on the main thread in ${SystemClock.uptimeMillis() - prerollStartedMs} ms")
        val label = if (level == PreviewLevel.AUTO) "Auto" else level.label
        prerollInfo = "${w}×$h ($label) · ${built.videoSequences}V/${built.audioSequences}A"
    }

    /** The pre-rolled player if it was built for exactly this playhead and content; otherwise it is released. */
    private fun takePreroll(p: Project, fromUs: Long): CompositionPlayer? {
        prerollJob?.cancel()
        prerollJob = null
        val pl = preroll ?: return null
        val usable = prerollKey == prerollKeyFor(p, fromUs) && pl.playerError == null &&
            pl.playbackState != Player.STATE_IDLE && pl.playbackState != Player.STATE_ENDED
        if (!usable) { dropPreroll(); return null }
        preroll = null
        prerollKey = null
        pl.removeListener(prerollListener)
        return pl
    }

    private fun startPrerolled(pl: CompositionPlayer, p: Project, fromUs: Long) {
        releasePlayer()
        _error.value = null
        lastPlayKey = playKey(p)
        playOriginUs = fromUs
        _positionUs.value = fromUs
        _buffering.value = true
        pl.addListener(listener)
        player = pl
        pl.play()
        // Follow the clock now (harmless if onIsPlayingChanged restarts it): the ticker waits for real progress.
        startTicker()
        _info.value = prerollInfo
    }

    private fun dropPreroll() {
        prerollJob?.cancel()
        prerollJob = null
        val pl = preroll ?: return
        preroll = null
        prerollKey = null
        pl.removeListener(prerollListener)
        val t0 = SystemClock.uptimeMillis()
        pl.release()
        Log.i(TAG, "pre-roll released in ${SystemClock.uptimeMillis() - t0} ms")
    }

    /** Leaving the screen frees the paused pre-rolled decoders; coming back prepares them again. */
    fun setForeground(value: Boolean) {
        foreground = value
        // A failure while away (decoders taken by another app) must not block this playhead for good.
        if (value) { prerollFailedKey = null; schedulePreroll() } else dropPreroll()
    }

    /** An export needs the hardware codecs: no paused decoders are kept waiting meanwhile. */
    fun setExporting(value: Boolean) {
        if (value == exporting) return
        exporting = value
        if (value) dropPreroll() else { prerollFailedKey = null; schedulePreroll() }
    }

    /**
     * A finger is on the editor (any gesture: drag, trim, pinch, scroll, long press). No standby player is
     * built meanwhile, and the stale one is not swapped; the quiet time starts again when the last finger
     * lifts. A standby player can still be released under the finger when it can no longer be used: it
     * failed while waiting, the surface changed, a LUT finished loading, an export started or the app left
     * the foreground.
     */
    fun setTouching(value: Boolean) {
        if (value == touching) return
        touching = value
        if (value) { prerollJob?.cancel(); prerollJob = null } else schedulePreroll()
    }

    private fun startPlayback(fromUs: Long) {
        val p = project ?: return
        val (w, h) = previewSize(p)
        val built = try {
            CompositionFactory.build(p, isOnline, w, h, null, fromUs) { LutStore.peek(it) }
        } catch (e: IllegalArgumentException) {
            _error.value = "No se puede construir la previsualización: ${e.message}"; stopPlayback(fromUs); return
        } catch (e: IllegalStateException) {
            _error.value = "No se puede construir la previsualización: ${e.message}"; stopPlayback(fromUs); return
        }
        if (built == null) { stopPlayback(fromUs); return }
        releasePlayer()
        dropPreroll()
        _error.value = null
        lastPlayKey = playKey(p)
        playOriginUs = fromUs
        _positionUs.value = fromUs
        _buffering.value = true
        val pl = CompositionPlayer.Builder(context)
            .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
            .build()
        pl.addListener(listener)
        surface?.let { pl.setVideoSurface(it, surfaceSize) }
        pl.setComposition(built.composition)
        pl.prepare()
        pl.play()
        player = pl
        val label = if (level == PreviewLevel.AUTO) "Auto" else level.label
        _info.value = "${w}×$h ($label) · ${built.videoSequences}V/${built.audioSequences}A"
    }

    fun pause() {
        scrub.cancel()
        if (!_playing.value) return
        stopPlayback(atUs = currentPosition())
    }

    private fun stopPlayback(atUs: Long) {
        pendingRestart?.cancel()
        ticker?.cancel()
        releasePlayer()
        _playing.value = false
        _buffering.value = false
        val rate = project?.settings?.frameRate
        val snapped = rate?.timeOf(rate.frameAt(atUs)) ?: atUs
        _positionUs.value = snapped.coerceIn(0, maxOf(0, durationUs))
        requestStill(exact = true)
        _showStill.value = true
        schedulePreroll()
    }

    private fun currentPosition(): Long = player?.let { playOriginUs + it.currentPosition * 1000 } ?: _positionUs.value

    /**
     * Posición al instante, leída del reloj del reproductor (hilo principal). La línea de tiempo la usa al
     * dibujar cada fotograma de pantalla para avanzar suave a 120 Hz, aunque [positionUs] se publique a 30 Hz.
     */
    fun livePositionUs(): Long = if (_playing.value && player != null && !_buffering.value) currentPosition().coerceIn(0, maxOf(0, durationUs)) else _positionUs.value

    /** Moves the playhead. While scrubbing, sync frames are shown (fast); otherwise the exact frame. */
    fun seekTo(us: Long, scrubbing: Boolean = false) {
        if (scrubbing) scrub.begin(_playing.value)
        val resume = !scrubbing && _playing.value
        if (_playing.value) stopPlayback(us)
        val rate = project?.settings?.frameRate
        val t = us.coerceIn(0, maxOf(0, durationUs))
        _positionUs.value = if (scrubbing || rate == null) t else rate.timeOf(rate.frameAt(t))
        requestStill(exact = !scrubbing)
        if (resume) play() else schedulePreroll()
    }

    fun endScrub() {
        val resume = scrub.finish()
        seekTo(_positionUs.value)
        if (resume) play()
    }

    /** Sets the playhead without rendering (used while the timeline has no media). */
    fun setPositionOnly(us: Long) {
        _positionUs.value = us.coerceAtLeast(0)
    }

    fun clearError() {
        _error.value = null
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val pl = player ?: break
                _positionUs.value = playOriginUs + pl.currentPosition * 1000
                // Hide the still once the player has really advanced (its frames are on screen).
                if (_showStill.value && pl.currentPosition > 60) {
                    _showStill.value = false; _buffering.value = false
                    Log.i(TAG, "play (${if (playWarm) "pre-rolled" else "cold"}) moving after ${SystemClock.uptimeMillis() - playRequestedMs} ms")
                }
                delay(33)
            }
        }
    }

    private fun releasePlayer() {
        val pl = player ?: return
        player = null
        pl.removeListener(listener)
        pl.release()
    }

    fun release() {
        scrub.cancel()
        pendingRestart?.cancel()
        ticker?.cancel()
        dropPreroll()
        releasePlayer()
        stillJob.cancel()
        // Not on [scope]: the composable's scope is being cancelled right now and would skip this,
        // leaking the native retrievers. Same single worker, so it runs after any in-flight render.
        CoroutineScope(stillWorker + NonCancellable).launch { renderer.close() }
    }

    private companion object {
        const val TAG = "NexusCutPreview"
        /** Quiet time before pre-rolling: long enough not to build players during a drag or a run of taps. */
        const val PREROLL_DELAY_MS = 450L
    }
}
