# 🛡️ MoodTunes Security Policy & Privacy Brief

At **MoodTunes**, we take the security, privacy, and integrity of your music listening experience seriously. As a private, open-source, local-first media player, MoodTunes is engineered from the ground up to respect user autonomy, eliminate third-party tracking, and safeguard media playback.

---

## 📌 Supported Versions

Security patches and dependency updates are actively maintained for the latest release branches. Users are encouraged to stay on the latest stable release.

| Version | Status | Security Support Level |
|---|---|---|
| **v1.8.x** | **Current Stable** | ✅ Full security updates, bug fixes, and maintenance |
| **v1.7.x** | Maintenance | ⚠️ Critical security patches only |
| **< v1.7.0** | Deprecated | ❌ Unsupported; please update to the latest release |

---

## 🔒 Security & Privacy Architecture

MoodTunes adheres to strict security and privacy standards across its Android architecture:

### 1. Zero Telemetry & Local-First Storage
- **No Third-Party Analytics**: MoodTunes contains **zero trackers, zero telemetry, and zero advertisements**.
- **On-Device Isolation**: Your listening habits, playlists, favorite songs, mood profiles, and replay stats are stored strictly inside private, sandboxed SQLite / Room databases (`com.moodtunes.app`) on your local device. No data is transmitted to central servers.

### 2. Hardened Network Security Configuration
- **HTTPS & TLS Enforcement**: All outgoing network requests (YouTube Music audio streaming, Subsonic/Navidrome endpoints, LRCLIB lyrics, and ListenBrainz scrobbling) mandate TLS 1.2 / 1.3 encryption.
- **No Cleartext Traffic**: Unencrypted cleartext (`HTTP`) communication is strictly blocked at the Android operating system level via [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml) (`cleartextTrafficPermitted="false"`), preventing man-in-the-middle (MitM) and downgrade attacks.

### 3. Credential & API Token Protection
- **Self-Hosted Credentials**: User-provided Navidrome / Subsonic passwords and ListenBrainz user tokens are stored exclusively in Android app-private storage (`MODE_PRIVATE` SharedPreferences).
- **No Credential Leakage**: Server credentials and tokens are only dispatched over authenticated, encrypted channels to the user's explicitly configured host endpoints.

### 4. Sandboxed Code Execution
- **AndroidX JavaScriptEngine (V8)**: Dynamic YouTube streaming signature solvers execute within a sandboxed, restricted V8 runtime on-device, completely isolated from device files, network interfaces, and system APIs.

### 5. Scoped Storage Compliance
- MoodTunes strictly adheres to modern Android Scoped Storage guidelines (Target SDK 36, Android 15/16). Downloaded tracks are stored via the standard `MediaStore.Audio` API without requiring broad, legacy filesystem read/write privileges.

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
- Denial-of-Service attacks targeting external third-party services (e.g. YouTube, LRCLIB, ListenBrainz).

---

## ⏱️ Response & Disclosure Timeline

| Phase | Target Timeline |
|---|---|
| **Initial Acknowledgment** | Within **48 hours** of report receipt |
| **Triage & Validation** | Within **5 business days** |
| **Patch Development & Release** | High/Critical vulnerabilities addressed via prioritized hotfix releases |
| **Public Disclosure** | Coordinated only **after** a security patch has been published |

Thank you for helping keep MoodTunes and its community safe! 🎵
