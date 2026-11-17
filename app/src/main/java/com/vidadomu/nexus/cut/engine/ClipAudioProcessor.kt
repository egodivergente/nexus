package com.vidadomu.nexus.cut.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * Per-clip audio stage: gain (any dB, including boost, with clipping protection), fades,
 * crossfades, volume keyframes and pan. Mono input is upmixed to stereo so that pan works.
 *
 * Media3's own GainProcessor only allows factors in [0, 1] and wraps around on 16-bit overflow,
 * which is why this processor exists.
 *
 * Position: Media3 flushes with the position relative to the start of the edited media item, so
 * timeline time = [segmentStartUs] + position. Gain is evaluated every [BLOCK] frames
 * (0.33 ms at 48 kHz), far below audible zipper noise.
 *
 * Length: Media3 cuts compressed audio at whole packets, so an item usually decodes a few
 * milliseconds more than its place in the montage (up to one AAC packet, 21 ms at 48 kHz), and a
 * sequence simply appends its items: every cut pushed the rest of the lane later (measured on a
 * Pixel 8 Pro with a flash/beep clip: +24 ms after eight cuts, growing with each one). With
 * [itemDurationUs] the item never emits more than its share of the lane (see [itemFrames]): the extra
 * decoded tail is dropped. What is left is a fixed offset below one packet per clip, which does not add
 * up. An item that decodes *less* than its share is not padded (Media3 rounds cuts up, so it has not
 * been seen).
 */
@UnstableApi
class ClipAudioProcessor(
    private val segmentStartUs: Long,
    private val gainAt: (timelineUs: Long) -> Float,
    private val animated: Boolean,
    private val panLeft: Float,
    private val panRight: Float,
    /** What the item lasts in the montage (after speed); [C.TIME_UNSET] = whatever is decoded. */
    private val itemDurationUs: Long = C.TIME_UNSET,
) : BaseAudioProcessor() {
    private var frame = 0L
    private var inChannels = 0
    private var maxFrames = Long.MAX_VALUE
    private val constGain: Float = if (animated) 1f else gainAt(segmentStartUs)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) throw UnhandledAudioFormatException(inputAudioFormat)
        inChannels = inputAudioFormat.channelCount
        maxFrames = if (itemDurationUs == C.TIME_UNSET) Long.MAX_VALUE
        else itemFrames(segmentStartUs, itemDurationUs, inputAudioFormat.sampleRate)
        return if (inChannels == 1) AudioFormat(inputAudioFormat.sampleRate, 2, enc) else inputAudioFormat
    }

    override fun isActive(): Boolean {
        if (!super.isActive()) return false
        // An item with a length to keep always runs: it is what stops the lane from drifting.
        return maxFrames != Long.MAX_VALUE || !identity
    }

    /** Nothing to change in the samples themselves (the length may still have to be kept). */
    private val identity: Boolean
        get() = !animated && constGain == 1f && panLeft == 1f && panRight == 1f && inChannels >= 2

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val inFmt = inputAudioFormat
        val float = inFmt.encoding == C.ENCODING_PCM_FLOAT
        val frames = framesToKeep(inputBuffer.remaining() / inFmt.bytesPerFrame, frame, maxFrames)
        val outCh = outputAudioFormat.channelCount
        val bytesPerSample = if (float) 4 else 2
        val out = replaceOutputBuffer(frames * outCh * bytesPerSample)
        if (identity) {
            // Same samples, bit for bit: only the length is enforced.
            // (Buffer casts: the ByteBuffer overloads of limit/position do not exist before Android 9.)
            val input: Buffer = inputBuffer
            val limit = input.limit()
            input.limit(input.position() + frames * inFmt.bytesPerFrame)
            out.put(inputBuffer)
            input.limit(limit)
            input.position(limit)
            frame += frames
            out.flip()
            return
        }
        val sr = inFmt.sampleRate
        var g = constGain
        for (i in 0 until frames) {
            if (animated && (i % BLOCK == 0)) g = gainAt(segmentStartUs + frame * 1_000_000L / sr)
            if (inChannels == 1) {
                val v = if (float) inputBuffer.getFloat() else inputBuffer.getShort() / 32768f
                write(out, v * g * panLeft, float)
                write(out, v * g * panRight, float)
            } else {
                for (ch in 0 until inChannels) {
                    val v = if (float) inputBuffer.getFloat() else inputBuffer.getShort() / 32768f
                    val p = when (ch) { 0 -> panLeft; 1 -> panRight; else -> 1f }
                    write(out, v * g * p, float)
                }
            }
            frame++
        }
        // Whatever was decoded beyond the item's length is consumed and dropped.
        (inputBuffer as Buffer).position(inputBuffer.limit())
        out.flip()
    }

    private fun write(out: ByteBuffer, v: Float, float: Boolean) {
        val c = v.coerceIn(-1f, 1f)
        if (float) out.putFloat(c) else out.putShort((c * 32767f).toInt().toShort())
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        val sr = inputAudioFormat.sampleRate
        frame = if (streamMetadata.positionOffsetUs == C.TIME_UNSET || sr <= 0) 0 else streamMetadata.positionOffsetUs * sr / 1_000_000L
    }

    override fun onReset() {
        frame = 0
        maxFrames = Long.MAX_VALUE
    }

    private companion object {
        const val BLOCK = 16
    }
}

/**
 * Frames an item that starts at [startUs] in its lane and lasts [durationUs] may emit. Both ends are
 * rounded on the lane's own grid, so consecutive items add up to the frames of the whole span and
 * rounding never accumulates either.
 */
internal fun itemFrames(startUs: Long, durationUs: Long, sampleRate: Int): Long {
    if (durationUs <= 0 || sampleRate <= 0) return 0
    fun frameAt(us: Long) = (us * sampleRate + 500_000L) / 1_000_000L
    return frameAt(startUs + durationUs) - frameAt(startUs)
}

/** How many of [incoming] frames still fit when [produced] of [maxFrames] have been emitted. */
internal fun framesToKeep(incoming: Int, produced: Long, maxFrames: Long): Int =
    if (maxFrames == Long.MAX_VALUE) incoming else (maxFrames - produced).coerceIn(0L, incoming.toLong()).toInt()
