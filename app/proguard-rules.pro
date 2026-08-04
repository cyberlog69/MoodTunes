# Add project specific ProGuard rules here.

# ── App domain models & DB entities ─────────────────────────────────────────
-keep class com.moodtunes.app.domain.model.** { *; }
-keep class com.moodtunes.app.data.local.db.entity.** { *; }
-keep class com.moodtunes.app.data.local.preferences.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keepattributes *Annotation*

# ── OkHttp & Okio ─────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Dagger / Hilt ──────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }
-keepclasseswithmembers class * {
    @dagger.* <fields>;
    @dagger.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
    @javax.inject.* <methods>;
}

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Coil image loader ─────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Kotlinx Coroutines ────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Room ──────────────────────────────────────────────────────────────────
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# ── Prevent stripping of crash/logging classes ────────────────────────────
-keep class android.util.Log { *; }
