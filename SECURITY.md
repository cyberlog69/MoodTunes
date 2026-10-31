# 🛡️ MoodTunes Security Policy & Privacy Brief

At **MoodTunes**, we take the security, privacy, and integrity of your music listening experience seriously. As a private, open-source, local-first media player, MoodTunes is engineered from the ground up to respect user autonomy, eliminate third-party tracking, and safeguard media playback.

---

## 📌 Supported Versions

Security patches and dependency updates are actively maintained for the latest release branch. Users are strongly encouraged to stay on the latest stable release.

| Version | Status | Security Support Level |
|---|---|---|
| **v1.9.x (v1.9.2+)** | **Current Stable (Primary Release)** | ✅ Full security updates, bug fixes, and continuous maintenance |
| **< v1.9.0** | **Deprecated / Revoked** | ❌ Unsupported; legacy releases signed with superseded keys. Please upgrade to v1.9.2+ |

> [!IMPORTANT]
> **Unified Signing Architecture**: Starting with `v1.9.2`, MoodTunes uses a permanent repository release keystore with APK Signature Schemes v2, v3, and v4. All previous releases prior to `v1.9.0` used ephemeral CI build signatures and have been retired. To transition to the unified signature line, install `v1.9.2+` cleanly.

---

## 🔒 Security & Privacy Architecture

MoodTunes adheres to defense-in-depth security and privacy standards across its Android architecture:

### 1. Cryptographic Binary Signing & Integrity
- **Unified Release Keystore**: Official release binaries are signed using a permanent 2048-bit RSA release key (`SHA384withRSA`) with validity extending through **March 2054**.
- **Multi-Scheme Enforcement**: Releases mandate **APK Signature Schemes v2 (Full APK), v3 (Key Rotation), and v4 (Streaming)** in addition to legacy v1 jar signing, preventing binary tampering, stripping attacks, and APK repacking.
- **Certificate Provenance**: Users and security researchers can verify authentic MoodTunes APK binaries using `apksigner`:
  - **Owner / Issuer**: `CN=MoodTunes, OU=App, O=MoodTunes, L=Global, ST=Global, C=US`
  - **SHA-256 Fingerprint**: `43:76:DF:FB:4A:F3:BC:5D:6E:26:E4:4A:59:F9:DA:22:03:38:43:95:0F:FD:C2:B5:8D:2B:E0:0B:87:50:C6:0F`
  - **SHA-1 Fingerprint**: `AE:82:2A:6F:03:D8:DA:67:97:7B:0B:67:4B:B8:22:7A:9A:AF:90:BB`

### 2. Secure In-App Updates
- **Cryptographic Verification**: Updates downloaded via the in-app updater are fetched over TLS directly from the official GitHub Releases API.
- **Android PackageInstaller Protection**: Android's OS-level package installer cryptographically checks that the update's signing certificate matches the currently installed application. An APK with an invalid or mismatched signature is unconditionally rejected by Android, preventing package hijacking.
- **Restricted FileProvider**: Downloaded APK update files are handled through a private `FileProvider` (`android:exported="false"`) granting temporary, scoped read-only URI permissions to the system installer.

### 3. Zero Telemetry & Local-First Storage
- **No Third-Party Analytics**: MoodTunes contains **zero trackers, zero telemetry, and zero advertisements**.
- **On-Device Isolation**: Listening history, playlists, favorite tracks, mood profiles, and replay stats are stored strictly inside private, sandboxed Room / SQLite databases (`com.moodtunes.app`) on your local device.
- **Backup Disabled**: Android auto-backup is disabled (`android:allowBackup="false"`) with strict `data_extraction_rules` to prevent local sensitive databases from being extracted via ADB backup or device migration backups.

