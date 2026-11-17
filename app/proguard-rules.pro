# ===== A&S 4K — قواعد الحماية/التصغير =====

# نخلي رسائل الأخطاء مفهومة
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# --- حارس الحماية: ما نخلي R8 يشيله أو يبسّطه ---
-keep class com.mtlive.iptv.data.SecurityGuard { *; }
-keep class com.mtlive.iptv.App { *; }
-keep class com.mtlive.iptv.BuildConfig { *; }

# --- Gson: الموديلات تنقرأ بالـ reflection حسب أسماء الحقول ---
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class com.mtlive.iptv.data.** { <fields>; <init>(...); }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# --- Kotlin ---
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Coil ---
-dontwarn coil.**

# ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** bind(android.view.View);
}

# --- Enums تنحفظ (Gson يستعملها بالاسم) ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# موديل التحديث (ينقرا من version.json عبر Gson)
-keep class com.mtlive.iptv.ui.UpdateInfo { <fields>; <init>(...); }
