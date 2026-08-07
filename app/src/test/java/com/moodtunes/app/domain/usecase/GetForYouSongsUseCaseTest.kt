package com.moodtunes.app.domain.usecase

import android.net.Uri
import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMoodRepository
import com.moodtunes.app.domain.repository.IMusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GetForYouSongsUseCaseTest {

    private fun song(
        id: Long,
        artist: String = "Artist",
        album: String = "Album",
        playCount: Int = 0,
        isFavorite: Boolean = false,
        moodTags: List<MoodType> = emptyList()
    ) = Song(
        id = id,
        title = "Title $id",
        artist = artist,
        album = album,
        duration = 180_000,
        uri = Uri.parse("content://test/$id"),
        albumArtUri = null,
        isFavorite = isFavorite,
        moodTags = moodTags,
        playCount = playCount
    )

    private class FakeMusicRepository(
        private val all: List<Song>,
        private val favorites: List<Song>,
        private val mostPlayed: List<Song>
    ) : IMusicRepository {
        override suspend fun getAllSongs(): List<Song> = all
        override suspend fun getSongsByMood(mood: MoodType): List<Song> = emptyList()
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(favorites)
        override suspend fun toggleFavorite(songId: Long): Boolean = false
        override suspend fun searchSongs(query: String): List<Song> = emptyList()
        override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> = flowOf(mostPlayed)
        override fun getMostPlayed(limit: Int): Flow<List<Song>> = flowOf(mostPlayed)
    }

    private class FakeMoodRepository(private val topMood: MoodType?) : IMoodRepository {
        override suspend fun saveMoodEntry(entry: MoodEntry) = Unit
        override fun getMoodHistory(): Flow<List<MoodEntry>> = flowOf(emptyList())
        override fun getMoodHistoryForDays(days: Int): Flow<List<MoodEntry>> = flowOf(emptyList())
        override suspend fun getTopMood(): MoodType? = topMood
        override suspend fun getTotalListeningTimeMs(): Long = 0L
    }

    @Test
    fun `returns recommendations built from favorites and most played`() = runTest {
        val seed = song(1, artist = "Liked Artist", playCount = 30, isFavorite = true)
        val match = song(2, artist = "Liked Artist")
        val other = song(3, artist = "Unknown", album = "Different Album")

        val useCase = GetForYouSongsUseCase(
            musicRepository = FakeMusicRepository(
                all = listOf(seed, match, other),
                favorites = listOf(seed),
                mostPlayed = listOf(seed)
            ),
            moodRepository = FakeMoodRepository(topMood = null)
        )

        val result = useCase(limit = 10)

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `returns empty when library is empty`() = runTest {
        val useCase = GetForYouSongsUseCase(
            musicRepository = FakeMusicRepository(emptyList(), emptyList(), emptyList()),
            moodRepository = FakeMoodRepository(topMood = MoodType.HAPPY)
        )

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun `passes top mood into recommendations`() = runTest {
        val seed = song(1, artist = "Seeded", isFavorite = true)
        val calmTrack = song(2, artist = "Other", moodTags = listOf(MoodType.SLEEP))

        val useCase = GetForYouSongsUseCase(
            musicRepository = FakeMusicRepository(
                all = listOf(seed, calmTrack),
                favorites = listOf(seed),
                mostPlayed = listOf(seed)
            ),
            moodRepository = FakeMoodRepository(topMood = MoodType.SLEEP)
        )

        // calmTrack is tagged with the dominant mood, so it is recommended ahead.
        val result = useCase(limit = 10)

        assertEquals(listOf(2L), result.map { it.id })
    }
}
