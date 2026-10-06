# PumpWatch API Reference

> 🚀 Commit 143 — چرا دستی؟ Dokka 1.x با Gradle 9.7 ناسازگار است.
> تا انتشار Dokka 2.x، این سند مرجع رسمی API است.

## engine/

### ScoringEngine
- `scoreFromCandles(candles: List<JsonArray>): Pair<Int, Double>` — امتیاز [-100..100] + ATR٪؛ فقط کندل بسته‌شده (بدون look-ahead)
- `emaL(d, p): Double` — EMA؛ اگر `size < p` → آخرین مقدار برمی‌گردد
- `rsi(d, p = 14): Double` — Wilder RSI؛ اگر `size <= p` → 50
- `macdU(d): Boolean` — شتاب MACD؛ نیاز به ≥ 35 نقطه

### ExitComparator
- `replayLegacy(bars, side, entry, stop0, t1, t2): ReplayResult`
- `replayEngine(bars, side, entry, stop0, t1, t2): ReplayResult`
- تضمین‌ها: trailing stop بین بارها به‌روز می‌شود؛ `mix()` برای partial برابر `0.5*partialR + 0.5*finalR`

### PositionSizer
- `fromPnlPercents(pnls, capital, fallbackPct = 0.05): SizingResult`
- نیم‌Kelly؛ سقف full-Kelly = 25٪؛ سقف مصرف = 10٪ سرمایه؛ حداقل 20 ترید بسته

### PaperRules
- `roundTripCostPct() = 0.3` — تنها منبع حقیقت هزینهٔ رفت‌وبرگشت
- `recomputeCash(state)` — cash فقط از روی تریدها باز محاسبه می‌شود

### BatchScanner
- `buildCandlesChecked(prices, volumes): CandleBuild` — bucket ساعتی UTC، شمارش gap، حذف trailing ناقص

## data/

### MultiExchange
- `candle(a, t, o, h, l, c, v, timeMs): BinanceCandle?` — parser مشترک Bybit/OKX/Gate

### SecureStorage
- `putString / getString: SecretResult` — fail-closed؛ هرگز plaintext نمی‌نویسد
- `isInsecureFallback(ctx)` — flag صادقانهٔ سقوط به حالت ناامن

### HistoricalUniverseRepository
- `recordSnapshot / resolve / coveragePct` — ضد نشت آینده؛ پنجرهٔ nearest = 7 روز

### WatchlistMigration
- `migrateIfNeeded(ctx): Boolean` — invariant: موفقیت → legacy حذف + flag؛ شکست → legacy حفظ

## store/

### WatchlistStore
- `evaluate(groups, priceOf)` / `markTriggered(...)` — pure و قابل تست JVM
- `lastEvalStatus(ctx): Pair<Boolean, Long>` — برای کارت صداقت UI

### AlertRulesStore
- `matches(rule, price, score, funding, rsi, volRatio)` — 8 شرط
- `inCooldown(rule, now)` — پنجرهٔ 6 ساعته

## worker/

### MonitorWorker
- `isTransient(e): Boolean` — retry فقط برای شبکه/429/5xx؛ باگ‌ها fail سریع

### SignalNavigator
- `observe(ctx): StateFlow<Boolean>` — replay=1؛ sync از prefs در شروع app
