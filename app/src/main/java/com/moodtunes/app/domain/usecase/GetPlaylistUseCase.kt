package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.repository.IPlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPlaylistUseCase @Inject constructor(
    private val playlistRepository: IPlaylistRepository
) {
    operator fun invoke(playlistId: Long): Flow<Playlist?> = playlistRepository.getPlaylist(playlistId)
}
