package com.moodtunes.app.data.remote.stream

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which InnerTube player client last produced a playable stream and
 * temporarily demotes clients that failed.
 *
 * YouTube rotates client behaviour frequently: a client that works today can
 * start returning `LOGIN_REQUIRED` or SABR-only responses tomorrow. Trying the
 * known-good client first keeps first-play latency low, and the cooldown stops
 * us paying for dead clients (IOS without SABR, TV without cookies) on every
 * track.
 */
@Singleton
class PlayerClientHealthStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("moodtunes_player_client_health", Context.MODE_PRIVATE)

    fun lastGoodClient(): String? =
        prefs.getString(KEY_LAST_GOOD, null)?.takeIf { it.isNotBlank() }

    fun coolingClients(now: Long = System.currentTimeMillis()): Set<String> {
        val cooling = mutableSetOf<String>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith(FAILURE_PREFIX)) continue
            val failedAt = (value as? Long) ?: continue
            if (now - failedAt < FAILURE_COOLDOWN_MS) {
                cooling += key.removePrefix(FAILURE_PREFIX)
            }
        }
        return cooling
    }

    fun recordSuccess(clientName: String) {
        prefs.edit()
            .putString(KEY_LAST_GOOD, clientName)
            .remove(FAILURE_PREFIX + clientName)
            .apply()
    }

    fun recordFailure(clientName: String, failedAt: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(FAILURE_PREFIX + clientName, failedAt)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_LAST_GOOD = "last_good_client"
        const val FAILURE_PREFIX = "failed:"
        const val FAILURE_COOLDOWN_MS = 10 * 60 * 1000L
    }
}
