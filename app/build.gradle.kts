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
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Chat brains. Provider keys are NEVER compiled into an APK — not even a debug one: anything
        // inside an APK can be pulled out, and a shared debug build leaks the key. Networked AI goes
        // through your own proxy (server/, set PIPO_PROXY_URL); without it Pipo uses his offline words.
        // GEMINI_API_KEY / GROQ_API_KEY stay as empty BuildConfig fields because AiEndpoints reads them.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        fun prop(name: String, default: String = "") = (local.getProperty(name) ?: System.getenv(name) ?: default).trim()
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${prop("GEMINI_MODEL", "gemini-2.5-flash")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"\"")
        buildConfigField("String", "GROQ_MODEL", "\"${prop("GROQ_MODEL", "llama-3.3-70b-versatile")}\"")
        // Store builds: the app talks to your own proxy (server/), which holds the real keys.
        buildConfigField("String", "PIPO_PROXY_URL", "\"${prop("PIPO_PROXY_URL")}\"")
        buildConfigField("String", "PIPO_PROXY_TOKEN", "\"${prop("PIPO_PROXY_TOKEN")}\"")
    }

    // Release signing: your own key, from keystore.properties (git-ignored; the key itself lives
    // outside the repo). A release build without it FAILS (see the guard below) instead of silently
    // falling back to the debug key, which would produce an unupgradable, poorly-reputed artifact.
    val releaseKey = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    // F-Droid builds Pipo from source and signs the result with F-Droid's own key, so it must be
    // handed an UNSIGNED release APK. This flag is the only way to produce a release artifact
    // without the release keystore, and it is never inferred from a missing keystore — it has to be
    // asked for explicitly: -Ppipo.unsignedRelease=true (F-Droid metadata: `gradleprops`), or
    // PIPO_UNSIGNED_RELEASE=1 in the environment.
    val unsignedRelease = listOfNotNull(
        providers.gradleProperty("pipo.unsignedRelease").orNull,
        System.getenv("PIPO_UNSIGNED_RELEASE")
    ).any { val v = it.trim(); v.equals("true", ignoreCase = true) || v == "1" }

    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = file(releaseKey.getProperty("storeFile"))
            storePassword = releaseKey.getProperty("storePassword")
            keyAlias = releaseKey.getProperty("keyAlias")
            keyPassword = releaseKey.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // Provider keys are never compiled into any APK (see defaultConfig); release talks to the
            // proxy (server/, set PIPO_PROXY_URL), which holds the real keys.
            // Phones only: the wake-word library ships ~10 MB of native code per CPU type, and x86
            // is for emulators (debug builds keep it for emulator tests).
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = false
            // An explicitly requested unsigned build (F-Droid) gets NO signing config at all:
            // not the release key, and deliberately never the debug key.
            signingConfig = if (unsignedRelease) null else signingConfigs.findByName("release")
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

    // Hard guard. Unless an unsigned build was explicitly requested (F-Droid), a release artifact
    // must FAIL here instead of shipping unsigned or debug-signed. A debug-signed APK can never be
    // upgraded over a properly signed install, and it looks untrustworthy to scanners. Only
    // release artifact tasks are checked, so `assembleDebug` and `lintRelease` still run keystore-free.
    val releaseSigningConfigured = releaseKey != null
    gradle.taskGraph.whenReady {
        val buildingReleaseArtifact = allTasks.any { t ->
            t.project == project && (
                t.name == "assembleRelease" ||
                    t.name == "bundleRelease" ||
                    t.name == "installRelease" ||
                    (t.name.startsWith("package") && t.name.endsWith("Release"))
                )
        }
        if (buildingReleaseArtifact && !releaseSigningConfigured && !unsignedRelease) {
            throw GradleException(
                "Release signing is not configured: keystore.properties is missing at the repository " +
                    "root. Create it with storeFile / storePassword / keyAlias / keyPassword pointing at " +
                    "your release keystore (see README, 'Signing'). Refusing to build a release artifact " +
                    "without the release identity."
            )
        }
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
    // Unity world, only when it has been exported (see settings.gradle.kts)
    if (rootProject.findProject(":unityLibrary") != null) implementation(project(":unityLibrary"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
