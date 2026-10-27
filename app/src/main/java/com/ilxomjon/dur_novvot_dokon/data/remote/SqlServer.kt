package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

enum class ServerError { NOT_CONFIGURED, CONNECT, TIMEOUT, QUERY, REJECTED }

class ServerException(val kind: ServerError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: kind.name, cause)

/** SQL Server (jTDS) ulanishi. Foydalanuvchi/parol URL ichiga yozilmaydi (maxsus belgilar xavfsiz). */
class SqlServer(private val config: () -> ServerConfig) {

    /** Oxirgi muvaffaqiyatli ulangan manzil (sinov natijasida ko'rsatish uchun). */
    @Volatile var lastHost: String? = null
        private set
    @Volatile private var preferRemote = false

    suspend fun <T> withConnection(override: ServerConfig? = null, block: suspend (Connection) -> T): T {
        val cfg = override ?: withContext(Dispatchers.IO) { config() }
        if (!cfg.isComplete) throw ServerException(ServerError.NOT_CONFIGURED)
        val hosts = ArrayList<String>()
        hosts += cfg.host
        if (cfg.remoteHost.isNotBlank() && cfg.remoteHost != cfg.host) hosts += cfg.remoteHost
        if (hosts.any { it.contains(';') } || cfg.database.contains(';')) {
            throw ServerException(ServerError.REJECTED, "Server yoki baza nomida ';' belgisi bo'lishi mumkin emas")
        }
        // Oxirgi ishlagan manzil birinchi sinaladi: tashqarida bo'lsangiz har safar do'kon manzilini kutib o'tirmaydi.
        if (hosts.size == 2 && preferRemote) hosts.reverse()

        var conn: Connection? = null
        var failure: ServerException? = null
        for ((i, h) in hosts.withIndex()) {
            val timeout = if (hosts.size == 1) 8 else if (i == 0) 4 else 10
            try {
                conn = withContext(Dispatchers.IO) { open(cfg, h, timeout) }
                lastHost = h
                if (hosts.size == 2) preferRemote = (h == cfg.remoteHost)
                break
            } catch (e: ServerException) {
                failure = e
            }
        }
        if (conn == null) throw failure ?: ServerException(ServerError.CONNECT)
        try {
            return block(conn)
        } finally {
            withContext(Dispatchers.IO) { runCatching { conn.close() } }
        }
    }

    private fun open(cfg: ServerConfig, host: String, loginTimeoutSec: Int): Connection {
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            val props = Properties().apply {
                setProperty("user", cfg.login)
                setProperty("password", cfg.password)
                setProperty("loginTimeout", loginTimeoutSec.toString())
                setProperty("socketTimeout", "60")
            }
            return DriverManager.getConnection("jdbc:jtds:sqlserver://$host;databaseName=${cfg.database}", props)
        } catch (e: SQLException) {
            val timeout = e.message.orEmpty().contains("timed out", ignoreCase = true)
            throw ServerException(if (timeout) ServerError.TIMEOUT else ServerError.CONNECT, e.message, e)
        } catch (e: ClassNotFoundException) {
            throw ServerException(ServerError.CONNECT, "SQL Server drayveri topilmadi", e)
        }
    }
}
