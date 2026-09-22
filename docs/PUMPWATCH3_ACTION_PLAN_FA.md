# PumpWatch3 — برنامه اجرایی جزبه‌جز برای ساخت نسخه حرفه‌ای

**Repository:** `hasanaskaryan1983/PumpWatch3`  
**شاخه بررسی‌شده:** `main`  
**تاریخ:** 2026-09-22  
**وضعیت:** برنامه اجرایی بر اساس بررسی ساختار، changelog، manifest و کد کیف‌پول

---

## 0. نتیجه بررسی در یک نگاه

PumpWatch3 از نظر قابلیت‌ها پروژه‌ای پرامکانات است: بازار اسپات، فیوچرز، بک‌تست، paper trading، اسکنر، هشدار، رادار نهنگ، رادار meme و پشتیبانی چند شبکه.

اما مهم‌ترین ضعف فعلی، **قابل اعتماد نبودن کامل داده و ارزش‌گذاری کیف‌پول** است. پیش از اضافه‌کردن قابلیت‌های جدید باید این موارد اصلاح شوند:

- قیمت‌گذاری نباید با `symbol` انجام شود؛ هویت دارایی باید `chain + contract` باشد.
- قدیمی‌ترین تراکنش نباید «اولین خرید» نامیده شود.
- قیمت روزانه CoinGecko نباید بدون اعلام دقت، قیمت اجرای معامله تلقی شود.
- شبکه‌ای که backend آن فعال نیست نباید مثل شبکه آماده در UI نمایش داده شود.
- کلید RPC در `SharedPreferences` عادی نباید ذخیره شود.
- منطق شبکه، parsing، محاسبه و UI نباید در فایل‌های بسیار بزرگ Compose مخلوط باشند.
- تمام اعداد باید source، زمان مشاهده و confidence داشته باشند.

**ترتیب ساخت پیشنهادی:**

```text
Data Truth
  → Wallet Core
  → Provider Reliability
  → Backtest Integrity
  → UX / Trust
  → AI و قابلیت‌های پیشرفته
```

---

# 1. روش اجرای پروژه

هر مرحله باید در branch جدا انجام شود و فقط بعد از سبزشدن تست‌ها merge شود.

```text
main
 └── feature/phase-0-baseline
 └── feature/phase-1-wallet-domain
 └── feature/phase-2-chain-parsers
 └── feature/phase-3-price-engine
 └── feature/phase-4-provider-gateway
 └── feature/phase-5-backtest-validation
 └── feature/phase-6-ux-release
```

برای هر مرحله این چهار مورد اجباری است:

1. تغییر کد
2. تست واحد و regression
3. ثبت منبع و محدودیت داده
4. بررسی دستی روی دستگاه واقعی

---

# 2. فاز صفر: baseline و قفل ایمنی

## هدف
قبل از تغییر معماری، وضعیت فعلی را قابل اندازه‌گیری و قابل بازگشت کنید.

## کارهای دقیق

### 2.1 ساخت و تست پایه

- اجرای `./gradlew testDebugUnitTest`.
- اجرای `./gradlew lint`.
- اجرای build نسخه release با minify.
- نصب روی حداقل یک دستگاه واقعی Android 7 و یک دستگاه Android جدید.
- ثبت خطاهای build، lint، crash و ANR.

### 2.2 یکسان‌سازی نام محصول

در حال حاضر manifest نام `PumpDump` دارد، در حالی که اسناد نام `PumpWatch` را استفاده می‌کنند.

کارها:

- یک نام نهایی انتخاب کنید؛ پیشنهاد: `PumpWatch`.
- مقدار `android:label` را اصلاح کنید.
- عنوان داخل `MainActivity` را اصلاح کنید.
- README، privacy policy، store listing و risk disclosure را یکسان کنید.
- package/applicationId را فقط در صورت ضرورت تغییر دهید؛ تغییر آن migration انتشار را سخت می‌کند.

### 2.3 حذف یا غیرفعال‌کردن ادعاهای غیرقابل اثبات

تا وقتی parser دقیق وجود ندارد، متن «اولین خرید» را به «اولین مشاهده در داده موجود» تغییر دهید.

