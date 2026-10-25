package com.moodtunes.shared

interface Platform {
    val name: String
    val isApple: Boolean
}

expect fun getPlatform(): Platform
