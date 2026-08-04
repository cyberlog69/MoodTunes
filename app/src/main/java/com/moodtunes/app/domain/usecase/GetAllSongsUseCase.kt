package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import javax.inject.Inject

class GetAllSongsUseCase @Inject constructor(
    private val musicRepository: IMusicRepository
) {
    suspend operator fun invoke(): List<Song> = musicRepository.getAllSongs()
}
