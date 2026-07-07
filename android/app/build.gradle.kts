plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.troly.nhac"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.troly.nhac"
        // Android 10 (API 29) is the floor: the bit-perfect USB Audio driver uses
        // the usbdevfs isochronous ABI available on stock Android 10+ (no root).
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 64-bit only: target hardware (arm64 phones/TV box, emulator) — and
        // libFLAC's CMake disables fseeko on 32-bit Android, breaking the build.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        // jsch and jspecify both ship META-INF/versions/9/OSGI-INF/MANIFEST.MF
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Android TV (X96 Max+) — leanback/D-pad surface
    implementation(libs.androidx.tv.material)

    // Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.jellyfin.media3.ffmpeg)

    // Subsonic / OpenSubsonic client
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)

    // ── Bit-perfect USB Audio (the "UAPP core") — decent-player, vendored ────
    implementation(project(":decent-usb-audio-driver"))
    implementation(project(":decent-usb-audio-wrapper-media3"))
    implementation(project(":decent-media3-decoder-flac"))
}
