# Changelog

فرمت: Keep a Changelog — هر عدد/ادعا باید به کامیت ارجاع‌پذیر باشد.

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
