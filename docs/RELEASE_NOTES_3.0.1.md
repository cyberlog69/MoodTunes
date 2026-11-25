# MoodTunes 3.0.1 — Leaner Player

A cleanup release that removes the scrobbling integrations and the karaoke and
DJ mixing tools. YouTube Music playback, the library, favorites, playlists,
lyrics and the audio DSP chain are unchanged.

## Removed

- **ListenBrainz scrobbling** — the service, settings section, token storage and
  Now Playing / submit-listen hooks are gone.
- **Last.fm scrobbling** — the service, settings section, session-key storage
  and scrobble hooks are gone.
- **Sing / Karaoke Studio** — the real-time vocal-attenuation processor, the
  semitone pitch shifter, the player sheet and its tool button were removed.
  The separate **Speed & Pitch** tool remains available.
- **DJ Mode & harmonic mixing** — the DJ sequencer, vibe presets, harmonic
  auto-mix, BPM/Camelot analysis and the player sheet were removed.

## Fixed

- **Playback pitch is restored correctly.** Speed & Pitch now loads the saved
  pitch factor instead of deriving it from the removed karaoke semitones.

## Install

Download `MoodTunes-v3.0.1.apk` below. It is signed with the same release key and
v3 lineage as 3.0.0, so it updates in place. Verify the signer against
[SECURITY.md](../SECURITY.md) if you sideload from elsewhere.

Full details: [`CHANGELOG.md`](../CHANGELOG.md).
