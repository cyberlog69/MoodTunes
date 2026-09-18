# 🎵 MoodTunes — Mood-Based Android Music Player

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Audio-androidx.media3%20ExoPlayer-FF6F00?style=flat-square)](https://developer.android.com/guide/topics/media/media3)
[![Hilt](https://img.shields.io/badge/DI-Hilt-00897B?style=flat-square)](https://dagger.dev/hilt/)
[![Room](https://img.shields.io/badge/Database-Room%202.7-4285F4?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![Release](https://img.shields.io/badge/Version-v1.5.0-blue?style=flat-square)](https://github.com/cyberlog69/MoodTunes/releases)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

---

## 📖 About MoodTunes

**MoodTunes** is a modern, high-performance, open-source Android music streaming and local playback application built with **Jetpack Compose**, **Media3 / ExoPlayer**, and **Android Clean Architecture**.

MoodTunes curates music tailored precisely to your emotional state. Whether you crave high-octane rhythms for an intense workout, tranquil melodies for deep focus or sleep, or euphoric beats to lift your spirit, MoodTunes seamlessly blends your local high-resolution audio library with full-length online streams powered by the **YouTube Music (InnerTube) Engine** and self-hosted **Navidrome / Subsonic** servers.

### 🌟 Key Vision & Design Philosophy
- **Emotion-Driven Curation**: Intelligently categorizes tracks into 6 emotional mood profiles using sentiment algorithms and genre classification.
- **YouTube Music Streaming Engine**: Built with an unthrottled InnerTube client, providing access to full-length tracks, official audio streams (Opus 160 kbps / AAC 130 kbps), automated radio station generation, and global charts.
- **Offline Song Downloads**: Download streamed tracks into standard Android MediaStore storage (`Music/MoodTunes/`) with offline artwork caching and local Room DB indexing for true zero-network playback.
- **Bot-Detection Immunity**: Implements hardened `VISIONOS` client context and automated `visitorData` acquisition & caching to bypass YouTube bot blocks and playback restrictions.
- **Audiophile Grade Fidelity**: Native decoding of 24-bit/32-bit float audio, FLAC Lossless, ALAC (`.m4a`), WAV Hi-Res, AAC, and MP3 formats.
- **Live FFT Audio Visualizers**: 60 FPS real-time audio visualization using Android Visualizer FFT data rendered on dynamic Compose Canvases (Neon Bars, Pulse Aura, Cosmic Particles).
- **Acoustic Reverb & 3D Spatial Audio**: Headphone-optimized Virtualizer and 6 environmental reverb presets (Small Room, Medium Room, Large Room, Medium Hall, Large Hall, Plate Reverb) with adjustable room reflection decay.
- **On-Device ID3 Tag & Artwork Editor**: Full-featured tag editor supporting metadata edits (Title, Artist, Album, Year, Genre, Track Number) and embedded album art injection directly into local audio files.
- **Privacy & Security First**: Zero tracking, HTTPS-only network transport, unexported services preventing third-party hijacking, and secure local preference encryption.

---

## ✨ Features

### 🎧 Playback & Streaming
- **⬇️ Offline Song Downloads**:
  - Download full-length streamed songs (YouTube Music / Subsonic) directly to standard device storage (`Music/MoodTunes/`).
  - Scoped storage compatible (Android 10+ MediaStore API with legacy fallback).
  - Real-time Android notification channel (`moodtunes_download_channel`) with percentage progress.
  - Offline thumbnail/artwork caching into internal storage (`custom_artwork/`).
  - Instant Room DB indexing (`isStream = false`) for zero-buffer offline playback.
  - Dedicated download actions in PlayerScreen and SongActionBottomSheet.
- **🎶 YouTube Music (InnerTube) Engine**:
  - **100% Full-Length Songs**: Unrestricted, full-length music streaming with zero 30-second preview limitations.
  - **High-Bitrate Direct Audio**: Crystal-clear Opus (~160 kbps) and AAC (~130 kbps) direct streams without intermediate transcoders.
  - **Automated Endless Radio**: Automatically queues and plays dynamically matching music when any song finishes or starts.
  - **Bot-Detection Immunity**: Employs an authenticated visitor token cache and Apple Vision Pro `VISIONOS` player context to bypass YouTube bot detection.
  - **Global Discovery & Charts**: Explore top global charts, mood-filtered playlists, trending releases, and instant search.
- **☁️ Self-Hosted Navidrome / Subsonic Client**: Connect your self-hosted lossless music server to stream your private library anywhere with native authentication and caching.
- **🎼 Hi-Res Lossless Audio Support**: Native hardware decoding for FLAC (`.flac`), ALAC (`.m4a`), WAV (`.wav`), AAC, and MP3 formats with 24-bit/32-bit float audio output pipeline.
- **📀 Animated Vinyl Disc Player**: Full-screen player featuring a smoothly rotating vinyl disc, interactive seek bar, queue reordering, and mood ambient gradient illumination.
- **🎤 Spotify-Style Real-Time Synced Lyrics**:
  - Live synced lyrics preview card directly inside the full-screen player.
  - Expanded full-screen view featuring dynamic mood ambient gradients and auto-scrolling synced lines.
  - Active line spring-scaling highlight, dimmed context lines, and **Tap-to-Seek** (tap any line to jump playback directly to that timestamp).
  - Integrated bottom mini-playback bar and one-tap lyrics snippet sharing.

### 🎛️ Audio Processing, DSP & Visualizers
- **📊 Real-Time FFT Audio Visualizers**:
  - **Neon Bars**: Dynamic multi-band frequency spectrum visualizer with vibrant mood gradient bars.
  - **Pulse Aura**: Rhythmic bass-reactive glowing aura expanding to the beat.
  - **Cosmic Particles**: Floating particle field reacting to audio frequencies and amplitude.
  - Smooth 60 FPS hardware-accelerated Compose Canvas rendering.
- **🔊 3D Spatial Audio & Reverb Acoustics**:
  - Hardware-accelerated Virtualizer with dedicated Headphone Mode.
  - 6 environmental reverb presets: *Small Room*, *Medium Room*, *Large Room*, *Medium Hall*, *Large Hall*, and *Plate Reverb*.
  - Configurable millibel-accurate Bass Boost.
- **🔁 Smooth Crossfade**: Configurable crossfade transitions between tracks (0.5s–3.0s).

### 📚 Library & Tooling
- **🏷️ On-Device ID3 Tag & Cover Art Editor**:
  - Edit song metadata (Title, Artist, Album, Year, Genre, Track Number).
  - Select and embed custom cover artwork images directly into MP3, FLAC, and M4A audio files.
  - Immediate MediaStore synchronization so changes reflect across the system.
- **📡 ListenBrainz Scrobbler**: Automatic real-time scrobbling to your ListenBrainz account with personalized listening analytics.
- **📁 Full Playlist Management**: Create, rename, delete, add/remove songs, and reorder tracks within playlists, persisted in Room.
- **🔍 Debounced Library Search**: Search tracks by title, artist, or album with real-time 300ms debouncing.
- **📊 Mood Analytics & Listening Stats**: View 30-day listening history, total session time, top mood, and animated vibe statistics.
- **❤️ Favorites Management**: Save and toggle favorite tracks persisted locally via Room database.

### 🎨 Material You & Modern UI
- **🎨 Material Design 3 Themed Icons**: Fully compliant Android 13+ monochrome / themed launcher icon adapting to system wallpaper palette.
- **🎭 Dynamic Theming**: Real-time Material You dynamic color extraction on Android 12+ (API 31+) with fallback to custom dark and light themes.
- **📱 Home Screen Widget & Quick Settings**: Home-screen playback widget, pause/play quick settings tile, app shortcuts per mood, and Android Auto / Automotive media support.
- **🔄 In-App Auto-Updater**: Checks GitHub Releases for new updates, downloads the APK in-app with progress feedback, and launches the package installer.
- **🛡️ Security & Reliability Hardening**:
  - Unexported `MusicPlaybackService` preventing unauthorized 3rd-party app hijacking.
  - HTTPS-only network security config disabling cleartext HTTP vulnerabilities.
  - 512 MB LRU stream cache served from disk when connectivity drops.
  - Crash recovery guardian that detects unexpected exits and restores the database from an automatic backup.

---

## 🛠️ Architecture & Tech Stack

Built following **Android Clean Architecture** guidelines with unidirectional data flow (MVI / MVVM):

```
app/src/main/java/com/moodtunes/app/
├── domain/            # Pure Kotlin: Models (Song, MoodType, LyricsLine), Repository Contracts & UseCases
├── data/              # MediaStore API, Room Entities/DAOs, YouTube Music API, Preferences & Navidrome Client
├── di/                # Hilt Dependency Injection Modules (DatabaseModule, RepositoryModule, NetworkModule)
├── service/           # MusicPlaybackService (Media3 MediaSession), PlaybackManager, AudioEffectsManager
├── platform/          # Media3 MediaSession callbacks, Audio Visualizer Manager & MediaItem factory
└── presentation/      # Jetpack Compose UI Screens, ViewModels, Navigation, Visualizers & Theme
```

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.1 |
| **UI Framework** | Jetpack Compose (BOM 2025.01.00) + Material 3 |
| **Audio Engine** | `androidx.media3:media3-exoplayer` & `media3-session` (1.6.1) |
| **Audio DSP** | Android AudioFX (Virtualizer, EnvironmentalReverb, BassBoost, Visualizer FFT) |
| **Streaming Backend** | YouTube Music InnerTube (`VISIONOS` client context) + Navidrome Subsonic |
| **Dependency Injection** | Dagger Hilt 2.56.2 |
| **Database & Storage** | Room 2.7.1 (SQLite), DataStore Preferences |
| **Networking & HTTP** | OkHttp 4.12.0 with connection pooling and DNS caching |
| **Image Loading** | Coil 2.7.0 (Compose image loader) |
| **Scrobbling** | ListenBrainz REST API |
| **Target Platform** | Android 8.0 (API 26) to Android 15 (API 36) |

---

## 📱 Screens Overview

1. **Home Screen**: Dynamic mood cards (*Happy, Sad, Energetic, Calm, Euphoric, Sleep*), top mood statistics banner, recent listening history, and persistent mini-player.
2. **Songs Hub**: Tabbed library with *Local Device*, *Downloaded*, *YouTube Music Streams*, *Favorites*, and *Custom Playlists*.
3. **Player Screen**: Immersive full-screen player with rotating vinyl disc animation, live FFT visualizer overlay, 3D reverb acoustic toggles, seek bar, queue drawer, and favorite heart toggle.
4. **Lyrics Screen**: Synchronized Spotify-style lyrics with tap-to-seek, smooth auto-scroll, dynamic background gradients, and lyrics sharing.
5. **ID3 Tag Editor**: Edit metadata fields and embed custom cover images directly into local audio files.
6. **Playlist Detail Screen**: Custom playlist management with drag-to-reorder, search, and bulk operations.
7. **Mood Analytics Screen**: Visual breakdown of your listening habits, mood trends, and session history over the past 30 days.
8. **Settings Screen**: Comprehensive customization for themes, dynamic colors, crossfade duration, YouTube stream quality, ListenBrainz scrobbling, Navidrome server setup, database backup/restore, in-app updates, and about info.

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (Ladybug 2024.2.1 or newer recommended)
- **JDK 21** configured in Android Studio or environment (`JAVA_HOME`)
- Physical Android device or Emulator running **Android 8.0 (API 26)** or higher

### Building & Running

1. **Clone the repository**:
   ```bash
   git clone https://github.com/cyberlog69/MoodTunes.git
   cd MoodTunes
   ```

2. **Build Debug APK**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat assembleDebug
   ```

3. **Install to Connected Device**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat installDebug
   ```

4. **Build Release APK**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat assembleRelease
   ```

---

## 📦 Releases & Downloads

Latest APK downloads and detailed release notes are available on the **[GitHub Releases](https://github.com/cyberlog69/MoodTunes/releases)** page.

---

## 📜 License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.

---

<p align="center">
  Crafted with ❤️ by <a href="https://github.com/cyberlog69">cyberlog69</a>
</p>
