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
