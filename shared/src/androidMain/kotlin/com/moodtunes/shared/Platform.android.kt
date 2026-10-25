package com.moodtunes.shared

class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
    override val isApple: Boolean = false
}

actual fun getPlatform(): Platform = AndroidPlatform()