تا وقتی confidence و sample size وجود ندارد، از واژه‌های «دقیق»، «قطعی»، «بهترین» و «درصد موفقیت تضمینی» استفاده نکنید.

### 2.4 تعریف KPI

این شاخص‌ها را در debug و beta ثبت کنید:

- زمان scan کیف پول، P50 و P95
- تعداد درخواست برای هر provider
- نرخ پاسخ‌های stale
- درصد دارایی‌های بدون قیمت
- درصد eventهای `UNKNOWN`
- نرخ خطای هر provider
- crash-free session
- ANR
- تعداد duplicate event
- زمان لغو scan

## معیار قبولی فاز صفر

- build و unit test سبز است.
- release با minify ساخته می‌شود.
- نام محصول در کد و اسناد یکسان است.
- baseline عملکرد و خطا ثبت شده است.
- هیچ claim مالی گمراه‌کننده در UI اصلی باقی نمانده است.

---

# 3. فاز یک: اصلاح فوری امنیت

## مشکل اصلی
در `WalletApi.kt`، کلید RPC سولانا در `SharedPreferences` عادی ذخیره می‌شود، با اینکه `SecureStorage.kt` برای AES-GCM و Android Keystore وجود دارد.

## کارهای دقیق

1. `RpcKeyStore` را به جای `getSharedPreferences` روی `SecureStorage` منتقل کنید.
2. کلید را در log، exception، analytics، clipboard دائمی یا crash report وارد نکنید.
3. هنگام نمایش تنظیمات، فقط چهار کاراکتر اول/آخر یا وضعیت `Configured` نشان دهید.
4. دکمه‌های `Save`, `Rotate`, `Clear` اضافه کنید.
5. در صورت خطای Keystore، به کاربر هشدار صادقانه نمایش دهید.
6. کلید Helius را با محدودیت‌های مناسب در پنل provider تنظیم کنید.
7. تست migration بنویسید تا کلید قدیمی به‌صورت امن منتقل و سپس مقدار قدیمی حذف شود.

## ایراد مهم در SecureStorage
در fallback فعلی، مقدار plain ذخیره می‌شود و پرچم ناامن ثبت می‌گردد. این رفتار صادقانه است، اما برای RPC key بهتر است:

- در صورت unavailable بودن Keystore، کلید ذخیره نشود یا کاربر صریحاً تأیید کند.
- کلید API نباید خودکار به plain fallback برود.
- برای ledger کاربر می‌توان migration و هشدار داشت؛ برای secret بهتر است fail closed اجرا شود.

## معیار قبولی

- هیچ RPC key در SharedPreferences معمولی وجود ندارد.
- تست ذخیره، خواندن، حذف و migration سبز است.
- secret در logcat قابل مشاهده نیست.
- Privacy Center وضعیت امنیت واقعی را نشان می‌دهد.

---

# 4. فاز دو: ساخت مدل دامنه کیف‌پول

## مشکل فعلی
`WalletHolding` و `HistTx` در فایل‌های UI تعریف شده‌اند و بخشی از هویت دارایی فقط با symbol نگهداری می‌شود.

## ساختار پیشنهادی

```text
app/src/main/java/com/pumpwatch/app/wallet/
├── domain/
│   ├── AssetId.kt
│   ├── WalletEvent.kt
│   ├── PriceQuote.kt
│   ├── WalletHolding.kt
│   ├── Confidence.kt
│   └── EventKind.kt
├── data/
│   ├── ChainRegistry.kt
│   ├── WalletRepository.kt
│   ├── PriceRepository.kt
│   ├── ProviderGateway.kt
│   └── WalletCache.kt
├── engine/
│   ├── EventClassifier.kt
│   ├── PortfolioCalculator.kt
│   └── CostBasisCalculator.kt
└── presentation/
    ├── WalletViewModel.kt
    ├── WalletState.kt
    └── WalletRoute.kt
```

## مدل AssetId

```kotlin
data class AssetId(
    val chainId: String,
    val contractAddress: String?,
    val standard: String? = null
)
```

قواعد:

- EVM: `chainId + checksum contract`.
- Solana: `solana + mint address`.
- SUI: `sui + coin type`.
- TON: `ton + jetton master`.
- دارایی native: قرارداد `null` و نوع `NATIVE`.