### 4. Hardened Network Security Configuration
- **HTTPS & TLS Enforcement**: All outgoing network requests (YouTube Music audio streaming, Subsonic/Navidrome endpoints, LRCLIB lyrics, Last.fm scrobbling, and ListenBrainz scrobbling) mandate TLS 1.2 or 1.3 encryption.
- **No Cleartext Traffic**: Unencrypted cleartext (`HTTP`) communication is strictly blocked at the Android operating system level via [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml) (`cleartextTrafficPermitted="false"`), preventing man-in-the-middle (MitM) and downgrade attacks.
- **System-Only Trust Anchors**: The application relies exclusively on system-managed CA certificates (`<certificates src="system" />`), resisting unauthorized interception from user-installed proxy certificates.

### 5. Credential & API Token Protection
- **App-Private Storage**: User-provided Navidrome / Subsonic credentials, Last.fm session keys, and ListenBrainz user tokens are saved exclusively in Android app-private storage (`MODE_PRIVATE` SharedPreferences).
- **Targeted Endpoint Dispatch**: Server credentials and tokens are only dispatched over authenticated, encrypted TLS channels to the user's explicitly configured host endpoints.
- **Token + Salt Authentication**: For Subsonic/Navidrome, the client prefers MD5/token-salt authentication per Subsonic API specifications rather than transmitting plaintext passwords where supported.

### 6. Component Hardening & IPC Isolation
- **Unexported Playback Service**: [`MusicPlaybackService`](app/src/main/java/com/moodtunes/service/MusicPlaybackService.kt) explicitly sets `android:exported="false"`, preventing unauthorized third-party apps on the device from binding or controlling media playback. Legitimate OS-level media session controls and Android Auto connect securely via standard Media3 `MediaSessionService` intent filters.
- **Unexported File Sharing**: All internal file providers are unexported (`android:exported="false"`), preventing third-party access to cached media or internal storage.

### 7. Sandboxed Code Execution
- **AndroidX JavaScriptEngine (V8)**: Dynamic YouTube streaming signature deciphering executes within an isolated, sandboxed V8 runtime on-device, completely restricted from device files, network interfaces, and system APIs.

### 8. Modern Scoped Storage Compliance
- MoodTunes adheres to modern Android Scoped Storage guidelines (Target SDK 36, Android 15/16). Downloaded tracks are saved via the standard `MediaStore.Audio` API, avoiding broad legacy filesystem read/write privileges (`MANAGE_EXTERNAL_STORAGE`).

---

## 🚨 Reporting a Vulnerability

We deeply appreciate the efforts of security researchers and community contributors who practice responsible disclosure.

If you believe you have discovered a security vulnerability in MoodTunes, please report it privately rather than opening a public issue.

### Preferred Reporting Method: GitHub Private Vulnerability Reporting
1. Navigate to the **[Security Tab](https://github.com/cyberlog69/MoodTunes/security)** on the MoodTunes GitHub repository.
2. Click on **Report a vulnerability** under *Advisories*.
3. Complete the advisory form with relevant technical details.

### What to Include in Your Report
To help us triage and resolve the issue quickly, please provide:
- A clear description of the vulnerability and its potential impact.
- Affected application version(s) and Android OS versions tested.
- Detailed step-by-step reproduction instructions or a minimal Proof-of-Concept (PoC).
- Any proposed remediation, mitigation, or suggested patch.

### Out-of-Scope Issues
Please avoid submitting:
- Issues that require a compromised/rooted host OS or physical tampering with unlocked devices.
- Reports generated entirely by automated vulnerability scanners without verified real-world exploitability.
- Denial-of-Service attacks targeting external third-party services (e.g., YouTube, LRCLIB, ListenBrainz, Last.fm).

---

## ⏱️ Response & Disclosure Timeline

| Phase | Target Timeline |
|---|---|
| **Initial Acknowledgment** | Within **48 hours** of report receipt |
| **Triage & Validation** | Within **5 business days** |
| **Patch Development & Release** | High/Critical vulnerabilities addressed via prioritized hotfix releases |
| **Public Disclosure** | Coordinated only **after** a security patch has been published |

---

Thank you for helping keep MoodTunes and its community safe! 🎵
