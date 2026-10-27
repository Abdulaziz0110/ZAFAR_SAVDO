package com.ilxomjon.dur_novvot_dokon.core

/**
 * Qidiruv uchun matnni bitta "kalit"ga keltiradi: kichik harf, kirill -> lotin, tutuq belgisi olib tashlanadi.
 * Natijada agent lotinda "yog" yozsa, bazadagi "Ёғ" ham topiladi (va aksincha).
 */
object Translit {
    private val cyr = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "j", 'з' to "z",
        'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "s", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh",
        'ъ' to "", 'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o", 'қ' to "q", 'ғ' to "g", 'ҳ' to "h",
    )

    fun key(input: String): String {
        val sb = StringBuilder(input.length + 4)
        for (ch in input.lowercase()) {
            val m = cyr[ch]
            when {
                m != null -> sb.append(m)
                ch == '\'' || ch == '’' || ch == 'ʻ' || ch == 'ʼ' || ch == '‘' || ch == '`' -> Unit
                ch == 'w' -> sb.append('v')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
