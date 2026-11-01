package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

/** 2-ilovaga kira oladigan telefon (mijozning o'zi yoki do'konidagi ishchi). Parol shu telefonda shifrlangan holda saqlanadi. */
data class ShareMember(val phone: String, val name: String, val password: String, val addedAt: Long)

/**
 * Bitta mijoz (yoki yuk beruvchi): o'z o'qish kaliti, ixtiyoriy chek printeri navbati, kira oladigan telefonlar.
 * [id] — katalogdagi do'kon id si (bo'lsa), aks holda nomdan yasalgan barqaror id.
 */
data class ShareClient(
    val id: String,
    val name: String,
    val storeId: String,
    val readKey: String,
    val queueKey: String = "",
    val members: List<ShareMember> = emptyList(),
    val dirty: Boolean = true,
    val lastPublishAt: Long = 0L,
    val lastRequestServed: Long = 0L,
    /** "c" — mijoz, "s" — yuk beruvchi. */
    val kind: String = "c",
)

/**
 * Ulashish sozlamalari (v2.6: ko'p mijozli). Firebase "secret", mijozlar ro'yxati va parollar Android Keystore bilan
 * shifrlangan holda saqlanadi va ekranga to'liq chiqarilmaydi (parol — faqat egasi so'rasa). Mavjud sozlamalarga tegmaydi.
 */
class ShareStore(private val context: Context) {
    private val prefs: SharedPreferences = create()

    private fun create(): SharedPreferences = try {
        build()
    } catch (e: Exception) {
        Log.w("ShareStore", "qayta yaratilmoqda", e)
        context.deleteSharedPreferences(FILE)
        build()
    }

    private fun build(): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun s(k: String) = prefs.getString(k, "").orEmpty()
    private fun put(k: String, v: String) = prefs.edit().putString(k, v).apply()

    var dbUrl: String get() = s("db_url"); set(v) = put("db_url", v)
    var secret: String get() = s("secret"); set(v) = put("secret", v)

