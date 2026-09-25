# libGDX / gdx-backend-android
-dontwarn com.badlogic.gdx.**
-keep class com.badlogic.gdx.** { *; }
-keep class com.badlogic.gdx.freetype.** { *; }
-dontwarn android.util.**
-keepclasseswithmembernames class * { native <methods>; }

# gdx-freetype native
-dontwarn com.badlogic.gdx.freetype.**
-keep class com.badlogic.gdx.freetype.** { *; }

# keep native methods (JNI)
-keepclasseswithmembernames class * { native <methods>; }

# prevent R8 from removing the app entry point (referenced from manifest)
-keep public class app.deepdepthdiver.AndroidLauncher {
    public void onCreate(...);
}