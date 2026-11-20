package uz.imsoft.shared.label

/** v5.3.3: matnli etiketka (58x40, Arial 14) uchun sof mantiq: matnni tozalash, nusxa soni, ro'yxat uchun qisqa matn. */
object TextLabels {
    const val PT = 14f
    const val MAX_COPIES = 99
    const val MAX_CHARS = 400

    /** Qator ajratgichlarini birxillashtiradi, qator oxiridagi bo'shliqlarni va chetdagi bo'sh qatorlarni olib tashlaydi. */
    fun clean(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' ')
            .lines().joinToString("\n") { it.trimEnd() }
            .trim()
            .take(MAX_CHARS)
            .trimEnd()

    fun copies(n: Int?): Int = (n ?: 1).coerceIn(1, MAX_COPIES)

    /** Tarix ro'yxati uchun bir qatorli ko'rinish: bo'sh qatorlar tashlanadi, qatorlar " · " bilan ulanadi. */
    fun summary(text: String, max: Int = 60): String {
        val one = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" \u00B7 ")
        return if (one.length <= max) one else one.take(max - 1).trimEnd() + "\u2026"
    }
}
