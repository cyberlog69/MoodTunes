package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.repository.IPlaylistRepository
import javax.inject.Inject

class MoveSongInPlaylistUseCase @Inject constructor(
    private val playlistRepository: IPlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, fromPosition: Int, toPosition: Int) =
        playlistRepository.moveSong(playlistId, fromPosition, toPosition)
}