## مدل PriceQuote

```kotlin
data class PriceQuote(
    val asset: AssetId,
    val priceUsd: BigDecimal?,
    val source: String,
    val observedAtMs: Long,
    val liquidityUsd: BigDecimal?,
    val confidence: Confidence,
    val pairAddress: String? = null
)
```

## مدل WalletEvent

```kotlin
data class WalletEvent(
    val id: String,
    val wallet: String,
    val asset: AssetId,
    val kind: EventKind,
    val amount: BigDecimal,
    val quoteAmountUsd: BigDecimal?,
    val executionPriceUsd: BigDecimal?,
    val timestampMs: Long,
    val txHash: String,
    val source: String,
    val confidence: Confidence
)
```

## EventKind

```text
BUY
SELL
SWAP
TRANSFER_IN
TRANSFER_OUT
MINT
BURN
BRIDGE
FEE
LP_ADD
LP_REMOVE
UNKNOWN
```

## معیار قبولی

- هیچ مدل اصلی برای شناسایی دارایی به symbol وابسته نیست.
- holding و event قابل تست JVM هستند.
- محاسبه مستقل از Compose اجرا می‌شود.
- تمام eventها txHash و source دارند.

---

# 5. فاز سه: اصلاح شناسایی آدرس و شبکه

## اشکال فعلی
در چند مسیر، آدرس با حذف کاراکترهای غیرالفبایی/عددی پاک‌سازی می‌شود. این کار ممکن است آدرس معتبر را تغییر دهد. تشخیص خودکار هم فقط با prefix و طول انجام می‌شود.

## کارهای دقیق

1. ورودی خام را فقط trim کنید.
2. برای هر chain validator جدا بنویسید.
3. EVM checksum و طول 42 کاراکتر را بررسی کنید.
4. Solana را با base58 decoder بررسی کنید.
5. SUI را با قواعد coin/address معتبر بررسی کنید.
6. TON را با variantهای پشتیبانی‌شده بررسی کنید.
7. اگر Auto چند احتمال ایجاد کرد، از کاربر انتخاب بخواهید.
8. آدرس normalize‌شده و آدرس نمایش‌داده‌شده را جدا نگه دارید.
9. برای هر validation پیام قابل فهم فارسی نمایش دهید.

## ChainRegistry

```kotlin
data class ChainCapability(
    val chainId: String,
    val displayName: String,
    val enabled: Boolean,
    val supportsBalances: Boolean,
    val supportsHistory: Boolean,
    val supportsPrices: Boolean,
    val primaryProvider: String?,
    val fallbackProviders: List<String>,
    val disabledReason: String?
)
```

شبکه‌هایی که backend معتبر ندارند باید disabled باشند، نه اینکه شبیه شبکه سالم نمایش داده شوند.

## معیار قبولی

- آدرس معتبر با normalization خراب نمی‌شود.
- آدرس نامعتبر قبل از network call رد می‌شود.
- شبکه غیرفعال دلیل واضح دارد.
- تست برای آدرس‌های معتبر و نامعتبر تمام chainهای فعال وجود دارد.

---

# 6. فاز چهار: اصلاح قیمت‌گذاری کیف‌پول

## اشکالات مشخص

- lookup با symbol در SUI، TON، Solana و EVM.
- fallback به top 1000 CoinGecko با symbol.
- در بعضی مسیرها قیمت نامعلوم با مقدار صفر در total جمع می‌شود.
- در DexScreener گاهی نتیجه دوباره با symbol روی چند holding اعمال می‌شود.

## ترتیب منبع قیمت

### برای دارایی دارای بازار مستقیم

1. execution price تراکنش
2. trade نزدیک به timestamp در همان pool
3. DEX quote با pair مشخص
4. hourly candle همان asset
5. daily candle با برچسب تقریبی
6. symbol fallback فقط برای نمایش غیرقطعی

### قانون مهم

قیمت symbol fallback نباید در محاسبه total یا PnL قطعی وارد شود.

## نمایش پیشنهادی

