package com.ilxomjon.dur_novvot_dokon.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Baza fayllari bilan xavfsiz ishlash: yangi versiyaga o'tishdan oldin nusxa, tiklashdan oldin nusxa,
 * kerak bo'lsa ortga qaytarish. Nusxalar ilovaning ichki papkasida (files/db_backups), oxirgi 5 tasi saqlanadi.
 */
object DbSafety {
    private const val DIR = "db_backups"
    private const val KEEP = 5

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Bazaning hozirgi user_version'i (fayl yo'q bo'lsa -1). Bazani o'zgartirmaydi. */
    fun userVersion(file: File): Int {
        if (!file.exists()) return -1
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA user_version", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            }
        }.getOrDefault(-1)
    }

    /** Agar bazani yangi versiyaga o'tkazish kerak bo'lsa — avval fayl nusxasi. */
    fun backupBeforeUpgrade(context: Context, name: String, targetVersion: Int) {
        runCatching {
            val f = context.getDatabasePath(name)
            val v = userVersion(f)
            if (v in 0 until targetVersion) copyDb(context, f, "$name.v$v.pre-upgrade")
        }
    }

    /** Bazaning to'liq nusxasi (journal/WAL fayllari bilan birga). @return nusxa fayli yoki null */
    fun copyDb(context: Context, db: File, tag: String): File? {
        if (!db.exists()) return null
        val out = File(dir(context), "$tag.${System.currentTimeMillis()}.bak")
        db.copyTo(out, overwrite = true)
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val side = File(db.path + suffix)
            if (side.exists()) side.copyTo(File(out.path + suffix), overwrite = true)
        }
        prune(context, tag.substringBefore('.'))
        return out
    }

    fun restoreCopy(copy: File, target: File) {
        for (suffix in listOf("-wal", "-shm", "-journal")) File(target.path + suffix).delete()
        copy.copyTo(target, overwrite = true)
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val side = File(copy.path + suffix)
            if (side.exists()) side.copyTo(File(target.path + suffix), overwrite = true)
        }
    }

    private fun prune(context: Context, prefix: String) {
        val files = dir(context).listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".bak") }?.sortedByDescending { it.lastModified() } ?: return
        for (f in files.drop(KEEP)) {
            f.delete()
            for (suffix in listOf("-wal", "-shm", "-journal")) File(f.path + suffix).delete()
        }
    }

    /** Fayl SQLite bazasimi va yaxlitmi (faqat o'qib). */
    fun integrityOk(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0).equals("ok", true) }
        }
    }.getOrDefault(false)
}
