# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\thevi\AppData\Local\Android\Sdk/tools/proguard/proguard-android-optimize.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Keep all data models used in Backup/Restore to prevent JSON key renaming
-keep class com.gate.tracker.data.model.** { *; }

# Keep Room entities to ensure database schema compatibility
-keep class com.gate.tracker.data.local.entity.** { *; }

# Keep generic Gson serializable classes if any
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep Retrofit/Network models and Supabase response models
-keep class com.gate.tracker.data.remote.** { *; }

# Gson specific rules to prevent "Abstract classes can't be instantiated" error
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# --- Google Drive API & Google Client Library Rules ---
# Critical for Release builds to prevent obfuscation of JSON models
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Prevent warnings for unused dependencies in google-api-client
-dontwarn com.google.api.client.**
-dontwarn com.google.appengine.**
-dontwarn com.google.android.gms.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn io.opencensus.**
-dontwarn java.nio.file.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# --- Jetpack Compose Rules ---
# Keep Compose runtime and prevent method stripping
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Prevent stripping of Composable functions
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# --- Kotlin Coroutines Rules ---
# Prevent coroutine classes from being stripped
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep coroutine continuation classes
-keepclassmembers class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    *;
}

# --- Java Time API (Desugaring) Rules ---
# Keep time-related classes for desugaring compatibility
-keep class java.time.** { *; }
-keep class java.time.temporal.** { *; }
-keep class java.time.format.** { *; }
-dontwarn java.time.**

# --- Kotlin Metadata and Reflection ---
# Required for Compose to work properly with R8
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**
