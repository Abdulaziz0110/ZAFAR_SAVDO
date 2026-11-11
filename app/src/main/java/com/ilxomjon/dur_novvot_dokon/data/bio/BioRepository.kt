package com.ilxomjon.dur_novvot_dokon.data.bio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec
import uz.imsoft.shared.bio.Bio
import uz.imsoft.shared.bio.BioBook
import java.io.File

/**
 * v5.2 (A7): mahsulot ma'lumotlari (bio). Telefonda faylda saqlanadi — INTERNETSIZ ham ochiladi.
 * Admin yozadi va bulutga qo'yadi; Agent o'qib oladi. Matn alohida yozuv, tovarlar unga ulanadi
 * (bitta matn bir nechta tovarga; kod almashtirilsa ko'chiriladi).
 */
class BioRepository(private val context: Context) {
    private val lock = Mutex()
    private val _state = MutableStateFlow<List<Bio>>(emptyList())
    val state: StateFlow<List<Bio>> = _state.asStateFlow()
    private var loaded = false

    private fun file() = File(context.filesDir, "bio.json")

    /** Tovar kodi -> ma'lumot (tez qidirish uchun). */
    @Volatile
    private var index: Map<String, Bio> = emptyMap()

    fun of(productId: String): Bio? = index[productId]

    fun has(productId: String): Boolean = index.containsKey(productId)

    suspend fun load() = lock.withLock { load0() }

    private fun publishState(list: List<Bio>) {
        index = BioBook.byProduct(list)
        _state.value = list
    }

    private suspend fun write(list: List<Bio>) {
        withContext(Dispatchers.IO) { runCatching { file().writeText(BioBook.pack(list)) } }
        publishState(list)
    }

    /** Matnni saqlash (yangi bo'lsa yaratiladi). @return yozuv id si. */
    suspend fun save(id: String?, text: String, products: List<String> = emptyList()): String = lock.withLock {
        load0()
        val clean = BioBook.clip(text)
        val now = System.currentTimeMillis()
        val cur = id?.let { x -> _state.value.firstOrNull { it.id == x } }
        val b = cur?.copy(text = clean, at = now) ?: Bio(BioBook.newId(now), clean, now, products)
        var list = BioBook.upsert(_state.value, b)
        if (products.isNotEmpty()) list = BioBook.attach(list, b.id, products)
        write(list)
        b.id
    }

    suspend fun attach(bioId: String, products: List<String>) = lock.withLock {
        load0()
        write(BioBook.attach(_state.value, bioId, products))
    }

    suspend fun detach(productId: String) = lock.withLock {
        load0()
        write(BioBook.detach(_state.value, productId))
    }

    /** Kod almashtirilganda: matnni boshqa tovarga ko'chirish. */
    suspend fun move(from: String, to: String) = lock.withLock {
        load0()
        write(BioBook.move(_state.value, from, to))
    }

    suspend fun remove(bioId: String) = lock.withLock {
        load0()
        write(BioBook.remove(_state.value, bioId))
    }

    /** Kompyuterdan (yoki fayldan) kelgan ro'yxatni butunlay almashtirish. */
    suspend fun replaceAll(list: List<Bio>) = lock.withLock {
        loaded = true
        write(list)
    }

    private suspend fun load0() {
        if (loaded) return
        val list = withContext(Dispatchers.IO) {
            runCatching { file().takeIf { it.isFile }?.readText() }.getOrNull().let(BioBook::unpack)
        }
        loaded = true
        publishState(list)
    }

    // ---------------- bulut ----------------

    /** Admin: bulutga qo'yish (agentlar o'qiydi). */
    suspend fun publish(cfg: RelayCodec.Config?, secret: String): Boolean {
        if (cfg == null || secret.isBlank()) return false
        load()
        val text = BioBook.pack(_state.value)
        return withContext(Dispatchers.IO) {
            runCatching { CloudDb(cfg.dbUrl, secret).put(cfg.path("bio"), linkedMapOf("at" to CloudDb.SERVER_TIME, "t" to text)) }.isSuccess
        }
    }

    /** Agent: bulutdan olish. @return yangilandimi. */
    suspend fun sync(cfg: RelayCodec.Config?): Boolean {
        if (cfg == null) return false
        load()
        val got = withContext(Dispatchers.IO) {
            @Suppress("UNCHECKED_CAST")
            runCatching { CloudDb(cfg.dbUrl).get(cfg.path("bio")) as? Map<String, Any?> }.getOrNull()
        } ?: return false
        val list = BioBook.unpack(got["t"]?.toString())
        if (list == _state.value) return false
        replaceAll(list)
        return true
    }
}
