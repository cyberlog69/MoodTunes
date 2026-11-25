# Changelog

All notable changes to MoodTunes are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org/).

## [3.0.2] - 2026-11-25

### Fixed

- **“Update available” no longer repeats after updating.** The update checker
  cached the `isUpdateAvailable` flag from the previous app version and kept
  serving it after the in-app update. Cache entries are now tied to the running
  version, and availability is recomputed against the installed version instead
  of trusting the stored boolean.
- **The launch dialog appears once per release.** A dismissed prompt is not
  shown again on every launch for the same version; the notification and the
  manual Settings check are unaffected.
- **Stale update notifications are cleared** when the app is already on the
  latest version.
- **Settings → Check for Updates always queries GitHub** instead of returning a
  cached result.
- **Version comparison handles numeric segments and build suffixes** (for
  example `3.0.9` vs `3.0.10`, and `3.0.1-beta`).

## [3.0.1] - 2026-11-25

Focused cleanup: scrobbling, karaoke and DJ mixing are gone, leaving a leaner
YouTube Music client.

### Removed

- **ListenBrainz scrobbling** — service, settings section, credentials and
  Now Playing/submit-listen hooks were removed.
- **Last.fm scrobbling** — service, settings section, session-key storage and
  scrobble hooks were removed.
- **Sing / Karaoke Studio** — the vocal-attenuation DSP processor, the semitone
  pitch shifter, the player sheet and its tool button were removed. The separate
  Speed & Pitch tool remains.
- **DJ Mode & harmonic mixing** — the DJ sequencer, vibe presets, harmonic
  auto-mix, BPM/Camelot analysis and the player sheet were removed.

### Fixed

- **Playback pitch persists correctly.** Speed & Pitch now restores the saved
  pitch factor instead of deriving it from the removed karaoke semitones.

## [3.0.0] - 2026-11-25

MoodTunes is now a YouTube Music client. The mood-first design, local library,
offline downloads, tag editor and self-hosted server support are gone.

### Breaking

- **YouTube Music is the only source.** Local-device scanning, the permission
  flow, song downloads and the offline Downloads tab, the ID3/tag editor, and
  Subsonic/Navidrome streaming were removed along with their settings, services
  and permissions.
- **Mood personalization is gone.** `MoodType`/`MoodEntry`, mood scoring and
  recommendations, mood history, the mood selector, mood EQ and the mood
  statistics screen were deleted. YouTube Music's own “Moods & genres” shelves
  remain as catalogue browsing.
- **Database migration 5 → 6.** The `songs` table is rebuilt without
  `moodTagsJson`, the `mood_history` table and the `moodType` playlist column
  are dropped, and a `videoId` column is added so saved YouTube tracks keep
  their identity. Favorites, play counts and playlists are preserved.

### Added

- **Home is Discover.** The YouTube Music browse graph (Made for you, Charts,
  Moods & genres, New releases) is the start destination, with bottom navigation
  for Home, Library, Search and Settings.
- **Unified Library.** Six tabs: Liked, Albums, Artists and YT Playlists from
  the connected account, plus app-managed Favorites and Playlists that work
  without signing in.
- **YouTube favorites.** Any track can be favorited; the `videoId` is persisted
  so replaying a favorite can still seed its radio queue.

### Changed

- **Audio format ranking is now bitrate-first.** AUTO picks YouTube Music's
  256 kbps AAC (itag 141) over a 160 kbps Opus stream (itag 251); Opus wins
  only on a tie. `High` caps at ~192 kbps for data efficiency, while `Maximum`
  takes the best available format.
- **Signed-in users try `WEB_REMIX` first.** It is the only client that can
  return YouTube Music Premium's 256 kbps AAC/Opus formats, and its player
  request now correctly targets `music.youtube.com`.
- **Quality labels and defaults are honest.** Streaming labels no longer claim
  FLAC 24-bit or 320 kbps; mobile data now defaults to High (192 kbps ceiling)
  instead of 128 kbps, and Settings explains the anonymous 160 kbps ceiling.
- **Player client order now matches the current YouTube landscape.** VISIONOS is
  tried first, followed by ANDROID_VR; the cookie-only clients (WEB_REMIX,
  ANDROID_MUSIC, TVHTML5) are only attempted when a YouTube account is
  connected. IOS stays last because it currently returns SABR-only responses.
- **Client health is remembered.** The last client that produced a stream is
  tried first and failing clients are skipped for 10 minutes, so a dead client
  cannot add latency to every track.
- **Each `/player` attempt has an 8-second timeout** instead of sharing the
  20-second read timeout.
- **The JS player is only fetched for clients that need it.** VISIONOS,
  ANDROID_VR, ANDROID_MUSIC and IOS return already-signed URLs.
- **Player JS failures are cached for 10 minutes**, preventing repeated
  `iframe_api` lookups on devices where it is unreachable.
- **NewPipe has an 8-second cap and a circuit breaker.** After a failure it is
  skipped for 10 minutes while InnerTube resolves.
- **Search is YouTube-only.** The debounced typed search, suggestions, LRCLIB
  lyric matching and playlist matching remain; local and Subsonic result
  branches were removed.
- **Android Auto exposes Favorites and local playlists.** The mood and
  all-songs folders were removed; assistant “play <query>” searches YouTube
  Music.

### Removed

- Manifest storage/media permissions, mood deep links and launcher shortcuts,
  the download notification channel, the Subsonic cleartext network exception
  and the obsolete settings sections.

## [2.0.1] - 2026-11-09

Hardening patch for v2.0.0, driven by a full Android lint and code review.

### Fixed

- **Quick Settings tile no longer crashes on Android 8/9.** `Tile#setSubtitle`
  is API 29+; it is now guarded by a version check.
