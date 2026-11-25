# MoodTunes 3.0.2 — Update Checker Fix

Fixes the repeated “Update Available” prompt that could appear after installing
an update.

## Fixed

- **The update checker no longer trusts cache from a previous app version.**
  Cache entries are tied to the running version, and availability is recomputed
  against the installed version instead of reusing a stored `true` flag. This is
  the root cause of the prompt that survived an update.
- **The launch dialog appears once per release.** Dismissing it no longer brings
  it back on every launch for the same version. The notification and the manual
  Settings check still work.
- **Stale update notifications are cleared** when the app is already up to date.
- **Settings → Check for Updates always queries GitHub** instead of returning a
  cached answer.
- **Version comparison is numeric and suffix-safe**, so `3.0.9` vs `3.0.10` and
  builds like `3.0.1-beta` compare correctly.

## Install

Download `MoodTunes-v3.0.2.apk` below. It is signed with the same release key and
v3 lineage as 3.0.x, so it updates in place. Verify the signer against
[SECURITY.md](../SECURITY.md) if you sideload from elsewhere.

Full details: [`CHANGELOG.md`](../CHANGELOG.md).
