# 🎵 MoodTunes — Mood-Based Android Music Player

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Audio-androidx.media3%20ExoPlayer-FF6F00?style=flat-square)](https://developer.android.com/guide/topics/media/media3)
[![Hilt](https://img.shields.io/badge/DI-Hilt-00897B?style=flat-square)](https://dagger.dev/hilt/)
[![Room](https://img.shields.io/badge/Database-Room%202.7-4285F4?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![Release](https://img.shields.io/badge/Version-v1.0.0--beta-purple?style=flat-square)](https://github.com/cyberlog69/MoodTunes/releases)

---

## 📖 About MoodTunes

**MoodTunes** is an advanced, high-performance Android music streaming and local playback application built with **Jetpack Compose**, **Media3 / ExoPlayer**, and **Android Clean Architecture**. 

Designed to deliver an effortless listening experience, MoodTunes curates music tailored precisely to your current emotional state. Whether you need high-octane tracks for a workout, soothing melodies for sleep, or uplifting beats to brighten your day, MoodTunes seamlessly blends local audio files from your device storage with global, unrestricted online streams.

### 🌟 Key Vision & Design Philosophy
- **Emotion-Driven Curation**: Categorizes music into 6 curated mood profiles using intelligent sentiment and metadata analysis.
- **Audiophile Grade Quality**: Full support for 24-bit/32-bit float audio output, FLAC Lossless, ALAC Lossless, and WAV Hi-Res formats.
- **Unrestricted Global Access**: Built-in dynamic host failover across decentralised Audius protocol nodes and YouTube proxy pools (Piped/Invidious), ensuring zero network blockades across Indian ISPs (Jio, Airtel, Vi, BSNL, ACT) and global networks.
- **Privacy & Security First**: Zero tracking, network security HTTPS enforcement, encrypted shared preferences exclusion, and non-exported background services.

---

## ✨ Features

- **🎭 6 Mood Categories**: Automatically groups tracks into *Happy*, *Sad*, *Energetic*, *Calm*, *Euphoric*, and *Sleep* playlists.
- **🎼 FLAC & ALAC Lossless Support**: Native decoding for FLAC (`.flac`), ALAC (`.m4a`), WAV (`.wav`), AAC, and MP3 formats with 24-bit float high-resolution audio pipeline.
- **🌐 Global & Indian ISP Unrestricted Streaming**: Dual-engine streaming powered by **Audius Protocol** (CC-licensed, royalty-free open protocol) + **YouTube (via Piped/Invidious fast proxy pools)** with dynamic host failover.
- **⚡ Ultra-Low Playback Latency**: Tuned 250ms buffer load control, OkHttp connection pooling, and parallel stream URL resolution for instant playback startup.
- **⚙️ Extensive Customization & Settings**:
  - **Theme Selection**: Switch between *Follow System*, *Always Dark*, and *Always Light* themes.
  - **Material You Dynamic Colors**: Adapts theme accent colors to your Android system wallpaper (Android 12+ / API 31+).
  - **Audio Source Mode**: Toggle between *Local Device Only*, *Online Streams Only*, or *Both Combined*.
  - **Streaming Provider Selection**: Select between *Audius Only*, *YouTube Only*, or *Both Combined*.
  - **Audio Stream Quality**: Choose between *FLAC 24-bit Lossless*, *320 kbps High*, *128 kbps Standard*, or *Adaptive (Auto)*.
  - **Network & Data Controls**: Enforce *Wi-Fi Only Streaming*, *Wi-Fi Only Downloads*, or allow *High Quality over Mobile Data*.
  - **In-App Update Checker**: Directly checks GitHub Releases for new updates with release notes and one-click download.
- **🛡️ Security & Copyright Hardening**:
  - Unexported `MusicPlaybackService` preventing unauthorized 3rd-party app hijacking.
  - HTTPS-only network security config disabling cleartext HTTP vulnerabilities.
  - GitHub domain validation on update downloads and scheme validation on stream resolution.
  - Backup rules excluding user preferences and local database from cloud extraction.
- **🎧 Seamless Background Playback**: `androidx.media3` `MediaSessionService` for background playback, notification controls, and Bluetooth headset button support.
- **📀 Animated Vinyl Disc Player**: Real-time rotating vinyl disc artwork animation synchronized with playback.
- **🔍 Debounced Library Search**: Search local tracks by title, artist, or album with real-time 300ms debouncing.
- **📊 Mood Analytics & Listening Stats**: View 30-day listening history, total session time, and animated vibe statistics.
- **❤️ Favorites Management**: Save and toggle favorite tracks persisted locally via Room database.

---

## 🛠️ Architecture & Tech Stack

Built following **Android Clean Architecture** guidelines and modern Android development best practices:

```
app/src/main/java/com/moodtunes/app/
├── domain/            # Pure Kotlin: Models (Song, MoodType), Repository Contracts & UseCases
├── data/              # MediaStore API, Room Database Entities, DAOs & Preferences Repositories
├── di/                # Hilt Dependency Injection Modules (DatabaseModule, RepositoryModule)
├── service/           # MusicPlaybackService (Media3 MediaSession) & PlaybackManager
└── presentation/      # Jetpack Compose UI Screens, ViewModels, Navigation & Dynamic Design System
```

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.1 |
| **UI Framework** | Jetpack Compose (Compose BOM 2025.01.00) + Material 3 |
| **Audio Engine** | `androidx.media3:media3-exoplayer` & `media3-session` (1.6.1) |
| **Dependency Injection** | Dagger Hilt 2.56.2 |
| **Database & Storage** | Room 2.7.1 (SQLite) & SharedPreferences |
| **Asynchrony** | Kotlin Coroutines 1.10.2 & StateFlow / SharedFlow |
| **Image Loading** | Coil 2.7.0 |
| **Permissions** | Accompanist Permissions 0.37.3 |
| **Build System** | Gradle 8.14+, Android Gradle Plugin 8.10.1, KSP 2.1 |
| **Target Platform** | Android 8.0 (API 26) to Android 15 (API 36) |

---

## 📱 Screens Overview

1. **Permission Screen**: Onboarding screen requesting audio storage permissions with animated bubble graphic.
2. **Home Screen**: Interactive grid featuring 6 animated mood cards, top listening stats header, and persistent mini-player.
3. **Player Screen**: Full-screen audio player with vinyl disc animation, seek bar, play/pause, shuffle, repeat, and favorite toggle.
4. **Library Screen**: Filterable track list with search bar and *All Songs* / *Favorites* tab switching.
5. **Mood History Screen**: Personal analytics dashboard displaying top listening mood, session history, and animated progress bars.
6. **Settings Screen**: Comprehensive preferences panel for theme, dark mode, dynamic colors, streaming providers, audio quality, network constraints, and update checking.

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (Ladybug 2024.2.1 or newer recommended)
- **JDK 21** configured in Android Studio / environment (`JAVA_HOME`)
- Physical Android device or Emulator running **Android 8.0 (API 26)** or higher

### Building & Running

1. **Clone the repository**:
   ```bash
   git clone https://github.com/cyberlog69/MoodTunes.git
   cd MoodTunes
   ```

2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Build Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

---

## 📦 Releases & Downloads

Latest APK downloads and release notes are available on the [GitHub Releases](https://github.com/cyberlog69/MoodTunes/releases) page.

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for details.
