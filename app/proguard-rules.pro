# Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.diez.stoiclauncher.domain.model.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.** { *; }
-dontwarn com.google.gson.**

# kotlinx-serialization
-keepattributes SerialVersionUID
-keep,includedescriptorclasses class com.diez.stoiclauncher.**$$serializer { *; }
-keepclassmembers class com.diez.stoiclauncher.** {
    *** Companion;
}
-keepclasseswithmembers class com.diez.stoiclauncher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# AndroidX Palette
-keep class androidx.palette.** { *; }

# ViewBinding
-keep class com.diez.stoiclauncher.databinding.** { *; }

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
