# Retrofit
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Kotlinx Serialization
-keep @kotlinx.serialization.Serializable class com.akash.voicetask.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Room
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Supabase
-keep class io.github.jan.supabase.** { *; }
-keep interface io.github.jan.supabase.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Credential Manager
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.android.gms.auth.**
