package com.moodtunes.app.data.remote

import java.net.URI

/**
 * Allow-list policy for the in-app updater.
 *
 * Only HTTPS URLs served by GitHub (the sole distribution channel) may be
 * downloaded and installed. This prevents a tampered release feed or an
 * injected redirect from pointing the installer at an arbitrary host.
 */
object UpdateEndpointPolicy {

    private val TRUSTED_HOSTS = listOf(
        "github.com",
        "githubusercontent.com"
    )

    fun isTrustedReleaseUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return TRUSTED_HOSTS.any { trusted -> host == trusted || host.endsWith(".$trusted") }
    }
}
