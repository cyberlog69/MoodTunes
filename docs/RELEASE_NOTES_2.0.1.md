# MoodTunes 2.0.1 — Hardening Patch

A focused reliability and security patch for 2.0.0, driven by a full Android
lint run and code review of the new YouTube Music layer.

## Fixed

- **Quick Settings tile** no longer crashes on Android 8/9 (`Tile#setSubtitle`
  is API 29+, now guarded).
- **Home-screen widget** uses real `dp` values instead of Glance's `@DimenRes`
  overloads with raw integers.
- **Android Auto** library errors now use a valid `SessionError` constant.
- **Assistant search** (`MEDIA_PLAY_FROM_SEARCH`) resolves the query against
  your local library.
- **Sign-out** now clears WebView cookies and storage, so the Google session is
  not silently re-captured.
- **Stale `visitorData`** is refreshed and the request retried once when
  YouTube Music returns 400/401/403.
- **Playlists with "album" in the title** are no longer treated as albums.
- **Shelf taps** play the correct track when a row mixes songs and cards.
- **Load more** stops when YouTube repeats its continuation token.
- **Play counts** parse without K/M/B suffixes.
- **Lyrics filter** hides YouTube Music rows.
- **Radio appends** are generation-guarded against queue switches.

## Added

- MockWebServer transport tests for the InnerTube client: endpoint URL, client
  headers, request body, and the stale-visitor retry. The v2.0.0 browse-URL
  typo would now be caught by CI.
- Parser tests for album-vs-playlist detection and plain play counts.

## CI

- `lintDebug` now runs on every build; lint errors block the release pipeline.

## Install

Download `MoodTunes-v2.0.1.apk` below. It is signed with the same release key
and v3 lineage as 2.0.0, so it updates in place. Verify the signer against
[SECURITY.md](../SECURITY.md) if you sideload from elsewhere.

Full details: [`CHANGELOG.md`](../CHANGELOG.md).