    /** Yangi ma'lumot (import, narx/qarz yangilanishi) bo'lsa o'zi yuborsin. */
    var autoPublish: Boolean get() = prefs.getBoolean("auto", true); set(v) = prefs.edit().putBoolean("auto", v).apply()
    /** 2-ilova "Yangilash" bosganda narx/qarzni (server ko'rinsa) yangilab yuborsin. */
    var answerRequests: Boolean get() = prefs.getBoolean("answer", true); set(v) = prefs.edit().putBoolean("answer", v).apply()
    var rulesOk: Boolean get() = prefs.getBoolean("rules_ok", false); set(v) = prefs.edit().putBoolean("rules_ok", v).apply()
    var lastPublishAt: Long get() = prefs.getLong("last_pub", 0L); set(v) = prefs.edit().putLong("last_pub", v).apply()
    var lastPublishError: String get() = s("last_err"); set(v) = put("last_err", v)
    /** O'rnatilgan qoidalar versiyasi (CloudDb.RULES_VERSION dan kichik bo'lsa qayta o'rnatiladi). */
    var rulesVersion: Int get() = prefs.getInt("rules_ver", if (rulesOk) 1 else 0); set(v) = prefs.edit().putInt("rules_ver", v).apply()
    /** 2-ilovadan buyurtma qabul qilinsin. */
    var acceptOrders: Boolean get() = prefs.getBoolean("accept_orders", true); set(v) = prefs.edit().putBoolean("accept_orders", v).apply()
    /** Ko'rilgan buyurtmalar (bildirishnoma qayta chiqmasin). */
    var seenOrders: String get() = s("seen_orders"); set(v) = put("seen_orders", v)
    /** Chek sarlavhasi (do'kon nomi), bo'sh bo'lishi mumkin. */
    var receiptHeader: String get() = s("receipt_header"); set(v) = put("receipt_header", v)
    /** Chek osti (telefonlar), har qator alohida. v2.6 */
    var receiptFooter: String get() = s("receipt_footer"); set(v) = put("receipt_footer", v)
    /** Chekdagi "Xodim" (masalan "Dokon"). Bo'sh — chiqmaydi. */
    var receiptStaff: String get() = s("receipt_staff"); set(v) = put("receipt_staff", v)
    /** Chek ko'rinishi ([uz.imsoft.shared.receipt.ReceiptStyle.encode]); bo'sh — standart. v2.9 */
    var receiptStyle: String get() = s("receipt_style"); set(v) = put("receipt_style", v)
    /** Chek logosi (PNG, eni <= 576) — ilova fayllarida. */
    private val logoFile get() = java.io.File(context.filesDir, "receipt_logo.png")
    var receiptLogo: ByteArray?
        get() = logoFile.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
        set(v) { if (v == null || v.isEmpty()) logoFile.delete() else logoFile.writeBytes(v) }
    /** v3.0 Admin: agentlar ko'prigi kaliti (g/{kalit}) — Agent ulanish kodida boradi. */
    var agentKey: String get() = s("agent_key"); set(v) = put("agent_key", v)
    /** v4.0 Admin: qurilmalar ro'yxati kaliti (d/{kalit}) — Agent/Mijoz kalitsiz ulanadi, Admin biriktiradi. */
    val devKey: String
        get() = s("dev_key").ifEmpty { CloudDb.newKey().also { put("dev_key", it) } }
    /** v3.0 Admin: ko'prik yoqilgan (fonda ishlaydi). */
    /** v3.1: standart — yoqilgan (Firebase sozlangan bo'lsa ko'prik o'zi ishlaydi; foydalanuvchi o'chirishi mumkin). */
    var relayOn: Boolean get() = prefs.getBoolean("relay_on", true); set(v) = prefs.edit().putBoolean("relay_on", v).apply()
    /** Fon yangilanishi oralig'i, daqiqa (0 — o'chiq). */
    var syncMinutes: Int get() = prefs.getInt("sync_min", 60); set(v) = prefs.edit().putInt("sync_min", v).apply()
    /** Kompyuterdan avtomatik eksport (1C COM) uchun "kirish qutisi" kaliti. */
    var inboxKey: String get() = s("inbox_key"); set(v) = put("inbox_key", v)
    /** Narx yorlig'i printeri (kompyuter) bilan kalit: LAN va bulut navbati shu bilan. v2.6 */
    val labelKey: String
        get() = s("label_key").ifEmpty { CloudDb.newKey().also { put("label_key", it) } }
    /** Kompyuter necha soatda bir eksport qilsin (0 — o'chiq) va necha kunlik. Bulut orqali kompyuterga yetkaziladi. */
    var pcHours: Int get() = prefs.getInt("pc_hours", 2); set(v) = prefs.edit().putInt("pc_hours", v).apply()
    var pcDays: Int get() = prefs.getInt("pc_days", 7); set(v) = prefs.edit().putInt("pc_days", v).apply()
    /** v3.2: kompyuter tezkor yangilash oralig'i (daqiqa, 0 — o'chiq). */
    var pcMinutes: Int get() = prefs.getInt("pc_min", 5); set(v) = prefs.edit().putInt("pc_min", v).apply()
    /** v3.2 jadvali (daqiqa, ish vaqti) kompyuterga bir marta avtomatik yuborilganmi. */
    var pcLiveSent: Boolean get() = prefs.getBoolean("pc_live_sent", false); set(v) = prefs.edit().putBoolean("pc_live_sent", v).apply()
    /** v3.2: tasdiqlangan mijoz chek tuzatishlari (JSON: mijoz id -> hujjat kaliti -> qatorlar). */
    var edits: String get() = s("edits"); set(v) = put("edits", v)
    /** v3.2: Admin tasdiqlagan mijoz to'lovlari (so'rov id -> navbat id), holat kuzatish uchun. */
    var payLinks: String get() = s("pay_links"); set(v) = put("pay_links", v)

    /** Ekranda tanlangan mijoz (1C tarixi, chek printeri). */
    var currentId: String get() = s("current_client"); set(v) = put("current_client", v)

    // ---------------- mijozlar ----------------

