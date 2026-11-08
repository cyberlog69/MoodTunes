# MoodTunes 2.0.0 — The YouTube Music Release

MoodTunes now browses YouTube Music like the real app: full home, charts,
moods, albums, artists, playlists, typed search, your signed-in library and
Spotify-style autoplay. Mood-first local playback, Subsonic, DSP, lyrics and the
tag editor are unchanged.

## Highlights

- **Discover** — Home, Charts, Moods & genres and New releases as shelf rows.
- **Full browse graph** — album, artist, playlist and mood pages with Play,
  Shuffle and continuation paging.
- **Typed search** — Songs / Albums / Artists / Playlists filters, typeahead
  suggestions, a Top result and relevance re-ranking.
- **Spotify-style search radio** — play one search result and MoodTunes builds
  a station from it, so the queue keeps going with related tracks.
- **Your YouTube Music library** — optional Google sign-in via an in-app WebView.
  Cookies are encrypted with the Android Keystore; liked songs, albums, artists
  and playlists appear in Collection.
- **Multi-client playback** — `/player` falls back through ANDROID_MUSIC,
  VISIONOS, TVHTML5, IOS and WEB_REMIX, with signature and `n` solving.
- **Automatic queue radio** — online queues extend when they reach the last
  track.

## Fixes in this release

- Browse requests no longer 404 (bad `/youtubei/v1` prefix).
- Playlist and mood pages show their real titles.
- Playlist/album tracks show durations instead of "Live Stream".
- Album tracks inherit the album artist.
- Long artist names no longer stretch song rows to twice their height.
- The Play/Shuffle row on YouTube pages no longer wraps.
- Search no longer duplicates the Top result.

## Verification

- 49 unit tests, 0 failures (`:app:testDebugUnitTest`).
- `assembleDebug` and CI `assembleRelease` succeed.
- Manually tested on a physical device: Discover shelves, mood/playlist/album
  pages, typed search and filters, search-seeded radio (20-track queue),
  playback, signed-out Collection and the Google sign-in WebView.

## Known limitations

- **PoToken / BotGuard are not implemented.** Playback relies on client rotation
  and the user's web session. Tracks that require a proof-of-origin token can
  still fail.
- **No YouTube Takeout history import.**
- **The YouTube library is read-only** — likes, saves and playlist edits are not
  written back to YouTube Music.

## Install

Download `MoodTunes-v2.0.0.apk` below. Official builds are signed with the
current release key and the v3 rotation lineage, so they update existing
installs in place. Verify the signer fingerprint documented in
[SECURITY.md](../SECURITY.md) if you sideload from anywhere else.

Full technical details: [`docs/YOUTUBE_MUSIC_INTEGRATION.md`](YOUTUBE_MUSIC_INTEGRATION.md).
