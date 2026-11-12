import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 2-ilova (IMSOFT Mijoz): 1-ilova bilan bir xil kalit bilan imzolanadi (keystore.properties bo'lsa).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "uz.imsoft.mijoz"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ibrohimjon.imsoft_mijoz"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "5.2"
        // v4.0: kalitsiz ishlash — do'kon kodidan faqat baza manzili va qurilmalar kaliti (agentlar kaliti Mijozga bormaydi)
        buildConfigField("String", "DEV_CODE", "\"" + devCode(envCode("SHOP_CODE")) + "\"")
        buildConfigField("String", "UPDATE_URL", "\"" + envCode("UPDATE_URL") + "\"")
        vectorDrawables {
            generatedDensities?.clear()
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("original") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Umumiy kod (qidiruv, hisobot, bulut) — 1-ilova bilan bir xil manba.
    sourceSets {
        getByName("main").java.srcDir("../shared/src/main/kotlin")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            signingConfigs.findByName("original")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("original")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // v2.5: fonda avtomatik yangilash (1–2 soatda, batareyani tejab)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
}

/** Muhit o'zgaruvchisi (CI secret): faqat xavfsiz belgilar qoldiriladi (BuildConfig satriga qo'yiladi). */
fun envCode(name: String): String =
    (System.getenv(name) ?: "").trim().filter { it.isLetterOrDigit() || it in ":/._-" }

/** IMSHOP1 (manzil, agentlar kaliti, qurilmalar kaliti) -> IMSDEV1 (manzil, qurilmalar kaliti). */
fun devCode(shop: String): String {
    val i = shop.indexOf("IMSHOP1:")
    if (i < 0) return ""
    val body = shop.substring(i + 8).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
    val parts = runCatching { String(Base64.getUrlDecoder().decode(body.padEnd((body.length + 3) / 4 * 4, '=')), Charsets.UTF_8).split("\n") }.getOrNull() ?: return ""
    if (parts.size < 3) return ""
    val dev = parts[0].trim() + "\n" + parts[2].trim()
    return "IMSDEV1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(dev.toByteArray(Charsets.UTF_8))
}
