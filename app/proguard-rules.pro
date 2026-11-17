# ===== حماية + تشويش الكود (R8) =====
# نُبقي ما تحتاجه المكتبات التي تعتمد على أسماء الحقول/التعميم (Gson)

-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# نماذج البيانات: نحافظ على أسماء الحقول حتى يعمل Gson بشكل صحيح
-keep class com.karastore.iptv.data.model.** { *; }
-keepclassmembers class com.karastore.iptv.data.** { <fields>; }

# التعدادات (ContentType.valueOf …)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-dontwarn coil.**

# NanoHTTPD (سيرفر الربط المحلي)
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ZXing (توليد الباركود)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# نُبقي طبقة الحماية كما هي
-keep class com.karastore.iptv.util.Guard { *; }

# عناصر أندرويد الأساسية
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
-keep class * implements android.os.Parcelable { *; }
