# JNI resolves these Java types and constructors by fixed names.
# ORT 1.23.2 and OpenCV 4.12.0 AARs do not supply consumer keep rules.
# Required ORT rule: https://onnxruntime.ai/docs/build/android.html#note-proguard-rules-for-r8-minimization-android-app-builds-to-work
-keep class ai.onnxruntime.** { *; }
-keep class org.opencv.core.CvException { public <init>(java.lang.String); }
