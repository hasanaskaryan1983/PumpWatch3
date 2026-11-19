# 🚀 S7 keep rules: جلوگیری از شکستن reflection/Gson/Retrofit/WorkManager توسط R8
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# پکیج‌های reflection-heavy / مدل‌های Gson / workerها
-keep class com.pumpwatch.app.data.** { *; }
-keep class com.pumpwatch.app.store.** { *; }
-keep class com.pumpwatch.app.engine.** { *; }
-keep class com.pumpwatch.app.wallet.** { *; }
-keep class com.pumpwatch.app.worker.** { *; }

# enumها (نام‌شان در منطق استفاده می‌شود)
-keepclassmembers enum * { *; }

# Gson / Retrofit
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class retrofit2.** { *; }

# Parcelable
-keepclassmembers class * implements android.os.Parcelable { *; }

# هشدارهای کتابخانه‌های اختیاری
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# 🚀 S7 tink/errorprone dontwarn: annotationهای compile-only که در runtime نیستند
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.auto.value.**
-dontwarn org.joda.time.**
-dontwarn javax.annotation.**


# ==========================================
# 🚀 P0 FIX: Absolute protection for Gson/Retrofit against R8
# ==========================================
# حفظ نام فیلدهایی که با @SerializedName مشخص شده‌اند (حیاتی برای Gson)
-keepclassmembers class com.pumpwatch.app.data.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# حفظ تمام کلاس‌های دیتا و مدل‌ها
-keep class com.pumpwatch.app.data.** { *; }
-keep class com.pumpwatch.app.engine.** { *; }

# حفظ اینترفیس‌های Retrofit
-keep interface com.pumpwatch.app.data.** { *; }

# حفظ متادیتای کاتلین (برای بازتاب یا Reflection)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }

# قوانین استاندارد Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.reflect.TypeToken
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}


# ==========================================
# 🚀 P0 FIX v3: Gson/Retrofit + R8 (نسخه رسمی Gson team)
# ==========================================
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep interface retrofit2.** { *; }
-keep class retrofit2.** { *; }
