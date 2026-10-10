# pumpdump 🐋

دستیار موبایلی پایش بازار کریپتو و سیگنال‌های **قاعده‌محور** — با مستندات صداقت‌محور.

> برند کاربرپسند: **pumpdump** (لوگوی دوتُنِ «pump» سبز + «dump» قرمز در خود اپ).
> تاریخچهٔ repo و پکیج بک‌اند: `PumpWatch3` / `com.pumpwatch.app` — بدون تغییر (تداوم آپدیت).

> ⚠️ این اپ توصیهٔ مالی نیست. همهٔ خروجی‌ها جنبهٔ آموزشی/پژوهشی دارند.

## 🤝 pumpdump چه چیزی نیست (صداقت اول)

- **هوش مصنوعی/ML در سیگنال‌دهی نداریم.** همهٔ لایه‌ها قاعده‌محورند: EMA، MACD، RSI، ADX، حجم.
- **رئال‌تایم نیستیم.** پایش هر ۳۰ دقیقه یک‌بار توسط `MonitorWorker` انجام می‌شود.
- **چک‌های rug:** تعداد متفاوت در EVM و Solana (شفاف در صفحهٔ متدولوژی).
- **بک‌تست بدون سوگیری:** universe نقطه‌درزمان با نمایش پوشش داده (ADR-0003).
- **همبستگی ≠ علیت.**

## ✨ امکانات

- سیگنال اسپات و فیوچرز از چند صرافی (Binance / Bybit / OKX / Gate)
- هشدارهای هوشمند: ۸ شرط + cooldown شش‌ساعته
- واچ‌لیست رمزنگاری‌شده (AES-256-GCM + Android Keystore، fail-closed)
- ترید کاغذی با مدل هزینهٔ واحد (۰٫۳٪ رفت‌وبرگشت)
- بک‌تست نقطه‌درزمان + مقایسهٔ A/B سیاست‌های خروج
- سایز پوزیشن Kelly با سقف‌های ایمنی

## 🏗️ معماری

| ماژول | مسئولیت |
|---|---|
| `engine/` | ScoringEngine، ExitComparator، PositionSizer، PaperRules، BatchScanner |
| `data/` | BinanceApi، MultiExchange، KlineCache، SecureStorage، HistoricalUniverseRepository، WatchlistMigration |
| `store/` | WatchlistStore (رمزنگاری‌شده)، AlertRulesStore |
| `worker/` | MonitorWorker (ارزیاب واحد)، SignalNavigator |
| `ui/` | صفحه‌های Compose |

## 🧪 Testing

### اجرای همهٔ تست‌ها
```bash
./gradlew testDebugUnitTest
```

### پوشش (JaCoCo)
```bash
./gradlew testDebugUnitTest jacocoTestReport
# HTML: app/build/reports/jacoco/jacocoTestReport/html/index.html
```

### لایه‌ها (مطابق ADR-0005)
1. **JVM unit** — منطق pure موتور/داده (PositionSizer، parserها، scoring، exit)
2. **Robolectric** — integration بدون دستگاه (prefs، مهاجرت، universe، fail-closed)
3. **Compose UI روی JVM** — ناوبری + گارد رگرسیون صداقت
4. **گارد عملکرد** — سقف‌های سخاوتمندانه برای کشف رژیم O(n²)

### CI
`.github/workflows/ci.yml` روی هر push/PR بیلد + تست‌ها را اجرا و گزارش‌ها را آپلود می‌کند.

### گارد صداقت
`HonestyScreensUiTest` متن‌های صادقانه را قفل می‌کند (بازهٔ ۳۰ دقیقه، بدون ادعای AI).
اگر کسی این متن‌ها را به ادعای دروغین برگرداند، CI قرمز می‌شود.

## 📚 مستندات

- `docs/API.md` — مرجع API
- `docs/adr/` — پنج تصمیم معماری (ADR-0001 تا ADR-0005)
- `docs/DEPRECATIONS.md` — سیاست و رجیستری نمادهای منسوخ
- `docs/RELEASE_CHECKLIST.md` — چک‌لیست انتشار
- `CHANGELOG.md` — تاریخچهٔ نسخه‌ها

## 🛠️ ساخت

```bash
./gradlew assembleDebug
```

## ⚖️ سلب‌مسئولیت

استفاده از این اپ به مسئولیت خود کاربر است. کریپتو پرنوسان و پرریسک است؛
ممکن است کل سرمایه از دست برود. این اپ توصیهٔ مالی نیست.
