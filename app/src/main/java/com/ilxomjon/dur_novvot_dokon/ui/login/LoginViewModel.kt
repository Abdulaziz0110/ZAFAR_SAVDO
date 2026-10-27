package com.ilxomjon.dur_novvot_dokon.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LoginError {
    data object BadPhone : LoginError
    data object BadPassword : LoginError
    data object Credentials : LoginError
    data object ServerMissing : LoginError
    class Failure(val cause: Throwable) : LoginError
}

data class LoginState(val loading: Boolean = false, val error: LoginError? = null, val loggedIn: Boolean = false)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun login(phone: String, password: String) {
        if (_state.value.loading) return
        if (phone.isBlank()) return fail(LoginError.BadPhone)
        if (password.isEmpty()) return fail(LoginError.BadPassword)
        _state.value = LoginState(loading = true)
        viewModelScope.launch {
            try {
                val configured = withContext(Dispatchers.IO) { auth.serverConfig.isComplete }
                if (!configured) return@launch fail(LoginError.ServerMissing)
                val ok = auth.login(phone.trim(), password)
                _state.value = if (ok) LoginState(loggedIn = true) else LoginState(error = LoginError.Credentials)
            } catch (e: Exception) {
                _state.value = LoginState(error = LoginError.Failure(e))
            }
        }
    }

    private fun fail(e: LoginError) = _state.update { it.copy(error = e, loading = false) }
}
