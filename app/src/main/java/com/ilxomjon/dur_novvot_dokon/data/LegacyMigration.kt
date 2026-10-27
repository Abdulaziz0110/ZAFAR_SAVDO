package com.ilxomjon.dur_novvot_dokon.data

import com.ilxomjon.dur_novvot_dokon.data.local.LocalDb
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Avvalgi ilovadan o'tish: SQL Server login/paroli SQLite'da ochiq matnda edi —
 * birinchi ishga tushishda shifrlangan omborga ko'chiriladi va jadvaldagi parol o'chiriladi.
 */
object LegacyMigration {
    suspend fun run(db: LocalDb, secure: SecureStore) = withContext(Dispatchers.IO) {
        runCatching {
            if (!secure.hasStored) {
                db.readableDatabase.rawQuery("SELECT ip, nomi, login, parol FROM sozlama", null).use { c ->
                    var cfg: ServerConfig? = null
                    while (c.moveToNext()) {
                        cfg = ServerConfig(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty())
                    }
                    // Eski IP saqlanadi; bo'sh qolgan maydonlar uchun oldindan belgilangan qiymat ishlaydi.
                    if (cfg != null && cfg.host.isNotBlank()) secure.serverConfig = cfg
                }
            }
            db.writableDatabase.execSQL("UPDATE sozlama SET parol = ''")
            db.writableDatabase.execSQL("UPDATE agent SET parol = ''")
        }
    }
}
