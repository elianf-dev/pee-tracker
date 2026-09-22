package com.peetracker.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peetracker.app.data.remote.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SIGN_IN, SIGN_UP }

data class AuthUiState(
    val isLoading: Boolean = false,
    val mode: AuthMode = AuthMode.SIGN_IN,
    val errorMessage: String? = null,
    val isSignedIn: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(isSignedIn = authRepository.currentUser != null)
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            mode = if (_uiState.value.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
            errorMessage = null
        )
    }

    fun signInWithGoogle(serverClientId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { authRepository.signInWithGoogleCredential(serverClientId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Google sign-in failed"
                    )
                }
        }
    }

    fun submitEmailForm(email: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = if (_uiState.value.mode == AuthMode.SIGN_IN) {
                runCatching { authRepository.signInWithEmail(email, password) }
            } else {
                runCatching { authRepository.signUpWithEmail(email, password) }
            }
            result
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Authentication failed"
                    )
                }
        }
    }
}
