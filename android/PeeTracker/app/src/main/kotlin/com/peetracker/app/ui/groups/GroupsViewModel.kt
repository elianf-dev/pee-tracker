package com.peetracker.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peetracker.app.data.remote.CreateGroupResult
import com.peetracker.app.data.remote.GroupRepository
import com.peetracker.app.data.remote.InvalidInviteCodeException
import com.peetracker.app.data.remote.JoinGroupResult
import com.peetracker.app.notifications.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val createdGroup: CreateGroupResult? = null,
    val joinedGroup: JoinGroupResult? = null,
    val didLeaveGroup: Boolean = false
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    fun createGroup(name: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { groupRepository.createGroup(name) }
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(isLoading = false, createdGroup = result)
                    runCatching { notificationRepository.subscribeToGroup(result.groupId) }
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.readableMessage("Failed to create group")
                    )
                }
        }
    }

    fun joinGroup(code: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { groupRepository.joinGroup(code) }
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(isLoading = false, joinedGroup = result)
                    runCatching { notificationRepository.subscribeToGroup(result.groupId) }
                }
                .onFailure { throwable ->
                    val message = if (throwable is InvalidInviteCodeException) {
                        throwable.message ?: "That invite code doesn't match any group"
                    } else {
                        throwable.readableMessage("Failed to join group")
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
                }
        }
    }

    fun leaveGroup(groupId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { groupRepository.leaveGroup(groupId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, didLeaveGroup = true)
                    runCatching { notificationRepository.unsubscribeFromGroup(groupId) }
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.readableMessage("Failed to leave group")
                    )
                }
        }
    }

    private fun Throwable.readableMessage(default: String): String = message ?: default
}
