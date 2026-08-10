# 🎵 MoodTunes — Mood-Based Android Music Player

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Audio-androidx.media3%20ExoPlayer-FF6F00?style=flat-square)](https://developer.android.com/guide/topics/media/media3)
[![Hilt](https://img.shields.io/badge/DI-Hilt-00897B?style=flat-square)](https://dagger.dev/hilt/)
[![Room](https://img.shields.io/badge/Database-Room%202.7-4285F4?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![Release](https://img.shields.io/badge/Version-v1.0.7-blue?style=flat-square)](https://github.com/cyberlog69/MoodTunes/releases)

---

## 📖 About MoodTunes

**MoodTunes** is an advanced, high-performance Android music streaming and local playback application built with **Jetpack Compose**, **Media3 / ExoPlayer**, and **Android Clean Architecture**. 

Designed to deliver an effortless listening experience, MoodTunes curates music tailored precisely to your current emotional state. Whether you need high-octane tracks for a workout, soothing melodies for sleep, or uplifting beats to brighten your day, MoodTunes seamlessly blends local audio files from your device storage with global, unrestricted online streams.

### 🌟 Key Vision & Design Philosophy
- **Emotion-Driven Curation**: Categorizes music into 6 curated mood profiles using intelligent sentiment and metadata analysis.
- **Audiophile Grade Quality**: Full support for 24-bit/32-bit float audio output, FLAC Lossless, ALAC Lossless, and WAV Hi-Res formats.
- **Unrestricted Global Access**: Multi-service streaming aggregator across **Audius Protocol**, **Jamendo (320 kbps)**, **Internet Archive**, **Global Radio Browser (live 24/7)**, **iTunes Previews (30s)**, and **Deezer Charts & Previews** — ensuring zero network blockades and rock-solid reliability.
- **Reliability First**: 512 MB LRU streaming cache with disk fallback, crash recovery guardian with automatic database backup/restore, and thread-safe playback pipeline.
- **Privacy & Security First**: Zero tracking, network security HTTPS enforcement, encrypted shared preferences exclusion, and non-exported background services.

---

## ✨ Features

- **🎭 6 Mood Categories**: Automatically groups tracks into *Happy*, *Sad*, *Energetic*, *Calm*, *Euphoric*, and *Sleep* profiles.
- **🎼 FLAC & ALAC Lossless Support**: Native decoding for FLAC (`.flac`), ALAC (`.m4a`), WAV (`.wav`), AAC, and MP3 formats with 24-bit float high-resolution audio pipeline.
- **📀 Animated Vinyl Disc Player**: Full-screen player with real-time rotating vinyl disc artwork, seek bar, play/pause, shuffle, repeat, queue, and favorite toggle.
- **📚 Songs Hub with Local & Online Tabs**: Spotify-style hub separating *Local* device tracks from curated *Online* streams, plus **Favorites** and **Playlists** tabs.
- **🌐 Multi-Service Online Streaming**: Aggregates global CC-licensed content and previews from **Audius Protocol**, **Jamendo**, **Internet Archive**, **Global Radio Browser**, **iTunes Search API**, and **Deezer API** with metadata enrichment from **MusicBrainz**.
- **🎤 Spotify-Style Real-Time Synced Lyrics**:
  - Live synced lyrics preview card directly inside the full-screen player.
  - Expanded full-screen view featuring dynamic mood ambient gradients and auto-scrolling synced lines.
  - Active line spring-scaling highlight, dimmed context lines, and **Tap-to-Seek** (click any line to jump playback directly to that timestamp).
  - Integrated bottom mini-playback bar and one-tap lyrics sharing.
- **🗣️ Music Language Selection**: Multi-select language filters (Hindi, English, Punjabi, Tamil, Telugu, Spanish, K-Pop, J-Pop, Instrumental) driving stream recommendations.
- **📁 Full Playlist Management**: Create, rename, delete, add/remove songs, and reorder tracks within playlists, persisted in Room.
- **⚡ Ultra-Low Playback Latency**: Tuned 250ms buffer load control, OkHttp connection pooling, and parallel stream URL resolution for instant playback startup.
- **🔁 Smooth Crossfade**: Configurable crossfade between tracks (0.5s–3.0s).
- **🎚️ Equalizer & Bass Boost**: Real-time AudioFX effects bound to the player's audio session via a system audio session.
- **⚙️ Extensive Customization & Settings**:
  - **Theme Selection**: Switch between *Follow System*, *Always Dark*, and *Always Light* themes.
  - **Material You Dynamic Colors**: Adapts theme accent colors to your Android system wallpaper (Android 12+ / API 31+).
  - **Audio Source Mode**: Toggle between *Local Device Only*, *Online Streams Only*, or *Both Combined*.
  - **Streaming Provider Selection**: Select between *All Combined*, *Audius Only*, *Jamendo Only*, *Internet Archive*, *Global Radio*, or *iTunes & Deezer Previews*.
  - **Audio Stream Quality**: Choose between *FLAC 24-bit Lossless*, *320 kbps High*, *128 kbps Standard*, or *Adaptive (Auto)*.
  - **Network & Data Controls**: Enforce *Wi-Fi Only Streaming*, *Wi-Fi Only Downloads*, or allow *High Quality over Mobile Data*.
  - **In-App Update Engine**: Checks GitHub Releases for new updates, downloads the APK in-app, launches the package installer, and shows a post-update changelog.
- **🛡️ Security, Reliability & Copyright Hardening**:
  - Unexported `MusicPlaybackService` preventing unauthorized 3rd-party app hijacking.
  - HTTPS-only network security config disabling cleartext HTTP vulnerabilities.
  - GitHub domain validation on update downloads and scheme validation on stream resolution.
  - 512 MB LRU stream cache served from disk when connectivity drops.
  - Crash recovery guardian that detects unexpected exits and restores the database from an automatic backup.
- **🎧 Seamless Background Playback**: `androidx.media3` `MediaSessionService` for background playback, notification controls, and Bluetooth headset button support.
- **🔍 Debounced Library Search**: Search tracks by title, artist, or album with real-time 300ms debouncing.
- **📊 Mood Analytics & Listening Stats**: View 30-day listening history, total session time, top mood, and animated vibe statistics.
- **❤️ Favorites Management**: Save and toggle favorite tracks persisted locally via Room database.
- **🎛️ Media Widget & Quick Settings Tile**: Home-screen playback widget, pause/play quick settings tile, app shortcuts per mood, and Android Auto / Automotive media support.

---

## 🛠️ Architecture & Tech Stack

Built following **Android Clean Architecture** guidelines and modern Android development best practices:

```
app/src/main/java/com/moodtunes/app/
├── domain/            # Pure Kotlin: Models (Song, MoodType, LyricsLine), Repository Contracts & UseCases
├── data/              # MediaStore API, Room Entities/DAOs, Backup/Guardian, Preferences & Remote Repositories
├── di/                # Hilt Dependency Injection Modules (DatabaseModule, RepositoryModule)
├── service/           # MusicPlaybackService (Media3 MediaSession), PlaybackManager, AudioEffectsManager
├── platform/          # Media3 MediaSession callbacks & MediaItem factory
└── presentation/      # Jetpack Compose UI Screens, ViewModels, Navigation & Dynamic Design System
```

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.1 |
| **UI Framework** | Jetpack Compose (Compose BOM 2025.01.00) + Material 3 |
| **Audio Engine** | `androidx.media3:media3-exoplayer` & `media3-session` (1.6.1) |
| **Dependency Injection** | Dagger Hilt 2.56.2 |
| **Database & Storage** | Room 2.7.1 (SQLite), SharedPreferences, JSON serialization |
| **Asynchrony** | Kotlin Coroutines 1.10.2 & StateFlow / SharedFlow |
| **Image Loading** | Coil 2.7.0 |
| **Permissions** | Accompanist Permissions 0.37.3 |
| **Build System** | Gradle 8.14+, Android Gradle Plugin 8.10.1, KSP 2.1, AGP Signing v2/v3/v4 + zipalign |
| **Target Platform** | Android 8.0 (API 26) to Android 15 (API 36) |

---

## 📱 Screens Overview

1. **Permission Screen**: Onboarding screen requesting audio storage permissions with animated bubble graphic.
2. **Home Screen**: Interactive grid featuring 6 animated mood cards, top listening stats header, and persistent mini-player.
3. **Songs Hub**: Bottom-navigation hub with *Local* / *Online* / *Favorites* / *Playlists* tabs, language-filtered streaming sections, live radio, and trending mixes.
4. **Player Screen**: Full-screen audio player with rotating vinyl disc animation, seek bar, play/pause, shuffle, repeat, queue, favorite toggle, and lyrics sheet.
5. **Playlist Detail Screen**: Reorder, rename, and manage songs inside a playlist.
6. **Mood History Screen**: Personal analytics dashboard displaying top listening mood, session history, and animated progress bars.
7. **Settings Screen**: Comprehensive preferences panel for theme, dynamic colors, crossfade, equalizer, streaming providers, audio quality, network constraints, language selection, and in-app updates.

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
