package com.vidadomu.corte.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectSettings(
    val width: Int = 1920,
    val height: Int = 1080,
    val frameRate: FrameRate = FrameRate.FPS_25,
    val sampleRate: Int = 48_000,
) {
    init {
        require(width in 16..8192 && height in 16..8192) { "invalid resolution ${width}x$height" }
        require(width % 2 == 0 && height % 2 == 0) { "resolution must be even" }
    }

    val aspectRatio: Double get() = width.toDouble() / height
}

@Serializable
enum class ExportResolution(val shortSide: Int, val label: String) {
    P720(720, "720p"), P1080(1080, "1080p"), P1440(1440, "1440p"), P2160(2160, "4K"),
}

@Serializable
enum class VideoCodec(val mime: String, val label: String) {
    H264("video/avc", "H.264"), H265("video/hevc", "H.265"),
}

@Serializable
enum class BitratePreset(val bitsPerPixelFrame: Double) {
    AUTO(0.0), LOW(0.05), MEDIUM(0.09), HIGH(0.15), CUSTOM(0.0),
}

@Serializable
data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.P1080,
    /** Null = project frame rate. */
    val frameRate: FrameRate? = null,
    val codec: VideoCodec = VideoCodec.H264,
    val bitrate: BitratePreset = BitratePreset.AUTO,
    val customBitrateKbps: Int = 20_000,
    val audioBitrateKbps: Int = 192,
)

@Serializable
data class Project(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val id: String,
    val name: String,
    val settings: ProjectSettings = ProjectSettings(),
    val media: List<MediaRef> = emptyList(),
    val timeline: Timeline = Timeline(),
    val export: ExportSettings = ExportSettings(),
    val createdAtMs: Long = 0,
    val modifiedAtMs: Long = 0,
) {
    fun media(id: String): MediaRef? = media.firstOrNull { it.id == id }

    companion object {
        const val CURRENT_SCHEMA = 1
    }
}
