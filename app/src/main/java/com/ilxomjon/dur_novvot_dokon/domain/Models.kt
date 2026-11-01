package com.ilxomjon.dur_novvot_dokon.domain

import com.ilxomjon.dur_novvot_dokon.core.Translit

/** Savdo turi. Kodlar serverga ketadigan qiymatlar bilan bir xil. */
enum class PriceType(val code: String) {
    CASH("0"),        // Naqd (chakana narx)
    WHOLESALE("1");   // Optom
    // Eslatma: avvalgi "2" (kelishilgan) kodi endi optom hisoblanadi; narx hamma turda tahrirlanadi.

    companion object {
        fun fromCode(code: String?): PriceType = entries.firstOrNull { it.code == code } ?: WHOLESALE

        /**
         * v3.1: 1C'dagi 4 narx (1..4). Serverga (savollar matni) faqat ikki kod boradi: 3-narx — Naqd ("0"), qolganlari "1";
         * qator narxi har doim aniq yuboriladi.
         */
        fun forLevel(level: Int): PriceType = if (level == 3) CASH else WHOLESALE
        fun levelOf(t: PriceType): Int = if (t == CASH) 3 else 4
    }
}

enum class Currency(val code: String) {
    SOM("0"), USD("1");

    companion object {
        fun fromCode(code: String?): Currency = entries.firstOrNull { it.code == code } ?: SOM
    }
}

/** "1" - sotuv, "2" - qaytarib olish (vozvrat). */
enum class DealMode(val code: String) {
    SALE("1"), RETURN("2");

    companion object {
        fun fromCode(code: String?): DealMode = entries.firstOrNull { it.code == code } ?: SALE
    }
}

data class Product(
    val groupId: String,
    val id: String,
    val name: String,
    /** Asl "valyuta_turi" == "1": asosiy narx so'mda, dollar kurs orqali hisoblanadi. Aks holda asosiy narx dollarda. */
    val baseIsSom: Boolean,
    val cashSom: Double,
    val cashUsd: Double,
    val wholesaleSom: Double,
    val wholesaleUsd: Double,
    val costSom: Double,
    val costUsd: Double,
    val blockSize: Double,
) {
    /** Qidiruv kaliti (kirill/lotin farqi yo'q). */
    val searchKey: String = Translit.key(name)
    val idLower: String = id.lowercase()
    val hasBlock: Boolean get() = blockSize > 1.0
}

data class Group(val id: String, val name: String)

data class Store(
    val id: String,
    val regionId: String,
    val name: String,
    val regionName: String,
    val debtSom: Double,
    val debtUsd: Double,
) {
    val searchKey: String = Translit.key(name)
    val regionKey: String = Translit.key(regionName)
}

data class Agent(val id: String, val fullName: String, val phone: String)

data class CartLine(
    val rowId: Long,
    val productId: String,
    val name: String,
    val blockSize: Double,
    val qty: Double,
    val price: Double,
) {
    val total: Double get() = qty * price
}

/** Joriy (tugallanmagan) savdo. Do'kon, savdo turi va valyuta qayta ishga tushganda ham saqlanadi. */
data class DraftState(
    val store: Store? = null,
    val priceType: PriceType = PriceType.WHOLESALE,
    val currency: Currency = Currency.SOM,
    val mode: DealMode = DealMode.SALE,
    val lines: List<CartLine> = emptyList(),
    /** v3.1: narx (1..4); 0 — [priceType] dan (3 yoki 4). */
    val priceLevel: Int = 0,
) {
    val level: Int get() = if (priceLevel in 1..4) priceLevel else PriceType.levelOf(priceType)
    val total: Double get() = lines.sumOf { it.total }
    val itemCount: Int get() = lines.size
    fun lineFor(productId: String): CartLine? = lines.firstOrNull { it.productId == productId }
}

data class ServerConfig(
    val host: String = "",
    val database: String = "",
    val login: String = "",
    val password: String = "",
    /** Do'kon tarmog'idan tashqarida ishlash uchun ixtiyoriy ikkinchi manzil (masalan VPN manzili). */
    val remoteHost: String = "",
) {
    val isComplete: Boolean get() = host.isNotBlank() && database.isNotBlank() && login.isNotBlank()

    companion object {
        /** Oldindan belgilangan server (Sozlamalarda o'zgartirish mumkin). */
        val DEFAULT = ServerConfig(host = "192.168.1.200", database = "Savdo", login = "sa", password = BuiltIn.password())
    }
}

data class Totals(val total: Double, val discount: Double, val payable: Double)

data class SentOrder(
    val id: Long,
    val sentAt: Long,
    val storeId: String,
    val storeName: String,
    val mode: DealMode,
    val priceType: PriceType,
    val currency: Currency,
    val total: Double,
    val discount: Double,
    val paid: Double,
    val lines: List<SentLine>,
    /** true - hali serverga yozilmagan (navbatda yoki tekshirish kerak). */
    val pending: Boolean = false,
    /** Navbat qatori (kvitansiya kodi va holat uchun). 0 - eski yozuv. */
    val outboxId: Long = 0,
)

data class SentLine(val name: String, val qty: Double, val price: Double, val productId: String = "")

data class SentPayment(
    val id: Long,
    val sentAt: Long,
    val storeId: String,
    val storeName: String,
    val cash: Double,
    val transfer: Double,
    val click: Double,
    val note: String,
    val pending: Boolean = false,
    val outboxId: Long = 0,
) {
    val total: Double get() = cash + transfer + click
}

/**
 * Ilovaga o'rnatilgan server paroli. Matn ko'rinishida saqlanmaydi (APK ichida oddiy qidiruvda topilmaydi) va
 * ekranga hech qachon chiqarilmaydi. DIQQAT: bu kuchli himoya EMAS — APK'ni tahlil qilgan odam baribir tiklay oladi.
 * Haqiqiy yechim serverda alohida cheklangan login (server tomoni o'zgarishi kerak, hozircha mumkin emas).
 */
internal object BuiltIn {
    private val K = byteArrayOf(0x5A, 0x13, 0x7C, 0x29, 0x44, 0x61, 0x0E, 0x35, 0x72)
    private val D = byteArrayOf(0x7E, 0x71, 0x1D, 0x45, 0x25, 0x0F, 0x7D, 0x04, 0x40, 0x69)

    fun password(): String {
        val out = CharArray(D.size)
        for (i in D.indices) out[i] = ((D[i].toInt() xor K[i % K.size].toInt()) and 0xFF).toChar()
        return String(out)
    }
}

