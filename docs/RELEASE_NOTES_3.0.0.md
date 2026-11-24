# MoodTunes 3.0.0 — YouTube Music Client

MoodTunes is now a YouTube Music client. Mood radio, mood analytics, the local
music library, offline downloads, the tag editor and Subsonic/Navidrome support
are gone. YouTube Music is the only source, and your own favorites and playlists
live on-device.

## Breaking changes

- **Moods removed.** The mood selector, mood scoring/recommendations, mood
  history, mood EQ and the vibe statistics screen were deleted. YouTube Music's
  own “Moods & genres” shelves remain for catalogue browsing.
- **Local library removed.** Device scanning, the storage permission flow, the
  local/online source switch and the Home/Library/Permission screens are gone.
- **Downloads removed.** Offline download, the Downloads tab, the download
  notification channel and batch queue downloads were removed.
- **Tag editor removed.** On-device ID3/artwork editing is no longer part of the
  app.
- **Subsonic/Navidrome removed.** The self-hosted server client, credentials,
  settings section and cleartext exception were removed.
- **Database migration 5 → 6.** The `songs` table is rebuilt without mood tags,
  `mood_history` is dropped, and `videoId` is added. Favorites, play counts and
  playlists are preserved.

## New

- **Home is Discover.** The YouTube Music browse graph (Made for you, Charts,
  Moods & genres, New releases) is the start destination, with bottom navigation
  for Home, Library, Search and Settings.
- **Unified Library.** Six tabs: Liked, Albums, Artists and YT Playlists from
  the connected account, plus app-managed Favorites and Playlists that work
  without signing in.
- **YouTube favorites.** Any track can be favorited. The `videoId` is persisted
  so replaying a saved favorite can still seed its radio queue.

## Improved

- **Bitrate-first format ranking.** AUTO prefers 256 kbps AAC over 160 kbps Opus;
  `High` caps around 192 kbps and `Maximum` takes the best available format.
- **Signed-in playback tries `WEB_REMIX` first**, the only client that returns
  YouTube Music Premium's 256 kbps formats, and now targets `music.youtube.com`.
- **Honest quality labels** and a mobile-data default of High instead of 128 kbps.
- **Client order matches late-2026 YouTube**: VISIONOS, then ANDROID_VR, with
  cookie-only clients only when signed in. Client health is remembered.
- **8-second `/player` attempts**, gated player-JS fetches and a 10-minute
  cooldown for failing player-JS and NewPipe lookups.
- **Search is YouTube-only** while keeping typed results, suggestions, lyric
  matching and playlist matches.
- **Android Auto** exposes Favorites and local playlists; assistant search uses
  YouTube Music.

## Install

Download `MoodTunes-v3.0.0.apk` below. It is signed with the same release key and
v3 lineage as 2.0.x, so it updates in place and migrates your database. Verify
the signer against [SECURITY.md](../SECURITY.md) if you sideload from elsewhere.

Full details: [`CHANGELOG.md`](../CHANGELOG.md).
