package com.moodtunes.app.data.remote.api

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Small, defensive JSON helpers for YouTube Music's deeply nested renderers.
 * InnerTube payloads change shape often and omit fields freely, so every helper
 * returns null rather than throwing.
 */

internal fun JSONObject?.obj(vararg keys: String): JSONObject? {
    var cursor: JSONObject = this ?: return null
    for (key in keys) {
        cursor = cursor.optJSONObject(key) ?: return null
    }
    return cursor
}

internal fun JSONObject?.arr(key: String): JSONArray? = this?.optJSONArray(key)

internal fun JSONArray?.objects(): List<JSONObject> {
    val source = this ?: return emptyList()
    return (0 until source.length()).mapNotNull { source.optJSONObject(it) }
}

/** Text of a `{ simpleText }` or `{ runs: [...] }` node. */
internal fun JSONObject?.text(): String? {
    val node = this ?: return null
    node.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
    node.optString("content").takeIf { it.isNotBlank() }?.let { return it }
    val runs = node.optJSONArray("runs")
    if (runs != null) {
        val value = buildString {
            for (index in 0 until runs.length()) {
                append(runs.optJSONObject(index)?.optString("text").orEmpty())
            }
        }
        if (value.isNotBlank()) return value
    }
    node.optString("text").takeIf { it.isNotBlank() && it != "null" }?.let { return it }
    return null
}

/** Every object named [name] anywhere below this node. */
internal fun JSONObject?.findAll(name: String): List<JSONObject> {
    val root = this ?: return emptyList()
    val found = mutableListOf<JSONObject>()

    fun walk(value: Any?) {
        when (value) {
            is JSONObject -> {
                value.optJSONObject(name)?.let { found.add(it) }
                val keys = value.keys()
                while (keys.hasNext()) walk(value.opt(keys.next()))
            }
            is JSONArray -> for (index in 0 until value.length()) walk(value.opt(index))
        }
    }

    walk(root)
    return found
}

internal fun JSONObject?.findArray(name: String): JSONArray? {
    val root = this ?: return null
    if (root.has(name)) root.optJSONArray(name)?.let { return it }
    val keys = root.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = root.opt(key)
        when (value) {
            is JSONObject -> value.findArray(name)?.let { return it }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    val child = value.optJSONObject(index) ?: continue
                    child.findArray(name)?.let { return it }
                }
            }
        }
    }
    return null
}

/** Best (largest) thumbnail URL out of whatever wrapper the renderer used. */
internal fun JSONObject?.bestThumbnail(): String? {
    val array = this?.findArray("thumbnails") ?: return null
    var best: String? = null
    var bestArea = -1
    for (index in 0 until array.length()) {
        val thumb = array.optJSONObject(index) ?: continue
        val url = thumb.optString("url").takeIf { it.isNotBlank() } ?: continue
        val area = thumb.optInt("width", 0) * thumb.optInt("height", 0)
        if (area >= bestArea) {
            bestArea = area
            best = url
        }
    }
    return best
}

internal fun upscaleArtwork(url: String?): Uri? {
    val raw = url?.takeIf { it.isNotBlank() } ?: return null
    val upscaled = raw.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
        .replace(Regex("=s\\d+"), "=s544")
    return Uri.parse(upscaled)
}

internal fun stableSongId(videoId: String): Long =
    (videoId.hashCode().toLong() and 0x0FFFFFFFFFFFFFFFL) or (1L shl 60)

internal fun parseDurationMs(text: String?): Long {
    val value = text?.trim() ?: return 0L
    val match = Regex("""^\d{1,2}(?::\d{2}){1,2}$""").find(value) ?: return 0L
    val parts = match.value.split(":").mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        2 -> (parts[0] * 60 + parts[1]) * 1000L
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
        else -> 0L
    }
}

internal fun parsePlayCount(text: String?): Long? {
    val value = text?.trim() ?: return null
    val match = Regex("""([\d.,]+)\s*([KMB])?\s*(?:plays|views)""", RegexOption.IGNORE_CASE)
        .find(value) ?: return null
    val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase(Locale.ROOT)) {
        "K" -> 1_000.0
        "M" -> 1_000_000.0
        "B" -> 1_000_000_000.0
        else -> 1.0
    }
    return (number * multiplier).toLong()
}

internal fun splitSubtitle(text: String?): List<String> =
    text.orEmpty()
        .split("•", "·")
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "•" }

internal fun JSONObject?.pageType(): String? =
    this?.obj(
        "browseEndpoint",
        "browseEndpointContextSupportedConfigs",
        "browseEndpointContextMusicConfig"
    )?.optString("pageType")?.takeIf { it.isNotBlank() }
