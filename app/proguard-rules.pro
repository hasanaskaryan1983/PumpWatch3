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