- **Home-screen widget dimensions are real dp values.** Glance's `@DimenRes`
  overloads were being called with raw integers, resolving to non-existent
  resources.
- **Android Auto library errors use a valid `SessionError` code** instead of a
  `PlaybackException` constant.
- **Assistant media search is handled.** `MEDIA_PLAY_FROM_SEARCH` is declared and
  `onAddMediaItems` resolves a `searchQuery` against the local library.
- **Sign-out clears the WebView session.** Cookies and WebStorage are removed,
  so the Google session cannot be silently re-captured after signing out.
- **Stale `visitorData` is refreshed automatically.** A 400/401/403 from
  InnerTube now invalidates the cached visitor id, strips it from the JSON body
  and retries once.
- **Album detection uses the browse id only.** A playlist whose title contains
  "album" is no longer treated as an album.
- **Shelf taps map to the correct track.** Card positions are translated to
  `playableSongs` positions before playback.
- **"Load more" stops on a repeated continuation token.**
- **Play counts parse without K/M/B** (e.g. `717 plays`).
- **Lyrics filter hides YouTube Music rows.**
- **Radio appends are generation-guarded**, so a slow request can no longer add
  tracks to a queue the user already replaced.

### Added

- `YouTubeInnerTubeClientTest` with MockWebServer coverage for endpoint URL,
  headers, request body and the stale-visitor retry. The old browse URL typo
  would now fail this test.
- Parser tests for album-vs-playlist detection and play-count parsing.

### Changed

- CI now runs `lintDebug` before building the release. Lint errors block the
  pipeline instead of shipping silently.

## [2.0.0] - 2026-11-08

The YouTube Music release: MoodTunes now speaks the full browse graph instead of
just search and playback.

### Added

- **Discover screen** — YouTube Music home, charts, moods & genres and new
  releases as shelf rows, reachable from Home.
- **Full browse graph and paging** — `/browse` for home, charts, explore, new
  releases, moods/genres, albums, artists and playlists, with continuation
  tokens for long lists.
- **Typed search** — Songs, Albums, Artists and Playlists filters, typeahead
  suggestions, a Top result and "From YouTube Music" rows.
- **Search ranking** — deterministic `SearchRank` re-ranks online results by
  title/artist overlap, official track type, play count and duration
  plausibility.
- **Spotify-style search autoplay** — playing a search result starts a radio
  station from that song and appends related tracks immediately.
- **YouTube Music account and library** — in-app Google sign-in WebView,
  encrypted cookie storage, `SAPISIDHASH` auth and a Collection screen with
  liked songs, albums, artists and playlists.
- **YouTube page screen** — album, artist, playlist and mood pages with
  Play/Shuffle, track lists and "load more" paging.
- **Multi-client player** — `/player` requests rotate through ANDROID_MUSIC,
  VISIONOS, TVHTML5, IOS and WEB_REMIX, with signature and `n` challenge
  solving before playback.
- **Automatic queue radio** — online queues extend themselves when they reach
  the last track.
- **New routes and entries** — Discover and Collection tiles on Home, and a
  YouTube Music Account entry in Settings.
- **Documentation** — `docs/YOUTUBE_MUSIC_INTEGRATION.md` describes the
  architecture, endpoints and limitations.
- **Tests** — parser regression coverage (`YouTubeParserTest`) and ranking
  coverage (`SearchRankTest`).

### Changed

- `Song` gained optional YouTube fields (`videoId`, `albumBrowseId`,
  `artistBrowseId`, `playlistId`, `isExplicit`, `year`, `playCountLong`,
  `videoType`). All defaults keep the Room schema unchanged.
- `StreamResolver` now resolves in three tiers: NewPipe, multi-client InnerTube
  player, then the legacy VISIONOS request.
- Search keeps the instant local scan but debounces typed YouTube results and
  suggestions alongside Subsonic and LRCLIB.
- Song rows now reserve badge width correctly, so long artist names no longer
  produce oversized list items.

### Fixed

- InnerTube browse requests were built with a duplicated path
  (`/youtubei/v1/youtubei/v1/...`), which returned HTTP 404 and left Discover,
  typed search and collection pages empty.
- Playlist and mood pages now read `microformat` / `musicHeaderRenderer` titles
  instead of falling back to "YouTube Music".
- Playlist and album durations are read from `fixedColumns`, so tracks no
  longer display "Live Stream" with no duration.
- Album pages fill track artist and album name from the page metadata.
- Search no longer repeats the Top result in "From YouTube Music".
- The Play/Shuffle row on YouTube pages no longer wraps "Shuffle" onto two
  lines.

### Known limitations

- **PoToken / BotGuard are not implemented.** Playback relies on client rotation
  and the user's web session. Tracks that require a proof-of-origin token can
  still fail.
- **No YouTube Takeout import.** Listening history stays local to MoodTunes.
- **The YouTube library is read-only.** Liking, saving and playlist edits are
  not sent to YouTube Music.
- **Search filters are opaque YouTube params** and may change upstream; an empty
  typed page falls back to the legacy song search.

## [1.9.7] - 2026-11-07

- YouTube lyrics fallback when LRCLIB has no match.
- Real-time lyric timing offset.
- Dual-network quality adaptation.

## [1.9.6] - 2026-11-01

- Removed the redundant inline search bar from Songs Hub.

## [1.9.5] - 2026-11-01

- Fixed a startup crash caused by inline application-scope work.

## [1.9.4] - 2026-11-01

- Rotated the release signing key with an APK Signature Scheme v3 lineage.

## [1.9.3] - 2026-11-01

- Security and reliability patch: hardened signing, updates, credential storage
  and data safety.

## [1.9.2] - 2026-10-31

- Main release baseline.
