package com.ilxomjon.dur_novvot_dokon.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.AuthRepository
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerSettingsState(
    val testing: Boolean = false,
    val testOk: Boolean? = null,
    val via: String = "",
    val testError: Throwable? = null,
    val saved: Boolean = false,
    /** v3.0 "Admin orqali" sinovi: Admin oxirgi marta ishlagan vaqt. */
    val adminAt: Long = 0L,
)

class ServerSettingsViewModel(
    private val auth: AuthRepository,
    private val secure: com.ilxomjon.dur_novvot_dokon.data.SecureStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ServerSettingsState())
    val state: StateFlow<ServerSettingsState> = _state.asStateFlow()

    val current: ServerConfig get() = auth.serverConfig

    val hasPassword: Boolean get() = auth.serverConfig.password.isNotEmpty()

    /** Faqat ulanish/saqlash uchun ichkarida; UI'da ko'rsatilmaydi. */
    internal fun currentPasswordInternal(): String = auth.serverConfig.password

    fun test(cfg: ServerConfig) {
        if (_state.value.testing) return
        _state.value = ServerSettingsState(testing = true)
        viewModelScope.launch {
            try {
                val via = auth.testServer(cfg)
                _state.value = ServerSettingsState(testOk = true, via = via)
            } catch (e: Exception) {
                _state.value = ServerSettingsState(testOk = false, testError = e)
            }
        }
    }

    fun save(cfg: ServerConfig) {
        auth.saveServerConfig(cfg)
        secure?.let { it.setRelay(it.relayConfig, false) }
        _state.value = ServerSettingsState(saved = true)
    }

    // ---------------- v3.0 Agent: "Admin orqali" ----------------

    val relayMode: Boolean get() = secure?.relayMode == true
    val relayCode: String get() = secure?.relayConfig?.let { com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.encodeCode(it) }.orEmpty()

    fun testRelay(c: com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Config) {
        if (_state.value.testing) return
        _state.value = ServerSettingsState(testing = true)
        viewModelScope.launch {
            try {
                val st = com.ilxomjon.dur_novvot_dokon.data.relay.RelayClient({ c }).status()
                    ?: throw com.ilxomjon.dur_novvot_dokon.data.remote.ServerException(com.ilxomjon.dur_novvot_dokon.data.remote.ServerError.CONNECT, "admin_never")
                _state.value = ServerSettingsState(testOk = true, adminAt = st.at)
            } catch (e: Exception) {
                _state.value = ServerSettingsState(testOk = false, testError = e)
            }
        }
    }

    fun saveRelay(c: com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Config) {
        secure?.setRelay(c, true)
        _state.value = ServerSettingsState(saved = true)
    }
}
