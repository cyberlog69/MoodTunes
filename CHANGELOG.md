# Changelog

All notable changes to MoodTunes are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org/).

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
