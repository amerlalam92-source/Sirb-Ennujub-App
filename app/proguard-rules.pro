# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers, annotations, signatures, and Kotlin metadata for crash reports & Firestore reflection
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile
-keep class kotlin.Metadata { *; }

# 1. Keep all domain models (Firestore toObject() / set() serialization, Progress Card, Hobby Badges, Finance, etc.)
-keep class com.example.domain.** { *; }
-keepclassmembers class com.example.domain.** { *; }

# 2. Keep all Firestore data sources and repositories
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }

# 3. Keep any class and members annotated with @Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# 4. Keep Firestore specific annotations and annotated fields/methods
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
}

# 5. Ensure default no-arg constructors are kept for Firestore reflection
-keepclassmembers class * {
    <init>();
}

# Firebase Auth, Firestore & App Check
-keep class com.google.firebase.auth.** { *; }
-keepnames class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.appcheck.** { *; }

# Jetpack Credential Manager & Google Identity Services
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ViewModel constructors
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Common compile-only / optional annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
