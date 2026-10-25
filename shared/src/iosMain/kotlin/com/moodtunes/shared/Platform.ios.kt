package com.moodtunes.shared

import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val isApple: Boolean = true
}

actual fun getPlatform(): Platform = IOSPlatform()
