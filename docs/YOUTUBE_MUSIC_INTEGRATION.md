# YouTube Music Integration

MoodTunes talks to YouTube Music directly over the InnerTube API. There is no
server in the middle, no API key and no bundled extraction library for the
browse graph. This document describes how the pieces fit together, what changed
in the app, and which parts are intentionally out of scope.

## Architecture

```
UI (Compose)
  SearchScreen / DiscoverScreen / CollectionScreen / YouTubePageScreen
        │
        ▼
ViewModels (SearchViewModel, DiscoverViewModel, CollectionViewModel, YouTubePageViewModel)
        │
        ▼
OnlineStreamRepository            ← single public surface for remote music
        │
        ├── YouTubeInnerTubeClient  /browse, /search, /next, suggestions, library
        │        └── YouTubeParser  renderer → YouTubeItem / Song
        │        └── YouTubeSessionStore  encrypted cookies + SAPISIDHASH
        │
        └── StreamResolver
                 ├── Tier 1  NewPipe extractor
                 ├── Tier 2  YouTubePlayerClient (multi-client /player)
                 └── Tier 3  legacy VISIONOS request in YouTubeMusicApiService
```

Playback stays inside the existing Media3 session. `PlaybackManager` resolves
each online track lazily and can extend a finished queue from YouTube Music's
radio.

## Data model

`domain/model/YouTube.kt` adds the catalogue types that `Song` cannot express:

| Type | Purpose |
| --- | --- |
| `YouTubeItem.Track` | A playable song, wrapping an existing `Song`. |
| `YouTubeItem.Album` / `Artist` / `Playlist` | Navigable catalogue rows with a `browseId`. |
| `YouTubeItem.Category` | A mood/genre card that needs `browseId` + `params`. |
| `YouTubeShelf` | A titled row of items, with `playableSongs` convenience. |
| `YouTubePage` | One browse surface: header, shelves, songs, continuation. |
| `YouTubeSearchPage` | Typed search result: top result + per-filter sections. |
| `YouTubeSearchFilter` | All/Songs/Albums/Artists/Playlists/... with YouTube's own params. |
| `YouTubeRadioPage` | Radio queue and its continuation token. |

`Song` gained optional fields (`videoId`, `albumBrowseId`, `artistBrowseId`,
`playlistId`, `isExplicit`, `year`, `playCountLong`, `videoType`). All of them
default to `null`/`0`, so the Room schema is untouched: they are populated by
the parser at runtime and are not persisted.

## Browse and pagination

`YouTubeInnerTubeClient` posts to
`https://music.youtube.com/youtubei/v1/...` with a `WEB_REMIX` context.

- `home()`, `moodsAndGenres()`, `charts()`, `explore()`, `newReleases()`
  fetch the fixed browse ids (`FEmusic_*`) used by music.youtube.com.
- `browse(browseId, params)` opens album/artist/playlist/mood pages.
- `browseContinuation(token)` reads `continuationContents`, because
  continuation responses do not reuse the section list shape.
- `search(query, filter)` sends the filter's opaque `params`; continuations
  reuse the same token flow.
- `suggestions(query)` calls `music/get_search_suggestions` for typeahead.
- `radio(videoId)` calls `/next` for a watch-next queue.
- `likedPlaylists()`, `likedAlbums()`, `libraryArtists()`, `librarySongs()`,
  `libraryLanding()` are the signed-in library surfaces.

All parsing lives in `YouTubeParser` and is defensive: every renderer lookup is
optional, malformed rows are skipped, and an empty page is a valid result.

## Search

`SearchViewModel` keeps doing the instant local scan, then adds:

1. a debounced typed search against the selected `YouTubeSearchFilter`,
2. the legacy song search as a fallback for the Songs/All tabs,
3. typeahead suggestions from the same client,
4. Subsonic results and LRCLIB lyric matches as before,
5. `SearchRank.rankSongs(...)` over the combined online list.

`SearchRank` is pure Kotlin and covered by `SearchRankTest`. It scores title
and artist token overlap, album hits, official-track `videoType`, play count
and duration plausibility, and penalises hour-long compilations and karaoke
durations. Provider order is only a tie-breaker.

