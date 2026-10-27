package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.repository.IPlaylistRepository
import javax.inject.Inject

/**
 * Persists custom procedural or imported cover art URI for a playlist.
 */
class UpdatePlaylistCoverUseCase @Inject constructor(
    private val playlistRepository: IPlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, coverUri: String?) =
        playlistRepository.updatePlaylistCover(playlistId, coverUri)
}
