package com.ilxomjon.dur_novvot_dokon.core

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.LegacyMigration
import com.ilxomjon.dur_novvot_dokon.data.SecureStore
import com.ilxomjon.dur_novvot_dokon.data.UiTheme
import com.ilxomjon.dur_novvot_dokon.data.local.CartDao
import com.ilxomjon.dur_novvot_dokon.data.local.CatalogDao
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.local.LocalDb
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi
import com.ilxomjon.dur_novvot_dokon.data.remote.SqlServer
import com.ilxomjon.dur_novvot_dokon.data.repo.AuthRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.DraftRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.HistoryRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.OrderRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.OutboxRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Oddiy qo'lda bog'lash (Hilt'siz): barcha singleton'lar shu yerda. */
object ServiceLocator {
    private lateinit var app: Application
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun init(application: Application) {
        app = application
        applyUiPrefs()
        // Keystore va baza obyektlari asosiy oqimni to'sib qo'ymasligi uchun IO'da oldindan yaratiladi.
        appScope.launch(Dispatchers.IO) {
            secure
            localDb
            historyDb
            LegacyMigration.run(localDb, secure)
            catalog.load()
            draft.load()
            outbox.refreshCount()
            if (outbox.pending.value > 0) outbox.kick()
        }
    }

    val context: Context get() = app

    val appPrefs by lazy { AppPrefs(app) }
    val secure by lazy { SecureStore(app) }
    val localDb by lazy { LocalDb(app) }
    val historyDb by lazy { HistoryDb(app) }

    private val catalogDao by lazy { CatalogDao(localDb) }
    private val cartDao by lazy { CartDao(localDb) }
    private val server by lazy { SqlServer { secure.serverConfig } }
    private val api by lazy { ServerApi(server) }

    val catalog by lazy { CatalogRepository(catalogDao, api, appPrefs) }
    val draft by lazy { DraftRepository(cartDao, catalog, app.getSharedPreferences("user", Context.MODE_PRIVATE), appScope) }
    val outbox by lazy { OutboxRepository(historyDb, api, appScope) }
    val auth by lazy { AuthRepository(api, catalogDao, secure, catalog, draft) }
    val orders by lazy { OrderRepository(catalog, draft, outbox) }
    val reports by lazy { ReportRepository(api, catalog) }
    val history by lazy { HistoryRepository(historyDb) }
    val backup by lazy { BackupRepository(app, localDb, historyDb, secure, appPrefs, catalog, draft) }

    fun applyUiPrefs() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appPrefs.language.tag))
        AppCompatDelegate.setDefaultNightMode(
            when (appPrefs.theme) {
                UiTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                UiTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                UiTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }
}