`SearchScreen` shows a "Top result" row, a "From YouTube Music" list for
albums/artists/playlists and a suggestion chip row. Tapping a non-track item
opens `YouTubePageScreen`; tapping a track plays it immediately.

## Account and library

`YouTubeAccountScreen` hosts a `WebView` for the real Google sign-in flow and
reads cookies from `CookieManager` for `music.youtube.com`. Cookies are
encrypted at rest with `SecretCipher` (Android Keystore) in
`YouTubeSessionStore`; nothing is stored in plain text.

`YouTubeSessionStore` also:

- exposes `isSignedIn` / `accountName` as flows,
- caches `visitorData`,
- derives `Authorization: SAPISIDHASH <ts>_<sha1>` for account-scoped calls.

Anonymous browsing works without any session: the client falls back to a
scraped `visitorData` and simply omits the cookie/authorization headers.

`CollectionScreen` exposes Songs, Albums, Artists and Playlists tabs. The
Songs tab reads `FEmusic_library_landing` first (it carries the same shelves
the web app shows) and falls back to `FEmusic_library_privately_owned_tracks`.

## Playback

`YouTubePlayerClient` sends `/player` requests across several clients, in
order: `ANDROID_MUSIC`, `VISIONOS`, `TVHTML5`, `IOS`, `WEB_REMIX`. Each attempt
is independent, so a bot check on one client does not fail playback. The first
client that returns `playabilityStatus == OK` wins.

Returned formats are ranked per `StreamQuality` (data saver, standard, high,
lossless, auto preferring Opus/WebM). `signatureCipher` / `cipher` URLs and the
`n` throttling parameter are normalised through `PlayerJsSolver` before the URL
is handed to Media3, so both signature and throttling challenges are solved.
The resulting stream carries bitrate, codec, sample rate, content length and
expiry, matching the existing `StreamResult` contract.

`StreamResolver` keeps its three tiers:

1. NewPipe extractor,
2. multi-client `YouTubePlayerClient`,
3. single-client VISIONOS fallback.

When an online queue reaches its last track, `PlaybackManager` calls
`getRadioQueue(videoId)` and appends fresh songs, so playback continues without
the user picking another track.

Playing a song from **search results** is Spotify-style: the tapped song becomes
the only queue item, and a radio station built from that song is appended
immediately. Search results are a launchpad, not a fixed queue. Album, playlist
and shelf playback keep their own ordering and only fall back to radio once the
list ends.

## UI surfaces

| Screen | Route | Entry point |
| --- | --- | --- |
| Discover | `discover` | Home → Discover tile |
| Collection | `collection` | Home → Collection tile |
| YouTube account | `youtube_account` | Settings → YouTube Music Account, Collection prompt |
| YouTube page | `youtube_page/{browseId}?params={params}` | Search results, Discover shelves, Collection rows |
| Search | `search` | Home search button (now with typed results) |

## Intentional limitations

- **PoToken / BotGuard are not implemented.** Some clients now require a
  proof-of-origin token for anonymous playback. MoodTunes relies on the client
  rotation above and on the user's web session for tricky tracks. If YouTube
  starts rejecting every client, this is the next thing to add.
- **No YouTube Takeout import.** Listening history is not imported; the local
  history and mood analytics remain MoodTunes' own data.
- **No write operations to YouTube Music.** Like/unlike, library edits and
  playlist mutations are read-only today; the app only browses and plays.
- **Search filters are opaque constants.** They are YouTube's own params and
  may change; the parser treats an empty typed page as a soft failure and keeps
  the legacy search results.
- **Only `WEB_REMIX` browse is used.** Other clients return different shapes;
  keeping one shape keeps the parser small and testable.

## Tests

- `YouTubeParserTest` covers carousel/song shelves, `continuationContents`
  rows and next tokens, and typed search top results.
- `SearchRankTest` covers official-vs-karaoke ordering, blank-query stability,
  artist matches and title ranking.

Both run under Robolectric as part of `:app:testDebugUnitTest`.
