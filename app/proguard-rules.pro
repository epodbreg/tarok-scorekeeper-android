# Keep kotlinx.serialization serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.epodbreg.stih.**$$serializer { *; }
-keepclassmembers class com.epodbreg.stih.** {
    *** Companion;
}
-keepclasseswithmembers class com.epodbreg.stih.** {
    kotlinx.serialization.KSerializer serializer(...);
}
