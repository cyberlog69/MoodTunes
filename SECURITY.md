# 🛡️ MoodTunes Security Policy & Privacy Brief

At **MoodTunes**, we take the security, privacy, and integrity of your music listening experience seriously. As a private, open-source, local-first media player, MoodTunes is engineered to respect user autonomy, avoid third-party tracking, and safeguard media playback.

---

## 📌 Supported Versions

Security patches and dependency updates are maintained for the latest release branch. Users are strongly encouraged to stay on the latest stable release.

| Version | Status | Security Support Level |
|---|---|---|
| **v1.9.x (v1.9.4+)** | **Current Stable (Primary Release)** | ✅ Security fixes, bug fixes, and maintenance |
| **< v1.9.0** | **Deprecated** | ❌ Unsupported; releases before v1.9.0 used ephemeral CI signatures. Please upgrade |

> [!IMPORTANT]
> **Signing key rotated in v1.9.4.** The original release key was committed to this repository up to v1.9.1, so its password is public. `v1.9.4` introduces a new 4096-bit key with an APK Signature Scheme v3 rotation lineage; Android 9+ updates transparently. See [Release signing & key rotation](#release-signing--key-rotation).

---

## 🔒 Security & Privacy Architecture

### 1. Release Signing

- Since `v1.9.4`, official releases use an APK Signature Scheme **v3 rotation lineage**.
  - Current signer (newest lineage entry) SHA-256: `c994adfc0c267996d7ada16b4c034084022f79ce4d996036c616419b0f511622`
  - Retired signer (oldest lineage entry, used for v1/v2 on older devices) SHA-256: `4376dffb4af3bc5d6e26e44a59f9da22033843950ffdc2b58d2be00b8750c60f`
- CI builds an unsigned APK, page-aligns it, then signs with both the retired and current keys plus `--lineage`, using `--rotation-min-sdk-version 28`. This keeps v2 signatures verifiable on Android 8.x while Android 9+ follows the lineage to the new key.
- Signing material (both keystores, the lineage, and their passwords) is supplied through repository secrets. `*.keystore`, `*.jks`, `*.lineage` and `keystore.properties` are git-ignored.
- CI fails if any signing secret is missing, runs unit tests first, and verifies the signature **and** the embedded lineage with `apksigner` before uploading.
- A local `assembleRelease` signs with the current key only (no lineage), so it installs cleanly but is not update-compatible with official builds.

Verify a downloaded APK:

```bash
apksigner verify --print-certs MoodTunes-v1.9.4.apk
apksigner lineage --in MoodTunes-v1.9.4.apk --print-certs
```

The first command should report the `c994adfc…` signer; the second should list the retired `4376dffb…` key as signer #1 and `c994adfc…` as signer #2.

#### Release signing & key rotation

The original key was committed to this repository, so its password must be considered public. It was rotated in `v1.9.4`:

1. A new 4096-bit RSA key was generated with a random password.
2. `apksigner rotate` produced a `SigningCertificateLineage` proving the old key signed the new one.
3. Release APKs are signed with the old key first (v1/v2) and the new key plus lineage (v3), so updates install in place.
   - Android 9+ (API 28+) validates the v3 lineage and adopts the new key.
   - Android 8.0–8.1 (API 26–27) do not support v3; they validate the v2 signature made with the retired key, which still matches `v1.9.3` and earlier.
   - Fresh installs work on all supported versions.

To rotate again in the future:

```bash
apksigner rotate --out lineage-next.bin --in lineage.bin \
  --old-signer --ks current.jks --ks-key-alias moodtunes \
  --new-signer --ks next.jks --ks-key-alias moodtunes
```

`apksigner sign` then needs the oldest signer (for v1/v2) and the newest signer (for v3) plus the extended lineage — see `apksigner sign --help`. Validate with `apksigner verify --min-sdk-version 26 --max-sdk-version 27` and `apksigner lineage --in <apk> --print-certs` before publishing.

If the oldest key is lost, API 26–27 devices must reinstall; API 28+ devices can still update through the lineage. Losing the current key **and** the lineage forces a reinstall for everyone.

### 2. Secure In-App Updates

- **Host allow-list**: `UpdateEndpointPolicy` accepts only `https://` URLs on `github.com` / `githubusercontent.com` (including subdomains). Non-HTTPS URLs, lookalike hosts, `file://` and `intent://` URLs are rejected before any download starts. The policy is re-checked against the final URL after redirects.
- **Package + signer verification**: before the installer is launched, the downloaded APK's package name must equal `com.moodtunes.app`, and every signer of the installed app must appear in the download's signing history. History is used rather than strict equality because the v3 lineage legitimately lists both the retired and current certificates; the platform installer still performs the authoritative lineage check.
- **OS-level enforcement**: Android's PackageInstaller independently verifies the signature for updates on top of the app-level check.
- **Restricted FileProvider**: update APKs are shared through a private, unexported `FileProvider` with temporary read-only URI permission for the system installer only.

### 3. Zero Telemetry & Local-First Storage

- **No Third-Party Analytics**: MoodTunes contains zero trackers, telemetry, and advertisements.
- **On-Device Isolation**: Listening history, playlists, favorites, mood profiles and stats live in the app-private Room/SQLite database (`com.moodtunes.app`).
- **Backup Disabled**: `android:allowBackup="false"` plus `data_extraction_rules`, preventing extraction of the local database via ADB backup or cloud migration.

### 4. Database Integrity & Migrations

- Room schema export is enabled (`exportSchema = true`), and the generated schema JSON is committed under `app/schemas/`.
- The database uses **no destructive migration fallback**. An unhandled schema version therefore fails loudly instead of silently erasing the user's library, favorites, playlists and mood history.
- Schema changes must ship an explicit `Migration`, covered by `MIGRATION_4_5` today; migration tests will be added once a `4.json` schema baseline exists.
- `MoodTunesDbGuardian` keeps an automatic backup and restores it if a previous session ended in a corrupt state.

### 5. Hardened Network Configuration

- **HTTPS by default**: all outgoing requests (YouTube Music, Subsonic/Navidrome, LRCLIB lyrics, Last.fm, ListenBrainz) use TLS.
- **Cleartext blocked**: `cleartextTrafficPermitted="false"` is the base policy in [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml).
- **Development exceptions only**: `localhost`, `127.0.0.1` and `10.0.2.2` (the emulator host) are allowed over cleartext so local development servers work. These are the only hosts the Subsonic client accepts over `http://`; any other `http://` server URL is rejected with an actionable error before a request is attempted.
- **System trust anchors only**: `<certificates src="system" />` — user-installed proxy certificates are not trusted, which resists local MitM interception (at the cost of not supporting enterprise MITM proxies).

### 6. Credential & Token Protection

- **Encrypted at rest**: ListenBrainz tokens, Last.fm session keys and Navidrome/Subsonic passwords are encrypted with AES-256-GCM (`SecretCipher`) using a non-exportable key held in the Android Keystore. Values are stored with an `enc:v1:` marker plus per-value IV.
- **Transparent migration**: legacy plaintext values written by older versions are re-encrypted on first read and replaced in place. If encryption fails, the value is dropped rather than persisted in plaintext.
- **Minimal dispatch**: credentials are only sent to the host the user configured, over TLS; Subsonic requests use token+salt authentication rather than the plaintext password.

### 7. Component Hardening & IPC Isolation

- **Unexported playback service**: [`MusicPlaybackService`](app/src/main/java/com/moodtunes/app/service/MusicPlaybackService.kt) is `android:exported="false"`, so third-party apps cannot bind or control playback. System media controls and Android Auto connect through the Media3 `MediaSessionService` contract.
- **Unexported file providers**: cached media and update files are never exposed to other apps.

### 8. Sandboxed Code Execution

- **AndroidX JavaScriptEngine**: dynamic YouTube signature deciphering runs in an isolated V8 `JavaScriptSandbox` on-device, with no access to device files, network interfaces or system APIs. If sandboxed JS is unavailable or the solver fails, resolution falls back to the native InnerTube path.

### 9. Scoped Storage Compliance

- Targets SDK 36 and follows modern scoped storage. Downloaded tracks are written through `MediaStore.Audio`; the app does not request `MANAGE_EXTERNAL_STORAGE`.

---

## ⚖️ License

MoodTunes is licensed under the **GNU General Public License v3.0** (see
[`LICENSE`](LICENSE)). The project was relicensed from MIT to GPL-3.0 because
it depends on **NewPipe Extractor**, which is GPL-3.0. Prior releases and
commits remain available under the MIT terms they were published with.

License texts for every bundled component ship in `assets/licenses/` and are
visible in the app under **Settings → About MoodTunes → Open-source Licenses**.
See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the full inventory.

---

## 🚨 Reporting a Vulnerability

We deeply appreciate the efforts of security researchers and community contributors who practice responsible disclosure.

If you believe you have discovered a security vulnerability in MoodTunes, please report it privately rather than opening a public issue.

### Preferred Reporting Method: GitHub Private Vulnerability Reporting

1. Navigate to the **[Security Tab](https://github.com/cyberlog69/MoodTunes/security)** on the MoodTunes GitHub repository.
2. Click **Report a vulnerability** under *Advisories*.
3. Complete the advisory form with relevant technical details.

### What to Include in Your Report

- A clear description of the vulnerability and its potential impact.
- Affected application version(s) and Android OS versions tested.
- Step-by-step reproduction instructions or a minimal Proof-of-Concept.
- Any proposed remediation or mitigation you would suggest.

### Out-of-Scope Issues

Please avoid submitting:

- Issues that require a compromised/rooted host OS or physical tampering with unlocked devices.
- Reports generated entirely by automated scanners without verified real-world exploitability.
- Denial-of-Service attacks against external third-party services (YouTube, LRCLIB, ListenBrainz, Last.fm).
- YouTube playback breakage caused by upstream client changes — report those as regular bugs.

---

## ⏱️ Response & Disclosure Timeline

| Phase | Target Timeline |
|---|---|
| **Initial Acknowledgment** | Within 48 hours of report receipt |
| **Triage & Validation** | Within 5 business days |
| **Patch Development & Release** | High/Critical issues addressed via prioritized hotfix releases |
| **Public Disclosure** | Coordinated only after a patch has been published |

---

Thank you for helping keep MoodTunes and its community safe! 🎵
