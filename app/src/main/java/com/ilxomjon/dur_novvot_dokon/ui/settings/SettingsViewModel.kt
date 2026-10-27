package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.UiLanguage
import com.ilxomjon.dur_novvot_dokon.data.UiTheme
import com.ilxomjon.dur_novvot_dokon.data.repo.AuthRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupException
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupPreview
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val agentName: String = "",
    val phone: String = "",
    val server: String = "",
    val language: UiLanguage = UiLanguage.LATIN,
    val theme: UiTheme = UiTheme.SYSTEM,
    val busy: Boolean = false,
    val keepScreen: Boolean = false,
    val favorites: List<Store> = emptyList(),
)

sealed interface SettingsEvent {
    data object BackupDone : SettingsEvent
    class BackupFailed(val cause: Throwable) : SettingsEvent
    /** [needsCheck] — tiklangan, avtomatik yuborilmaydigan navbat qatorlari. */
    class RestoreDone(val needsCheck: Int) : SettingsEvent
    class RestoreFailed(val cause: Throwable) : SettingsEvent
    class Preview(val uri: Uri, val preview: BackupPreview, val password: CharArray?) : SettingsEvent
    /** Zaxira shifrlangan: parol so'rash kerak. */
    class NeedPassword(val uri: Uri) : SettingsEvent
    data object LoggedOut : SettingsEvent
}

class SettingsViewModel(
    private val auth: AuthRepository,
    private val backup: BackupRepository,
    private val prefs: AppPrefs,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private fun read(): SettingsState {
        val agent = catalog.state.value.agent
        val cfg = auth.serverConfig
        return SettingsState(
            agentName = agent?.fullName.orEmpty(), phone = agent?.phone.orEmpty(),
            server = if (cfg.isComplete) "${cfg.host} · ${cfg.database}" else "",
            language = prefs.language, theme = prefs.theme, keepScreen = prefs.keepScreenOn,
            favorites = prefs.favoriteStoreIds.mapNotNull { catalog.storeById(it) },
        )
    }

    fun refresh() { _state.update { read().copy(busy = it.busy) } }

    fun setLanguage(l: UiLanguage) {
        if (prefs.language == l) return
        prefs.language = l
        ServiceLocator.applyUiPrefs()
        refresh()
    }

    /** [slot] 0 yoki 1: o'sha o'rindagi do'konni almashtiradi (yoki oxiriga qo'shadi). */
    fun setFavorite(slot: Int, storeId: String) {
        val list = prefs.favoriteStoreIds.toMutableList()
        if (slot < list.size) list[slot] = storeId else list.add(storeId)
        prefs.favoriteStoreIds = list.distinct()
        refresh()
    }

    fun clearFavorite(slot: Int) {
        val list = prefs.favoriteStoreIds.toMutableList()
        if (slot < list.size) list.removeAt(slot)
        prefs.favoriteStoreIds = list
        refresh()
    }

    fun setKeepScreen(on: Boolean) {
        prefs.keepScreenOn = on
        refresh()
    }

    fun setTheme(t: UiTheme) {
        if (prefs.theme == t) return
        prefs.theme = t
        ServiceLocator.applyUiPrefs()
        refresh()
    }

    fun logout() {
        viewModelScope.launch {
            auth.logout()
            _events.tryEmit(SettingsEvent.LoggedOut)
        }
    }

    fun export(uri: Uri, password: CharArray?) = runBusy {
        try { backup.export(uri, password); _events.tryEmit(SettingsEvent.BackupDone) } catch (e: Exception) { _events.tryEmit(SettingsEvent.BackupFailed(e)) }
    }

    /** Avval ko'rib chiqish: nima tiklanishini ko'rsatadi, hech narsa o'zgarmaydi. */
    fun preview(uri: Uri, password: CharArray?) = runBusy {
        try {
            _events.tryEmit(SettingsEvent.Preview(uri, backup.preview(uri, password), password))
        } catch (e: BackupException) {
            if (e.kind == BackupException.Kind.WRONG_PASSWORD && (password == null || password.isEmpty())) _events.tryEmit(SettingsEvent.NeedPassword(uri))
            else _events.tryEmit(SettingsEvent.RestoreFailed(e))
        } catch (e: Exception) {
            _events.tryEmit(SettingsEvent.RestoreFailed(e))
        }
    }

    fun import(uri: Uri, password: CharArray?) = runBusy {
        try { _events.tryEmit(SettingsEvent.RestoreDone(backup.import(uri, password))) } catch (e: Exception) { _events.tryEmit(SettingsEvent.RestoreFailed(e)) }
    }

    fun setFlag(set: (com.ilxomjon.dur_novvot_dokon.data.AppPrefs) -> Unit) { set(prefs); refresh() }

    val prefsRef get() = prefs

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() } finally { _state.update { read().copy(busy = false) } }
        }
    }
}
