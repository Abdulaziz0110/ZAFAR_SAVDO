package com.ilxomjon.dur_novvot_dokon.core

import com.ilxomjon.dur_novvot_dokon.data.repo.TransferPaths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoleTest {
    @Test fun roleFromPackage() {
        // Agent — asl applicationId, Admin — ".admin" qo'shimchasi (build.gradle.kts productFlavors)
        assertFalse(AppRole.isAdminPackage("com.ibrohimjon.imsoft_dokon_som_val"))
        assertTrue(AppRole.isAdminPackage("com.ibrohimjon.imsoft_dokon_som_val.admin"))
        assertFalse(AppRole.isAdminPackage("com.ibrohimjon.imsoft_dokon_som_val.administrator"))
    }

    @Test fun transferPaths() {
        assertTrue(TransferPaths.allowed("share/clients/c1.json"))
        assertTrue(TransferPaths.allowed("receipt_logo.png"))
        assertTrue(TransferPaths.allowed("codes_1c.json"))
        assertTrue(TransferPaths.allowed("prices_1c.json"))
        assertFalse(TransferPaths.allowed("share/"))
        assertFalse(TransferPaths.allowed("share/../../databases/x.db"))
        assertFalse(TransferPaths.allowed("/data/data/x"))
        assertFalse(TransferPaths.allowed("databases/local.db"))
        assertFalse(TransferPaths.allowed("shared_prefs/secure_server.xml"))
        assertFalse(TransferPaths.allowed("receipt_logo.png.evil"))
        assertFalse(TransferPaths.allowed(""))
    }
}
