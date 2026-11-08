package com.moodtunes.app.presentation.ui.account

import androidx.lifecycle.ViewModel
import com.moodtunes.app.data.remote.api.YouTubeSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class YouTubeAccountUiState(
    val isSignedIn: Boolean = false,
    val accountName: String = "",
    val cookieCount: Int = 0,
    val detectedCookies: String = "",
    val message: String? = null
)

@HiltViewModel
class YouTubeAccountViewModel @Inject constructor(
    private val sessionStore: YouTubeSessionStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        YouTubeAccountUiState(
            isSignedIn = sessionStore.isSignedIn.value,
            accountName = sessionStore.accountName.value,
            cookieCount = sessionStore.cookieHeader()?.count { it == ';' }?.plus(1) ?: 0
        )
    )
    val uiState: StateFlow<YouTubeAccountUiState> = _state.asStateFlow()

    fun onCookiesDetected(cookies: String) {
        _state.value = _state.value.copy(detectedCookies = cookies)
    }

    fun connect() {
        val cookies = _state.value.detectedCookies
        if (cookies.isBlank()) {
            _state.value = _state.value.copy(
                message = "Sign in to music.youtube.com in the browser above, then tap Connect again."
            )
            return
        }
        sessionStore.saveSession(cookies)
        _state.value = _state.value.copy(
            isSignedIn = sessionStore.isSignedIn.value,
            accountName = sessionStore.accountName.value,
            cookieCount = cookies.count { it == ';' } + 1,
            message = if (sessionStore.isSignedIn.value) {
                "YouTube Music connected. Your library will load in Collection."
            } else {
                "Cookies were saved but no account cookie was found. Make sure you finished signing in."
            }
        )
    }

    fun signOut() {
        sessionStore.signOut()
        _state.value = _state.value.copy(
            isSignedIn = false,
            accountName = "",
            cookieCount = 0,
            detectedCookies = "",
            message = "Signed out of YouTube Music."
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
