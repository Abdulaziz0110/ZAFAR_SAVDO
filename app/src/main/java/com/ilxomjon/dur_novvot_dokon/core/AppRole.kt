package com.ilxomjon.dur_novvot_dokon.core

import android.content.Context

/**
 * v3.0: bitta kod — ikki ilova. Agent (ishchilar) va Admin (egasi: hamma imkoniyat).
 * Rol paket nomidan aniqlanadi (admin flavor'ida applicationId ".admin" bilan tugaydi) — qo'shimcha sozlama shart emas.
 */
object AppRole {
    const val ADMIN_SUFFIX = ".admin"

    @Volatile var isAdmin: Boolean = false
        private set

    fun init(ctx: Context) {
        isAdmin = isAdminPackage(ctx.packageName)
    }

    fun isAdminPackage(packageName: String): Boolean = packageName.endsWith(ADMIN_SUFFIX)
}
