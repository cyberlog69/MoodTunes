package com.moodtunes.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SharedMoodType(
    val displayName: String,
    val emoji: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long,
    val keywords: List<String>,
    val description: String
) {
    HAPPY(
        displayName = "Happy",
        emoji = "😊",
        gradientStartHex = 0xFFFFB300,
        gradientEndHex = 0xFFFF6D00,
        keywords = listOf(
            "happy", "joy", "fun", "party", "dance", "upbeat", "cheerful",
            "positive", "good", "bright", "sunny", "smile", "pop", "summer"
        ),
        description = "Bright & uplifting"
    ),
    SAD(
        displayName = "Sad",
        emoji = "😢",
        gradientStartHex = 0xFF5C6BC0,
        gradientEndHex = 0xFF1A237E,
        keywords = listOf(
            "sad", "blues", "lonely", "heartbreak", "melancholy", "emotional",
            "tearful", "sorrow", "loss", "slow", "ballad", "cry", "miss"
        ),
        description = "Deeply emotional"
    ),
    ENERGETIC(
        displayName = "Energetic",
        emoji = "⚡",
        gradientStartHex = 0xFFE53935,
        gradientEndHex = 0xFFFFB300,
        keywords = listOf(
            "energy", "rock", "workout", "power", "intense", "fire", "fast",
            "pump", "hype", "beast", "strong", "aggressive", "metal", "run"
        ),
        description = "High-octane & powerful"
    ),
    CALM(
        displayName = "Calm",
        emoji = "😌",
        gradientStartHex = 0xFF00897B,
        gradientEndHex = 0xFF388E3C,
        keywords = listOf(
            "calm", "relax", "chill", "acoustic", "peaceful", "soft", "gentle",
            "quiet", "breeze", "nature", "meditation", "ambient", "lofi", "slow"
        ),
        description = "Peaceful & serene"
    ),
    EUPHORIC(
        displayName = "Euphoric",
        emoji = "🤩",
        gradientStartHex = 0xFF8E24AA,
        gradientEndHex = 0xFFE91E63,
        keywords = listOf(
            "euphoria", "rave", "edm", "electronic", "dance", "festival", "club",
            "dj", "drop", "synth", "disco", "techno", "house", "trance", "beat"
        ),
        description = "Pure electric euphoria"
    ),
    SLEEP(
        displayName = "Sleep",
        emoji = "😴",
        gradientStartHex = 0xFF1A237E,
        gradientEndHex = 0xFF0A0A2E,
        keywords = listOf(
            "sleep", "ambient", "lullaby", "night", "dream", "meditation",
            "soothing", "white noise", "rain", "piano", "soft", "quiet", "peace"
        ),
        description = "Drift into dreams"
    );

    val onMoodColorHex: Long get() = 0xFFFFFFFF
}
