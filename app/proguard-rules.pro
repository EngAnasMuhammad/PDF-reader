# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\anas1\AppData\Local\Android\Sdk\tools\proguard\proguard-android.txt
# You can edit the include flags and comments in this file.

# Room Keep Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Hilt & Dagger Keep Rules
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.** { *; }
-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.** *;
}

# AndroidPdfViewer Keep Rules (Prevents stripping PDF engine & JNI bindings)
-keep class com.github.barteksc.pdfviewer.** { *; }
-keepclassmembers class com.github.barteksc.pdfviewer.** { *; }
-keep class com.shockwave.** { *; }
-keepclassmembers class com.shockwave.** { *; }

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep App Data & Domain Models
-keep class com.example.pdfreader.domain.model.** { *; }
-keep class com.example.pdfreader.data.local.** { *; }
