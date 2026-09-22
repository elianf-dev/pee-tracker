package com.peetracker.app.ui.streaks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.peetracker.app.data.model.Badge
import com.peetracker.app.data.model.Streak
import com.peetracker.app.data.remote.BadgeRepository
import com.peetracker.app.data.remote.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StreaksUiState(
    val isLoading: Boolean = true,
    val streak: Streak = Streak(),
    val myBadges: List<Badge> = emptyList(),
    val newlyAwardedBadge: Badge? = null
)

@HiltViewModel
class StreaksViewModel @Inject constructor(
    private val badgeRepository: BadgeRepository,
    private val userRepository: UserRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId")
        ?: error("StreaksViewModel requires a groupId nav argument")

    private val uid: String = auth.currentUser?.uid
        ?: error("StreaksViewModel requires a signed-in user")

    private val _uiState = MutableStateFlow(StreaksUiState())
    val uiState: StateFlow<StreaksUiState> = _uiState.asStateFlow()

    // Badge ids already observed, so the celebration overlay only fires for a badge that
    // actually arrives while this screen is alive rather than the user's whole history on
    // first load. Null until the first snapshot establishes a baseline.
    private var seenBadgeIds: Set<String>? = null

    init {
        viewModelScope.launch {
            runCatching { userRepository.fetchUserProfile(uid) }
                .getOrNull()
                ?.let { profile ->
                    _uiState.value = _uiState.value.copy(streak = profile.streak, isLoading = false)
                }
        }
        viewModelScope.launch {
            // .catch guards against the listener dying mid-screen (e.g. PERMISSION_DENIED on
            // sign-out/leave-group while attached) — see the matching comment in
            // LeaderboardViewModel for why silently keeping the last state is fine here.
            badgeRepository.observeBadges(groupId).catch { }.collect { badges ->
                val mine = badges.filter { it.awardedToUid == uid }
                val currentIds = badges.mapNotNull { it.id }.toSet()
                val baseline = seenBadgeIds
                val newlyArrived = baseline?.let { seen ->
                    mine.firstOrNull { badge -> badge.id != null && badge.id !in seen }
                }
                seenBadgeIds = currentIds
                _uiState.value = _uiState.value.copy(
                    myBadges = mine,
                    newlyAwardedBadge = newlyArrived ?: _uiState.value.newlyAwardedBadge
                )
            }
        }
    }

    fun consumeNewlyAwardedBadge() {
        _uiState.value = _uiState.value.copy(newlyAwardedBadge = null)
    }
}
