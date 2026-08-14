# kotlinx.serialization and Supabase use generated serializers.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { static <1>$$serializer INSTANCE; }
-keep,includedescriptorclasses class com.wordbattle.com.data.remote.dto.** { *; }
-dontwarn org.bouncycastle.**
