package com.moodtunes.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SharedAudioFormat(
    val displayName: String,
    val isLossless: Boolean,
    val badgeColorHex: Long
) {
    FLAC("FLAC Lossless", true, 0xFF00E676),
    ALAC("ALAC Lossless", true, 0xFF00B0FF),
    WAV("WAV Hi-Res", true, 0xFFFFD600),
    AAC_HQ("AAC HQ", false, 0xFFAA00FF),
    MP3("MP3", false, 0xFF78909C),
    STREAM("HQ Stream", false, 0xFFE91E63)
}
