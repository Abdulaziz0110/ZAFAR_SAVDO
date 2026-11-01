package com.ilxomjon.dur_novvot_dokon.data.share

import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.DeviceAssignment
import uz.imsoft.shared.DeviceCodes
import uz.imsoft.shared.DeviceInfo

/**
 * v4.0 Admin: "Qurilmalar" — Agent va Mijoz telefonlari o'zi ro'yxatga tushadi, Admin kimligini belgilaydi yoki bloklaydi.
 * Ro'yxat telefonda ham saqlanadi (internetsiz oxirgisi ko'rinadi). Bloklanganlar ro'yxati ko'prikda ishlatiladi.
 */
class DeviceAdmin(
    private val store: ShareStore,
    private val linkCode: (clientId: String) -> String,
    private val agents: suspend () -> List<RawAgent>,
) {
    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()
    @Volatile var loadedAt = 0L
        private set

    val ready: Boolean get() = store.isCloudReady

    /** GitHub "SHOP_CODE" secret'i uchun do'kon kodi (bir marta qo'yiladi). */
    fun shopCode(): String {
        if (!store.isCloudReady) return ""
        if (store.agentKey.isEmpty()) store.agentKey = CloudDb.newKey()
        return DeviceCodes.encodeShop(DeviceCodes.Shop(CloudDb.normalizeUrl(store.dbUrl), store.agentKey, store.devKey))
    }

    private fun db() = CloudDb(CloudDb.normalizeUrl(store.dbUrl), store.secret)
    private fun path(id: String = "", sub: String = "") = "d/${store.devKey}" + (if (id.isEmpty()) "" else "/$id") + (if (sub.isEmpty()) "" else "/$sub")

    @Suppress("UNCHECKED_CAST")
    suspend fun refresh(): List<DeviceInfo> {
        if (!ready) return _devices.value
        val m = withContext(Dispatchers.IO) { db().get(path()) as? Map<String, Any?> }.orEmpty()
        val list = m.mapNotNull { (id, v) -> DeviceInfo.parse(id, v) }.sortedByDescending { it.seenAt }
        _devices.value = list
        loadedAt = System.currentTimeMillis()
        return list
    }

    /** Bloklangan qurilmalar (ko'prik: ulardan kelgan savdo qabul qilinmaydi). */
    fun blockedIds(): Set<String> = _devices.value.filter { it.assignment?.blocked == true }.map { it.id }.toSet()

    private suspend fun write(id: String, a: DeviceAssignment) {
        withContext(Dispatchers.IO) { db().put(path(id, "x"), a.toMap()) }
        refresh()
    }

    suspend fun assignAgent(id: String, agent: RawAgent, label: String) =
        write(id, DeviceAssignment(DeviceAssignment.OK, listOf(agent.id, agent.fullName, agent.boss, agent.phone), "", label.ifBlank { agent.fullName }, 0L))

    suspend fun assignClient(id: String, clientId: String, label: String) {
        val code = linkCode(clientId)
        require(code.isNotEmpty()) { "client" }
        write(id, DeviceAssignment(DeviceAssignment.OK, emptyList(), code, label, 0L))
    }

    suspend fun setBlocked(id: String, blocked: Boolean) {
        val cur = _devices.value.firstOrNull { it.id == id }?.assignment ?: DeviceAssignment(DeviceAssignment.OK, emptyList(), "", "", 0L)
        write(id, cur.copy(status = if (blocked) DeviceAssignment.BLOCKED else DeviceAssignment.OK))
    }

    suspend fun remove(id: String) {
        withContext(Dispatchers.IO) { db().delete(path(id)) }
        refresh()
    }

    suspend fun agentList(): List<RawAgent> = agents()
}
