package com.moodtunes.app.data.remote.stream

import com.moodtunes.app.data.local.preferences.StreamQuality
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFormatRankingTest {

    private fun format(itag: Int, mimeType: String, bitrate: Int) = JSONObject()
        .put("itag", itag)
        .put("mimeType", mimeType)
        .put("bitrate", bitrate)

    private val aac48 = format(139, "audio/mp4; codecs=\"mp4a.40.5\"", 48_000)
    private val aac130 = format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 130_000)
    private val aac256 = format(141, "audio/mp4; codecs=\"mp4a.40.2\"", 258_000)
    private val opus64 = format(250, "audio/webm; codecs=\"opus\"", 64_000)
    private val opus160 = format(251, "audio/webm; codecs=\"opus\"", 163_000)
    private val opus256 = format(774, "audio/webm; codecs=\"opus\"", 277_000)

    private val all = listOf(aac48, aac130, aac256, opus64, opus160, opus256)

    @Test
    fun `auto prefers 256k aac over 160k opus`() {
        val ranked = rankAudioFormats(listOf(opus160, aac256), StreamQuality.AUTO)
        assertEquals(141, ranked.first().optInt("itag"))
    }

    @Test
    fun `auto uses opus as the tie breaker`() {
        val ranked = rankAudioFormats(
            listOf(aac130, format(999, "audio/webm; codecs=\"opus\"", 130_000)),
            StreamQuality.AUTO
        )
        assertEquals(999, ranked.first().optInt("itag"))
    }

    @Test
    fun `high caps at 192k and maximum takes the best`() {
        assertEquals(251, rankAudioFormats(all, StreamQuality.HIGH).first().optInt("itag"))
        assertEquals(774, rankAudioFormats(all, StreamQuality.LOSSLESS).first().optInt("itag"))
    }

    @Test
    fun `standard targets 128k and data saver takes the smallest`() {
        assertEquals(140, rankAudioFormats(all, StreamQuality.STANDARD).first().optInt("itag"))
        assertEquals(139, rankAudioFormats(all, StreamQuality.DATA_SAVER).first().optInt("itag"))
    }
}