```text
ارزش قیمت‌گذاری‌شده: $1,240.50
دارایی با قیمت نامشخص: 3 مورد
پوشش ارزش‌گذاری: 87%
آخرین بروزرسانی: 24 ثانیه قبل
منبع: DexScreener / pair address
```

## کارهای دقیق

1. `priceSource` به holding اضافه کنید.
2. `observedAt` و `confidence` اضافه کنید.
3. `UNKNOWN` را از صفر متمایز کنید.
4. `totalPriced` و `totalUnknown` جدا محاسبه شوند.
5. liquidity حداقلی برای پذیرش quote تعریف کنید.
6. quote قدیمی‌تر از TTL را stale اعلام کنید.
7. assetهای بدون pair معتبر با contract نمایش داده شوند، نه symbol مبهم.
8. برای دو token هم‌symbol تست regression بنویسید.

## معیار قبولی

- قیمت‌گذاری اصلی فقط با AssetId انجام می‌شود.
- هیچ token ناشناس به token شناخته‌شده با symbol مشابه متصل نمی‌شود.
- total وضعیت پوشش قیمت را نشان می‌دهد.
- stale و unknown از مقدار صفر جدا هستند.

---

# 7. فاز پنج: اصلاح «اولین خرید» و تاریخچه

## اشکال فعلی
در کد فعلی، مواردی مانند قدیمی‌ترین signature، قدیمی‌ترین account یا اولین transfer به‌عنوان first buy استفاده می‌شوند. این می‌تواند airdrop یا انتقال باشد.

## کارهای دقیق برای EVM

1. transaction و receipt را دریافت کنید.
2. event logهای Transfer را decode کنید.
3. router و pool را شناسایی کنید.
4. token ورودی و token خروجی را مشخص کنید.
5. native value و fee را محاسبه کنید.
6. swap را به BUY/SELL تبدیل کنید.
7. انتقال ساده را TRANSFER_IN/OUT ثبت کنید.
8. eventهایی که decode نمی‌شوند UNKNOWN بمانند.

## کارهای دقیق برای Solana

1. preTokenBalances و postTokenBalances را مقایسه کنید.
2. inner instructions را بخوانید.
3. program و pool را شناسایی کنید.
4. SOL delta، token delta و fee را جدا کنید.
5. swap را از transfer ساده جدا کنید.
6. transaction failed را حذف یا FAILED ثبت کنید.
7. mint/burn را با instruction مربوط تشخیص دهید.

## کارهای دقیق برای TON و SUI

- event/action را به event domain تبدیل کنید.
- sender/receiver را بررسی کنید.
- jetton/coin master را به AssetId وصل کنید.
- bridge و internal transfer را از خرید جدا کنید.
- اگر quote اجرای معامله در دسترس نیست، confidence پایین ثبت کنید.

## تغییر متن UI

- `اولین خرید` فقط برای event با `BUY` و confidence کافی.
- برای event غیرقطعی: `اولین مشاهده در داده موجود`.
- در کنار آن منبع، زمان پوشش و confidence نمایش داده شود.

## معیار قبولی

- airdrop به‌عنوان BUY ثبت نمی‌شود.
- self-transfer خرید محسوب نمی‌شود.
- failed transaction وارد cost basis نمی‌شود.
- first buy بر اساس event classifier است، نه oldest record.

---

# 8. فاز شش: قیمت تاریخی و Cost Basis

## اشکال فعلی
قیمت روزانه با تاریخ تراکنش تطبیق داده می‌شود. این برای چند معامله در یک روز و دارایی‌های پرنوسان نادرست است.

## روش پیشنهادی

برای هر event:

1. اگر execution price وجود دارد، همان استفاده شود.
2. در غیر این صورت نزدیک‌ترین trade همان pool در بازه زمانی تعریف‌شده.
3. در غیر این صورت hourly candle.
4. در غیر این صورت daily candle.
5. در آخر unknown؛ نه مقدار ساختگی.

## Cost basis

حداقل دو روش را پشتیبانی کنید:

- FIFO
- Average Cost

کاربر باید روش را انتخاب کند و روش انتخاب‌شده در گزارش ذخیره شود.

## PnL

تفکیک کنید:

```text
Realized PnL
Unrealized PnL
Fees
Slippage
Transfer Cost
Unknown Cost Basis
```

