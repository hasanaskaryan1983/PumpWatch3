# CHANGELOG — PumpWatch

## [2.0.0-p0-complete] — فاز P0 ممیزی به‌طور کامل بسته شد

### Added — زیرساخت مشترک
- `AssetIdentity` + registry: هویت استاندارد دارایی (providerId/symbol/name/chain/contract/venue/rank)
- `ContractRef` + `platformContractRef()`: نمایش زنجیره + آدرس کانترکت کنار هر دارایی (ضد اشتباه توکن هم‌تیکر)
- `CoinMarket.toAssetIdentity()`: تبدیل یکپارچه به هویت استاندارد
- `DataEnvelope`: پوشش تازگی داده (observedAt / servedFrom / age / isStale) + `MarketMeta`
- `SingleFlight`: حذف درخواست‌های هم‌زمان تکراری در `ApiClient` (چهار endpoint + کلید نمودار)
- `NotificationGuard`: dedup در سطح تحویل نوتیفیکیشن + شمارندهٔ پوشش (delivered/suppressed)
- `FuturesJournalCalculator`: محاسبه‌گر مشترک ژورنال با تعریف شفاف واحدها (Gross، نه Net)
- `FakeClock` + `TestFixtures` + `TimeDependentLogicTest`: پایهٔ تست‌های زمانی بدون dependency جدید

### Changed — صداقت محصول و زبان خنثی (همهٔ ۱۶ تب)
- حذف ادبیات دستوری/هیجانی (BUY/SELL/نهنگ/تاپ/ربات) → برچسب‌های توصیفی (مومنتوم، جریان حجم، شبیه‌ساز خودکار)
- حذف همهٔ ادعاهای "live" → برچسب منبع/سن/پوشش روی هر داده
- PnL همه‌جا به‌صورت «تغییر قیمت ناخالص (تقریبی)» برچسب‌گذاری شد
- حذف Max Drawdown از ژورنال فیوچرز تا زمان فراهم شدن دفترکل سرمایهٔ واقعی
- Disclaimerهای شفاف: کپی‌تریدینگ (تب ۱۰)، شبیه‌سازی (تب ۰۷)، بک‌تست/سوگیری بقا (تب ۰۵/۱۶)، کیف پول (تب ۰۸)

### Changed — مدیریت وضعیت داده (Sealed Outcomes)
- تب‌های ۰۱/۰۳/۰۴/۰۶/۱۰/۱۱/۱۲: وضعیت تایپ‌شدهٔ Success/Partial/Insufficient/Error
- تب‌های ۰۲/۱۳: Stale Gate — توقف ارزیابی وقتی داده کهنه است
- تب ۱۴/۰۷: Fresh Gate — مسدودسازی ورود با قیمت کهنه
- تب ۱۵: Typed Read State (Loaded/Unreadable) + دکمهٔ تلاش مجدد
- تب ۰۴: انتخاب صریح `AssetCandidate` پیش از تحلیل
- تب ۰۸: Total = known value + unpriced count + coverage + افشای حریم خصوصی
- تب ۰۹: واچ‌لیست پیشرفته (۵ دسته × ۱۰ ارز × هشدارهای چندگانه) + fail-closed mutation

### Changed — UX
- فشرده‌سازی هدر تب هشدارها (کارت‌های جمع‌شونده) → نمایش ۳-۴ هشدار همزمان
- نوار هویت قرارداد زیر هر کارت هشدار: زنجیره + آدرس کوتاه + کپی + جزئیات

### Fixed
- رفع اسپم نوتیفیکیشن `WhaleMemeWorker` (قبلاً هر ۱۵ دقیقه تکرار می‌شد)
- رفع درخواست‌های موازی تکراری شبکه هنگام باز شدن هم‌زمان تب‌ها

### Performance
- شش لایهٔ محافظ شبکه: SingleFlight → کش RAM → کش دیسک → HostLimiter → Backoff 429 → Notif Dedup

### Tests
- تمام ۶۵۹+ تست واحد سبز
- تست‌های جدید زمانی با FakeClock (cooldown، dedup پنجره‌ای، suppression)
- سازگاری کامل با `WatchlistScreenUiTest` (بنر fail-closed + کارت ارزیابی)
