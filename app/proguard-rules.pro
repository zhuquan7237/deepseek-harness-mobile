# Keep default rules; minification is off for now.

# --- DSH Mobile -------------------------------------------------------------
# zxing decodes QR frames directly (the embedded activity is gone).
-keep class com.google.zxing.** { *; }
# OkHttp/Okio: only the optional JVM integrations are missing on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
# Kotlin serialization-free JSON lives in org.json (platform), nothing to keep.
