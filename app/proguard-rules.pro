# Proguard rules for Rewire
# Library rules (Compose, AndroidX, coroutines, kotlinx.serialization) ship as consumer rules.

# Readable crash traces; upload build/outputs/mapping/release/mapping.txt to de-obfuscate.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: models persisted to disk (habits, warnings, events) and nav routes.
# Keep generated serializers so stored JSON and type-safe navigation survive obfuscation.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature
-if @kotlinx.serialization.Serializable class com.aiyu.rewire.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.aiyu.rewire.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.aiyu.rewire.**$$serializer { *; }

# Enums persisted by name (DataStore, intent extras) are restored with valueOf().
-keepclassmembers enum com.aiyu.rewire.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Strip verbose/debug logging from release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
