# 🛡️ MoodTunes Security Policy & Privacy Brief

At **MoodTunes**, we take the security, privacy, and integrity of your music listening experience seriously. As a private, open-source, local-first media player, MoodTunes is engineered to respect user autonomy, avoid third-party tracking, and safeguard media playback.

---

## 📌 Supported Versions

Security patches and dependency updates are maintained for the latest release branch. Users are strongly encouraged to stay on the latest stable release.

| Version | Status | Security Support Level |
|---|---|---|
| **v1.9.x (v1.9.3+)** | **Current Stable (Primary Release)** | ✅ Security fixes, bug fixes, and maintenance |
| **< v1.9.0** | **Deprecated** | ❌ Unsupported; releases before v1.9.0 used ephemeral CI signatures. Please upgrade |

> [!IMPORTANT]
> **Release keys are no longer stored in this repository.** Up to v1.9.1 the release keystore and its password were committed to git, so that password must be treated as public. See [Release signing & key rotation](#-release-signing--key-rotation) — rotating the key is strongly recommended.

---

## 🔒 Security & Privacy Architecture

### 1. Release Signing

- Release APKs are signed with APK Signature Schemes v1, v2, v3 and v4 when signing credentials are provided.
- Signing material is supplied at build time through environment variables (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) or a local, git-ignored `keystore.properties`. There is no hard-coded fallback: without credentials the build produces an unsigned APK and logs a warning.
- `*.keystore`, `*.jks` and `keystore.properties` are git-ignored.
- CI refuses to build a release if the keystore secret is missing, runs unit tests first, and verifies the produced APK with `apksigner verify` before uploading.

Verify a downloaded APK:

```bash
apksigner verify --print-certs MoodTunes-v1.9.2.apk
```

#### Release signing & key rotation

The historical key's password is public, so anyone with a copy of that key file could produce an APK that Android accepts as an update. Rotate it:

1. Generate a new keystore with a fresh, strong password (keep it only in your password manager / CI secrets):
   ```bash
   keytool -genkeypair -v -keystore moodtunes-upload.jks -alias moodtunes \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Produce a v3 rotation lineage signed by **both** the old and new keys, using the old APK as the lineage source:
   ```bash
   apksigner rotate --out lineage.bin \
     --old-signer --ks old-release.jks \
     --new-signer --ks moodtunes-upload.jks
   ```
3. Sign subsequent releases with the new key and the lineage:
   ```bash
   apksigner sign --ks moodtunes-upload.jks \
     --lineage lineage.bin --rotation-min-sdk-version 28 \
     --out MoodTunes-v1.10.0.apk app-release-unsigned.apk
   ```
   Or configure `signingConfigs.release` with a `apksigner` lineage file in Gradle.
4. Keep the old key available for the lifetime of the lineage; future rotations extend it.
5. If rotation is not possible, users must uninstall and reinstall — an APK signed by a different key will always be rejected as an update. Document that in the release notes.

Note that a rotated key with lineage requires Android 9 (API 28) or newer for transparent updates; MoodTunes supports API 26+, so devices below API 28 will need the reinstall path.

### 2. Secure In-App Updates

- **Host allow-list**: `UpdateEndpointPolicy` accepts only `https://` URLs on `github.com` / `githubusercontent.com` (including subdomains). Non-HTTPS URLs, lookalike hosts, `file://` and `intent://` URLs are rejected before any download starts. The policy is re-checked against the final URL after redirects.
- **Package + signer verification**: before the installer is launched, the downloaded APK's package name must equal `com.moodtunes.app` and its signing certificate set must match the installed app's. A mismatch is logged and the file is deleted; the user is offered the browser fallback instead.
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