    private var cacheRaw: String? = null
    private var cacheList: List<ShareClient> = emptyList()

    @Synchronized
    fun clients(): List<ShareClient> {
        migrateLegacy()
        val raw = s("clients")
        if (raw == cacheRaw) return cacheList
        cacheList = if (raw.isEmpty()) emptyList() else runCatching { (Json.parse(raw) as? List<*>).orEmpty().mapNotNull { fromJson(it) } }.getOrDefault(emptyList())
        cacheRaw = raw
        return cacheList
    }

    fun saveClients(list: List<ShareClient>) = put("clients", Json.write(list.map { toJson(it) }))

    fun client(id: String): ShareClient? = clients().firstOrNull { it.id == id }

    fun current(): ShareClient? {
        val l = clients()
        return l.firstOrNull { it.id == currentId } ?: l.firstOrNull()
    }

    @Synchronized
    fun update(id: String, f: (ShareClient) -> ShareClient) {
        saveClients(clients().map { if (it.id == id) f(it) else it })
    }

    @Synchronized
    fun upsert(c: ShareClient) {
        val l = clients()
        saveClients(if (l.any { it.id == c.id }) l.map { if (it.id == c.id) c else it } else l + c)
    }

    fun remove(id: String) = saveClients(clients().filter { it.id != id })

    fun markAllDirty() = saveClients(clients().map { it.copy(dirty = true) })

    /** v3.0: sozlamalarni ko'chirish (faqat parolli zaxira ichida). */
    fun exportAll(): org.json.JSONObject = com.ilxomjon.dur_novvot_dokon.data.PrefsDump.toJson(prefs.all)

    @Synchronized
    fun importAll(json: org.json.JSONObject): Boolean {
        cacheRaw = null
        return com.ilxomjon.dur_novvot_dokon.data.PrefsDump.apply(prefs, json)
    }

    val isConfigured: Boolean get() = dbUrl.isNotBlank() && secret.isNotBlank() && clients().isNotEmpty()
    val isCloudReady: Boolean get() = dbUrl.isNotBlank() && secret.isNotBlank()

    /** v2.5 dan: bitta mijoz (client_id, read_key, queue_key) -> ro'yxat. Ledger fayli o'sha id bilan qoladi. */
    private fun migrateLegacy() {
        if (prefs.getBoolean("migrated_v26", false)) return
        val cid = s("client_id")
        if (cid.isNotEmpty() && s("clients").isEmpty()) {
            val c = ShareClient(
                id = cid, name = s("client_name"), storeId = cid, readKey = s("read_key").ifEmpty { CloudDb.newKey() },
                queueKey = s("queue_key"), dirty = prefs.getBoolean("dirty", true), lastPublishAt = prefs.getLong("last_pub", 0L),
                lastRequestServed = prefs.getLong("last_req", 0L),
            )
            put("clients", Json.write(listOf(toJson(c))))
            put("current_client", cid)
        }
        prefs.edit().putBoolean("migrated_v26", true).apply()
    }

    private fun toJson(c: ShareClient): Map<String, Any?> = linkedMapOf(
        "id" to c.id, "n" to c.name, "st" to c.storeId, "r" to c.readKey, "q" to c.queueKey, "k" to c.kind,
        "d" to c.dirty, "pub" to c.lastPublishAt, "req" to c.lastRequestServed,
        "m" to c.members.map { linkedMapOf("p" to it.phone, "n" to it.name, "pw" to it.password, "at" to it.addedAt) },
    )

    @Suppress("UNCHECKED_CAST")
    private fun fromJson(x: Any?): ShareClient? {
        val o = JObj(x as? Map<String, Any?> ?: return null)
        val id = o.str("id")
        if (id.isEmpty()) return null
        return ShareClient(
            id, o.str("n"), o.str("st"), o.str("r"), o.str("q"),
            o.objs("m").map { ShareMember(it.str("p"), it.str("n"), it.str("pw"), it.long("at")) },
            o.bool("d", true), o.long("pub"), o.long("req"), o.str("k", "c"),
        )
    }

    private companion object { const val FILE = "share_secure" }
}
