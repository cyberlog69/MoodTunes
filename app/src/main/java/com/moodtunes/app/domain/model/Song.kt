package com.moodtunes.app.domain.model

import android.net.Uri

enum class AudioFormat(val displayName: String, val isLossless: Boolean, val badgeColorHex: Long) {
    FLAC("FLAC Lossless", true, 0xFF00E676),
    ALAC("ALAC Lossless", true, 0xFF00B0FF),
    WAV("WAV Hi-Res", true, 0xFFFFD600),
    AAC_HQ("AAC HQ", false, 0xFFAA00FF),
    MP3("MP3", false, 0xFF78909C),
    STREAM("HQ Stream", false, 0xFFE91E63)
}

/**
 * Domain model representing a single audio track.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,           // milliseconds
    val uri: Uri,
    val albumArtUri: Uri?,
    val genre: String? = null,
    val isFavorite: Boolean = false,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val isStream: Boolean = false,
    val isPreview: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0,
    // ── YouTube Music identity ────────────────────────────────────────────
    // Populated by the InnerTube browse/search parsers. Optional so a Song can
    // still be built from a bare URI, but they are what make radio and
    // catalogue navigation work.
    val videoId: String? = null,
    val albumBrowseId: String? = null,
    val artistBrowseId: String? = null,
    val playlistId: String? = null,
    val isExplicit: Boolean = false,
    val year: Int? = null,
    val playCountLong: Long? = null,
    val videoType: String? = null
) {
    /** Formatted duration as mm:ss */
    val formattedDuration: String
        get() {
            if (isStream && duration <= 0) return "Live Stream"
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
