package com.moodtunes.app.domain.model

import androidx.compose.ui.graphics.Color

/**
 * The 6 supported moods in MoodTunes, each with its own visual identity
 * and music-matching configuration.
 */
enum class MoodType(
    val displayName: String,
    val emoji: String,
    val gradientStart: Color,
    val gradientEnd: Color,
    val keywords: List<String>,
    val description: String
) {
    HAPPY(
        displayName = "Happy",
        emoji = "😊",
        gradientStart = Color(0xFFFFB300),
        gradientEnd = Color(0xFFFF6D00),
        keywords = listOf(
            "happy", "joy", "fun", "party", "dance", "upbeat", "cheerful",
            "positive", "good", "bright", "sunny", "smile", "pop", "summer"
        ),
        description = "Bright & uplifting"
    ),
    SAD(
        displayName = "Sad",
        emoji = "😢",
        gradientStart = Color(0xFF5C6BC0),
        gradientEnd = Color(0xFF1A237E),
        keywords = listOf(
            "sad", "blues", "lonely", "heartbreak", "melancholy", "emotional",
            "tearful", "sorrow", "loss", "slow", "ballad", "cry", "miss"
        ),
        description = "Deeply emotional"
    ),
    ENERGETIC(
        displayName = "Energetic",
        emoji = "⚡",
        gradientStart = Color(0xFFE53935),
        gradientEnd = Color(0xFFFFB300),
        keywords = listOf(
            "energy", "rock", "workout", "power", "intense", "fire", "fast",
            "pump", "hype", "beast", "strong", "aggressive", "metal", "run"
        ),
        description = "High-octane & powerful"
    ),
    CALM(
        displayName = "Calm",
        emoji = "😌",
        gradientStart = Color(0xFF00897B),
        gradientEnd = Color(0xFF388E3C),
        keywords = listOf(
            "calm", "relax", "chill", "acoustic", "peaceful", "soft", "gentle",
            "quiet", "breeze", "nature", "meditation", "ambient", "lofi", "slow"
        ),
        description = "Peaceful & serene"
    ),
    EUPHORIC(
        displayName = "Euphoric",
        emoji = "🤩",
        gradientStart = Color(0xFF8E24AA),
        gradientEnd = Color(0xFFE91E63),
        keywords = listOf(
            "euphoria", "rave", "edm", "electronic", "dance", "festival", "club",
            "dj", "drop", "synth", "disco", "techno", "house", "trance", "beat"
        ),
        description = "Pure electric euphoria"
    ),
    SLEEP(
        displayName = "Sleep",
        emoji = "😴",
        gradientStart = Color(0xFF1A237E),
        gradientEnd = Color(0xFF0A0A2E),
        keywords = listOf(
            "sleep", "ambient", "lullaby", "night", "dream", "meditation",
            "soothing", "white noise", "rain", "piano", "soft", "quiet", "peace"
        ),
        description = "Drift into dreams"
    );

    /** Returns a text color that is readable on top of this mood's gradient */
    val onMoodColor: Color get() = Color.White
}
