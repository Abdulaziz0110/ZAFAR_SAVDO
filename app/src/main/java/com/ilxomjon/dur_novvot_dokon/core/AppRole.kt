package com.ilxomjon.dur_novvot_dokon.core

import android.content.Context
import android.content.Intent

/**
 * v5.3: BITTA ilova — rol KIRISHDAN aniqlanadi (avval ikkita alohida ilova edi: Agent va Admin).
 *
 *  * egasining raqami ([OWNER_PHONE]) va paroli bilan kirilsa — Admin: hamma imkoniyat;
 *  * boshqa raqam bilan kirilsa — xodim (agent): savdo, to'lov, narx va qarzlar; boshqaruv yo'q.
 *
 * Rol telefonda saqlanadi. Ilova birinchi marta ochilganda (hali kirilmagan) — xodim ko'rinishi: kirishdan oldin
 * hech qanday boshqaruv ko'rinmaydi. Avvalgi Admin ilovasi ustiga yangilanganda — Admin bo'lib qoladi.
 */
object AppRole {
    const val ADMIN_SUFFIX = ".admin"

    /** Do'kon egasining raqami (faqat raqamlar). Shu raqam + o'z paroli = Admin. */
    const val OWNER_PHONE = "998907788510"

    private const val PREFS = "app_role"
    private const val KEY = "role"
    private const val ADMIN = "admin"
    private const val STAFF = "agent"
    private const val LEGACY_DB = "assafocopy.sqlite"

    @Volatile var isAdmin: Boolean = false
        private set

    fun init(ctx: Context) {
        if (!isAdminPackage(ctx.packageName)) { isAdmin = false; return }
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getString(KEY, null)
        isAdmin = decide(saved, legacyInstall = saved == null && ctx.getDatabasePath(LEGACY_DB).exists())
        // Qaror darhol saqlanadi: yangi o'rnatishda keyin baza fayli paydo bo'ladi — u "eski Admin" deb o'ylanmasin
        if (saved == null) p.edit().putString(KEY, if (isAdmin) ADMIN else STAFF).commit()
    }

    /** Saqlangan rol; yo'q bo'lsa — eski Admin ilovasi ustiga yangilangan bo'lsa Admin, yangi o'rnatishda xodim. */
    fun decide(saved: String?, legacyInstall: Boolean): Boolean = when (saved) {
        ADMIN -> true
        STAFF -> false
        else -> legacyInstall
    }

    fun isAdminPackage(packageName: String): Boolean = packageName.endsWith(ADMIN_SUFFIX)

    /** Raqam egasinikimi: probel, "+", qavs va 998 bor-yo'qligi ahamiyatsiz (oxirgi 9 raqam solishtiriladi). */
    fun isOwner(phone: String): Boolean {
        val d = phone.filter { it.isDigit() }
        return d.length >= 9 && d.takeLast(9) == OWNER_PHONE.takeLast(9)
    }

    /**
     * Kirish muvaffaqiyatli bo'lgach chaqiriladi. @return rol o'zgardimi (o'zgargan bo'lsa ilova qayta ochilishi kerak —
     * barcha xizmatlar rolga qarab quriladi).
     */
    fun onLogin(ctx: Context, phone: String): Boolean {
        val admin = isOwner(phone)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, if (admin) ADMIN else STAFF).commit()
        return admin != isAdmin
    }

    /** Ilovani yangi rol bilan qayta ochish. */
    fun restart(ctx: Context) {
        val app = ctx.applicationContext
        val i = app.packageManager.getLaunchIntentForPackage(app.packageName)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            app.startActivity(i)
        }
        Runtime.getRuntime().exit(0)
    }
}
