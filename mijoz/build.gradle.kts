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
        versionCode = 2
        versionName = "1.1"
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
}
