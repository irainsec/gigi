# Preservation rules for R8/ProGuard
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# GSON
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep interface * extends androidx.room.Dao

# Hilt
-keep class * extends androidx.hilt.lifecycle.ViewModelAssistedFactory
-keep class * extends androidx.lifecycle.ViewModel

# Project Models
-keep class com.aman.gigi.model.** { *; }

# Cloudy (Glassmorphism) - PixelCopy rules
-keep class com.skydoves.cloudy.** { *; }

# jaudiotagger
-dontwarn org.jaudiotagger.**
-keep class org.jaudiotagger.** { *; }
