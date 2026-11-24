plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One release number for PC and phone: the VERSION file at the repo root, bumped at every release zip.
val nexusVersion: String = rootProject.file("../VERSION").takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null } ?: "1.1.0"
val nexusVersionCode: Int = (nexusVersion.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0))
    .take(3)
    .let { (major, minor, patch) -> major * 1_000_000 + minor * 1_000 + patch }

android {
    namespace = "it.cerrone.dailyhub"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.cerrone.dailyhub"
        minSdk = 26
        targetSdk = 35
        versionCode = nexusVersionCode
        versionName = nexusVersion
        // Vosk ships a native library per CPU; phones are ARM, so x86 copies would only grow the APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal sideloaded app: sign release with the debug key (no Play Store).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Offline "Lilith" listening for sport mode (free, Apache 2.0). Vosk needs JNA as an AAR.
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
