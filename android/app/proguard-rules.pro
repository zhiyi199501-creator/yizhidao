# Cronet / Chromium native JNI
-keep class org.chromium.** { *; }
-dontwarn org.chromium.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.yizhidao.**$$serializer { *; }
-keepclassmembers class com.yizhidao.** {
    *** Companion;
}
-keepclasseswithmembers class com.yizhidao.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep app models used via reflection / JSON
-keep @kotlinx.serialization.Serializable class com.yizhidao.** { *; }
