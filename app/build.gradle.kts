import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.pipo.robot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pipo.robot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Chat brains. Keys live in local.properties (gitignored), never in the repo; without
        // them Pipo still works and talks with his own offline words.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        fun prop(name: String, default: String = "") = (local.getProperty(name) ?: System.getenv(name) ?: default).trim()
        buildConfigField("String", "GEMINI_API_KEY", "\"${prop("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${prop("GEMINI_MODEL", "gemini-2.5-flash")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${prop("GROQ_API_KEY")}\"")
        buildConfigField("String", "GROQ_MODEL", "\"${prop("GROQ_MODEL", "llama-3.3-70b-versatile")}\"")
        // Store builds: the app talks to your own proxy (server/), which holds the real keys.
        buildConfigField("String", "PIPO_PROXY_URL", "\"${prop("PIPO_PROXY_URL")}\"")
        buildConfigField("String", "PIPO_PROXY_TOKEN", "\"${prop("PIPO_PROXY_TOKEN")}\"")
    }

    buildTypes {
        release {
            // Provider keys are NEVER compiled into a release APK: anything in an APK can be pulled
            // out by whoever has the file. Release talks to the proxy (server/, set PIPO_PROXY_URL),
            // which holds the real keys; without a proxy he uses his own offline words.
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
            buildConfigField("String", "GROQ_API_KEY", "\"\"")
            // Phones only: the wake-word library ships ~10 MB of native code per CPU type, and x86
            // is for emulators (debug builds keep it for emulator tests).
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = false
            // Debug signing so `assembleRelease` produces an installable APK out of the box.
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // "Pipo" wake word: offline speech recognition on the phone (Apache 2.0). The model itself is
    // downloaded once, checksum-pinned, when you switch Pipo Voice on (voice/WakeModel.kt).
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