برای محاسبات مالی از `BigDecimal` استفاده کنید. `Double` برای مقدارهای کوچک token و fee می‌تواند خطا ایجاد کند.

## معیار قبولی

- PnL بدون event قیمت معتبر محاسبه قطعی نمی‌شود.
- روش cost basis در UI قابل مشاهده است.
- fee و slippage از سود جدا هستند.
- تست FIFO و Average Cost وجود دارد.

---

# 9. فاز هفت: Provider Gateway و rate-limit

## اشکال فعلی
در scan کیف، تعداد زیادی درخواست موازی برای metadata، price، chart و history ارسال می‌شود. `delay` و `chunk` به‌تنهایی کافی نیستند.

## ProviderGateway باید داشته باشد

- timeout جدا برای connect/read/write
- retry محدود
- احترام به Retry-After
- exponential backoff با سقف
- circuit breaker
- token bucket rate limiter
- request deduplication
- cache با TTL
- provider health score
- typed error
- cancellation

## قرارداد نتیجه

```kotlin
sealed interface ProviderResult<out T> {
    data class Success<T>(val value: T, val meta: Provenance): ProviderResult<T>
    data class RateLimited(val retryAfterMs: Long?): ProviderResult<Nothing>
    data class Unavailable(val provider: String): ProviderResult<Nothing>
    data class InvalidData(val reason: String): ProviderResult<Nothing>
}
```

## Cache

### Memory
برای قیمت بسیار کوتاه‌مدت و request coalescing.

### Disk
برای metadata و داده‌هایی که TTL طولانی‌تر دارند.

### Network
آخرین منبع، فقط پس از بررسی health و quota.

## معیار قبولی

- یک request تکراری همزمان فقط یک بار به provider می‌رود.
- rate-limit باعث crash یا total اشتباه نمی‌شود.
- کاربر می‌داند داده از cache یا network آمده است.
- scan با خروج از صفحه لغو می‌شود.

---

# 10. فاز هشت: جداسازی UI از منطق

## اشکال فعلی
`WalletScreen.kt` و `WalletHistory.kt` بسیار بزرگ هستند و network، parser، aggregation و rendering را با هم انجام می‌دهند.

## کارهای دقیق

1. تمام `Retrofit` و provider callها را از Composable خارج کنید.
2. `WalletViewModel` بسازید.
3. UI فقط `WalletUiState` را observe کند.
4. use caseهای زیر را بسازید:
   - `ScanWalletUseCase`
   - `LoadWalletHistoryUseCase`
   - `ValuePortfolioUseCase`
   - `ExplainPriceUseCase`
   - `CalculatePnlUseCase`
5. stateها را مشخص کنید:
   - Idle
   - Loading
   - Partial
   - Success
   - Empty
   - Error
   - Stale
6. عملیات طولانی را با progress مرحله‌ای نشان دهید.
7. دکمه cancel اضافه کنید.

## معیار قبولی

- Composable هیچ network call مستقیمی ندارد.
- use caseها با JVM unit test قابل اجرا هستند.
- state بعد از rotate یا background از بین نمی‌رود.
- partial result و provider failure قابل نمایش است.

---

# 11. فاز نه: اصلاح موتور سیگنال و بک‌تست

## نقاط مثبت فعلی
طبق changelog، look-ahead bias، stop priority، fee و slippage تا حدی اصلاح شده‌اند.

## کارهای لازم برای اعتبار بیشتر

1. data split را به train/validation/test جدا کنید.
2. walk-forward واقعی برای هر market regime اجرا کنید.
3. survivorship bias را حذف کنید.
4. delisted/failed tokenها را در dataset نگه دارید.
5. spread، latency و partial fill را مدل کنید.
6. funding و liquidation را در futures اضافه کنید.
7. signal version را کنار هر نتیجه ذخیره کنید.
8. sample size و confidence interval را نمایش دهید.
9. metricهای زیر را گزارش کنید:
   - expectancy
   - max drawdown
   - profit factor
   - win rate
   - average R
   - Sharpe با توضیح محدودیت
   - losing streak
10. از بهینه‌سازی بیش‌ازحد پارامترها جلوگیری کنید.

