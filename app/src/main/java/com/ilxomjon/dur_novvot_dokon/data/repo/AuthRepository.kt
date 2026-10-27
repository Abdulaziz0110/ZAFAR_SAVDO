package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.SecureStore
import com.ilxomjon.dur_novvot_dokon.data.local.CatalogDao
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: ServerApi,
    private val dao: CatalogDao,
    private val secure: SecureStore,
    private val catalog: CatalogRepository,
    private val draft: DraftRepository,
) {
    val serverConfig: ServerConfig get() = secure.serverConfig

    fun saveServerConfig(cfg: ServerConfig) { secure.serverConfig = cfg }

    suspend fun testServer(cfg: ServerConfig): String = api.testConnection(cfg)

    /** @return true - kirildi, false - telefon yoki parol noto'g'ri */
    suspend fun login(phone: String, password: String): Boolean {
        val agent = api.login(phone, password) ?: return false
        withContext(Dispatchers.IO) { dao.saveAgent(agent) }
        catalog.load()
        return true
    }

    suspend fun logout() {
        draft.finish()
        withContext(Dispatchers.IO) { dao.clearAgent() }
        catalog.load()
    }
}
