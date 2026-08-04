# Add project specific ProGuard rules here.
-keep class com.moodtunes.app.domain.model.** { *; }
-keep class com.moodtunes.app.data.local.db.entity.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keepattributes *Annotation*
