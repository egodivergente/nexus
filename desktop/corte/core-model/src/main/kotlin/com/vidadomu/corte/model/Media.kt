package com.vidadomu.corte.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind { VIDEO, AUDIO, IMAGE }

/**
 * A reference to an original file. The original is never modified or copied; [uri] is a persisted
 * SAF/MediaStore content URI. Everything else is metadata probed at import (re-derivable).
 */
@Serializable
data class MediaRef(
    val id: String,
    val uri: String,
    val displayName: String,
    val kind: MediaKind,
    /** Null for still images (unbounded source). */
    val durationUs: Long?,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    /** Nominal frame rate reported by the container, if any. */
    val frameRate: Double? = null,
    val variableFrameRate: Boolean = false,
    val videoMime: String? = null,
    val audioMime: String? = null,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val sizeBytes: Long = -1,
    /** Used to check that a relinked file is plausibly the same media. */
    val fingerprint: String = "",
) {
    /** Display size after applying rotation metadata. */
    val displayWidth: Int get() = if (rotationDegrees % 180 != 0) height else width
    val displayHeight: Int get() = if (rotationDegrees % 180 != 0) width else height
}
