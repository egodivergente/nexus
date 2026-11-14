package com.vidadomu.nexus.cut.export

import com.vidadomu.nexus.cut.model.BitratePreset
import com.vidadomu.nexus.cut.model.ExportSettings
import com.vidadomu.nexus.cut.model.ProjectSettings
import com.vidadomu.nexus.cut.model.VideoCodec
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Resolved, concrete export parameters. */
data class ExportPlan(
    val width: Int,
    val height: Int,
    /** Null = keep the project/source frame rate. */
    val frameRate: Int?,
    val effectiveFps: Double,
    val videoMime: String,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val durationUs: Long,
) {
    /** Container overhead ~1.5 %. */
    val estimatedBytes: Long get() = ((videoBitrate + audioBitrate).toDouble() / 8 * durationUs / 1e6 * 1.015).roundToLong()
}

/** Pure arithmetic, unit-tested on the JVM. */
object ExportMath {
    /** Container duration alone can hide a shortened video track behind intact audio. */
    fun trackCoversWindow(expectedUs: Long, actualUs: Long, fps: Double): Boolean {
        val validFps = fps.takeIf { it.isFinite() && it > 0.0 } ?: 30.0
        val frameUs = 1_000_000.0 / validFps
        val tolerance = maxOf(100_000L, (frameUs + 50_000).roundToLong())
        return actualUs > 0 && kotlin.math.abs(expectedUs - actualUs) <= tolerance
    }
    fun outputSize(project: ProjectSettings, shortSide: Int): Pair<Int, Int> {
        val pw = project.width
        val ph = project.height
        val scale = shortSide.toDouble() / minOf(pw, ph)
        fun even(v: Double) = ((v / 2).roundToInt() * 2).coerceAtLeast(16)
        return even(pw * scale) to even(ph * scale)
    }

    fun bitsPerPixelFrame(preset: BitratePreset): Double = when (preset) {
        BitratePreset.AUTO -> 0.10
        BitratePreset.CUSTOM -> 0.0
        else -> preset.bitsPerPixelFrame
    }

    fun plan(project: ProjectSettings, settings: ExportSettings, durationUs: Long): ExportPlan {
        val (w, h) = outputSize(project, settings.resolution.shortSide)
        val fpsOverride = settings.frameRate?.fps?.roundToInt()
        val fps = settings.frameRate?.fps ?: project.frameRate.fps
        val codecFactor = if (settings.codec == VideoCodec.H265) 0.6 else 1.0
        val video = if (settings.bitrate == BitratePreset.CUSTOM) settings.customBitrateKbps * 1000
        else (w.toLong() * h * fps * bitsPerPixelFrame(settings.bitrate) * codecFactor).roundToLong().coerceIn(500_000, 150_000_000).toInt()
        return ExportPlan(w, h, fpsOverride, fps, settings.codec.mime, video, settings.audioBitrateKbps * 1000, durationUs)
    }

    fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.2f GB", b / (1024.0 * 1024 * 1024))
        b >= 1L shl 20 -> String.format(java.util.Locale.ROOT, "%.0f MB", b / (1024.0 * 1024))
        else -> String.format(java.util.Locale.ROOT, "%.0f KB", b / 1024.0)
    }
}
