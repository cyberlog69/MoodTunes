package com.moodtunes.app.presentation.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.usecase.GetMoodHistoryUseCase
import com.moodtunes.app.domain.repository.IMoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoodStat(val mood: MoodType, val count: Int, val percentage: Float)

data class HistoryUiState(
    val recentEntries: List<MoodEntry> = emptyList(),
    val moodStats: List<MoodStat> = emptyList(),
    val topMood: MoodType? = null,
    val totalListeningTimeMs: Long = 0L,
    val totalSessions: Int = 0,
    val isLoading: Boolean = true
) {
    val formattedTotalTime: String
        get() {
            val totalMinutes = totalListeningTimeMs / 60_000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getMoodHistoryUseCase: GetMoodHistoryUseCase,
    private val moodRepository: IMoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
        loadStats()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            getMoodHistoryUseCase(30).collect { entries ->
                _uiState.update { it.copy(recentEntries = entries, isLoading = false) }
                computeStats(entries)
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val topMood = moodRepository.getTopMood()
            val totalTime = moodRepository.getTotalListeningTimeMs()
            _uiState.update { it.copy(topMood = topMood, totalListeningTimeMs = totalTime) }
        }
    }

    private fun computeStats(entries: List<MoodEntry>) {
        val countByMood = entries.groupBy { it.moodType }.mapValues { it.value.size }
        val total = entries.size.toFloat().coerceAtLeast(1f)

        val stats = MoodType.entries.mapNotNull { mood ->
            val count = countByMood[mood] ?: 0
            if (count > 0) MoodStat(mood, count, count / total) else null
        }.sortedByDescending { it.count }

        _uiState.update { it.copy(moodStats = stats, totalSessions = entries.size) }
    }
}
