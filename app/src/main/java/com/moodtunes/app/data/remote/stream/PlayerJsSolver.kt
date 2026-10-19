package com.moodtunes.app.data.remote.stream

import android.content.Context
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.javascriptengine.EvaluationFailedException
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Solves the challenges in YouTube audio/video stream URLs:
 * - Signature challenge (`sig` / `signatureCipher` / `s`)
 * - Throttling challenge (`n` parameter) which YouTube uses to cap playback at ~40 kbps
 *
 * Uses yt-dlp's EJS solver (bundled in assets/ejs), executed inside Android's native isolated
 * V8 JavaScript engine via androidx.javascriptengine (JavaScriptSandbox).
 */
@Singleton
class PlayerJsSolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient
) {
    private class Player(val id: String, val code: String, val sts: Int)

    private val lock = Any()
    private val players = LinkedHashMap<String, Player>()
    private var latestId: String? = null
    private var checkedAt = 0L
    private var sandbox: JavaScriptSandbox? = null
    private var isolate: JavaScriptIsolate? = null
    private var loadedId: String? = null

    /**
     * Retrieves the current YouTube player's ID and signature timestamp (sts).
     */
    fun currentPlayer(): Pair<String, Int> = synchronized(lock) {
        latest().let { it.id to it.sts }
    }

    /**
     * Solves [sig] and [n] challenges for [playerId].
     * Must NOT be called on Android's main thread.
     */
    fun solve(playerId: String, sig: String?, n: String?): Pair<String?, String?> = synchronized(lock) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "PlayerJsSolver.solve cannot be called on the main thread" }
        val iso = isolateFor(playerId)
        val args = JSONArray().apply {
            put(sig ?: JSONObject.NULL)
            put(n ?: JSONObject.NULL)
        }.toString()

        val rawResult = evaluate(iso, "prismSolve.apply(null, $args)", SOLVE_TIMEOUT_S)
        val out = JSONObject(rawResult)
        if (out.has("error") && !out.isNull("error")) {
            throw IOException("Player JS solver error: ${out.getString("error")}")
        }

        val solvedSig = if (out.has("sig") && !out.isNull("sig")) out.getString("sig") else null
        val solvedN = if (out.has("n") && !out.isNull("n")) out.getString("n") else null
        solvedSig to solvedN
    }

    private fun latest(): Player {
        val now = System.currentTimeMillis()
        val known = latestId?.let { players[it] }
        if (known != null && now - checkedAt < RECHECK_MS) return known

        val id = try {
            val iframeApi = get("https://www.youtube.com/iframe_api")
            PLAYER_ID.find(iframeApi)?.groupValues?.get(1) ?: throw IOException("No player id found in iframe_api")
        } catch (e: IOException) {
            return known ?: throw e
        }
        checkedAt = now
        latestId = id
        return player(id)
    }

    private fun player(id: String): Player {
        players[id]?.let { return it }
        val code = get("https://www.youtube.com/s/player/$id/player_ias.vflset/en_US/base.js")
        val sts = STS.find(code)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IOException("No signature timestamp found in player $id")
        val p = Player(id, code, sts)
        players[id] = p
        while (players.size > 2) {
            val firstKey = players.keys.firstOrNull { it != id } ?: break
            players.remove(firstKey)
        }
        return p
    }

    private fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} fetching $url")
            return r.body?.string() ?: throw IOException("Empty body for $url")
        }
    }

    private fun isolateFor(playerId: String): JavaScriptIsolate {
        isolate?.let { if (loadedId == playerId) return it }
        val p = player(playerId)
        val iso = isolate ?: newIsolate()
        val escapedCode = JSONObject.quote(p.code)
        val result = evaluateLarge(iso, "prismLoad($escapedCode)")
        if (result != "ok") throw IOException("Failed loading player $playerId: $result")
        loadedId = playerId
        return iso
    }

    private fun newIsolate(): JavaScriptIsolate {
        if (!JavaScriptSandbox.isSupported()) {
            throw IOException("Android System WebView JavaScriptSandbox is not supported on this device")
        }
        val sb = sandbox ?: try {
            JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext)
                .get(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw IOException("Couldn't start JavaScript sandbox", e.cause)
        }.also { sandbox = it }

        val iso = sb.createIsolate(IsolateStartupParameters())
        isolate = iso
        loadedId = null

        val lib = context.assets.open("ejs/yt.solver.lib.min.js").use { it.readBytes().decodeToString() }
        val core = context.assets.open("ejs/yt.solver.core.min.js").use { it.readBytes().decodeToString() }
        evaluate(iso, "$lib\nvar meriyah = lib.meriyah, astring = lib.astring;\n$core\n$GLUE", LOAD_TIMEOUT_S)
        return iso
    }

    private fun evaluateLarge(iso: JavaScriptIsolate, script: String): String {
        val sb = sandbox!!
        if (sb.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT)) {
            return evaluate(iso, script, LOAD_TIMEOUT_S)
        }
        if (!sb.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_FROM_FD)) {
            throw IOException("Update Android System WebView to process YouTube stream deciphering")
        }
        val file = File(context.cacheDir, "player-solver.js")
        try {
            file.writeText(script)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                await(iso.evaluateJavaScriptAsync(fd), LOAD_TIMEOUT_S)
            }
        } finally {
            if (file.exists()) file.delete()
        }
    }

    private fun evaluate(iso: JavaScriptIsolate, script: String, timeoutS: Long): String =
        await(iso.evaluateJavaScriptAsync(script), timeoutS)

    private fun await(future: java.util.concurrent.Future<String>, timeoutS: Long): String = try {
        future.get(timeoutS, TimeUnit.SECONDS)
    } catch (e: ExecutionException) {
        if (e.cause !is EvaluationFailedException) reset()
        throw IOException("Player JS failed: ${e.cause?.message}", e.cause)
    } catch (e: Exception) {
        reset()
        throw IOException("Player JS execution failed: $e", e)
    }

    private fun reset() {
        runCatching { isolate?.close() }
        isolate = null
        loadedId = null
        runCatching { sandbox?.close() }
        sandbox = null
    }

    private companion object {
        val PLAYER_ID = Regex("""player[\\/]([0-9a-fA-F]{8})[\\/]""")
        val STS = Regex("""(?:signatureTimestamp|sts)\s*:\s*(\d{5})""")
        const val RECHECK_MS = 60 * 60_000L
        const val CONNECT_TIMEOUT_S = 20L
        const val LOAD_TIMEOUT_S = 60L
        const val SOLVE_TIMEOUT_S = 15L

        val GLUE = """
            var prismSolvers = null;
            function prismLoad(code) {
              try {
                var out = jsc({ type: "player", player: code, requests: [], output_preprocessed: true });
                if (out.type !== "result") return String(out.error).slice(0, 2000);
                var r = { n: null, sig: null };
                Function("_result", out.preprocessed_player)(r);
                if (!r.sig) return "no signature function found";
                prismSolvers = r;
                return "ok";
              } catch (e) {
                return String((e && e.stack) || e).slice(0, 2000);
              }
            }
            function prismSolve(sig, n) {
              try {
                return JSON.stringify({
                  sig: sig == null ? null : prismSolvers.sig(sig),
                  n: n == null || !prismSolvers.n ? n : prismSolvers.n(n),
                });
              } catch (e) {
                return JSON.stringify({ error: String(e).slice(0, 2000) });
              }
            }
            "ready";
        """.trimIndent()
    }
}
