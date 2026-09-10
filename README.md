# 🚀 PumpDump (PumpWatch)

یک اپلیکیشن پیشرفته تحلیل و سیگنال‌دهی ارزهای دیجیتال برای اندروید، ساخته‌شده با Jetpack Compose و Kotlin.

## ✨ ویژگی‌های کلیدی
- **تحلیل چندلایه (Confluence):** ترکیب EMA, MACD, RSI, Volume و Bollinger Bands با وزن‌دهی هوشمند.
- **پشتیبانی چند صرافی:** دریافت داده از Binance, Bybit, OKX, Gate.io و CoinGecko با فال‌بک خودکار.
- **بک‌تست واقع‌بینانه:** شبیه‌سازی معاملات با در نظر گرفتن کارمزد، استاپ-لاس اولویت‌دار و عدم نگاه به آینده (No Look-ahead Bias).
- **اسکنر پس‌زمینه:** پایش خودکار بازار و ارسال نوتیفیکیشن برای سیگنال‌های قوی (حتی وقتی اپ بسته است).
- **معامله کاغذی (Paper Trading):** امکان فعال‌سازی دستی برای تست استراتژی بدون ریسک واقعی.

## 🛠️ معماری و تکنولوژی‌ها
- **UI:** Jetpack Compose (Material 3, Dark Theme, RTL)
- **معماری:** MVVM / Repository Pattern
- **شبکه:** Retrofit + OkHttp (با Rate-Limiting و Retry-After هوشمند)
- **هم‌روندی:** Kotlin Coroutines + Flow
- **پس‌زمینه:** WorkManager (با Backoff نمایی برای پایداری در خطاهای شبکه)
- **ذخیره‌سازی:** SharedPreferences + کش حافظه‌ای Thread-Safe

## 📂 ساختار پروژه
```text
app/src/main/java/com/pumpwatch/app/
├── data/               # لایه داده (ApiClient, BinanceClient, RateLimiter)
├── engine/             # موتور تحلیل (MacdCalc, BatchScanner, SignalLogger)
├── ui/                 # صفحات Compose (MarketScreen, CoinDetailScreen, BacktestScreen)
├── worker/             # ورکرهای پس‌زمینه (MonitorWorker, SignalScannerWorker)
└── MainActivity.kt     # نقطه ورود و مدیریت مجوزها
```

## 🔄 تاریخچه اصلاحات (Changelog)

### فاز ۱: صحت داده و محاسبه (بحرانی)
- ✅ اصلاح نگاشت داده‌های Gate.io (جابه‌جایی Open/Close)
- ✅ پیاده‌سازی `MacdCalc` با تراز زمانی (Timeline) مشترک برای حذف خطای محاسباتی
- ✅ رفع Look-ahead Bias در بک‌تست و افزودن محاسبه کارمزد (0.2%) و اولویت استاپ-لاس
- ✅ افزودن تست‌های واحد (Unit Tests) برای `MacdCalc` و `GateParser`

### فاز ۲: پایداری و مدیریت Rate-Limit
- ✅ افزایش فاصله درخواست‌های CoinGecko به 1500ms + احترام به هدر `Retry-After`
- ✅ افزودن `BackoffPolicy.EXPONENTIAL` به WorkManager برای جلوگیری از بن شدن API
- ✅ Thread-Safe کردن کش‌های سراسری (`GlobalKlineCache`) با سقف 500 کلید
- ✅ اصلاح فیلتر استیبل‌کوین (جلوگیری از حذف اشتباهی نمادهایی مثل USDTBULL)
- ✅ غیرفعال کردن پیش‌فرض `paper_bot` برای جلوگیری از معاملات ناخواسته

### فاز ۳: بهبود کیفیت و تجربه کاربری (UX)
- ✅ افزودن دکمه وضعیت "معامله کاغذی" در نوار بالا با بازخورد فوری (Toast)
- ✅ نمایش هوشمند خطاهای Rate-Limit به کاربر به جای پیام خطای عمومی
- ✅ بخش جدید "چرا این سیگنال؟" در صفحه جزئیات ارز برای شفاف‌سازی دلایل امتیازدهی

## 🚀 نحوه ساخت و Deployment
این پروژه به‌صورت خودکار با GitHub Actions بیلد می‌شود:
1. هر Push به شاخه `main` یک Workflow را اجرا می‌کند.
2. ابتدا تست‌های واحد (`testDebugUnitTest`) اجرا می‌شوند.
3. در صورت موفقیت، APK دیباگ (`app-debug.apk`) ساخته و به عنوان Artifact آپلود می‌شود.

## ⚠️ سلب مسئولیت
این اپلیکیشن صرفاً برای اهداف آموزشی و تحلیل بازار طراحی شده است. سیگنال‌ها تضمینی برای سود نیستند. همیشه قبل از معامله واقعی، تحقیقات خود را انجام دهید (DYOR).
