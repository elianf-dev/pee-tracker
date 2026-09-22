package com.peetracker.app.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    fun registerTokenForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { notificationRepository.registerCurrentToken(uid) }
        }
    }
}
