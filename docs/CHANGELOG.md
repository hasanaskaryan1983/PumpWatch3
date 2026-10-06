# Changelog

فرمت: Keep a Changelog — هر عدد/ادعا باید به کامیت ارجاع‌پذیر باشد.

## [1.5.0] — Sprint 16 (Commits 150–156)

### Added
- پیام‌های صادقانهٔ شکست: بنر قرمز + پیام هر عملیات وقتی Keystore در دسترس نیست (150، 151)
- گارد reflection برای جلوگیری از بازگشت کلاس‌های حذف‌شده (154)

### Changed
- همهٔ mutator های WatchlistStore حالا نتیجهٔ واقعی ذخیره را برمی‌گردانند (150)
- checkAndFire اگر persist شکست بخورد، ارزیابی را fail ثبت می‌کند (150)
- versionCode 6، versionName 1.5.0 (155)

### Removed (مطابق سیاست DEPRECATIONS.md)
- `store.WatchlistScheduler` → لغو inline با WorkManager در MainActivity (152)
- `store.WatchlistWorker` → ارزیابی از 1.3.0 داخل MonitorWorker است (154)
- bridge `data.WatchlistStore` → `WatchlistEntry` به WatchlistMigration منتقل شد (153، 154)

### Fixed
- شکاف UX fail-closed (ADR-0002): کاربر دیگر «موفقیت» دروغین نمی‌بیند

### CI
- ارتقای اکشن‌ها به نسخه‌های سازگار با Node 24 (156)

### آمار تست
- 658 تست — 0 شکست، 6 skip عمدی

## [1.4.0] — Sprint 15 Part 2 (Commits 131–149)

### Added
- تست‌های integration با Robolectric (131–133): universe نقطه‌درزمان، مهاجرت fail-safe
- تست‌های WatchlistStore + SecureStorage + SignalNavigator با assume-guard (134–135)
- گزارش پوشش JaCoCo (136) + workflow CI با آپلود گزارش‌ها (137)
- تست‌های Compose UI روی JVM (138–140): smoke، ناوبری Onboarding، گارد صداقت
- گاردهای رگرسیون عملکرد (141–142)
- مرجع API دستی: `docs/API.md` (143)
- پنج ADR: `docs/adr/` (144)
- چک‌لیست انتشار + اسکریپت تگ (146)
- بخش Testing در README (147)
- سیاست و رجیستری Deprecation (149)

### Changed
- versionCode 5، versionName 1.4.0 (145)

### آمار تست
- 642 تست unit/integration/UI — 0 شکست، 6 skip عمدی (مسیرهای وابسته به Keystore)

## [1.3.0] — Sprint 15 Part 1 (Commits 102–130)

### Fixed (P0)
- Toggle اسنایپر میم‌کوین واقعاً اثرگذار شد (104)
- مدل ریسک بک‌تست فیوچرز (106)
- سوگیری بقا/انتخاب بک‌تست اسپات → universe نقطه‌درزمان (112)
- یکپارچگی Paper اسپات با مدل هزینهٔ واحد (107)
- تب Paper فیوچرز (105)

### Security
- Secure storage fail-closed + AES-256-GCM (109)
- مهاجرت رمزنگاری‌شدهٔ واچ‌لیست با حفظ دادهٔ legacy (113)

### Changed
- ارزیاب واحد در MonitorWorker؛ WatchlistWorker منسوخ (114)
- SignalNavigator به StateFlow؛ BinanceApi با Mutex + dedup کندل (117)
- سایز پوزیشن Kelly با نیم‌Kelly و سقف‌ها (120)
- جاروب صداقت متن‌های UI (108، 116، 118)

### Added
- تست‌های واحد: 430 → 590 (کامیت‌های 121–129)
- مستندات پایه: README + CHANGELOG (119)

## [قبل از 1.3.0]
- در این فایل ثبت نشده؛ به تاریخچهٔ git مراجعه کنید.
