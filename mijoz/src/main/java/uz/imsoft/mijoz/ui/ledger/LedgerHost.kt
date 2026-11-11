package uz.imsoft.mijoz.ui.ledger

import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.StateFlow
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.LedgerEntry

// NUSXA (tools/sync_ledger_kit.py) — o'zgartirmang. ui/ledger/ papkasi ikki ilovada BIR XIL (tools/sync_ledger_kit.py 1-ilovadan 2-ilovaga ko'chiradi).
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
    /** Chek ko'rinishi (1-ilova sozlamasi; 2-ilovada do'kondan kelgan). v2.9 */
    val receiptStyle: uz.imsoft.shared.receipt.ReceiptStyle get() = uz.imsoft.shared.receipt.ReceiptStyle.STANDARD
    /** Chek logosi (yo'q — null). */
    val receiptLogo: android.graphics.Bitmap? get() = null
    val fuzzySearch: Boolean
    /** "Takrorlash" (1-ilova) yoki "Qayta buyurtma" (2-ilova); 0 — tugma yo'q. */
    fun repeatLabel(doc: LedgerDoc): Int
    suspend fun prepareRepeat(ctx: android.content.Context, doc: LedgerDoc): RepeatPlan
    /** [option] — [RepeatPlan.options] dan tanlangani (-1 — so'ralmadi). @return foydalanuvchiga ko'rsatiladigan natija matni. */
    suspend fun submitRepeat(ctx: android.content.Context, plan: RepeatPlan, lines: List<RepeatLine>, wholesale: Boolean, note: String, option: Int): String
    /** Muvaffaqiyatli yuborilgandan keyin (masalan savdo ekraniga o'tish). */
    fun afterSubmit(activity: android.app.Activity, plan: RepeatPlan) = Unit
    fun printTargets(ctx: android.content.Context): List<PrintTarget>

    /** v3.2: "Chekni tuzatish" (2-ilova -> Admin tasdig'i) tugmasi matni; 0 — tugma yo'q. */
    fun editLabel(doc: LedgerDoc): Int = 0

    /** v3.2: tuzatish so'rovi oynasi. */
    fun requestEdit(f: Fragment, doc: LedgerDoc) = Unit

    /** v3.6: ro'yxat ustidagi holat qatori (Admin: serverdan yuklash holati). null — qator ko'rinmaydi. */
    val syncStatus: StateFlow<String>? get() = null

    /** v3.6: holat qatori bosilganda (batafsil, hozir yangilash). */
    fun onSyncStatus(f: Fragment) = Unit


    /** v3.7: ma'lumot umuman yo'q bo'lganda matn (null — odatiy "Mijoz kartochkasi faylini yuklang"). */
    fun emptyText(ctx: android.content.Context): String? = null
}

/**
 * Fragment daraxti bo'ylab yuqoriga qarab LedgerHost ni topadi. Har ilovada yagona host bor —
 * daraxtda topilmasa (masalan buyurtmalar varaqasidan ochilgan "Savatchaga" oynasi) o'sha host qaytadi.
 */
fun Fragment.ledgerHost(): LedgerHost? {
    var f: Fragment? = this
    while (f != null) {
        if (f is LedgerHost) return f
        f = f.parentFragment
    }
    return (activity as? LedgerHost) ?: uz.imsoft.mijoz.ui.ledgerhost.MijozLedgerHost
}
