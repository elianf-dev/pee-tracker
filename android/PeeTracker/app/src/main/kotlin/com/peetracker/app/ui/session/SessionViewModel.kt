package com.peetracker.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.peetracker.app.data.remote.AuthRepository
import com.peetracker.app.data.remote.DateKeys
import com.peetracker.app.data.remote.StreakLogic
import com.peetracker.app.data.remote.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data object NeedsGroup : SessionState
    data class InGroup(val groupId: String) : SessionState
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(
        if (authRepository.currentUser != null) SessionState.Loading else SessionState.SignedOut
    )
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        if (authRepository.currentUser != null) refreshGroupState()
    }

    fun onSignedIn() {
        refreshGroupState()
    }

    fun refreshGroupState() {
        val user = authRepository.currentUser
        if (user == null) {
            _sessionState.value = SessionState.SignedOut
            return
        }
        _sessionState.value = SessionState.Loading
        viewModelScope.launch {
            var profile = runCatching { userRepository.fetchUserProfile(user.uid) }.getOrNull()
            if (profile == null) {
                // The client creates users/{uid} itself right after sign-up (no onUserCreate
                // trigger in Spark mode) — if a prior attempt crashed before that write landed,
                // retry it here rather than assuming the doc always exists.
                runCatching {
                    userRepository.createProfileIfMissing(
                        uid = user.uid,
                        displayName = user.displayName.orEmpty(),
                        photoURL = user.photoUrl?.toString(),
                        authProviders = user.authProviderNames()
                    )
                }
                profile = runCatching { userRepository.fetchUserProfile(user.uid) }.getOrNull()
            }

            profile?.let { p ->
                if (p.streak.current > 0 && StreakLogic.isBroken(p.streak.lastLogDateKey, DateKeys.todayDateKey())) {
                    // Best-effort, fire-and-forget: there's no daily streakSweep cron in Spark
                    // mode, so a broken streak is only caught the next time this device happens
                    // to open the app. Not awaited — it must not delay the session state below.
                    viewModelScope.launch {
                        runCatching {
                            userRepository.resetStreakCurrent(user.uid, p.streak.longest, p.streak.lastLogDateKey)
                        }
                    }
                }
            }

            val groupId = profile?.currentGroupIds?.firstOrNull()
            _sessionState.value = if (groupId != null) {
                SessionState.InGroup(groupId)
            } else {
                SessionState.NeedsGroup
            }
        }
    }

    private fun FirebaseUser.authProviderNames(): List<String> {
        val names = providerData.mapNotNull { info ->
            when (info.providerId) {
                "password" -> "password"
                "google.com" -> "google"
                else -> null
            }
        }.distinct()
        return names.ifEmpty { listOf("password") }
    }

    fun signOut() {
        authRepository.signOut()
        _sessionState.value = SessionState.SignedOut
    }
}
