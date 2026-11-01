package uz.imsoft.mijoz.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.imsoft.shared.pos.PosLine

/**
 * v3.9 Mijoz: o'z savdosi savatchasi (agentdagidek). Telefonda saqlanadi — ilova yopilsa yoki internet bo'lmasa ham
 * yig'ilgan savatcha yo'qolmaydi. Savdo faqat shu telefonda (Kassa bazasi), 1-ilovaga bormaydi.
 */
class MCart(context: Context) {
    data class State(val lines: List<PosLine> = emptyList(), val customerId: Long = 0L) {
        val total: Double get() = Math.round(lines.sumOf { it.sum } * 100.0) / 100.0
        val qty: Double get() = lines.sumOf { it.qty }
    }

    private val prefs = context.getSharedPreferences("mcart", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(State(PosDb.linesOf(prefs.getString("lines", "[]").orEmpty()), prefs.getLong("customer", 0L)))
    val state: StateFlow<State> = _state.asStateFlow()

    private fun save(s: State) {
        _state.value = s
        prefs.edit().putString("lines", PosDb.linesJson(s.lines)).putLong("customer", s.customerId).apply()
    }

    private fun key(name: String, code: String) = code.ifEmpty { name }

    fun qtyOf(name: String, code: String): Double = _state.value.lines.filter { key(it.name, it.code) == key(name, code) }.sumOf { it.qty }

    /** Miqdor va narx (0 — olib tashlanadi). */
    fun set(name: String, code: String, qty: Double, price: Double) {
        val k = key(name, code)
        val list = _state.value.lines.toMutableList()
        val i = list.indexOfFirst { key(it.name, it.code) == k }
        when {
            qty <= 0.0 -> if (i >= 0) list.removeAt(i)
            i >= 0 -> list[i] = PosLine(name, code, qty, price)
            else -> list += PosLine(name, code, qty, price)
        }
        save(_state.value.copy(lines = list))
    }

    fun add(name: String, code: String, delta: Double, price: Double) {
        val cur = _state.value.lines.firstOrNull { key(it.name, it.code) == key(name, code) }
        set(name, code, ((cur?.qty ?: 0.0) + delta).coerceAtLeast(0.0), cur?.price ?: price)
    }

    fun setLine(index: Int, line: PosLine?) {
        val list = _state.value.lines.toMutableList()
        if (index !in list.indices) return
        if (line == null || line.qty <= 0.0) list.removeAt(index) else list[index] = line
        save(_state.value.copy(lines = list))
    }

    fun setCustomer(id: Long) = save(_state.value.copy(customerId = id))

    fun clear() = save(State())
}
