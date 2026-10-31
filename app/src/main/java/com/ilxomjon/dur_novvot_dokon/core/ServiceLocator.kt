package com.ilxomjon.dur_novvot_dokon.core

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.DbSafety
import com.ilxomjon.dur_novvot_dokon.data.NetworkMonitor
import com.ilxomjon.dur_novvot_dokon.data.repo.DiagnosticsRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.KpiTracker
import com.ilxomjon.dur_novvot_dokon.data.repo.LocalReportRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.ParkedCartRepository
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
import com.ilxomjon.dur_novvot_dokon.data.share.ShareRepository
import com.ilxomjon.dur_novvot_dokon.data.share.ShareStore
import com.ilxomjon.dur_novvot_dokon.data.share.ShareWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Oddiy qo'lda bog'lash (Hilt'siz): barcha singleton'lar shu yerda. */
object ServiceLocator {
    private lateinit var app: Application
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, t ->
        // Fon ishida kutilmagan xato ilovani yiqitmasin (masalan disk to'lgan). Diagnostikada ko'rinadi.
        lastBackgroundError = t
    })

    @Volatile var lastBackgroundError: Throwable? = null
        private set

    /** Baza yengil tekshiruvi natijasi (null - hali tekshirilmagan). */
    @Volatile var dbHealthy: Boolean? = null
        private set

    val isInitialized: Boolean get() = ::app.isInitialized

    fun init(application: Application) {
        if (::app.isInitialized) return
        app = application
        applyUiPrefs()
        // Keystore va baza obyektlari asosiy oqimni to'sib qo'ymasligi uchun IO'da oldindan yaratiladi.
        appScope.launch(Dispatchers.IO) {
            // Navbat bazasi yangi versiyaga o'tishidan OLDIN fayl nusxasi olinadi (rollback uchun).
            DbSafety.backupBeforeUpgrade(app, HistoryDb.NAME, HistoryDb.VERSION)
            secure
            localDb
            historyDb
            dbHealthy = historyDb.quickCheck()
            LegacyMigration.run(localDb, secure)
            catalog.load()
            draft.load()
            runCatching { historyDb.purgeOld(System.currentTimeMillis()) }
            outbox.refreshCount()
            outbox.kick()
            network.start()
            // v2.4: 2-ilova bilan ulashish (sozlangan bo'lsa)
            runCatching {
                share.load()
                catalog.onChanged = { barcodes.invalidate(); share.markDirty(); if (shareStore.isConfigured && shareStore.autoPublish) ShareWorker.kick(app) }
                if (shareStore.isConfigured) ShareWorker.schedule(app)
            }
            runCatching { com.ilxomjon.dur_novvot_dokon.data.extras.Extras.onAppStart(app) }
        }
    }

    /** WorkManager jarayonni o'zi ishga tushirganda ham hammasi tayyor bo'lsin. */
    fun ensureInit(context: Context) {
        if (!::app.isInitialized) init(context.applicationContext as Application)
    }

    val context: Context get() = app

    val appPrefs by lazy { AppPrefs(app) }
    val secure by lazy { SecureStore(app) }
    val localDb by lazy { LocalDb(app) }
    val historyDb by lazy { HistoryDb(app) }

    private val catalogDao by lazy { CatalogDao(localDb) }
    private val cartDao by lazy { CartDao(localDb) }
    private val server by lazy { SqlServer { secure.serverConfig } }
    /** To'g'ridan-to'g'ri SQL server (Admin har doim shu; Agent "Server" rejimida). */
    val directApi by lazy { ServerApi(server) }
    /** v3.0 Agent "Admin orqali": faqat internet, server ishini Admin bajaradi. */
    val relayClient: com.ilxomjon.dur_novvot_dokon.data.relay.RelayClient by lazy {
        com.ilxomjon.dur_novvot_dokon.data.relay.RelayClient({ secure.relayConfig }, saleDir = java.io.File(app.filesDir, "relay_sales")).also { r ->
            r.onCodes = { bytes -> labels.applyCodes(bytes) }
        }
    }
    /** Agent Admin orqali ishlayaptimi (Admin ilovada hech qachon). */
    val isRelay: Boolean get() = !AppRole.isAdmin && secure.relayMode && secure.relayConfig != null
    val api: com.ilxomjon.dur_novvot_dokon.data.remote.Backend by lazy {
        com.ilxomjon.dur_novvot_dokon.data.remote.SwitchingBackend(directApi, relayClient) { isRelay }
    }

    val network by lazy { NetworkMonitor(app) }
    val catalog by lazy { CatalogRepository(catalogDao, api, appPrefs, historyDb) }
    val draft by lazy { DraftRepository(cartDao, catalog, app.getSharedPreferences("user", Context.MODE_PRIVATE), appScope) }
    val outbox by lazy {
        OutboxRepository(app, historyDb, api, appScope).also { o ->
            o.beforeDrain = {
                if (isRelay) {
                    val items = withContext(Dispatchers.IO) { historyDb.outboxList() }
                        .filter { !it.state.isDone && it.state != com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState.NEEDS_CHECK }
                        .map { Triple(it.agentId, it.kind.protocolType, it.payload) }
                    relayClient.pushAll(items)
                }
            }
        }
    }
    val auth by lazy { AuthRepository(api, catalogDao, secure, catalog, draft) { isRelay } }
    val kpi by lazy { KpiTracker(historyDb, appPrefs) }
    val orders by lazy {
        OrderRepository(catalog, draft, outbox, kpi).also { r ->
            // v2.6: savdo -> mijozning 2-ilovasi (sozlanmagan bo'lsa onSale hech narsa qilmaydi)
            r.onOrderQueued = { s, id, code -> if (!isRelay) appScope.launch(Dispatchers.IO) { runCatching { share.onSale(s, id, code) } } }
            // Admin orqali: chek ko'zgusi savdo bilan birga Admin'ga boradi (Admin 2-filialga yuboradi)
            r.onOrderPayload = { agentId, payload, s, code -> if (isRelay) relayClient.rememberSale(agentId, payload, s, code) }
        }
    }
    val reports by lazy { ReportRepository(api, catalog) }
    val history by lazy { HistoryRepository(historyDb) }
    val parked by lazy { ParkedCartRepository(localDb, draft, catalog) }
    val localReports by lazy { LocalReportRepository(historyDb) }
    val diagnostics by lazy { DiagnosticsRepository(app, secure, api, network, outbox, historyDb, localDb) }
    val repeatSale by lazy { com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale(catalog, draft, parked) }
    val shareStore by lazy { ShareStore(app) }
    val share by lazy {
        ShareRepository(app, shareStore, catalog, api).also { s ->
            s.codesHook = { bytes -> labels.applyCodes(bytes) }
            s.pluOf = { id -> labels.pluFor(id) }
        }
    }
    val barcodes by lazy { com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository(app, catalog) }
    val labels: com.ilxomjon.dur_novvot_dokon.data.labels.LabelRepository by lazy {
        com.ilxomjon.dur_novvot_dokon.data.labels.LabelRepository(app, catalog, barcodes, shareStore).also { l ->
            l.relayActive = { isRelay }
            l.relayPrint = { data, copies -> relayClient.printLabels(data, copies) }
            l.relayRefreshCodes = { catalog.sync() }
        }
    }
    /** v3.0 Admin: agentlar ko'prigi. */
    val relayHost by lazy {
        com.ilxomjon.dur_novvot_dokon.data.relay.RelayHost(
            config = { com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Config(shareStore.dbUrl.let(uz.imsoft.shared.CloudDb::normalizeUrl), shareStore.agentKey).takeIf { AppRole.isAdmin && shareStore.isCloudReady && it.isValid } },
            cloud = { url -> uz.imsoft.shared.CloudDb(url, shareStore.secret) },
            server = com.ilxomjon.dur_novvot_dokon.data.relay.RelayServerAdapter(directApi),
            hooks = object : com.ilxomjon.dur_novvot_dokon.data.relay.RelayHost.Hooks {
                override fun adminAgentId(): String = catalog.state.value.agent?.id.orEmpty()
                override fun codes(): Pair<Long, ByteArray>? {
                    val f = java.io.File(app.filesDir, "codes_1c.json")
                    return if (f.isFile) f.lastModified() to f.readBytes() else null
                }
                override suspend fun printLabels(data: ByteArray): String = labels.printRaw(data)
                override suspend fun mirrorSale(sale: com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Sale, key: String) =
                    share.onSale(sale.order, 0L, sale.code, key)
            },
        )
    }
    val backup by lazy { BackupRepository(app, localDb, historyDb, secure, appPrefs, catalog, draft, outbox, shareStore) }

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
