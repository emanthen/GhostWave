plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ghostwave.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ghostwave.app"
        minSdk = 26          // Android 8.0 — Keystore AES-GCM + BiometricPrompt stable
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Build constants — never put secrets here, only aliases
        buildConfigField("String", "KEYSTORE_KEY_ALIAS",      "\"ghostwave_db_master_key\"")
        buildConfigField("String", "SIGNAL_STORE_KEY_ALIAS",  "\"ghostwave_signal_identity\"")
        buildConfigField("String", "PROMO_DEVICE_KEY_ALIAS",  "\"ghostwave_promo_device_key\"")
        buildConfigField("String", "PROMO_API_BASE",          "\"https://api.ghostwave.app\"")
    }

    // ── Signing ────────────────────────────────────────────────────────
    // Keys read from environment variables — NEVER hardcoded in source.
    // Set in CI via GitHub Secrets; set locally via ~/.gradle/gradle.properties.
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_PATH")
                ?: project.findProperty("KEYSTORE_PATH") as String?
            val storePass    = System.getenv("KEYSTORE_PASSWORD")
                ?: project.findProperty("KEYSTORE_PASSWORD") as String?
            val keyAliasVal  = System.getenv("KEY_ALIAS")
                ?: project.findProperty("KEY_ALIAS") as String?
            val keyPass      = System.getenv("KEY_PASSWORD")
                ?: project.findProperty("KEY_PASSWORD") as String?

            if (storeFilePath != null && storePass != null && keyAliasVal != null && keyPass != null) {
                storeFile     = file(storeFilePath)
                storePassword = storePass
                keyAlias      = keyAliasVal
                keyPassword   = keyPass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
            isDebuggable        = true
            // cleartext only in debug for local IPFS Kubo node (127.0.0.1)
            // The network_security_config.xml controls it; manifest placeholder
            // kept for legacy compatibility
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility          = JavaVersion.VERSION_17
        targetCompatibility          = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true   // java.time on API 26+
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            jniLibs.keepDebugSymbols += "**/*.so"
        }
    }

    splits {
        abi {
            isEnable     = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // Schema export for Room migration tracking
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental",    "true")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ── Core AndroidX ───────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    // ── Compose BOM ─────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)

    // ── Navigation ──────────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Hilt DI ─────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Room + SQLCipher ────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)

    // ── DataStore ───────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── EncryptedSharedPreferences (promo gate + settings) ──────────
    implementation(libs.security.crypto)

    // ── WorkManager ─────────────────────────────────────────────────
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.workmanager)
    kapt(libs.hilt.workmanager.compiler)

    // ── Biometric lock ──────────────────────────────────────────────
    implementation(libs.biometric)

    // ── Firebase (push wakeup ping only) ────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    // ── Signal Protocol — X3DH + Double Ratchet ─────────────────────
    implementation(libs.signal.libsignal.android)

    // ── WebRTC — DTLS-SRTP calls ────────────────────────────────────
    implementation(libs.webrtc.android)

    // ── QR Code ─────────────────────────────────────────────────────
    implementation(libs.zxing.android)
    implementation(libs.zxing.core)

    // ── CameraX (QR scanner + video calls) ──────────────────────────
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ── HTTP (IPFS Kubo RPC API) ─────────────────────────────────────
    implementation(libs.okhttp)

    // ── JSON (reactions, settings) ───────────────────────────────────
    implementation(libs.gson)

    // ── Image loading (chat thumbnails) ─────────────────────────────
    implementation(libs.coil.compose)

    // ── Runtime permissions + system UI ─────────────────────────────
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)

    // ── Testing ─────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
