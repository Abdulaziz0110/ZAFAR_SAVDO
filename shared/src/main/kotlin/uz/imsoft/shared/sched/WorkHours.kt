package uz.imsoft.shared.sched

import java.util.Calendar

/**
 * Ish vaqti (v3.2): avtomatik yangilash faqat shu oraliqda tez ishlaydi; tugashidan 1 daqiqa oldin oxirgi to'liq yuklab olish.
 * [start] va [end] — kun boshidan daqiqa (0..1439). end < start bo'lsa — tungi smena (masalan 20:00–04:00).
 * [on] = false — vaqtdan qat'i nazar doim yangilanadi.
 */
data class WorkHours(val on: Boolean = true, val start: Int = 8 * 60, val end: Int = 20 * 60) {
    fun normalized() = copy(start = start.coerceIn(0, 1439), end = end.coerceIn(0, 1439))

    /** Hozir ish vaqtimi (o'chiq bo'lsa doim true). */
    fun isWorkTime(minuteOfDay: Int): Boolean {
        if (!on) return true
        val n = normalized()
        val m = minuteOfDay.coerceIn(0, 1439)
        return when {
            n.start == n.end -> true
            n.start < n.end -> m >= n.start && m < n.end
            else -> m >= n.start || m < n.end
        }
    }

    fun isWorkTime(millis: Long, cal: Calendar = Calendar.getInstance()): Boolean = isWorkTime(minuteOf(millis, cal))

    /** Oxirgi yuklab olish daqiqasi: tugashdan 1 daqiqa oldin. */
    val finalMinute: Int get() = (normalized().end + 1439) % 1440

    /** [now] dan keyingi oxirgi yuklab olish vaqti (ms). Ish vaqti o'chiq bo'lsa ham kunlik bir marta ishlaydi. */
    fun nextFinal(now: Long, cal: Calendar = Calendar.getInstance()): Long {
        cal.timeInMillis = now
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, finalMinute / 60)
        cal.set(Calendar.MINUTE, finalMinute % 60)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    /** Yangilash oralig'i (ms): ish vaqtida [workMs], tashqarida — [idleMs]. */
    fun interval(millis: Long, workMs: Long, idleMs: Long, cal: Calendar = Calendar.getInstance()): Long =
        if (isWorkTime(millis, cal)) workMs else idleMs

    fun encode(): String = "${if (on) 1 else 0}|$start|$end"

    companion object {
        val DEFAULT = WorkHours()

        fun decode(s: String?): WorkHours {
            val p = s?.split('|') ?: return DEFAULT
            if (p.size != 3) return DEFAULT
            val st = p[1].toIntOrNull() ?: return DEFAULT
            val en = p[2].toIntOrNull() ?: return DEFAULT
            return WorkHours(p[0] == "1", st, en).normalized()
        }

        fun minuteOf(millis: Long, cal: Calendar = Calendar.getInstance()): Int {
            cal.timeInMillis = millis
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }

        fun format(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    }
}
