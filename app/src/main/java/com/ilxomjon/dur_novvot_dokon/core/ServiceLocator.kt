package com.ilxomjon.dur_novvot_dokon.core

import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.ui.money
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
        // v3.1: 1- va 2-narx 1C'dan (kompyuter yuborgan kodlar fayli)
        com.ilxomjon.dur_novvot_dokon.domain.Pricing.level12 = { id, level -> labels.priceForLevel(id, level) }
        // v4.0: kelgan narx — oxirgi kirim (Admin: 1C kirimlaridan; Agent: Admin yuborgan)
        com.ilxomjon.dur_novvot_dokon.domain.Pricing.lastCost = { id -> if (AppRole.isAdmin) onec.lastCost(id) else if (cloudReady) relayClient.lastCosts[id] else null }
        // Keystore va baza obyektlari asosiy oqimni to'sib qo'ymasligi uchun IO'da oldindan yaratiladi.
        appScope.launch(Dispatchers.IO) {
            // Navbat bazasi yangi versiyaga o'tishidan OLDIN fayl nusxasi olinadi (rollback uchun).
            DbSafety.backupBeforeUpgrade(app, HistoryDb.NAME, HistoryDb.VERSION)
            secure
            // v4.0: kalitsiz ishlash — build'dagi do'kon kodi bilan Agent o'zi "Admin orqali" ulanadi
            if (!AppRole.isAdmin) shopCode?.let { sc ->
                if (secure.relayConfig?.key != sc.agentKey || !secure.relayMode) secure.setRelay(com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Config(sc.dbUrl, sc.agentKey), true)
            }
            localDb
            historyDb
            dbHealthy = historyDb.quickCheck()
            LegacyMigration.run(localDb, secure)
            catalog.load()
            draft.load()
            runCatching { historyDb.purgeOld(System.currentTimeMillis()) }
            outbox.refreshCount()
            outbox.kick()
            // v4.0: yangi savdo/to'lov navbatga tushishi bilan yagona ro'yxatda ko'rinsin
            appScope.launch(Dispatchers.IO) { outbox.counts.collect { runCatching { refreshLocalDocs() } } }
            network.start()
            // v2.4: 2-ilova bilan ulashish (sozlangan bo'lsa)
            runCatching {
                share.load()
                if (AppRole.isAdmin) {
                    share.history.load()
                    // v5.0: birinchi ochilishda baza bo'sh bo'lsa — o'zi to'ldiradi (keyin faqat qo'lda yoki kompyuter o'zgarganda)
                    runCatching { share.history.startIfEmpty() }
                }
                catalog.onChanged = { barcodes.invalidate(); share.markDirty(); if (shareStore.isConfigured && shareStore.autoPublish) ShareWorker.kick(app) }
                if (shareStore.isConfigured) ShareWorker.schedule(app)
            }
            runCatching { com.ilxomjon.dur_novvot_dokon.data.extras.Extras.onAppStart(app) }
            // v5.3: dollar kursi har kuni Markaziy bank kursidan (qo'lda yozilgani ham almashtiriladi)
            appScope.launch(Dispatchers.IO) { runCatching { refreshRate() } }
        }
    }

    /**
     * v5.3: dollar kursi — HAR KUNI O'ZI, Markaziy bankning rasmiy kursidan (qo'lda yozish yo'q; avval qo'lda
     * yozilgan son ham almashtiriladi). Markaziy bank javob bermasa — bank kursi; internet yo'q bo'lsa oxirgisi qoladi.
     */
    suspend fun refreshRate(force: Boolean = false) {
        val p = appPrefs
        if (!force && !p.localRateManual && p.localRate > 0 && System.currentTimeMillis() - p.localRateAt < 6 * 3_600_000L) return
        val list = rateOnline.load()
        val official = list.firstOrNull { q -> uz.imsoft.shared.android.RateOnline.SOURCES.firstOrNull { it.title == q.source }?.market == false }
        val v = (official ?: list.firstOrNull())?.value ?: return
        if (v < 1000 || v > 1_000_000) return
        p.localRate = v
        p.localRateManual = false
        runCatching { catalog.load() }
    }

    /** WorkManager jarayonni o'zi ishga tushirganda ham hammasi tayyor bo'lsin. */
    fun ensureInit(context: Context) {
        if (!::app.isInitialized) init(context.applicationContext as Application)
    }

    val context: Context get() = app

    /** v4.0: build'ga qo'yilgan do'kon kodi (GitHub secret SHOP_CODE). Yo'q bo'lsa — eskicha (ulanish kodi). */
    val shopCode: uz.imsoft.shared.DeviceCodes.Shop? by lazy { uz.imsoft.shared.DeviceCodes.decodeShop(com.ilxomjon.dur_novvot_dokon.BuildConfig.SHOP_CODE) }
    /** v4.0: Agent kalitsiz rejimda (Admin biriktiradi, login yo'q). */
    val deviceMode: Boolean get() = !AppRole.isAdmin && shopCode != null
    @Volatile private var deviceCache: uz.imsoft.shared.android.DeviceSession? = null

    /** v4.0: shu telefon qurilma sifatida (Agent: build'dagi koddan; Admin: o'z bulut sozlamasidan). */
    fun device(): uz.imsoft.shared.android.DeviceSession? {
        val dev = if (AppRole.isAdmin) {
            if (shareStore.isCloudReady) uz.imsoft.shared.DeviceCodes.Dev(uz.imsoft.shared.CloudDb.normalizeUrl(shareStore.dbUrl), shareStore.devKey) else null
        } else shopCode?.dev
        dev ?: return null
        deviceCache?.takeIf { it.dev == dev }?.let { return it }
        return uz.imsoft.shared.android.DeviceSession(
            app, dev, if (AppRole.isAdmin) uz.imsoft.shared.DeviceCodes.ROLE_ADMIN else uz.imsoft.shared.DeviceCodes.ROLE_AGENT,
            com.ilxomjon.dur_novvot_dokon.BuildConfig.VERSION_NAME, com.ilxomjon.dur_novvot_dokon.BuildConfig.VERSION_CODE.toLong(),
        ).also { deviceCache = it }
    }

    /** v4.0 "Yagona ro'yxat": telefondan yuborilgan oxirgi 3 kunlik savdo/to'lovlar (1C'dan kelguncha ro'yxatda turadi). */
    val localDocs = kotlinx.coroutines.flow.MutableStateFlow<List<uz.imsoft.shared.report.PendingMerge.Local>>(emptyList())

    suspend fun refreshLocalDocs() {
        val from = System.currentTimeMillis() - 3L * 86_400_000L
        val orders = runCatching { history.orders() }.getOrDefault(emptyList()).filter { it.sentAt >= from }
        val pays = runCatching { history.payments() }.getOrDefault(emptyList()).filter { it.sentAt >= from }
        val list = orders.map { o ->
            uz.imsoft.shared.report.PendingMerge.Local(
                "o${o.id}", o.sentAt,
                if (o.mode == com.ilxomjon.dur_novvot_dokon.domain.DealMode.RETURN) uz.imsoft.shared.report.EntryKind.RETURN else uz.imsoft.shared.report.EntryKind.SALE,
                o.storeId, o.storeName, o.lines.map { uz.imsoft.shared.report.PendingMerge.Line(it.name, it.qty, it.price) }, o.total,
                o.currency == com.ilxomjon.dur_novvot_dokon.domain.Currency.USD,
            )
        } + pays.map { p ->
            uz.imsoft.shared.report.PendingMerge.Local("p${p.id}", p.sentAt, uz.imsoft.shared.report.EntryKind.PAYMENT, p.storeId, p.storeName, emptyList(), p.total)
        }
        if (list != localDocs.value) localDocs.value = list
    }

    /** v4.0: ilova o'zi yangilanadi (build'dagi yashirin manzildan). */
    val updater by lazy {
        uz.imsoft.shared.android.AppUpdater(app, com.ilxomjon.dur_novvot_dokon.BuildConfig.UPDATE_URL, "admin",
            com.ilxomjon.dur_novvot_dokon.BuildConfig.VERSION_CODE.toLong())
    }

    /** v4.0 Admin: kechki hisobot bildirishnomasi (kuniga bir marta, 20:00 dan keyin). */
    private fun eveningReport() {
        val cal = java.util.Calendar.getInstance()
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 20) return
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(cal.time)
        val p = app.getSharedPreferences("evening", Context.MODE_PRIVATE)
        if (p.getString("day", "") == day) return
        val d = com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost.dbData.value
        if (!d.loaded) return
        val rate = catalog.state.value.rate
        val from = (day + "000000").toLong()
        val t = uz.imsoft.shared.report.Stats.totals(d.entries, from, from + 1_000_000L, rate, com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment.costByName(rate))
        val m = { v: Double -> app.money(v, com.ilxomjon.dur_novvot_dokon.domain.Currency.SOM) }
        val debt = catalog.state.value.stores.sumOf { it.debtSom.coerceAtLeast(0.0) }
        com.ilxomjon.dur_novvot_dokon.data.extras.Extras.notify(app, 4040, app.getString(R.string.ev_title),
            app.getString(R.string.ev_text, m(t.sales), t.docs, m(t.profit), m(t.payments), m(debt)))
        p.edit().putString("day", day).apply()
    }

    /** v4.0 Admin "Boss": agentlarning kunlik savdosi. */
    val agentStats by lazy { com.ilxomjon.dur_novvot_dokon.data.share.AgentStats(app).also { it.load() } }

    /** v4.0 Admin: qurilmalar (biriktirish, bloklash). */
    val deviceAdmin by lazy {
        com.ilxomjon.dur_novvot_dokon.data.share.DeviceAdmin(shareStore, { share.linkCode(it) }, { directApi.agents() })
    }

    val appPrefs by lazy { AppPrefs(app) }
    val secure by lazy { SecureStore(app) }
    /** v5.3.8: rasm orqali kirim (rasmni o'qish va o'rganilganlar). */
    val receiptAi by lazy {
        com.ilxomjon.dur_novvot_dokon.data.photo.ReceiptAi(app, { secure.aiKey }, { secure.aiModel.ifBlank { com.ilxomjon.dur_novvot_dokon.data.photo.ReceiptScan.DEFAULT_MODEL } })
    }
    val localDb by lazy { LocalDb(app) }
    val historyDb by lazy { HistoryDb(app) }

    private val catalogDao by lazy { CatalogDao(localDb) }
    private val cartDao by lazy { CartDao(localDb) }
    private val server by lazy { SqlServer { secure.serverConfig } }
    /** To'g'ridan-to'g'ri SQL server (Admin har doim shu; Agent "Server" rejimida). */
    val directApi by lazy { ServerApi(server) }
    /** v3.0 Agent "Admin orqali": faqat internet, server ishini Admin bajaradi. */
    /**
     * v5.3: bulut yo'li sozlamasi. Admin — o'z buluti (agentlar guruhi kaliti); agent — build'dagi do'kon kodi yoki
     * Admin bergan ulanish kodi.
     */
    val cloudConfig: com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Config?
        get() = if (AppRole.isAdmin) {
            com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Config(uz.imsoft.shared.CloudDb.normalizeUrl(shareStore.dbUrl), shareStore.agentKey)
                .takeIf { shareStore.isCloudReady && it.isValid }
        } else secure.relayConfig

    val relayClient: com.ilxomjon.dur_novvot_dokon.data.relay.RelayClient by lazy {
        com.ilxomjon.dur_novvot_dokon.data.relay.RelayClient(
            { cloudConfig },
            cloud = { url -> if (AppRole.isAdmin) uz.imsoft.shared.CloudDb(url, shareStore.secret) else uz.imsoft.shared.CloudDb(url) },
            saleDir = java.io.File(app.filesDir, "relay_sales"),
        ).also { r ->
            r.onCodes = { bytes -> if (!AppRole.isAdmin) labels.applyCodes(bytes) }
            r.deviceId = { if (deviceMode) device()?.id.orEmpty() else "" }
            r.own = { AppRole.isAdmin }
        }
    }

    /** v5.3: bulut yo'li bormi (kompyuter ko'prigi orqali — VPN kerak emas). */
    val cloudReady: Boolean get() = cloudConfig != null

    /** v5.3: yo'lni ilova o'zi tanlaydi: do'kon Wi-Fi'sida to'g'ridan serverga, bo'lmasa bulut orqali. */
    val autoApi: com.ilxomjon.dur_novvot_dokon.data.remote.AutoBackend by lazy {
        com.ilxomjon.dur_novvot_dokon.data.remote.AutoBackend(
            direct = directApi, cloud = relayClient,
            directReady = { secure.serverConfig.isComplete },
            // Admin bulut yo'lidan FAQAT kompyuter ko'prigi ishlab turganda foydalanadi: aks holda so'rovga javob
            // beradigan hech kim yo'q (Admin o'ziga o'zi so'rov yuborib kutib qolardi)
            cloudReady = { cloudReady && (!AppRole.isAdmin || relayHost.pcHost) },
            localNet = { network.state.value.let { it.wifi || it.vpn } },
        )
    }

    /** Hozir bulut orqali ishlayaptimi (agent yorlig'i, filial cheki kabi "Admin orqali" imkoniyatlar uchun). */
    val isRelay: Boolean get() = cloudReady && autoApi.usingCloud
    val api: com.ilxomjon.dur_novvot_dokon.data.remote.Backend get() = autoApi

    /** Agent telefoni bulut orqali ulangan: yorliq va filial cheki kabi ishlar Admin/kompyuter orqali o'tadi. */
    val viaCloudAgent: Boolean get() = !AppRole.isAdmin && cloudReady

    val network by lazy { NetworkMonitor(app).also { n -> n.onWifiChanged = { autoApi.retryDirect(); outbox.kick() } } }
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
    val auth by lazy { AuthRepository(api, catalogDao, secure, catalog, draft) { cloudReady } }
    val kpi by lazy { KpiTracker(historyDb, appPrefs) }
    val orders: OrderRepository by lazy {
        OrderRepository(catalog, draft, outbox, kpi).also { r ->
            // v2.6: savdo -> mijozning 2-ilovasi (sozlanmagan bo'lsa onSale hech narsa qilmaydi)
            r.onOrderQueued = { s, id, code ->
                if (AppRole.isAdmin || !cloudReady) appScope.launch(Dispatchers.IO) {
                    runCatching { share.onSale(s, id, code) }
                    // v3.4: Admin — server qabul qilgach qarz va 1C tarixi 2-ilovaga o'zi
                    afterDealSent(s.storeId, id)
                }
            }
            // Admin orqali: chek ko'zgusi savdo bilan birga Admin'ga boradi (Admin 2-filialga yuboradi)
            r.onOrderPayload = { agentId, payload, s, code -> if (!AppRole.isAdmin && cloudReady) relayClient.rememberSale(agentId, payload, s, code) }
            // v3.2: Admin'da qabul qilingan to'lov — 2-ilovada darhol, qarz esa server qabul qilgach yangilanadi
            r.onPaymentQueued = { p, id, code ->
                if (AppRole.isAdmin) appScope.launch(Dispatchers.IO) {
                    runCatching { share.onPayment(p, "t$id", code) }
                    afterDealSent(p.storeId, id)
                }
            }
        }
    }
    val reports by lazy { ReportRepository(api, catalog) }
    val history by lazy { HistoryRepository(historyDb) }
    val parked by lazy { ParkedCartRepository(localDb, draft, catalog) }
    val textLabels by lazy { com.ilxomjon.dur_novvot_dokon.data.labels.TextLabelStore(localDb) }
    val localReports by lazy { LocalReportRepository(historyDb) }
    // v5.3: diagnostika serverni TO'G'RIDAN tekshiradi (bulut yo'li alohida) — xato aniq ko'rinsin
    val diagnostics by lazy { DiagnosticsRepository(app, secure, directApi, network, outbox, historyDb, localDb) }
    val repeatSale by lazy { com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale(catalog, draft, parked) }
    val shareStore by lazy { ShareStore(app) }
    val share: ShareRepository by lazy {
        ShareRepository(app, shareStore, catalog, api).also { s ->
            s.codesHook = { bytes -> labels.applyCodes(bytes) }
            s.pluOf = { id -> labels.pluFor(id) }
            // v3.2: 1C savdolar/ta'minotchilar, ish vaqti, mijoz so'rovlari (faqat Admin)
            s.workHours = { appPrefs.workHours }
            // v4.0.1: yangi 1C savdolari kelganda 2-ilova mijozlari qayta yuboriladi (tarixda tovarlar, Narxlar ro'yxati)
            s.dataHook = { kind, bytes ->
                onec.onData(kind, bytes).also { ok ->
                    if (ok && kind == "sales" && AppRole.isAdmin) {
                        s.markDirty()
                        // v5.0: kompyuterda o'zgarish bo'ldi — davriy so'rov o'rniga faqat shu mijozlar yangilanadi
                        val names = onec.state.value.sales?.docs.orEmpty().map { it.party }.filter { it.isNotBlank() }.distinct().take(40)
                        appScope.launch { runCatching { s.history.bumpByNames(names) } }
                    }
                }
            }
            s.salesOf = { if (AppRole.isAdmin) onec.state.value.sales else null }
            if (AppRole.isAdmin) {
                s.editHook = { id, entries -> clientRequests.apply(id, entries) }
                s.extraTick = {
                    // v3.2: daqiqalik jadval va ish vaqti kompyuterga birinchi marta o'zi yuboriladi
                    if (shareStore.isCloudReady && !shareStore.pcLiveSent) {
                        runCatching { s.setPcSchedule(shareStore.pcHours, shareStore.pcDays, shareStore.pcMinutes) }.onSuccess { shareStore.pcLiveSent = true }
                    }
                    runCatching { clientRequests.refresh(notify = true) }
                    runCatching { onec.refreshResults() }
                    // v4.0: kechki hisobot — har kuni 20:00 dan keyin bir marta (bugungi savdo, foyda, tushum)
                    runCatching { eveningReport() }
                    // v4.0: qurilmalar ro'yxati (bloklanganlar ko'prikda ishlatiladi) — 3 daqiqada bir
                    if (System.currentTimeMillis() - deviceAdmin.loadedAt > 3 * 60_000L) runCatching { deviceAdmin.refresh() }
                    // v3.7: kompyuterdan 1 yillik savdolar (30 kunlik bo'laklar)
                    runCatching { onec.backfillStep() }
                    // v3.7: "1C tarixi" agentlarga (faqat o'zgargan 10 kunlik bo'laklar, daqiqada ko'pi bilan bir)
                    runCatching {
                        val d = adminHistory.data.value
                        val cfg = com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Config(uz.imsoft.shared.CloudDb.normalizeUrl(shareStore.dbUrl), shareStore.agentKey)
                        val pend = uz.imsoft.shared.report.PendingMerge.pending(d.entries, localDocs.value, app.getString(R.string.pending_1c))
                        historyShare.publish(cfg.takeIf { it.isValid }, shareStore.secret, d.entries, d.version * 65_536 + (pend.hashCode() and 0xFFFF), d.loaded, pend)
                    }
                }
            }
        }
    }
    /**
     * v3.2: to'lov serverga yozilishini kutib (navbat SENT), keyin qarzni serverdan bir necha marta o'qiydi — server 1C'dan
     * qarzni kechikib olishi mumkin (0, 1, 3, 10 daqiqa). Har safar 2-ilovaga yuboriladi.
     */
    suspend fun afterDealSent(storeId: String, outboxId: Long) {
        // v3.6: Admin "1C tarixi" barcha mijozlar uchun — 2-ilovada bo'lmagan mijoz ham yangilanadi
        if (!AppRole.isAdmin || storeId.isEmpty()) return
        val deadline = System.currentTimeMillis() + 15 * 60_000L
        while (outboxId > 0 && System.currentTimeMillis() < deadline) {
            val st = runCatching { outbox.statesFor(listOf(outboxId))[outboxId] }.getOrNull()
            if (st == null || st.isDone) break
            kotlinx.coroutines.delay(10_000L)
        }
        runCatching { share.afterDeal(storeId) }
        pcSalesSoon()
    }

    @Volatile private var pcSalesJob: kotlinx.coroutines.Job? = null

    /**
     * v4.0: savdodan keyin kompyuterdan bugungi 1C savdolari so'raladi (1C serverdan olishiga vaqt beriladi: 2 va 6 daqiqa) —
     * yagona ro'yxatda "1C'ga ketmoqda" tezroq "1C" yozuviga almashadi. Ketma-ket savdolarda bitta so'rov.
     */
    fun pcSalesSoon() {
        if (!AppRole.isAdmin || !onec.ready || pcSalesJob?.isActive == true) return
        pcSalesJob = appScope.launch(Dispatchers.IO) {
            val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
            for (wait in listOf(120_000L, 240_000L)) {
                kotlinx.coroutines.delay(wait)
                runCatching { onec.requestData("sales", day, day) }
            }
        }
    }

    /** v3.7 Admin: "1C tarixi" — kompyuterdagi barcha savdolar + server kartochkalari (to'lovlar bilan), 1 yil. */
    val adminHistory: com.ilxomjon.dur_novvot_dokon.data.share.AdminHistory by lazy {
        com.ilxomjon.dur_novvot_dokon.data.share.AdminHistory(app, share.history, onec, share, catalog, appScope)
    }

    /** v3.7: Admin "1C tarixi" -> agentlar (bulut orqali). */
    val historyShare: com.ilxomjon.dur_novvot_dokon.data.share.HistoryShare by lazy { com.ilxomjon.dur_novvot_dokon.data.share.HistoryShare(app) }

    /** v3.2 Admin: 1C ma'lumotlari va "Tasdiqlab kiritish" navbati. */
    val onec: com.ilxomjon.dur_novvot_dokon.data.onec.OneCRepository by lazy {
        com.ilxomjon.dur_novvot_dokon.data.onec.OneCRepository(app, shareStore).also { it.scope = appScope }
    }
    /** v5.0: dollar kursi internetdan (bozorga yaqin bank kursi + rasmiy kurs). */
    /** v5.3: mahsulot rasmlari (faqat shu telefonda). */
    val images by lazy { com.ilxomjon.dur_novvot_dokon.data.images.ProductImages(app) }
    val rateOnline: uz.imsoft.shared.android.RateOnline by lazy { uz.imsoft.shared.android.RateOnline(app) }

    /** v5.2 (A7): mahsulot ma'lumotlari (bio) — internetsiz ham ochiladi. */
    val bio: com.ilxomjon.dur_novvot_dokon.data.bio.BioRepository by lazy {
        com.ilxomjon.dur_novvot_dokon.data.bio.BioRepository(app).also { r -> appScope.launch { runCatching { r.load() } } }
    }

    /** v3.12: "Shtrix kod → 1C" ro'yxati. */
    val barcodeBatch by lazy { com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeBatch(app) }

    /**
     * v3.12: ro'yxatdagi kodlarni 1C'ga: Admin — kompyuter navbatiga (bitta ulanishda yoziladi), Agent ("Admin orqali") —
     * Admin'ga. @return qabul qilingan soni; -1 — bu rejimda mumkin emas (agent serverga to'g'ridan ulangan yoki bulut sozlanmagan).
     */
    suspend fun queueBarcodes(items: List<com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeBatch.Item>): Int {
        if (items.isEmpty()) return 0
        if (!AppRole.isAdmin && cloudReady) return relayClient.sendBarcodes(items.map { it.productId to it.code })
        if (!AppRole.isAdmin || !onec.ready) return -1
        val byId = catalog.state.value.productsById
        val reqs = items.map { i ->
            uz.imsoft.shared.onec.WriteRequest(
                uz.imsoft.shared.onec.WriteCodec.newId(), uz.imsoft.shared.onec.WriteKind.BARCODE, System.currentTimeMillis(),
                party = byId[i.productId]?.name ?: i.name, partyCode = i.productId, barcode = i.code,
            )
        }
        return reqs.size - onec.submitAll(reqs, "Admin")
    }
    /** v3.2 Admin: 2-ilovadan to'lov va chek tuzatish so'rovlari. */
    val clientRequests: com.ilxomjon.dur_novvot_dokon.data.share.ClientRequestsRepository by lazy {
        com.ilxomjon.dur_novvot_dokon.data.share.ClientRequestsRepository(app, shareStore, historyDb) { storeId, storeName, cash, transfer, click, note ->
            orders.sendPaymentId(storeId, storeName, cash, transfer, click, note, "")
        }
    }
    val barcodes: com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository by lazy {
        com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository(app, catalog).also { b ->
            // v3.2: yangi shtrix kod 1C'ga (tovarning shtrix ro'yxati va Штрих ma'lumotnomasi)
            b.onLearned = { productId, code -> queueBarcode(productId, code, fromAgent = false) }
        }
    }

    /** Admin: 1C'ga yozish navbatiga; Agent "Admin orqali": Admin'ga yuboradi; Agent to'g'ridan — faqat telefonda qoladi. */
    suspend fun queueBarcode(productId: String, code: String, fromAgent: Boolean): Boolean {
        val name = catalog.state.value.productsById[productId]?.name.orEmpty()
        if (!AppRole.isAdmin && cloudReady) return runCatching { relayClient.sendBarcode(productId, name, code) }.getOrDefault(false)
        if (!AppRole.isAdmin || !onec.ready) return false
        val req = uz.imsoft.shared.onec.WriteRequest(
            uz.imsoft.shared.onec.WriteCodec.newId(), uz.imsoft.shared.onec.WriteKind.BARCODE, System.currentTimeMillis(),
            party = name, partyCode = productId, note = if (fromAgent) "agent" else "", barcode = code.filter { it.isLetterOrDigit() },
        )
        if (req.problem() != null) return false
        return runCatching { onec.submit(req, "Admin") }.isSuccess
    }
    val labels: com.ilxomjon.dur_novvot_dokon.data.labels.LabelRepository by lazy {
        com.ilxomjon.dur_novvot_dokon.data.labels.LabelRepository(app, catalog, barcodes, shareStore).also { l ->
            l.relayActive = { !AppRole.isAdmin && cloudReady }
            l.relayPrint = { data, copies -> relayClient.printLabels(data, copies) }
            l.relayRefreshCodes = { catalog.sync() }
            // pc 35: Admin Tailscale'siz — kompyuter ko'prigi orqali
            l.pcPrintActive = { AppRole.isAdmin }
            l.pcPrint = { data, copies -> relayHost.printViaPc(data, copies) }
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
                override suspend fun mirrorSale(sale: com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Sale, key: String) {
                    share.onSale(sale.order, 0L, sale.code, key, sale.branch)
                    appScope.launch(Dispatchers.IO) { afterDealSent(sale.order.storeId, 0L) }
                }
                override suspend fun barcode(productId: String, name: String, code: String): Boolean = queueBarcode(productId, code, fromAgent = true)
                override fun blockedDevice(deviceId: String): Boolean = deviceId in deviceAdmin.blockedIds()
                override fun blockedDevices(): List<String> = deviceAdmin.blockedIds().toList()
                override fun productIds(): List<String> = catalog.state.value.products.map { it.id }
                override fun updateUrl(): String = com.ilxomjon.dur_novvot_dokon.BuildConfig.UPDATE_URL
                override fun pcLink(): Pair<String, Map<String, String>>? {
                    val cfg = secure.serverConfig
                    if (!cfg.isComplete || shareStore.inboxKey.isEmpty()) return null
                    // pc 28: "lq" — yorliq printerining Windows'dagi ulashish nomi (kompyuter agent yorliqlarini shu printerga chiqaradi)
                    return shareStore.inboxKey to linkedMapOf("host" to cfg.host, "database" to cfg.database, "user" to cfg.login, "password" to cfg.password,
                        "lq" to labels.state.value.queue)
                }
                override fun lastCosts(): Map<String, Pair<Double, Double>> = onec.lastCosts()
                override fun written(agentId: String, type: String, payload: String) = agentStats.record(agentId, type, payload)
                override fun branchStores(): List<String> = shareStore.clients().filter { it.queueKey.isNotEmpty() && it.storeId.isNotEmpty() }.map { it.storeId }
                override suspend fun barcodes(items: List<Pair<String, String>>): Int {
                    if (!onec.ready) return 0
                    val byId = catalog.state.value.productsById
                    val reqs = items.mapNotNull { (id, code) ->
                        val c = com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeBatch.clean(code)
                        uz.imsoft.shared.onec.WriteRequest(
                            uz.imsoft.shared.onec.WriteCodec.newId(), uz.imsoft.shared.onec.WriteKind.BARCODE, System.currentTimeMillis(),
                            party = byId[id]?.name.orEmpty(), partyCode = id, note = "agent", barcode = c,
                        ).takeIf { it.problem() == null }
                    }
                    return reqs.size - onec.submitAll(reqs, "Admin")
                }
                override fun level12(productIds: List<String>): Map<String, List<Double>> {
                    val out = LinkedHashMap<String, List<Double>>()
                    for (id in productIds) {
                        val a = labels.priceForLevel(id, 1) ?: 0.0
                        val b = labels.priceForLevel(id, 2) ?: 0.0
                        if (a > 0 || b > 0) out[id] = listOf(a, b)
                    }
                    return out
                }
                override suspend fun mirrorPayment(storeId: String, cash: Double, transfer: Double, click: Double, note: String, key: String) {
                    val st = catalog.storeById(storeId)
                    val p = com.ilxomjon.dur_novvot_dokon.domain.SentPayment(0, System.currentTimeMillis(), storeId, st?.name.orEmpty(), cash, transfer, click, note)
                    runCatching { share.onPayment(p, key, "") }
                    appScope.launch(Dispatchers.IO) { afterDealSent(storeId, 0L) }
                }
            },
        ).also { h -> h.catalogPeriodMs = { com.ilxomjon.dur_novvot_dokon.data.AutoUpdate.intervalMs().coerceIn(2 * 60_000L, 20 * 60_000L) } }
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
