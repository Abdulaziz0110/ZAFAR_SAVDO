package com.ilxomjon.dur_novvot_dokon.data.barcode

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import java.io.File

/**
 * v3.12 "Shtrix kod → 1C": skanerlangan kodlar va tanlangan tovarlar ro'yxati. Bitta tugma bilan hammasi 1C'ga
 * (Admin: kompyuter navbatiga, Agent: Admin orqali). Ilova yopilsa ham ro'yxat saqlanadi (fayl).
 */
class BarcodeBatch(private val context: Context) {
    data class Item(val code: String, val productId: String, val name: String, val at: Long)

    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()
    @Volatile private var loaded = false

    private fun file() = File(context.filesDir, "barcode_batch.json")

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        _items.value = withContext(Dispatchers.IO) { read() }
        loaded = true
    }

    @Suppress("UNCHECKED_CAST")
    private fun read(): List<Item> = runCatching {
        val f = file()
        if (!f.isFile) return emptyList()
        (Json.parse(f.readText()) as? List<Any?>).orEmpty().mapNotNull { x ->
            val o = JObj(x as? Map<String, Any?> ?: return@mapNotNull null)
            val code = o.str("c")
            if (code.isEmpty()) null else Item(code, o.str("p"), o.str("n"), o.long("t"))
        }
    }.getOrDefault(emptyList())

    private suspend fun save(l: List<Item>) {
        _items.value = l
        withContext(Dispatchers.IO) {
            val tmp = File(context.filesDir, "barcode_batch.json.tmp")
            tmp.writeText(Json.write(l.map { linkedMapOf("c" to it.code, "p" to it.productId, "n" to it.name, "t" to it.at) }))
            if (!tmp.renameTo(file())) { file().delete(); tmp.renameTo(file()) }
        }
    }

    /** Kodni (tovari bilan) qo'shadi; shu kod bor bo'lsa — tovari almashtiriladi. Ro'yxat boshiga (eng yangisi tepada). */
    suspend fun put(code: String, productId: String, name: String) = mutex.withLock {
        val c = clean(code)
        if (c.isEmpty()) return@withLock
        save(listOf(Item(c, productId, name, System.currentTimeMillis())) + _items.value.filter { it.code != c })
    }

    suspend fun remove(code: String) = mutex.withLock { save(_items.value.filter { it.code != code }) }

    suspend fun removeAll(codes: Collection<String>) = mutex.withLock { save(_items.value.filter { it.code !in codes }) }

    suspend fun clear() = mutex.withLock { save(emptyList()) }

    fun contains(code: String): Boolean = _items.value.any { it.code == clean(code) }

    companion object {
        /** 1C'dagidek: faqat harf va raqam (bo'sh joy, chiziqcha va boshqasi olib tashlanadi). */
        fun clean(code: String): String = code.trim().filter { it.isLetterOrDigit() && it.code < 128 }.take(40)

        fun valid(code: String): Boolean = clean(code).length in 4..40
    }
}
