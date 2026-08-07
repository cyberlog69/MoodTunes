package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.repository.IPlaylistRepository
import javax.inject.Inject

class RemoveSongFromPlaylistUseCase @Inject constructor(
    private val playlistRepository: IPlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, songId: Long) =
        playlistRepository.removeSongFromPlaylist(playlistId, songId)
}