## قانون محصول

هیچ signal بدون این موارد معتبر نیست:

```text
Evidence
Confidence
Invalidation
Freshness
Risk
Sample Size
```

---

# 12. فاز ده: UX و اعتماد کاربر

## داشبورد کیف‌پول پیشنهادی

```text
ارزش کل قیمت‌گذاری‌شده
ارزش دارایی‌های نامشخص
پوشش قیمت‌گذاری
آخرین بروزرسانی
تعداد شبکه‌های موفق
تعداد providerهای خطادار
```

## کارت هر دارایی

- نام و symbol
- chain
- contract/mint
- مقدار
- قیمت فعلی
- ارزش
- source
- observedAt
- confidence
- liquidity
- لینک explorer

## حالت‌های اجباری

- Empty wallet
- No provider
- Partial data
- Rate limited
- Stale data
- Unknown token
- Invalid address
- Failed transaction

## امنیت محصول

- برنامه non-custodial باقی بماند.
- seed phrase هرگز درخواست نشود.
- withdrawal permission درخواست نشود.
- warning ضد impersonation در onboarding و Privacy Center بماند.
- clipboard فقط با اقدام واضح کاربر استفاده شود.

---

# 13. فاز یازده: تست کامل

## تست واحد

- AssetId equality
- address validation
- event classification
- decimal scaling
- FIFO
- Average Cost
- price confidence
- stale policy
- PnL
- fee/slippage

## تست fixture واقعی

حداقل این سناریوها:

1. دو token هم‌symbol با contract متفاوت
2. airdrop
3. self-transfer
4. bridge
5. LP add
6. LP remove
7. native fee
8. failed transaction
9. duplicate page
10. pagination ناقص
11. no price
12. stale price
13. low liquidity
14. Solana inner instruction
15. EVM swap چندمرحله‌ای
16. TON jetton transfer
17. SUI coin transfer
18. re-scan تکراری
19. provider 429
20. provider timeout

## تست UI

- TalkBack
- RTL
- فونت بزرگ
- dark mode
- screen rotation
- process death
- slow network
- offline cache
- cancel scan

## معیار انتشار

- تست‌های domain صددرصد سبز.
- regression برای هر provider موجود.
- no critical/high security finding.
- crash-free beta حداقل 99.5% هدف‌گذاری شود.
- P95 scan کیف ۵۰ دارایی کمتر از ۸ ثانیه با cache گرم.

---

# 14. فاز دوازده: CI/CD و انتشار

## GitHub Actions

workflowها باید این مراحل را داشته باشند:

1. checkout
2. validate Gradle
3. unit test
4. lint
5. dependency vulnerability scan
6. secret scan
7. assemble debug
8. assemble release
9. upload artifact
10. گزارش coverage

## انتشار مرحله‌ای

```text
Internal testing
  → Closed testing
  → Beta محدود
  → 5% production
  → 25%
  → 100%
```

در هر مرحله این موارد پایش شود:

- crash
- ANR
- provider failure
- stale data
- notification delivery
- user-reported incorrect valuation

## قبل از Store

- support email واقعی به privacy policy اضافه شود.
- Data Safety با رفتار واقعی کد تطبیق داده شود.
- نام PumpWatch/PumpDump یکسان شود.
- Risk Disclosure در onboarding و store قابل دسترس باشد.
- ادعای accuracy بدون methodology حذف شود.

---

# 15. ترتیب دقیق پیاده‌سازی پیشنهادی

## هفته اول

- baseline build/lint/test
- انتخاب نام محصول
- Secure کردن RPC key
- ثبت KPI
- تغییر «اولین خرید» به «اولین مشاهده»

## هفته دوم

- ساخت AssetId و PriceQuote
- ساخت WalletEvent و EventKind
- انتقال مدل‌ها از UI به domain
- نوشتن تست مدل‌ها

## هفته سوم

- ChainRegistry
- validatorهای شبکه
- disabled state شبکه‌های بدون backend
- تست آدرس‌ها

## هفته چهارم و پنجم

- EVM parser
- Solana parser
- fixture و regression
- حذف symbol-only از valuation

## هفته ششم

