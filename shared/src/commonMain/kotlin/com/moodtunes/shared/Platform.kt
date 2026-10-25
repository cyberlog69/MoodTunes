package com.moodtunes.shared

interface Platform {
    val name: String
    val isApple: Boolean
}

expect fun getPlatform(): Platform

class PlatformHelper {
    val currentPlatform: Platform = getPlatform()
    val platformName: String get() = currentPlatform.name
}

