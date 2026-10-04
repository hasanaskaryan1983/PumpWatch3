# 📋 تاریخچهٔ تغییرات PumpWatch

## نسخهٔ ۲.۰ — Sprint 15 (۱۴ مهر ۱۴۰۴)

### ✨ ویژگی‌های جدید

#### 🧪 بک‌تست اسپات: رفع سوگیری بقا (کامیت ۱۱۲)
- **HistoricalUniverseRepository**: ذخیرهٔ snapshot روزانه از رتبهٔ ارزها (تا ۱۲۰ روز)
- انتخاب ارز برای بک‌تست بر اساس **رتبهٔ تاریخی** به‌جای حجم امروز
- سه منبع universe: نقطه‌درزمان / نزدیک‌ترین snapshot / امروز (با برچسب صادقانه)
- افشای درصد پوشش تاریخی و شمارش ارزهای حذف‌شده
- جمع‌آوری غیرفعال snapshot در `MarketScreen` (هر بار که کاربر تب بازار را باز می‌کند)

#### 🔐 مهاجرت واچ‌لیست (کامیت ۱۱۳)
- **WatchlistMigration**: مهاجرت یک‌باره از `data.WatchlistStore` (plaintext) به `store.WatchlistStore` (AES-256-GCM)
- flag `watchlist_migrated_v2` برای جلوگیری از اجرای مجدد
- حفاظت از دادهٔ legacy در صورت خرابی store جدید
- `data.WatchlistStore` به bridge تبدیل شد (backward compatibility)

#### 🔄 یکپارچگی ارزیابی هشدارها (کامیت ۱۱۴)
- **حذف WatchlistWorker**: ارزیابی هشدارها از worker جداگانه (هر ۱۵ دقیقه) به `MonitorWorker` (هر ۳۰ دقیقه) منتقل شد
- `WatchlistScheduler.stop()`: لغو کار دوره‌ای قدیمی پس از آپدیت
- **منبع یگانهٔ ارزیابی**: `MonitorWorker` حالا تنها evaluator اپ است (مطابق گزارش ممیزی)

#### 📊 شفافیت وضعیت هشدارها (کامیت ۱۱۵)
- **کارت وضعیت ارزیابی** در `WatchlistScreen`: نمایش "آخرین بررسی X دقیقه پیش" یا "ارزیابی نشده (قطع شبکه)"
- **دکمه re-arm**: فعال‌سازی مجدد هشدارهای تریگرشده بدون حذف
- `WatchlistStore.markEval()`: ثبت موفقیت/شکست ارزیابی برای UI صادقانه
- به‌روزرسانی متن توضیحی: "هر ۳۰ دقیقه توسط MonitorWorker" به‌جای "هر ۱۵ دقیقه"

#### 🔍 جاروب صداقت — بخش ۱ (کامیت ۱۱۶)
- **SmartAlertsScreen**: "هشدارهای هوشمند" → "هشدارهای بازار" + زیرعنوان صادقانه "اسکنر قاعده‌محور، بدون AI"
- **MemeRadarScreen**: حذف ادعای "۱۲ چک" → "بررسی‌های امنیتی GoPlus (تعداد متفاوت در EVM و Solana)"

#### 📡 رفع Infra5 + M5 (کامیت ۱۱۷)
- **SignalNavigator**: جایگزینی `callbackFlow` با `StateFlow` + sync اولیه از persisted value
  - تضمین delivery حتی اگر subscription بعد از emission باشد
  - حل race condition در fresh start پس از click روی notification
- **BinanceApi.kt**: رفع کندل تکراری با:
  - `distinctBy { time }` برای حذف duplicate
  - `sortedBy { time }` برای ترتیب صعودی
  - `takeLast(limit)` برای truncate
  - `Mutex.withLock` به‌جای `synchronized` (coroutine-safe)

