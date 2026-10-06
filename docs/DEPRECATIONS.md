# Deprecation Policy & Registry

## سیاست (Commit 149)
هر نماد منسوخ **یک نسخهٔ کامل** پس از معرفی باقی می‌ماند و در نسخهٔ بعد حذف می‌شود.
هر ورودی جدید باید شامل هر ۵ ستون زیر باشد؛ وگرنه merge نمی‌شود.

## رجیستری (بسته‌شده)

| Symbol | Deprecated in | Removed in | Replacement | Migration commit |
|---|---|---|---|---|
| `store.WatchlistScheduler` | 1.3.0 (114) | **1.5.0** (152) | لغو inline با WorkManager در MainActivity | 152 |
| `store.WatchlistWorker` | 1.3.0 (114) | **1.5.0** (154) | MonitorWorker (ارزیاب واحد) | 154 |
| `data.WatchlistStore` (bridge) | 1.3.0 (113) | **1.5.0** (154) | `store.WatchlistStore` | 153 + 154 |

### جزئیات مهاجرت
- **Commit 152:** فراخوانی `WatchlistScheduler.stop(this)` در MainActivity با
  `WorkManager.getInstance(this).cancelUniqueWork("WatchlistAlerts")` جایگزین شد؛
  ثابت `LEGACY_WATCHLIST_WORK_NAME` نام کار قدیمی را مستند می‌کند.
- **Commit 153:** `WhaleMemeWorker` از `data.WatchlistStore.load()` به
  `store.WatchlistStore.loadGroups().flatMap { it.coins }` مهاجرت کرد.
- **Commit 154:** data class `WatchlistEntry` به `WatchlistMigration.kt` منتقل شد
  (مسیر مهاجرت legacy حفظ شد)؛ سپس هر سه کلاس حذف شدند.

## رویهٔ حذف (برای ورودی‌های آینده)
1. همهٔ ارجاع‌ها را grep کن و به replacement مهاجرت بده.
2. نماد منسوخ را حذف کن.
3. `./gradlew testDebugUnitTest` → باید سبز بماند.
4. یک ورودی «Removed» در CHANGELOG اضافه کن و ردیف رجیستری را ببند.
5. یک تست گارد reflection اضافه کن (مثل DeprecatedClassesRemovedTest).

## نگهبان رگرسیون (Commit 154)
تست reflection `DeprecatedClassesRemovedTest` تضمین می‌کند این کلاس‌ها
تصادفاً برنگردند — اگر برگردند، CI قرمز می‌شود.
