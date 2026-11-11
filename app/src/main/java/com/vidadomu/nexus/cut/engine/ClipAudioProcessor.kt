package com.vidadomu.nexus.cut.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
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
 */
@UnstableApi
class ClipAudioProcessor(
    private val segmentStartUs: Long,
    private val gainAt: (timelineUs: Long) -> Float,
    private val animated: Boolean,
    private val panLeft: Float,
    private val panRight: Float,
) : BaseAudioProcessor() {
    private var frame = 0L
    private var inChannels = 0
    private val constGain: Float = if (animated) 1f else gainAt(segmentStartUs)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) throw UnhandledAudioFormatException(inputAudioFormat)
        inChannels = inputAudioFormat.channelCount
        return if (inChannels == 1) AudioFormat(inputAudioFormat.sampleRate, 2, enc) else inputAudioFormat
    }

    override fun isActive(): Boolean {
        if (!super.isActive()) return false
        val identity = !animated && constGain == 1f && panLeft == 1f && panRight == 1f && inChannels >= 2
        return !identity
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val inFmt = inputAudioFormat
        val float = inFmt.encoding == C.ENCODING_PCM_FLOAT
        val frames = inputBuffer.remaining() / inFmt.bytesPerFrame
        val outCh = outputAudioFormat.channelCount
        val bytesPerSample = if (float) 4 else 2
        val out = replaceOutputBuffer(frames * outCh * bytesPerSample)
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
    }

    private companion object {
        const val BLOCK = 16
    }
}
