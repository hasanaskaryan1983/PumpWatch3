package com.pumpwatch.app.data

import com.pumpwatch.app.wallet.gateway.ProviderError
import com.pumpwatch.app.wallet.gateway.ProviderException
import com.pumpwatch.app.wallet.gateway.ProviderGateway
import com.pumpwatch.app.wallet.gateway.ProviderResult
import kotlinx.coroutines.delay

/**
 * 🚀 Commit 90 (A2 ادامه + A3): GoPlus با Gateway (کش + circuit breaker + thread-safe).
 *
 * چرا:
 *   - Commit 89: GoPlusClient حالا تاب‌آوری مستقل دارد (timeout + retry + HostLimiter)
 *   - ولی در اسکن میم (۳۰۰ استخر × یک تماس)، هنوز ۴۲۹ می‌گرفت
 *     چون هر استخر یک تماس fresh می‌زد، حتی اگر همان توکن قبلاً چک شده باشد
 *
 * حالا:
 *   - کش TTL ۲ دقیقه (برای همان توکن در همان scan دوباره API نزنیم)
 *   - Circuit breaker: بعد از ۵ شکست پشت سر هم، ۱ دقیقه open
 *     (اگر GoPlus مشکل دارد، همهٔ اسکن را نابود نکنیم)
 *   - Thread-safe: چند coroutine می‌توانند همزمان از آن استفاده کنند
 *
 * استفاده:
 *   MemeRadar باید `GoPlusGateway.getSecurity(chain, address)` را صدا بزند،
 *   نه `GoPlusClient.getTokenSecurityResult(...)` مستقیم.
 *
 * نکته: `GoPlusClient` مستقیم هم برای تست و مسیرهای دیگر (مثل Wallet)
 * در دسترس می‌ماند؛ این فقط یک لایهٔ بالاتر با caching است.
 */
object GoPlusGateway {

    /** کش ۲ دقیقه — یک توکن در یک اسکن دوباره چک نمی‌شود */
    private const val TTL_MS = 120_000L

    /** circuit breaker: بعد از ۵ شکست، ۶۰ ثانیه open */
    private val gateway: ProviderGateway = ProviderGateway(
        failureThreshold = 5,
        cooldownMs = 60_000L,
        sleepSuspend = { delay(it) }
    )

    /**
     * گرفتن دادهٔ امنیتی یک توکن با کش و circuit breaker.
     *
     * @param chain نام زنجیره (ethereum, bsc, solana, ...)
     * @param address آدرس contract توکن
     * @return SecurityResult: Ready / Empty / Failed (CircuitOpen هم Failed می‌شود)
     */
    suspend fun getSecurity(chain: String, address: String): SecurityResult {
        if (address.isBlank()) {
            return SecurityResult.Failed("Empty contract address", retryable = false)
        }

        val result: ProviderResult<SecurityData?> = gateway.callSuspend(
            source = "goplus",
            key = "goplus:$chain:${address.lowercase()}",
            ttlMs = TTL_MS,
            attempts = 1  // GoPlusClient خودش ۳ بار retry می‌کند، اینجا دوباره تکرار نکنیم
        ) {
            when (val r = GoPlusClient.getTokenSecurityResult(chain, address)) {
                is SecurityResult.Ready -> r.security
                is SecurityResult.Empty -> null
                is SecurityResult.Failed -> throw ProviderException(
                    ProviderError.Unknown(r.reason),
                    r.reason
                )
            }
        }

        return when {
            result.error == ProviderError.CircuitOpen ->
                SecurityResult.Failed("GoPlus circuit breaker open — API موقتاً در دسترس نیست", retryable = false)
            result.error != null ->
                SecurityResult.Failed("Gateway error: ${result.error}", retryable = true)
            result.value == null ->
                SecurityResult.Empty("No security data for $address on $chain")
            else ->
                SecurityResult.Ready(result.value)
        }
    }

    /**
     * پاک کردن کش (برای تست یا ریست دستی).
     */
    fun clearCache() = gateway.cacheClear()

    /**
     * وضعیت circuit breaker برای دیباگ.
     * @return "open", "half-open", یا "closed"
     */
    fun breakerState(): String = gateway.breakerState("goplus")
}
