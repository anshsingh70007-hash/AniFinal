# AniFlow Custom Keep Rules

# Keep Stormbreaker security module
-keep class com.example.aniflow.security.Stormbreaker { *; }

# Keep all data models and network API deserialization entities
-keep class com.example.aniflow.data.model.** { *; }
-keep class com.example.aniflow.data.** { *; }

# Keep Ktor classes and suppress warnings
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlinx Serialization Rules
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt

# Keep serializable classes and companion objects
-keep @kotlinx.serialization.Serializable class * {
    <fields>;
    public synthetic <init>(...);
}

# Keep generated serializers
-keep,includedescriptorclasses class com.example.aniflow.**$$serializer { *; }
-keep class *$$serializer {
    public static final ** INSTANCE;
}

# Optimization flags
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
