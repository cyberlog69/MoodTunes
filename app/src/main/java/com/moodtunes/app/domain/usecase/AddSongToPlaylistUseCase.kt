package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IPlaylistRepository
import javax.inject.Inject

class AddSongToPlaylistUseCase @Inject constructor(
    private val playlistRepository: IPlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, song: Song) =
        playlistRepository.addSongToPlaylist(playlistId, song)
}
