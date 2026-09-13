package com.moodtunes.app.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsTranslationService @Inject constructor() {

    private val client = OkHttpClient()

    /**
     * Translates a list of lyric line strings into the target language (defaults to device language or English).
     * Returns a map of index to translated string.
     */
    suspend fun translateLines(
        lines: List<String>,
        targetLanguageCode: String = Locale.getDefault().language.ifBlank { "en" }
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyMap()

        // Batch lines into chunks of up to 40 lines to avoid URL length limits
        val chunkSize = 40
        val resultMap = mutableMapOf<Int, String>()

        lines.chunked(chunkSize).forEachIndexed { chunkIndex, chunk ->
            val joinedText = chunk.joinToString("\n") { it.ifBlank { " " } }
            val encoded = URLEncoder.encode(joinedText, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguageCode&dt=t&q=$encoded"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("Translation request failed with code: ${response.code}")
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    val jsonArray = JSONArray(body)
                    val sentencesArray = jsonArray.optJSONArray(0) ?: return@use

                    val translatedBuilder = StringBuilder()
                    for (i in 0 until sentencesArray.length()) {
                        val sentence = sentencesArray.optJSONArray(i) ?: continue
                        val piece = sentence.optString(0)
                        translatedBuilder.append(piece)
                    }

                    val translatedLines = translatedBuilder.toString().lines()
                    chunk.forEachIndexed { lineIdxInChunk, _ ->
                        val globalIndex = chunkIndex * chunkSize + lineIdxInChunk
                        val translatedLine = translatedLines.getOrNull(lineIdxInChunk)?.trim()
                        if (!translatedLine.isNullOrBlank()) {
                            resultMap[globalIndex] = translatedLine
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Exception while translating lyrics chunk $chunkIndex")
            }
        }

        resultMap
    }
}
