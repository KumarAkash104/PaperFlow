# ProGuard / R8 Rules for PaperFlow

# PDFBox Android (Reflective font loading & rendering)
-keep class com.tom_roush.pdfbox.** { *; }
-keepclassmembers class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Coil Image Loading
-keep class coil3.** { *; }
-dontwarn coil3.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
