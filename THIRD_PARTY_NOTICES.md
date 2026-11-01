# Third-Party Notices

MoodTunes is distributed under the **GNU General Public License v3.0** (see
[`LICENSE`](LICENSE)). It bundles and/or links the third-party components listed
below. Each component remains under its own license; the full license texts ship
inside the APK in `app/src/main/assets/licenses/` and are shown in the app at
**Settings → About MoodTunes → Open-source Licenses**.

## Runtime libraries

| Component | License | Notes |
|---|---|---|
| AndroidX Core / Lifecycle / Activity / Compose / Navigation | Apache-2.0 | |
| AndroidX Media3 (ExoPlayer, MediaSession) | Apache-2.0 | |
| Dagger Hilt | Apache-2.0 | |
| Room | Apache-2.0 | |
| DataStore | Apache-2.0 | |
| Coil | Apache-2.0 | |
| OkHttp | Apache-2.0 | |
| Lottie | Apache-2.0 | |
| Timber | Apache-2.0 | |
| Accompanist | Apache-2.0 | |
| **NewPipe Extractor** | **GPL-3.0** | Stream resolution fallback; see the licensing note below. |

## Bundled assets

| Component | License | Notes |
|---|---|---|
| yt-dlp EJS core + solver library (`assets/ejs/`) | Unlicense | Version 0.8.0 |
| meriyah (bundled inside the EJS solver library) | ISC | Copyright (c) 2019 and later, KFlash and others |
| astring (bundled inside the EJS solver library) | MIT | Copyright (c) 2015, David Bonnet |

## Licensing note

NewPipe Extractor is licensed under **GPL-3.0**. MoodTunes was previously
MIT-licensed, which was not compatible with distributing a combined binary.
The project has since been relicensed to **GPL-3.0** to match, so the
application and its dependencies now share a compatible license family.

Practical consequences for redistributors:

- If you publish an APK built from this repository, you must also make the
  corresponding source available under GPL-3.0, including your modifications.
- Keep the copyright, license and attribution notices intact.
- Contributions are accepted under GPL-3.0.
- Commits and releases published before the relicensing remain available under
  the MIT terms they carried at the time.
