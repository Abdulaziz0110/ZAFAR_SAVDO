package com.ilxomjon.dur_novvot_dokon.ui.ledger

import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.StateFlow
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.LedgerEntry

// DIQQAT: ui/ledger/ papkasi ikki ilovada BIR XIL (tools/sync_ledger_kit.py 1-ilovadan 2-ilovaga ko'chiradi).
// Ilovaga xos ish (takrorlash/buyurtma, printer) — LedgerHost orqali.

/** 1C tarixi ma'lumoti. [version] o'zgarsa qidiruv indeksi qayta quriladi. */
data class LedgerData(val entries: List<LedgerEntry>, val clientName: String, val version: Long, val loaded: Boolean)

enum class MatchStatus { EXACT, APPROX, NONE, UNCHECKED }

/** Takrorlash/buyurtma oynasidagi qator. */
class RepeatLine(
    val name: String,
    var qty: Double,
    val productId: String,
    val status: MatchStatus,
    /** Katalogdagi nom (taxminiy bog'langanda ko'rsatiladi). */
    val matchedName: String,
    var checked: Boolean,
)

class RepeatPlan(
    val title: String,
    val lines: List<RepeatLine>,
    /** Naqd/Optom tanlovi ko'rinsinmi (1-ilova). */
    val showPriceType: Boolean,
    val defaultWholesale: Boolean,
    /** Izoh maydoni (2-ilova buyurtmasi). */
    val showNote: Boolean,
    val confirmLabel: Int,
    val hint: String,
    /** Ilova uchun qo'shimcha ma'lumot (masalan buyurtma id si). */
    val tag: Any? = null,
    /** Tasdiqlashdan oldin so'raladigan tanlov (masalan savatcha bo'sh emas: almashtirish / qo'shish / kutishga). Bo'sh — so'ralmaydi. */
    val options: List<String> = emptyList(),
    val optionsTitle: String = "",
)

/** Chek printeri (1-ilova: 2-filial printeri bulut orqali; 2-ilova: USB). */
class PrintTarget(val label: String, val send: suspend (ByteArray) -> String)

interface LedgerHost {
    val ledgerData: StateFlow<LedgerData>
    /** Chek sarlavhasi (do'kon nomi). */
    val receiptHeader: String
    /** Chek osti: telefonlar va h.k. (har qator alohida). v2.6 */
    val receiptFooter: String get() = ""
    /** Chekdagi "Xodim". Bo'sh — qator chiqmaydi. */
    val receiptStaff: String get() = ""
    val fuzzySearch: Boolean
    /** "Takrorlash" (1-ilova) yoki "Qayta buyurtma" (2-ilova); 0 — tugma yo'q. */
    fun repeatLabel(doc: LedgerDoc): Int
    suspend fun prepareRepeat(ctx: android.content.Context, doc: LedgerDoc): RepeatPlan
    /** [option] — [RepeatPlan.options] dan tanlangani (-1 — so'ralmadi). @return foydalanuvchiga ko'rsatiladigan natija matni. */
    suspend fun submitRepeat(ctx: android.content.Context, plan: RepeatPlan, lines: List<RepeatLine>, wholesale: Boolean, note: String, option: Int): String
    /** Muvaffaqiyatli yuborilgandan keyin (masalan savdo ekraniga o'tish). */
    fun afterSubmit(activity: android.app.Activity, plan: RepeatPlan) = Unit
    fun printTargets(ctx: android.content.Context): List<PrintTarget>
}

/** Fragment daraxti bo'ylab yuqoriga qarab LedgerHost ni topadi. */
fun Fragment.ledgerHost(): LedgerHost? {
    var f: Fragment? = this
    while (f != null) {
        if (f is LedgerHost) return f
        f = f.parentFragment
    }
    return activity as? LedgerHost
}