- TON/SUI parser
- price source و confidence
- unknown/stale policy

## هفته هفتم

- ProviderGateway
- quota، retry، circuit breaker
- cache و deduplication

## هفته هشتم

- WalletViewModel
- use caseها
- حذف network call از Compose
- cancel و partial result

## هفته نهم و دهم

- cost basis
- PnL
- قیمت تاریخی دقیق‌تر
- تست‌های مالی

## هفته یازدهم

- بک‌تست walk-forward
- futures funding/liquidation
- گزارش metricها

## هفته دوازدهم

- UX نهایی
- accessibility
- beta
- release checklist

---

# 16. اولویت‌بندی اشکالات

## بحرانی — قبل از انتشار

- symbol-only valuation
- RPC key در SharedPreferences عادی
- نام‌گذاری oldest transfer به‌عنوان first buy
- total اشتباه در حضور price نامعلوم
- نمایش شبکه بدون backend فعال
- نبود provenance برای قیمت تاریخی

## مهم — قبل از beta عمومی

- منطق سنگین داخل UI
- نبود cancellation
- نبود provider gateway مشترک
- catchهای خاموش گسترده
- نبود تست fixture چندزنجیره‌ای
- استفاده گسترده از Double برای محاسبات مالی

## متوسط — بعد از تثبیت هسته

- بهبود UI
- watchlist پیشرفته
- alert customization
- export حرفه‌ای
- localization گسترده

## کم‌اولویت

- AI assistant
- مدل‌های پیش‌بینی پیچیده
- شبکه‌های بیشتر بدون provider پایدار
- قابلیت‌های تبلیغاتی

---

# 17. تعریف Done برای هر feature

هر feature زمانی Done است که:

- domain model دارد.
- source و freshness دارد.
- error state دارد.
- تست واحد دارد.
- تست regression دارد.
- با provider failure رفتار مشخص دارد.
- بدون network call مستقیم در UI کار می‌کند.
- در RTL و فونت بزرگ قابل استفاده است.
- privacy و risk impact آن مستند شده است.
- log و metric قابل مشاهده دارد.

---

# 18. نتیجه نهایی بررسی

PumpWatch3 از نظر گستردگی قابلیت‌ها جلوتر از یک prototype ساده است، اما برای تبدیل‌شدن به یک اپ حرفه‌ای بازار کریپتو باید از «قابلیت‌محوری» به «اعتمادمحوری» حرکت کند.

مهم‌ترین توصیه این است که فعلاً feature جدید اضافه نشود و تیم ابتدا این زنجیره را کامل کند:

```text
هویت صحیح دارایی
  → event صحیح
  → قیمت صحیح
  → cost basis صحیح
  → PnL صحیح
  → نمایش شفاف منبع و عدم قطعیت
```

تا زمانی که این زنجیره کامل نشده، هر signal، radar یا wallet report ممکن است ظاهر حرفه‌ای داشته باشد اما خروجی مالی نادرست بدهد.

**اولین PR پیشنهادی:**

```text
Secure RpcKeyStore + AssetId/PriceQuote domain models
+ rename first-buy UI to first-observed
+ tests for same-symbol tokens
```

**آخرین شرط انتشار:**

کاربر باید برای هر عدد بتواند بفهمد:

1. این عدد از کجا آمده؟
2. چه زمانی مشاهده شده؟
3. چقدر قابل اعتماد است؟
4. چه بخشی از داده ناقص است؟

اگر پاسخ این چهار سؤال در UI وجود داشته باشد، PumpWatch3 می‌تواند به محصولی قابل اعتماد و متمایز تبدیل شود.

---

## فایل‌های بررسی‌شده

- `docs/CHANGELOG.md`
- `docs/RISK_DISCLOSURE.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/pumpwatch/app/data/WalletApi.kt`
- `app/src/main/java/com/pumpwatch/app/data/SecureStorage.kt`
- `app/src/main/java/com/pumpwatch/app/data/Trade.kt`
- `app/src/main/java/com/pumpwatch/app/ui/WalletScreen.kt`
- `app/src/main/java/com/pumpwatch/app/ui/WalletHistory.kt`
- `app/src/main/java/com/pumpwatch/app/MainActivity.kt`
