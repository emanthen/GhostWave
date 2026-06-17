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
        minSdk = 26          // Android 8.0 — required for BiometricPrompt stable API + hardware keystore guarantees
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Provide the SQLCipher passphrase derivation key alias as a build constant
        // so it can be referenced from Kotlin without a hard-coded string literal.
        buildConfigField("String", "KEYSTORE_KEY_ALIAS", "\"ghostwave_db_master_key\"")
        buildConfigField("String", "SIGNAL_STORE_KEY_ALIAS", "\"ghostwave_signal_identity\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            // Never enable cleartext in release — only in debug for local STUN/TURN testing
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            signingConfig = signingConfigs.getByName("debug") // replace with release keystore
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // for java.time APIs on API 26+
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
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            // WebRTC and libsignal both ship native .so files; keep them all
            jniLibs.keepDebugSymbols += "**/*.so"
        }
    }

    // Separate APKs per ABI to keep download size small; for dev just use universal
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    // Compose BOM — keeps all Compose artifact versions in sync
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room + SQLCipher (encrypted local DB)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)

    // DataStore (encrypted settings)
    implementation(libs.datastore.preferences)

    // WorkManager (offline message delivery)
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.workmanager)
    kapt(libs.hilt.workmanager.compiler)

    // Biometric lock
    implementation(libs.biometric)

    // Firebase — messaging ONLY (wakeup ping, no content)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    // Signal Protocol — X3DH + Double Ratchet
    implementation(libs.signal.libsignal.android)

    // WebRTC — audio/video calls with DTLS-SRTP
    implementation(libs.webrtc.android)

    // IPFS Helia node — photo storage
    implementation(libs.ipfs.kotlin)

    // QR Code generation (Java) + scanning (C++ native)
    implementation(libs.zxing.android)
    implementation(libs.zxing.core)

    // CameraX (QR scanner, video calls)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // JSON (reactions, settings serialisation)
    implementation(libs.gson)

    // HTTP (IPFS Kubo API)
    implementation(libs.okhttp)

    // Image loading (chat thumbnails)
    implementation(libs.coil.compose)

    // Runtime permissions + system UI
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
