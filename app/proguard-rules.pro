# Keep Zenith Native JNI functions
-keepclassmembers class com.zenith.tuner.dsp.ZenithNativeDsp {
    native <methods>;
}
-keep class com.zenith.tuner.dsp.ZenithNativeDsp { *; }

# Keep AudioEffect and DynamicsProcessing
-keep class android.media.audiofx.DynamicsProcessing** { *; }
-keep class android.media.audiofx.AudioEffect** { *; }

# Compose rules
-keep class androidx.compose.** { *; }
