# Room and kotlinx-serialization ship their own consumer rules.
# Keep the serializable export models' names stable in the exported JSON.
-keepclassmembers class com.emilio.deutschdrill.domain.** { *** Companion; }
-keepclasseswithmembers class com.emilio.deutschdrill.domain.** { kotlinx.serialization.KSerializer serializer(...); }