#### 📖 جاروب صداقت — بخش ۲ (کامیت ۱۱۸)
- **OnboardingScreen**:
  - صفحه ۳: "تحلیل مثل حرفه‌ای‌ها" → "تحلیل قاعده‌محور (بدون AI)"
  - صفحه ۵: حذف دروغ "۵۰ تریدر برتر دنیا" → "سیگنال‌های امتیازدار با وزن‌های شفاف"
- **MethodologyScreen**:
  - Rug Safety: "۱۲ چک" → "تعداد متفاوت در EVM و Solana"
  - هشدارها: "هر ۶ ساعت" → "هر ۳۰ دقیقه توسط MonitorWorker"
  - نسخه: Sprint 15 / Commit 118
- **RiskDisclosureScreen**:
  - مرزهای فنی: حذف شماره‌گذاری موتور قدیمی
  - "هشدارها هر ۳۰ دقیقه توسط MonitorWorker"

### 🐛 رفع باگ‌ها

| کامیت | مشکل | راه‌حل |
|------|------|--------|
| ۱۱۲ | سوگیری بقا در بک‌تست اسپات | `HistoricalUniverseRepository` با snapshot روزانه |
| ۱۱۳ | دادهٔ واچ‌لیست در plaintext | مهاجرت به `SecureStorage` + flag |
| ۱۱۴ | Worker تکراری (دو evaluator) | حذف `WatchlistWorker` + انتقال به `MonitorWorker` |
| ۱۱۵ | عدم شفافیت وضعیت ارزیابی | کارت وضعیت + `markEval` + `rearmAlert` |
| ۱۱۶ | ادعاهای غیرقابل اثبات | بازنام‌گذاری + حذف اعداد جعلی |
| ۱۱۷ | event گم‌شده در SignalNavigator | `StateFlow` با sync اولیه |
| ۱۱۷ | کندل تکراری در BinanceApi | dedup + sort + truncate + `Mutex` |
| ۱۱۸ | ادعاهای دروغین در onboarding | "تحلیل قاعده‌محور" + حذف "۵۰ تریدر برتر" |
| ۱۱۸ | تناقض در MethodologyScreen | "تعداد متفاوت در EVM و Solana" |
| ۱۱۸ | فاصله اشتباه هشدارها | "هر ۳۰ دقیقه" به‌جای "هر ۶ ساعت" |

### 🏗️ تغییرات معماری

- **منبع یگانهٔ ارزیابی**: `MonitorWorker` حالا تنها evaluator هشدارها است (SignalAlerts + Watchlist + SignalLogger)
- **Store رمزنگاری‌شده**: واچ‌لیست از plaintext به AES-256-GCM مهاجرت کرد
- **Coroutine-safe locking**: جایگزینی `synchronized` با `Mutex.withLock` در hot path
- **StateFlow برای navigation**: تضمین delivery حتی در fresh start

### 📊 آمار

- **۷ کامیت** بسته شد
- **۱۰ باگ** رفع شد
- **۳ تغییر معماری** مهم
- **۰ خط build** در نهایت

---

## نسخهٔ ۱.۹ — Sprint 14 (قبلی)

### ویژگی‌ها
- Multi-exchange fallback (Bybit → OKX → Gate)
- ثبت منبع واقعی کندل‌ها per symbol
- `WatchlistStore` با گروه‌بندی + هشدار
- Worker ارزیابی هشدارهای واچ‌لیست (بعداً در Sprint 15 حذف شد)

### رفع باگ‌ها
- C1: اضافه شدن عضو هفتم به آرایهٔ کندل (`closeTime`)
- C2b: `SignalNavigator` با `callbackFlow` (بعداً در Commit 117 به `StateFlow` ارتقا یافت)
- C3: گارد `POST_NOTIFICATIONS` در `MonitorWorker`

---

## نسخه‌های قدیمی‌تر

برای تاریخچهٔ کامل، به git log مراجعه کنید.
