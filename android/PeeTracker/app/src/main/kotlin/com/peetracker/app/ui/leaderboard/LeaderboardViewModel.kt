package com.peetracker.app.ui.leaderboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.peetracker.app.data.model.LeaderboardEntry
import com.peetracker.app.data.remote.LeaderboardRepository
import com.peetracker.app.data.remote.PeriodBadgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LeaderboardPeriod { TODAY, THIS_WEEK }

data class LeaderboardRow(
    val uid: String,
    val entry: LeaderboardEntry,
    val rank: Int,
    val isCurrentUser: Boolean
)

data class LeaderboardUiState(
    val selectedPeriod: LeaderboardPeriod = LeaderboardPeriod.TODAY,
    val dailyEntries: Map<String, LeaderboardEntry> = emptyMap(),
    val weeklyEntries: Map<String, LeaderboardEntry> = emptyMap(),
    val currentUid: String? = null
) {
    val rows: List<LeaderboardRow>
        get() {
            val entries = if (selectedPeriod == LeaderboardPeriod.TODAY) dailyEntries else weeklyEntries
            return entries.entries
                .sortedByDescending { it.value.count }
                .mapIndexed { index, (uid, entry) ->
                    LeaderboardRow(uid = uid, entry = entry, rank = index + 1, isCurrentUser = uid == currentUid)
                }
        }
}

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    private val periodBadgeRepository: PeriodBadgeRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId")
        ?: error("LeaderboardViewModel requires a groupId nav argument")

    private val _uiState = MutableStateFlow(LeaderboardUiState(currentUid = auth.currentUser?.uid))
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        // .catch guards against the listener dying mid-screen — most commonly a PERMISSION_DENIED
        // when the user signs out or leaves this group while the listener is still attached, which
        // otherwise propagates as an uncaught exception and crashes the app. Silently keeping the
        // last known entries is fine: by the time this fires, session/nav state has already moved
        // the user off this screen.
        viewModelScope.launch {
            leaderboardRepository.observeDailyLeaderboard(groupId)
                .catch { }
                .collect { entries -> _uiState.value = _uiState.value.copy(dailyEntries = entries) }
        }
        viewModelScope.launch {
            leaderboardRepository.observeWeeklyLeaderboard(groupId)
                .catch { }
                .collect { entries -> _uiState.value = _uiState.value.copy(weeklyEntries = entries) }
        }

        // Opportunistic period-badge finalize: no server cron in Spark mode, so whichever
        // device opens the leaderboard after a day/week rolls over computes and awards
        // yesterday's/last week's badge itself. Chosen over StreaksScreen since this screen is
        // the more natural "just saw the results" moment. Best-effort and silent by design —
        // PeriodBadgeRepository already swallows the expected "someone else wrote it first"
        // failure, and this is wrapped again here so it can never surface to the UI.
        viewModelScope.launch {
            runCatching { periodBadgeRepository.finalizeYesterdayIfNeeded(groupId) }
            runCatching { periodBadgeRepository.finalizeLastWeekIfNeeded(groupId) }
        }
    }

    fun onPeriodSelected(period: LeaderboardPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
    }
}
