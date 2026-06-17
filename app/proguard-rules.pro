# ──────────────────────────────────────────────────────────────
# GhostWave ProGuard / R8 rules
# ──────────────────────────────────────────────────────────────

# ── Signal Protocol (libsignal-android) ───────────────────────
# JNI bridges must not be renamed — native layer looks them up by name
-keep class org.signal.** { *; }
-keep class org.whispersystems.** { *; }
-dontwarn org.signal.**
-dontwarn org.whispersystems.**

# ── WebRTC ────────────────────────────────────────────────────
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# ── SQLCipher native bindings ─────────────────────────────────
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ── Room ──────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration { *; }

# ── Hilt / Dagger ─────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-dontwarn dagger.**

# ── Firebase Messaging ────────────────────────────────────────
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.**

# ── WorkManager ───────────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── EncryptedSharedPreferences / Tink ─────────────────────────
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class androidx.security.crypto.** { *; }

# ── Promo code gate — obfuscate the embedded hash list ────────
# The class name itself will be renamed by R8; only its hashes
# (plain String arrays) must survive. Members are kept; the class
# name is intentionally obfuscated to slow static analysis.
-keepclassmembers class com.ghostwave.app.promo.EmbeddedHashList {
    static final java.lang.String[] HASHES;
}

# ── Kotlin coroutines ─────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Kotlin metadata (required for reflection-free serialisation) ─
-keep class kotlin.Metadata { *; }

# ── OkHttp ────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# ── Gson (reactions JSON) ─────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**

# ── ZXing QR ──────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-dontwarn io.github.zxing.**

# ── Coil image loading ────────────────────────────────────────
-dontwarn coil.**

# ── CameraX ───────────────────────────────────────────────────
-dontwarn androidx.camera.**

# ── Accompanist ───────────────────────────────────────────────
-dontwarn com.google.accompanist.**

# ── Generic suppression for annotation processors ────────────
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn java.lang.invoke.**
