package uz.imsoft.mijoz.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.imsoft.shared.ClientReqCodec
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.EditLine
import uz.imsoft.shared.EditRequest
import uz.imsoft.shared.PayRequest

/**
 * v3.2: 2-ilova -> Admin so'rovlari. To'lov: Admin tasdiqlab serverga yozadi; "Tasdiqlandi" va chek — faqat server
 * ID'si kelgandan keyin. Chekni tuzatish: Admin tasdiqlasa, keyingi yangilanishda chek o'zgaradi.
 */
class ClientRequests(private val prefs: MijozPrefs) {
    private fun ids(raw: String) = raw.split('|').filter { it.isNotEmpty() }

    suspend fun sendPay(som: Double, method: String, note: String): String = withContext(Dispatchers.IO) {
        require(som > 0) { "amount" }
        val l = prefs.link ?: throw IllegalStateException("not_linked")
        val id = ClientReqCodec.newId('p')
        CloudDb(l.dbUrl).put("s/${l.readKey}/pay/$id", ClientReqCodec.payToCloud(som, 0.0, method, note, prefs.memberName))
        prefs.myPays = (ids(prefs.myPays) + id).takeLast(100).joinToString("|")
        id
    }

    suspend fun sendEdit(docKey: String, lines: List<EditLine>, note: String): String = withContext(Dispatchers.IO) {
        val l = prefs.link ?: throw IllegalStateException("not_linked")
        val id = ClientReqCodec.newId('e')
        CloudDb(l.dbUrl).put("s/${l.readKey}/ed/$id", ClientReqCodec.editToCloud(docKey, lines, note, prefs.memberName))
        prefs.myEdits = (ids(prefs.myEdits) + id).takeLast(100).joinToString("|")
        id
    }

    /** Shu telefondan yuborilgan to'lovlar (yangisi birinchi). */
    suspend fun myPays(): List<PayRequest> = withContext(Dispatchers.IO) {
        val l = prefs.link ?: return@withContext emptyList()
        val mine = ids(prefs.myPays).toSet()
        if (mine.isEmpty()) return@withContext emptyList()
        ClientReqCodec.all(CloudDb(l.dbUrl).get("s/${l.readKey}/pay")) { k, v -> if (k in mine) ClientReqCodec.payFromCloud(k, v) else null }
            .sortedByDescending { it.at }
    }

    suspend fun myEdits(): List<EditRequest> = withContext(Dispatchers.IO) {
        val l = prefs.link ?: return@withContext emptyList()
        val mine = ids(prefs.myEdits).toSet()
        if (mine.isEmpty()) return@withContext emptyList()
        ClientReqCodec.all(CloudDb(l.dbUrl).get("s/${l.readKey}/ed")) { k, v -> if (k in mine) ClientReqCodec.editFromCloud(k, v) else null }
            .sortedByDescending { it.at }
    }
}
