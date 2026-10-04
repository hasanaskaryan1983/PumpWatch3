# 🚀 PumpWatch — رادار هوشمند پامپ/دامپ

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/yourusername/pumpwatch)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9-purple)](https://kotlinlang.org)

**PumpWatch** یک اپلیکیشن اندروید برای ردیابی زودهنگام حرکت‌های بزرگ بازار رمزارز است. با ترکیب تحلیل تکنیکال قاعده‌محور، جریان نهنگ‌ها، و هشدارهای سفارشی، به شما کمک می‌کند لحظهٔ درست را شکار کنید.

---

## ✨ ویژگی‌های کلیدی

### 🎯 هشدارهای بازار (اسکنر قاعده‌محور)
- **تشخیص زودهنگام**: شتاب ۱ ساعته + حجم غیرعادی + شکست سقف/کف ۲۴ ساعته
- **بدون AI**: تمام سیگنال‌ها از فرمول‌های شفاف و قابل مشاهده تولید می‌شوند
- **فیلترهای سفارشی**: HOT (≥۷۰) / MID (۵۰-۶۹) / EARLY (زودهنگام)
- **قوانین شخصی**: قیمت بالای/زیر X، RSI، فاندینگ، حجم

### 🐳 شکار نهنگ‌ها
- **Large venue trades**: معاملات تکی ≥ ۱۰۰ هزار دلار در صرافی‌های بزرگ
- **Pool buy/sell mix**: نسبت خرید/فروش استخرهای DEX
- **جدا برچسب‌گذاری**: تا مفهوم "نهنگ واقعی" و "فشار خرده‌فروش" قاطی نشود

### 🧠 تحلیل قاعده‌محور
- **۵ تایم‌فریم**: ۱س، ۴س، ۱۲س، ۱ روز، ۱ هفته
- **۸ اندیکاتور**: EMA، Supertrend، RSI، MACD، ADX، حجم، OBV، ATR
- **نقاط دقیق**: ورود، استاپ، هدف با فرمول‌های شفاف
- **بدون look-ahead bias**: فقط کندل‌های بسته‌شده وارد تحلیل می‌شوند

### 🐸 رادار میم‌کوین‌ها
- **اسکن ۹ شبکه**: Solana, BSC, Base, Ethereum, TON, Robinhood, Avalanche, SEI, Arc
- **Rug Safety Check**: بررسی امنیتی GoPlus (honeypot، mint، owner، tax، قفل نقدینگی و…)
- **حالت اسنایپر**: فیلتر برای توکن‌های تازه با Rug Score بالا
- **نمودار استخر**: اتصال مستقیم به GeckoTerminal

### 📓 ژورنال عملکرد
- **وین‌ریت واقعی**: ارزیابی خودکار سیگنال‌ها (WIN/LOSS/EXP)
- **Profit Factor**: نسبت سود به ضرر
- **Max Drawdown**: بیشترین افت از قله
- **R-multiple**: کیفیت معاملات نسبت به ریسک

### 🧪 بک‌تست بدون سوگیری
- **Historical Universe**: انتخاب ارز بر اساس رتبهٔ تاریخی (نه حجم امروز)
- **Snapshot روزانه**: ذخیرهٔ عضویت بازار تا ۱۲۰ روز
- **افشای صادقانه**: منبع universe + درصد پوشش + شمارش ارزهای حذف‌شده
- **مدل هزینه واقعی**: کارمزد + slippage + فاندینگ (برای فیوچرز)

### 📝 Paper Trading
- **اسپات**: ledger واحد با هزینهٔ واقعی (۰.۳٪ رفت‌وبرگشت)
- **فیوچرز**: سوییچ opt-in + مدل ریسک واقعی (leverage، funding، liquidation)
- **بدون سرمایه واقعی**: شبیه‌سازی کامل بدون اجرای سفارش

### 🔔 هشدارهای سفارشی
- **قوانین شخصی**: قیمت، RSI، فاندینگ، حجم
- **Cooldown هوشمند**: جلوگیری از اسپم نوتیفیکیشن
- **منبع یگانهٔ ارزیابی**: `MonitorWorker` هر ۳۰ دقیقه
- **ارزیابی واچ‌لیست**: هشدارهای واچ‌لیست هم در همان worker

### ⭐ واچ‌لیست گروه‌بندی‌شده
- **تا ۱۰ گروه**: دسته‌بندی ارزهای مورد علاقه
- **هشدار قیمت**: "بالای X" یا "زیر X" برای هر ارز
- **شفافیت وضعیت**: "آخرین بررسی X دقیقه پیش" یا "ارزیابی نشده"
- **Re-arm**: فعال‌سازی مجدد هشدار تریگرشده

### 🔐 امنیت و حریم خصوصی
- **AES-256-GCM**: رمزنگاری داده‌های حساس با Android Keystore
- **غیرامانی**: هیچ کلید، seed یا مجوز برداختی دریافت نمی‌کند
- **Fail-closed**: اگر Keystore کار نکند، داده ذخیره نمی‌شود (نه plaintext)
- **مرکز حریم خصوصی**: مشاهدهٔ وضعیت امنیتی + کنترل داده‌ها

---

## 🏗️ معماری

### تکنولوژی‌ها
- **Kotlin 1.9** + **Jetpack Compose** (UI مدرن)
- **Coroutines + Flow** (async/non-blocking)
- **WorkManager** (background tasks)
- **Retrofit + OkHttp** (network)
- **Android Keystore** (رمزنگاری)

### اصول طراحی
- **Single Source of Truth**: `MonitorWorker` تنها evaluator هشدارها
- **Fail-Closed**: در صورت خطای امنیتی، داده ذخیره نمی‌شود
- **Honest UI**: هیچ ادعای غیرقابل اثبات (مثل "AI" یا "۵۰ تریدر برتر")
- **Transparent Methodology**: صفحهٔ Methodology دقیقاً رفتار واقعی کد را توضیح می‌دهد

---

## 📦 نصب

```bash
git clone https://github.com/yourusername/pumpwatch.git
cd pumpwatch
./gradlew assembleDebug
```

APK در `app/build/outputs/apk/debug/app-debug.apk` ساخته می‌شود.

---

## 🚀 استفاده

### اولین اجرا
1. اپ را باز کنید
2. مجوز نوتیفیکیشن را بدهید (برای هشدارها)
3. Onboarding را رد کنید یا کامل ببینید

### اسکن بازار
- تب "بازار": لیست ۱۰۰۰ ارز برتر CoinGecko
- تب "هشدارهای بازار": سیگنال‌های فعال + قوانین شخصی
- تب "واچ‌لیست": ارزهای مورد علاقه + هشدارهای قیمت

### بک‌تست
- تب "بک‌تست": انتخاب بازهٔ رتبه + اجرای استراتژی
- نتایج: وین‌ریت، Profit Factor، Max Drawdown، R-multiple

### Paper Trading
- تب "Paper اسپات": معاملات کاغذی با ledger واحد
- تب "Paper فیوچرز": معاملات کاغذی با مدل ریسک واقعی

---

## 📖 مستندات

- **[CHANGELOG.md](CHANGELOG.md)**: تاریخچهٔ کامل تغییرات
- **[Methodology](app/src/main/java/com/pumpwatch/app/ui/MethodologyScreen.kt)**: فرمول‌ها و محدودیت‌ها (داخل اپ)
- **[Risk Disclosure](app/src/main/java/com/pumpwatch/app/ui/RiskDisclosureScreen.kt)**: صداقت و مرزهای فنی (داخل اپ)

---

## ⚠️ سلب مسئولیت

**PumpWatch توصیهٔ مالی نیست.** هر خروجی صرفاً مشاهدهٔ دادهٔ عمومی است. تصمیم خرید/فروش، مسئولیت کامل شماست و می‌تواند به از دست رفتن سرمایه منجر شود.

- بازار رمزارز ذاتاً پرنوسان و پرریسک است
- نتایج گذشته تضمین آینده نیست
- همیشه خودتان قرارداد توکن، تیم، و نقدینگی را بررسی کنید (DYOR)

---

## 🤝 مشارکت

Pull request خوشحال‌کننده است! لطفاً:
1. Fork کنید
2. Branch جدید بسازید (`git checkout -b feature/amazing-feature`)
3. Commit کنید (`git commit -m 'feat: add amazing feature'`)
4. Push کنید (`git push origin feature/amazing-feature`)
5. Pull Request باز کنید

---

## 📄 لایسنس

MIT License - جزئیات در [LICENSE](LICENSE)

---

## 📞 تماس

- **GitHub Issues**: [گزارش باگ یا درخواست ویژگی](https://github.com/yourusername/pumpwatch/issues)
- **Email**: your.email@example.com

---

<p align="center">
  <strong>ساخته شده با ❤️ برای جامعهٔ رمزارز فارسی‌زبان</strong>
</p>
