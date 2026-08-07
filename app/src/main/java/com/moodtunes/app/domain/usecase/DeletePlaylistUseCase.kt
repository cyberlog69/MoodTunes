package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.repository.IPlaylistRepository
import javax.inject.Inject

class DeletePlaylistUseCase @Inject constructor(
    private val playlistRepository: IPlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long) = playlistRepository.deletePlaylist(playlistId)
}
