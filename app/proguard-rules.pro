# Keep libVLC JNI bindings
-keep class org.videolan.libvlc.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.synocam.api.** {
    *** Companion;
}
-keepclasseswithmembers class com.synocam.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}
