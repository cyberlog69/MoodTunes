package com.moodtunes.app.data.local.db

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes [Song] objects to/from JSON so playlists and backups can persist
 * full song snapshots (including online streams) without a direct dependency
 * on the source repository.
 */
object SongJsonSerializer {

    fun serialize(song: Song): String = JSONObject().apply {
        put("id", song.id)
        put("title", song.title)
        put("artist", song.artist)
        put("album", song.album)
        put("duration", song.duration)
        put("uri", song.uri?.toString() ?: "")
        put("albumArtUri", song.albumArtUri?.toString() ?: JSONObject.NULL)
        put("genre", song.genre ?: JSONObject.NULL)
        put("isFavorite", song.isFavorite)
        put("isStream", song.isStream)
        put("playCount", song.playCount)
        put("lastPlayedAt", song.lastPlayedAt)
        put("audioFormat", song.audioFormat.name)
        put("moodTags", JSONArray(song.moodTags.map { it.name }))
    }.toString()

    fun deserialize(json: String): Song? = runCatching {
        val obj = JSONObject(json)
        Song(
            id = obj.optLong("id"),
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            album = obj.optString("album"),
            duration = obj.optLong("duration"),
            uri = Uri.parse(obj.optString("uri")),
            albumArtUri = if (obj.isNull("albumArtUri")) null else Uri.parse(obj.optString("albumArtUri")),
            genre = if (obj.isNull("genre")) null else obj.optString("genre"),
            isFavorite = obj.optBoolean("isFavorite"),
            moodTags = runCatching {
                val arr = obj.optJSONArray("moodTags") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { MoodType.valueOf(arr.optString(i)) }.getOrNull()
                }
            }.getOrDefault(emptyList()),
            audioFormat = runCatching { AudioFormat.valueOf(obj.optString("audioFormat")) }.getOrDefault(AudioFormat.MP3),
            isStream = obj.optBoolean("isStream"),
            playCount = obj.optInt("playCount"),
            lastPlayedAt = obj.optLong("lastPlayedAt")
        )
    }.getOrNull()
}
