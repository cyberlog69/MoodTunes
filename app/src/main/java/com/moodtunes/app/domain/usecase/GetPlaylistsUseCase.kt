package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.repository.IPlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPlaylistsUseCase @Inject constructor(
    private val playlistRepository: IPlaylistRepository
) {
    operator fun invoke(): Flow<List<Playlist>> = playlistRepository.getPlaylists()
}
