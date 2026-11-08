package com.moodtunes.app.data.remote.api

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.moodtunes.app.data.local.preferences.SecretCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the YouTube Music web session captured from the in-app sign-in
 * WebView. The cookie header is encrypted at rest with [SecretCipher], the
 * same Android Keystore-backed helper used for Last.fm and Subsonic secrets.
 *
 * Anonymous browsing keeps working when no session exists — every member here
 * simply returns null and InnerTube falls back to a scraped visitorData.
 */
@Singleton
class YouTubeSessionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("moodtunes_youtube_session", Context.MODE_PRIVATE)

    private val cipher = SecretCipher(KEY_ALIAS)

    private val _isSignedIn = MutableStateFlow(hasAccountCookie(readCookies()))
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _accountName = MutableStateFlow(prefs.getString(KEY_ACCOUNT_NAME, "").orEmpty())
    val accountName: StateFlow<String> = _accountName.asStateFlow()

    /** Raw `Cookie` header value, or null when browsing anonymously. */
    fun cookieHeader(): String? = readCookies().takeIf { it.isNotBlank() }

    var visitorData: String?
        get() = prefs.getString(KEY_VISITOR_DATA, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_VISITOR_DATA) else putString(KEY_VISITOR_DATA, value)
            }.apply()
        }

    fun saveSession(cookieHeader: String, accountName: String? = null) {
        val trimmed = cookieHeader.trim()
        if (trimmed.isBlank()) return

        val encrypted = cipher.encrypt(trimmed)
        if (encrypted == null) {
            Timber.e("Refusing to persist YouTube cookies without encryption")
            return
        }
        prefs.edit()
            .putString(KEY_COOKIES, encrypted)
            .putString(KEY_ACCOUNT_NAME, accountName?.trim().orEmpty())
            .apply()
        _accountName.value = accountName?.trim().orEmpty()
        _isSignedIn.value = hasAccountCookie(trimmed)
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_COOKIES)
            .remove(KEY_ACCOUNT_NAME)
            .apply()
        _accountName.value = ""
        _isSignedIn.value = false
        // Clearing only the encrypted copy is not enough: the sign-in WebView
        // still holds the live Google session, so a later visit would silently
        // re-capture the same cookies.
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }.onFailure { Timber.w(it, "Could not clear the WebView session") }
    }

    /**
     * `Authorization: SAPISIDHASH <ts>_<sha1>` is required by account-scoped
     * InnerTube endpoints such as the account menu and library mutations.
     */
    fun sapisidHash(): String? {
        val cookies = readCookies()
        if (cookies.isBlank()) return null
        val sapisid = cookieValue(cookies, "SAPISID")
            ?: cookieValue(cookies, "__Secure-3PAPISID")
            ?: return null
        val timestamp = System.currentTimeMillis() / 1000L
        val digest = sha1("$timestamp $sapisid https://music.youtube.com")
        return "SAPISIDHASH ${timestamp}_$digest"
    }

    private fun readCookies(): String {
        val stored = prefs.getString(KEY_COOKIES, "") ?: ""
        if (stored.isEmpty()) return ""
        return if (cipher.isEncrypted(stored)) cipher.decrypt(stored).orEmpty() else stored
    }

    private fun hasAccountCookie(cookies: String): Boolean =
        cookies.isNotBlank() && (
            cookieValue(cookies, "SAPISID") != null ||
                cookieValue(cookies, "__Secure-3PAPISID") != null ||
                cookieValue(cookies, "SID") != null
            )

    private fun cookieValue(cookies: String, name: String): String? =
        cookies.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val KEY_COOKIES = "cookies"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_VISITOR_DATA = "visitor_data"
        const val KEY_ALIAS = "moodtunes_youtube_session_key"
    }
}
