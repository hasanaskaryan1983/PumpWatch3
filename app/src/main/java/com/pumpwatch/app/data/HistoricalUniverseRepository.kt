package com.pumpwatch.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.TimeZone

/**
 * 🚀 Commit 112 (B1): universe نقطه‌درزمان برای بک‌تست اسپات.
 *
 * مشکل قبلی: BacktestScreen ارزها را با «حجم امروز» sort می‌کرد و برچسب
 * «بازه رتبه» می‌زد → سوگیری بقا/انتخاب + برچسب دروغین.
 *
 * راه‌حل موبایلی (بدون backend):
 * - هر روز یک snapshot از top-N رتبه‌ها ذخیره می‌شود (Prefs، سقف ۱۲۰ روز)
 * - resolve() اول snapshot دقیق روز شروع بازه را می‌گیرد (POINT_IN_TIME)
 * - اگر نبود، نزدیک‌ترین snapshot قبلی تا ۷ روز (NEAREST_SNAPSHOT)
 * - اگر هیچ نبود، فهرست امروز با برچسب صادقانهٔ TODAY_BIASED
 *
 * نکتهٔ ضد نشتی: snapshot های بعد از روز شروع بازه هرگز استفاده نمی‌شوند
 * (عضویت آینده نباید به گذشته نشت کند).
 */
object HistoricalUniverseRepository {

    private const val PREFS = "pumpwatch_universe"
    private const val KEY_DAYS = "universe_days"
    private const val MAX_DAYS = 120
    private const val MAX_RANK = 300
    private const val DAY_MS = 86_400_000L
    private const val NEAREST_WINDOW_DAYS = 7L

    private val GSON = Gson()
    private val lock = Any()

    /** یک روز از تاریخچهٔ عضویت بازار: rankedIds[0] = رتبه ۱ */
    data class UniverseDay(val dayMs: Long, val rankedIds: List<String>)

    enum class UniverseSource { POINT_IN_TIME, NEAREST_SNAPSHOT, TODAY_BIASED }

    data class ResolvedUniverse(
        val rankedIds: List<String>,
        val source: UniverseSource,
        val snapshotDayMs: Long,
        val coveragePct: Int
    )

    private fun dayStart(ms: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun loadDays(ctx: android.content.Context): MutableList<UniverseDay> = try {
        val json = ctx.getSharedPreferences(PREFS, 0).getString(KEY_DAYS, "") ?: ""
        if (json.isEmpty()) mutableListOf()
        else GSON.fromJson(json, object : TypeToken<MutableList<UniverseDay>>() {}.type) ?: mutableListOf()
    } catch (_: Exception) {
        mutableListOf()
    }

    private fun saveDays(ctx: android.content.Context, days: List<UniverseDay>) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY_DAYS, GSON.toJson(days)).apply()
    }

    /**
     * ثبت snapshot امروز (top MAX_RANK بر اساس market_cap_rank).
     * فراخوانی مکرر بی‌خطر است (همان روز overwrite می‌شود).
     */
    fun recordSnapshot(ctx: android.content.Context, coins: List<CoinMarket>) {
        synchronized(lock) {
            val ranked = coins
                .filter { it.market_cap_rank != null }
                .sortedBy { it.market_cap_rank!! }
                .take(MAX_RANK)
                .map { it.id }
            if (ranked.isEmpty()) return

            val today = dayStart(System.currentTimeMillis())
            val days = loadDays(ctx)
            val idx = days.indexOfFirst { it.dayMs == today }
            if (idx >= 0) days[idx] = UniverseDay(today, ranked)
            else days.add(0, UniverseDay(today, ranked))
            while (days.size > MAX_DAYS) days.removeAt(days.size - 1)
            saveDays(ctx, days)
        }
    }

    /** درصد روزهایی از بازه که snapshot دارند (شفافیت پوشش) */
    fun coveragePct(ctx: android.content.Context, fromMs: Long, toMs: Long): Int {
        val known = loadDays(ctx).map { it.dayMs }.toSet()
        var total = 0
        var hit = 0
        var d = dayStart(fromMs)
        val end = dayStart(toMs)
        while (d <= end) {
            total++
            if (d in known) hit++
            d += DAY_MS
        }
        return if (total == 0) 0 else hit * 100 / total
    }

    /**
     * انتخاب universe برای بازهٔ [fromMs, امروز].
     * هرگز snapshot بعد از fromMs استفاده نمی‌شود (ضد نشت آینده).
     */
    fun resolve(
        ctx: android.content.Context,
        fromMs: Long,
        todayCoins: List<CoinMarket>
    ): ResolvedUniverse {
        val days = loadDays(ctx)
        val start = dayStart(fromMs)
        val coverage = coveragePct(ctx, fromMs, System.currentTimeMillis())

        days.firstOrNull { it.dayMs == start }?.let {
            return ResolvedUniverse(it.rankedIds, UniverseSource.POINT_IN_TIME, it.dayMs, coverage)
        }

        val nearest = days
            .filter { it.dayMs <= start && it.dayMs >= start - NEAREST_WINDOW_DAYS * DAY_MS }
            .maxByOrNull { it.dayMs }
        nearest?.let {
            return ResolvedUniverse(it.rankedIds, UniverseSource.NEAREST_SNAPSHOT, it.dayMs, coverage)
        }

        val todayRanked = todayCoins
            .filter { it.market_cap_rank != null }
            .sortedBy { it.market_cap_rank!! }
            .map { it.id }
        return ResolvedUniverse(
            todayRanked,
            UniverseSource.TODAY_BIASED,
            dayStart(System.currentTimeMillis()),
            coverage
        )
    }
}
