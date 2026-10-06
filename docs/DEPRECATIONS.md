# Deprecation Policy & Registry

## سیاست (Commit 149)
هر نماد منسوخ **یک نسخهٔ کامل** پس از معرفی باقی می‌ماند و در نسخهٔ بعد حذف می‌شود.
هر ورودی جدید باید شامل هر ۵ ستون زیر باشد؛ وگرنه merge نمی‌شود.

## رجیستری

| Symbol | Deprecated in | Remove in | Replacement | References |
|---|---|---|---|---|
| `worker.WatchlistScheduler` | 1.3.0 (Commit 114) | 1.5.0 | ارزیابی داخل MonitorWorker | MainActivity |
| `store.WatchlistWorker` | 1.3.0 (Commit 114) | 1.5.0 | MonitorWorker | WatchlistStore.kt |
| `data.WatchlistStore` (bridge) | 1.3.0 (Commit 113) | 1.5.0 | `store.WatchlistStore` | WhaleMemeWorker |

## رویهٔ حذف
1. همهٔ ارجاع‌ها را grep کن و به replacement مهاجرت بده.
2. نماد منسوخ را حذف کن.
3. `./gradlew testDebugUnitTest` → باید سبز بماند.
4. یک ورودی «Removed» در CHANGELOG اضافه کن و ردیف رجیستری را ببند.
