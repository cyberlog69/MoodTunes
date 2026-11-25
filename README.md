# 🎵 MoodTunes — YouTube Music Client for Android

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Audio-androidx.media3%20ExoPlayer-FF6F00?style=flat-square)](https://developer.android.com/guide/topics/media/media3)
[![Hilt](https://img.shields.io/badge/DI-Hilt-00897B?style=flat-square)](https://dagger.dev/hilt/)
[![Room](https://img.shields.io/badge/Database-Room%202.7-4285F4?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![Release](https://img.shields.io/badge/Version-v3.0.2-blue?style=flat-square)](https://github.com/cyberlog69/MoodTunes/releases)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

---

## 📖 About MoodTunes

**MoodTunes** is an open-source **YouTube Music client for Android**, built with
**Jetpack Compose**, **Media3 / ExoPlayer**, and **Android Clean Architecture**.

YouTube Music is the only source. The app browses the real catalogue — home,
charts, moods & genres, new releases, albums, artists and playlists — streams
full-length tracks through InnerTube, and keeps your own favorites and playlists
on-device.

### 🌟 Key Vision & Design Philosophy
- **YouTube Music Streaming Engine**: Full InnerTube integration — browse graph (home, charts, moods, releases, albums, artists, playlists), typed search with ranking, signed-in library, multi-client stream resolution, full-length tracks, Spotify-style search radio, and global charts.
- **App-Managed Collection**: Favorites and playlists work with or without a YouTube account. Saved tracks keep their `videoId`, so replaying a favorite can seed its radio queue.
- **Resilient Stream Resolution**: Layered resolver (NewPipe extractor → InnerTube client fallback → on-device JS cipher solver) that recovers from throttled or expired stream URLs. YouTube changes its clients frequently, so playback can break until the resolver is updated.
- **Audiophile Grade DSP**: Parametric equalizer, bass boost, virtualizer, environmental reverb, loudness normalization and true gapless playback on top of Media3.
- **Live FFT Audio Visualizers**: 60 FPS real-time audio visualization using Android Visualizer FFT data rendered on dynamic Compose Canvases (Neon Bars, Pulse Aura, Cosmic Particles).
- **Privacy & Security First**: Zero tracking, encrypted credential storage (AES-256-GCM with Android Keystore), unexported services preventing third-party hijacking, and HTTPS-enforced network transport.

---

## ✨ Features

### 🎧 Playback & Streaming
- **🎶 YouTube Music (InnerTube) Engine**:
  - **Full-Length Streams**: Resolves full-length audio streams rather than 30-second previews where the upstream service allows it.
  - **Direct Audio Streams**: Bitrate-first format ranking; AAC up to 256 kbps and Opus up to 160 kbps depending on account and track.
  - **Full Browse Graph**: Home, Charts, Explore, New Releases, Moods & Genres, album, artist and playlist pages with continuation paging.
  - **Typed Search & Ranking**: Songs/Albums/Artists/Playlists filters, typeahead suggestions, a Top result and deterministic relevance re-ranking.
  - **Search Autoplay Radio**: Playing a search result starts a Spotify-style station from that song and keeps appending related tracks.
  - **Your Library**: Optional YouTube Music account sign-in (encrypted WebView cookies) for liked songs, albums, artists and playlists.
  - **Resilient Player Clients**: Multi-client `/player` fallback (VISIONOS, ANDROID_VR, then signed-in clients) with signature and `n` challenge solving.
  - **Automated Endless Radio**: Automatically queues similar tracks when a song finishes or a station starts.
  - **Global Discovery & Charts**: Explore top global charts, trending releases, and instant search.
- **📀 Animated Vinyl Disc Player**: Full-screen player featuring a smoothly rotating vinyl disc, interactive seek bar, queue reordering, and ambient gradient illumination.
- **🎤 Spotify-Style Real-Time Synced Lyrics**:
  - Live synced lyrics preview card directly inside the full-screen player.
  - Expanded full-screen view featuring dynamic ambient gradients and auto-scrolling synced lines.
  - Active line spring-scaling highlight, dimmed context lines, and **Tap-to-Seek** (tap any line to jump playback directly to that timestamp).
  - Integrated bottom mini-playback bar and one-tap lyrics snippet sharing.

### 🎛️ Audio Processing, DSP & Visualizers
- **📊 Real-Time FFT Audio Visualizers**:
  - **Neon Bars**: Dynamic multi-band frequency spectrum visualizer with vibrant gradient bars.
  - **Pulse Aura**: Rhythmic bass-reactive glowing aura expanding to the beat.
  - **Cosmic Particles**: Floating particle field reacting to audio frequencies and amplitude.
  - Smooth 60 FPS hardware-accelerated Compose Canvas rendering.
- **🔊 3D Spatial Audio & Reverb Acoustics**:
  - Hardware-accelerated Virtualizer with dedicated Headphone Mode.
  - 6 environmental reverb presets: *Small Room*, *Medium Room*, *Large Room*, *Medium Hall*, *Large Hall*, and *Plate Reverb*.
  - Configurable millibel-accurate Bass Boost.
- **🎚️ Parametric Equalizer & Loudness**: Multi-band EQ with presets, plus EBU R128-style loudness normalization.
- **🔁 Smooth Crossfade & Gapless**: Configurable crossfade transitions between tracks and true gapless playback.

### 📚 Library & Tooling
- **📁 Full Playlist Management**: Create, rename, delete, add/remove songs, and reorder tracks within playlists, persisted in Room.
- **🔍 YouTube Search**: Debounced online search by song, album, artist or playlist, with lyric matching.
- **❤️ Favorites Management**: Save and toggle favorite YouTube tracks persisted locally via Room database.

### 🎨 Material You & Modern UI
- **🎨 Material Design 3 Themed Icons**: Fully compliant Android 13+ monochrome / themed launcher icon adapting to system wallpaper palette.
- **🎭 Dynamic Theming**: Real-time Material You dynamic color extraction on Android 12+ (API 31+) with fallback to custom dark and light themes.
- **📱 Home Screen Widget & Quick Settings**: Home-screen playback widget, pause/play quick settings tile, and Android Auto / Automotive media support (Favorites and local playlists).
- **🔄 In-App Auto-Updater**: Checks GitHub Releases for new updates, downloads the APK in-app with progress feedback, and launches the package installer.
- **🛡️ Security & Reliability Hardening**:
  - Unexported `MusicPlaybackService` preventing unauthorized 3rd-party app hijacking.
  - HTTPS-only network security config; cleartext HTTP is permitted only for `localhost` / `127.0.0.1` / `10.0.2.2` during local development.
  - Update downloads are restricted to GitHub HTTPS hosts and verified against the installed app's signing certificate and package name before the installer is launched.
  - User secrets (YouTube cookies) are encrypted at rest with AES-256-GCM keys held in the Android Keystore.
  - 512 MB LRU stream cache served from disk when connectivity drops.
  - Crash recovery guardian that detects unexpected exits and restores the database from an automatic backup.
  - Room schema is exported and versioned; there is no destructive fallback, so an unknown schema change fails loudly instead of wiping your data.

---

## 🛠️ Architecture & Tech Stack

Built following **Android Clean Architecture** guidelines with unidirectional data flow (MVI / MVVM):

```
app/src/main/java/com/moodtunes/app/
├── domain/            # Pure Kotlin: Models (Song, YouTubeItem, LyricsLine), Repository Contracts & UseCases
├── data/              # Room Entities/DAOs, YouTube Music API, Preferences & Backup
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
| **Streaming Backend** | YouTube Music InnerTube (`WEB_REMIX` browse/search + multi-client `/player`) |
| **Dependency Injection** | Dagger Hilt 2.56.2 |
| **Database & Storage** | Room 2.7.1 (SQLite), DataStore Preferences |
| **Networking & HTTP** | OkHttp 4.12.0 with connection pooling and DNS caching |
| **Image Loading** | Coil 2.7.0 (Compose image loader) |
| **Target Platform** | Android 8.0 (API 26) to Android 16 (API 36) |

---

## 📱 Screens Overview

1. **Home (Discover)**: YouTube Music's browse surfaces — Made for you, Charts, Moods & genres and New releases — as horizontal shelves, with bottom navigation.
2. **Library**: Six tabs — *Liked*, *Albums*, *Artists* and *YT Playlists* from the connected account, plus app-managed *Favorites* and *Playlists*.
3. **Search**: Typed YouTube Music results, Top result, suggestions, lyric matching and playlist matches.
4. **Player Screen**: Immersive full-screen player with rotating vinyl disc animation, live FFT visualizer overlay, 3D reverb acoustic toggles, seek bar, queue drawer, and favorite heart toggle.
5. **Lyrics Screen**: Synchronized Spotify-style lyrics with tap-to-seek, smooth auto-scroll, dynamic background gradients, and lyrics sharing.
6. **YouTube Page**: Album, artist, playlist and category pages with Play/Shuffle, track lists and continuation paging.
7. **YouTube Music Account**: In-app Google sign-in WebView; cookies are encrypted with the Android Keystore.
8. **Playlist Detail Screen**: Custom playlist management with reordering and song removal.
9. **Settings Screen**: Themes, dynamic colors, stream quality, YouTube Music account, DSP toggles, database backup/restore, in-app updates, and about info.

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

   Release builds are signed only when signing credentials are configured. See [Release signing](#-release-signing) below.

### 🔑 Release Signing & Key Rotation

MoodTunes does **not** ship a keystore in the repository. Local builds read signing credentials from environment variables or a git-ignored `keystore.properties`:

| Environment variable | `keystore.properties` key | Meaning |
|---|---|---|
| `KEYSTORE_FILE` | `storeFile` | Path to the `.keystore`, relative to the repo root |
| `KEYSTORE_PASSWORD` | `storePassword` | Keystore password |
| `KEY_ALIAS` | `keyAlias` | Key alias inside the keystore |
| `KEY_PASSWORD` | `keyPassword` | Key password |

```powershell
Copy-Item keystore.properties.example keystore.properties
# fill in real values, then:
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

If no credentials are found, `assembleRelease` still completes but produces an **unsigned** APK and logs a warning.

**Official releases are signed differently.** Since `v1.9.4` the project uses an APK Signature Scheme v3 rotation lineage: CI builds an unsigned APK, then signs it with both the retired key and the current key so Android 8.x still verifies v2 while Android 9+ follows the v3 lineage. CI therefore needs these repository secrets:

| Secret | Purpose |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Current (new) keystore, base64-encoded |
| `RELEASE_OLD_KEYSTORE_BASE64` | Previous keystore, kept to sign v1/v2 for legacy devices |
| `RELEASE_LINEAGE_BASE64` | v3 `SigningCertificateLineage` binary |
| `KEYSTORE_PASSWORD` / `KEY_PASSWORD` | Current key passwords |
| `OLD_KEYSTORE_PASSWORD` / `OLD_KEY_PASSWORD` | Previous key passwords |
| `KEY_ALIAS` | Alias used in both keystores |

A plain local `assembleRelease` signs with the current key only, so it installs cleanly but cannot update an official lineage-signed install in place. Use CI artifacts for release testing. Rotation details and fingerprints live in [SECURITY.md → Release signing & key rotation](SECURITY.md#release-signing--key-rotation).

> [!WARNING]
> The original key was committed to this repository, so its password must be considered public. It has been rotated as of `v1.9.4`; only install APKs from the official GitHub Releases page and verify the signer fingerprint documented in `SECURITY.md`.

---

## 📦 Releases & Downloads

Latest APK downloads and detailed release notes are available on the **[GitHub Releases](https://github.com/cyberlog69/MoodTunes/releases)** page.

---

## 📜 License

MoodTunes is distributed under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE) for the full text.

Third-party components keep their own licenses:

- **NewPipe Extractor** — GPL-3.0, compatible with this project's license.
- **yt-dlp EJS solver** (bundled JS assets) — Unlicense, including meriyah (ISC) and astring (MIT).
- **AndroidX / Media3 / Hilt / Room / OkHttp / Coil / Timber** — Apache-2.0.

License texts are bundled inside the APK under `assets/licenses/` and are viewable in the app at **Settings → About MoodTunes → Open-source Licenses**. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the full inventory.

> [!NOTE]
> Redistributed binaries must comply with the GPL: ship the corresponding source (including your modifications) and keep the licence and attribution notices intact.

---

<p align="center">
  Crafted with ❤️ by <a href="https://github.com/cyberlog69">cyberlog69</a>
</p>
