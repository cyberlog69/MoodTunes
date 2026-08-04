# 🎵 MoodTunes — Mood-Based Android Music Player

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Audio-androidx.media3%20ExoPlayer-FF6F00?style=flat-square)](https://developer.android.com/guide/topics/media/media3)
[![Hilt](https://img.shields.io/badge/DI-Hilt-00897B?style=flat-square)](https://dagger.dev/hilt/)
[![Room](https://img.shields.io/badge/Database-Room%202.7-4285F4?style=flat-square)](https://developer.android.com/training/data-storage/room)

**MoodTunes** is a modern, high-performance Android music player built with **Jetpack Compose**, **Media3 / ExoPlayer**, and **Clean Architecture**. It automatically categorizes and curates local audio tracks into mood-based playlists tailored to how you are feeling.

---

## ✨ Features

- **🎭 6 Mood Categories**: Automatically groups songs into *Happy*, *Sad*, *Energetic*, *Calm*, *Euphoric*, and *Sleep* playlists using metadata sentiment analysis.
- **🎼 FLAC & ALAC Lossless Support**: Native decoding for FLAC (`.flac`), ALAC (`.m4a`), WAV (`.wav`), AAC, and MP3 audio formats with 24-bit/32-bit float audiophile-grade audio output.
- **📻 High-Quality Music Streaming**: Built-in HTTP/HTTPS/HLS streaming data source with load controls for smooth online audio streaming.
- **🌐 Global & Indian ISP Unrestricted Streaming**: Dual-engine streaming powered by **Audius Protocol** + **YouTube (via Piped/Invidious proxy pools)** with dynamic host failover to bypass ISP throttling on Airtel, Jio, Vi, BSNL, and ACT.
- **🏷️ Audio Quality Badges**: Visual badges (`FLAC Lossless`, `ALAC Lossless`, `WAV Hi-Res`, `HQ Stream`) displayed on Player, MiniPlayer, and Track list.
- **🎨 Dynamic Material You Theme**: Adapts system accent colors on Android 12+ (API 31+) alongside mood-specific gradient palettes.
- **🎧 Seamless Background Playback**: Built on `androidx.media3` `MediaSessionService` for continuous playback, lock-screen controls, and Bluetooth headset button support.
- **📀 Animated Spinning Vinyl Player**: Visual vinyl disc artwork animation synchronized with playback status.
- **🔍 Debounced Library Search**: Search through local tracks by title, artist, or album with real-time 300ms debounced queries.
- **📊 Mood Analytics & Listening Stats**: View 30-day listening history, total session time, and animated vibe statistics.
- **❤️ Favorites Management**: Save and toggle favorite tracks persisted locally via Room database.

---

## 🛠️ Architecture & Tech Stack

Built following **Android Clean Architecture** guidelines and modern Android development best practices:

```
app/src/main/java/com/moodtunes/app/
├── domain/            # Pure Kotlin: Models (Song, MoodType), Repository Contracts & UseCases
├── data/              # MediaStore API, Room Database Entities, DAOs & Repositories
├── di/                # Hilt Dependency Injection Modules (DatabaseModule, RepositoryModule)
├── service/           # MusicPlaybackService (Media3 MediaSession) & PlaybackManager
└── presentation/      # Jetpack Compose UI Screens, ViewModels, Navigation & Design System
```

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.1 |
| **UI Framework** | Jetpack Compose (Compose BOM 2025.01.00) + Material 3 |
| **Audio Engine** | `androidx.media3:media3-exoplayer` & `media3-session` (1.6.1) |
| **Dependency Injection** | Dagger Hilt 2.56.2 |
| **Database** | Room 2.7.1 (SQLite) |
| **Asynchrony** | Kotlin Coroutines 1.10.2 & StateFlow / SharedFlow |
| **Image Loading** | Coil 2.7.0 |
| **Permissions** | Accompanist Permissions 0.37.3 |
| **Build System** | Gradle 8.14+ / 9.6+, Android Gradle Plugin 8.10.1, KSP 2.1 |
| **JDK Compatibility** | Java 21 / JVM 21 (`targetSdk = 36`) |

---

## 📱 Screens Overview

1. **Permission Screen**: Onboarding screen requesting audio storage permissions.
2. **Home Screen**: Interactive grid featuring 6 animated mood cards, top listening stats header, and persistent mini-player.
3. **Player Screen**: Full-screen audio player with vinyl disc animation, seek bar, play/pause, shuffle, repeat, and favorite toggle.
4. **Library Screen**: Filterable track list with search bar and *All Songs* / *Favorites* tab switching.
5. **Mood History Screen**: Personal analytics dashboard displaying top listening mood, session history, and animated progress bars.

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (Ladybug 2024.2.1 or newer recommended)
- **JDK 21** configured in Android Studio Gradle settings
- Physical Android device or Emulator running **Android 8.0 (API 26)** or higher

### Building & Running

1. **Clone the repository**:
   ```bash
   git clone https://github.com/<your-username>/MoodTunes.git
   cd MoodTunes
   ```

2. **Open in Android Studio**:
   - Open Android Studio -> **File -> Open** -> Select `MoodTunes` root folder.
   - Wait for Gradle sync to complete.

3. **Deploy to Device**:
   - Connect an Android device or start an emulator.
   - Click **Run ▶** (`Shift + F10`).

> [!NOTE]
> Ensure your test device or emulator has audio files (e.g. `.mp3`) saved in the `/sdcard/Music/` directory to display songs in the player. You can push audio files to an emulator via:
> ```bash
> adb push sample_song.mp3 /sdcard/Music/
> ```

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.
