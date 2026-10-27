package com.ilxomjon.dur_novvot_dokon.domain.outbox

/**
 * Navbat (outbox) holatlari. Kodlar bazada saqlanadi — O'ZGARTIRMANG.
 *
 *  READY        — telefonda saqlandi, yuborishga tayyor.
 *  SENDING      — hozir yuborilmoqda (lease vaqti bilan "egallangan").
 *  RETRY_WAIT   — oldingi urinish serverga YETIB BORMAGANI aniq (ulanish yo'q edi); keyinroq qayta uriniladi.
 *  UNCERTAIN    — INSERT yuborilgan, lekin natijasi noma'lum. Qayta yuborishdan OLDIN serverda qidiriladi (probe).
 *  SENT         — serverdagi "savollar" jadvaliga yozildi (ID bor). Bu "server qayta ishladi" degani EMAS.
 *  NEEDS_CHECK  — avtomatik hal qilib bo'lmadi: agent/ofis tekshirishi kerak. Avtomatik yuborilmaydi.
 *  SENT_MANUAL  — agent "serverda bor" deb qo'lda belgiladi.
 */
enum class OutboxState(val code: String) {
    READY("READY"), SENDING("SENDING"), RETRY_WAIT("RETRY_WAIT"), UNCERTAIN("UNCERTAIN"),
    SENT("SENT"), NEEDS_CHECK("NEEDS_CHECK"), SENT_MANUAL("SENT_MANUAL");

    val isDone: Boolean get() = this == SENT || this == SENT_MANUAL

    companion object {
        /** Noma'lum/bo'sh kod — xavfsiz tomonga: avval tekshiriladi. */
        fun fromCode(c: String?): OutboxState = entries.firstOrNull { it.code == c } ?: UNCERTAIN
    }
}

enum class OutboxKind(val code: String, val protocolType: String, val receiptPrefix: String) {
    ORDER("order", "1", "B"), PAYMENT("payment", "4", "T");

    companion object {
        fun fromCode(c: String?): OutboxKind = entries.firstOrNull { it.code == c } ?: ORDER
    }
}

/** NEEDS_CHECK sabablari (bazada kod sifatida). */
object CheckReason {
    const val PROBE_UNRELIABLE = "PROBE_UNRELIABLE"   // serverda topilmadi, lekin qidiruvga ishonib bo'lmaydi
    const val RESTORED = "RESTORED"                   // zaxiradan tiklangan
    const val REJECTED = "REJECTED"                   // server bir necha marta rad etdi (masalan, matn juda uzun)
    const val PROBE_FAILED = "PROBE_FAILED"           // tekshiruv ko'p marta bajarilmadi
}

data class OutboxEntry(
    val id: Long,
    val kind: OutboxKind,
    val agentId: String,
    val payload: String,
    val localUuid: String,
    val createdAt: Long,
    val state: OutboxState,
    val attempts: Int,
    /** Yuborishdan oldin shu agentning serverdagi eng katta so'rov ID'si (probe shundan keyingilarni qidiradi). */
    val watermark: Long?,
    val serverRowId: String?,
    val leaseUntil: Long,
    val nextAt: Long,
    /** 1 = INSERT boshlangan (natija noma'lum bo'lishi mumkin). */
    val phase: Int,
    val prevState: OutboxState?,
    val lastError: String?,
    val checkReason: String?,
    val forceSend: Boolean,
    val storeId: String,
    val storeName: String,
    val amount: Double,
    val sentAt: Long,
) {
    /** Kvitansiya kodi, masalan "B-0412". */
    val receipt: String get() = receiptOf(kind, id)

    companion object {
        fun receiptOf(kind: OutboxKind, id: Long): String = kind.receiptPrefix + "-" + (id % 10000).toString().padStart(4, '0')
    }
}

/** Navbatni saqlovchi. Android'da SQLite ([com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb]), testda xotirada. */
interface OutboxStore {
    /**
     * Eng eski yuborilishi mumkin bo'lgan qatorni ATOMIK egallaydi: holati SENDING, lease = now + leaseMs.
     * Mos keladiganlar: READY; RETRY_WAIT/UNCERTAIN (nextAt <= now); muddati o'tgan SENDING.
     * Qaytgan yozuvda [OutboxEntry.prevState] — egallashdan oldingi holat.
     */
    fun claimNext(now: Long, leaseMs: Long): OutboxEntry?

    fun setWatermark(id: Long, watermark: Long, now: Long)
    /** INSERT boshlanishidan OLDIN yoziladi (phase = 1). */
    fun markInserting(id: Long, now: Long)
    fun markSent(id: Long, serverRowId: String, now: Long)
    /** Serverga yetib bormagani aniq: phase = 0, keyinroq qayta. */
    fun markRetry(id: Long, error: String?, nextAt: Long, now: Long)
    /** Natija noma'lum: keyingi urinishda avval probe. */
    fun markUncertain(id: Long, error: String?, nextAt: Long, now: Long)
    fun markNeedsCheck(id: Long, reason: String, error: String?, now: Long)

    /** Shu agentning oxirgi serverga yozilgan (ID ma'lum) so'rovi — probe ishonchliligini tekshirish uchun. */
    fun lastServerRowId(agentId: String): String?
}

/** Serverga yozish va o'qish (faqat mavjud savollar jadvali; yangi server obyekti YO'Q). */
interface OutboxSink {
    /** SELECT MAX(ID) FROM savollar WHERE user_id = ?. Yo'q bo'lsa 0. */
    suspend fun maxRequestId(userId: String): Long

    /** INSERT ... OUTPUT INSERTED.ID. Xatoda [NotSentException] (yetib bormagani aniq) yoki boshqa istisno (noma'lum). */
    suspend fun insert(userId: String, type: String, payload: String): String

    /** Aynan shu matnli so'rovni qidiradi. [afterId] berilsa faqat undan keyingilar orasidan. */
    suspend fun find(userId: String, type: String, payload: String, afterId: Long?): String?

    /** Shu ID'li so'rov serverda hali bormi. */
    suspend fun exists(requestId: String): Boolean
}

/** So'rov serverga yetib bormagani ANIQ bo'lganda (masalan, ulanishning o'zi ochilmadi). */
class NotSentException(message: String?, cause: Throwable? = null) : Exception(message, cause)

/** Server so'rovni rad etdi (SQL xatosi), yozilmagani aniq. */
class RejectedException(message: String?, cause: Throwable? = null) : Exception(message, cause)

fun interface Clock { fun now(): Long }
