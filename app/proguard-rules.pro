# ──────────────────────────────────────────────────────────────
# GhostWave ProGuard rules
# ──────────────────────────────────────────────────────────────

# Keep all Signal Protocol classes — native JNI bridges must not be renamed
-keep class org.signal.** { *; }
-keep class org.whispersystems.** { *; }
-dontwarn org.signal.**

# WebRTC — JNI entry points and observer interfaces
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# SQLCipher — native library references
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# IPFS / libp2p
-keep class io.ipfs.** { *; }
-dontwarn io.ipfs.**

# Room — generated DAO implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Firebase Messaging
-keep class com.google.firebase.messaging.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Suppress warnings for known-safe missing classes
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
