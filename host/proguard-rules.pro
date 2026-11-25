# kotlinx.serialization ships consumer rules in its jar; these keep our @Serializable models explicit
# because content JSON is stored on disk and exchanged with the Host app.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class com.guestexperience.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.guestexperience.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.guestexperience.**$$serializer { *; }
