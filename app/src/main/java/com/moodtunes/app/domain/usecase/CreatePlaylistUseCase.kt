package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.repository.IPlaylistRepository
import javax.inject.Inject

class CreatePlaylistUseCase @Inject constructor(
    private val playlistRepository: IPlaylistRepository
) {
    suspend operator fun invoke(name: String): Long = playlistRepository.createPlaylist(name)
}
