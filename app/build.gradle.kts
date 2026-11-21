import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Ixtiyoriy: asl keystore bo'lsa, loyiha ildizida keystore.properties yarating
// (namuna: keystore.properties.example). Fayl bo'lmasa, odatdagi debug kaliti ishlatiladi.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.ilxomjon.dur_novvot_dokon"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ibrohimjon.imsoft_dokon_som_val"
        minSdk = 23
        targetSdk = 35
        versionCode = 62
        versionName = "5.3.12"

        // v4.0: kalitsiz ishlash va o'zi yangilanish — GitHub secret SHOP_CODE (Admin ▸ Qurilmalar) va build skripti beradi.
        // Bo'sh bo'lsa ilova eskicha ishlaydi (ulanish kodi so'raladi, yangilanish tekshirilmaydi).
        buildConfigField("String", "SHOP_CODE", "\"" + envCode("SHOP_CODE") + "\"")
        buildConfigField("String", "UPDATE_URL", "\"" + envCode("UPDATE_URL") + "\"")

        // Vektorlar build vaqtida PNG'ga aylantirilmaydi (AGP bunda ?attr/@color havolalarda yiqiladi).
        vectorDrawables {
            generatedDensities?.clear()
        }
    }

    // v5.3: BITTA ilova (avval "agent" va "admin" ikki ilova edi). Rol kirishdan aniqlanadi: egasining raqami — Admin,
    // boshqalar — xodim (core/AppRole). applicationId avvalgi Admin ilovasiniki (".admin") — ustiga yangilanadi.
    flavorDimensions += "role"
    productFlavors {
        create("admin") {
            dimension = "role"
            applicationIdSuffix = ".admin"
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

    // Ikki ilova uchun umumiy kod (qidiruv, hisobot o'quvchi, bulut) — alohida modulsiz, manba papkasi sifatida.
    sourceSets {
        getByName("main").java.srcDir("../shared/src/main/kotlin")
        getByName("test").java.srcDir("../shared/src/test/kotlin")
        getByName("test").resources.srcDir("../shared/src/test/resources")
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

    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST")
        }
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
    implementation(libs.androidx.security.crypto)
    implementation(libs.jtds)
    // Ilova yopiq bo'lsa ham navbatni ishonchli yuborish (internet paydo bo'lganda)
    implementation(libs.androidx.work.runtime.ktx)
    // v2.6: shtrix kod skaneri. transitive=false + core 3.3.0: minSdk 23 bilan mos (zxing-android-embedded README)
    implementation(libs.zxing.android.embedded) { isTransitive = false }
    implementation(libs.zxing.core)

    // Faqat JVM unit testlar (APK ichiga kirmaydi)
    testImplementation(libs.junit)
}

/** Muhit o'zgaruvchisi (CI secret): faqat xavfsiz belgilar qoldiriladi (BuildConfig satriga qo'yiladi). */
fun envCode(name: String): String =
    (System.getenv(name) ?: "").trim().filter { it.isLetterOrDigit() || it in ":/._-" }
