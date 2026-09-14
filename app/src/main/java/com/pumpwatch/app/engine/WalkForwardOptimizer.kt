package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * گزارش بهینه‌سازی Walk-Forward (برای نمایش در داشبورد SignalLogScreen)
 */
data class OptReport(
    val chosenMinScore: Int,
    val previousMinScore: Int,
    val inSampleSize: Int,
    val outSampleSize: Int,
    val inWinRate: Double,
    val outWinRate: Double,
    val inExpectancy: Double,
    val outExpectancy: Double,
    val applied: Boolean,
    val ts: Long
)

/**
 * 🚀 Sprint 4 (Walk-Forward Optimization):
 * بهینه‌سازی آستانهٔ صدور سیگنال (minScore) بر اساس عملکرد تاریخی خودِ اپ.
 *
 * چرا این رویکرد:
 *  - لاگ سیگنال‌ها برای هر سیگنال score و نتیجهٔ قطعی (WIN/LOSS + exitPrice) دارد
 *  - پس بدون نیاز به کندل‌های تاریخی، می‌توان عملکرد هر آستانهٔ کاندیدا را
 *    روی سیگنال‌های ثبت‌شده بازسازی کرد
 *  - تقسیم in-sample/out-of-sample از overfitting جلوگیری می‌کند:
 *    پارامتر برندهٔ in-sample باید روی out-of-sample هم بدتر از فعلی نباشد
 *
 * خروجی: OptReport (برای داشبورد) + در صورت applied بودن، ParamsStore.save
 */
object WalkForwardOptimizer {

    private const val PREFS = "pumpwatch_prefs"
    private const val REPORT_KEY = "wf_opt_report"
    private const val MIN_CLOSED = 50      // حداقل سیگنال بسته برای شروع
    private const val MIN_DECIDED = 30     // حداقل سیگنال با نتیجهٔ قطعی (WIN/LOSS)
    private const val MIN_SUBSET = 10      // حداقل نمونه برای هر کاندیدا
    private const val MIN_OUT_SAMPLE = 5   // حداقل نمونهٔ out-of-sample
    private val GSON = Gson()

    internal data class CandidateStats(
        val minScore: Int,
        val n: Int,
        val winRate: Double,
        val expectancy: Double
    )

    /**
     * آمار عملکرد یک آستانهٔ minScore روی مجموعه‌سیگنال داده‌شده.
     * فقط سیگنال‌هایی با score >= minScore شمرده می‌شوند.
     */
    internal fun statsFor(signals: List<LoggedSignal>, minScore: Int): CandidateStats? {
        val subset = signals.filter { it.score >= minScore }
        val wins = subset.count { it.status == "WIN" }
        val losses = subset.count { it.status == "LOSS" }
        val decidedN = wins + losses
        if (decidedN == 0) return null

        val winRate = wins * 100.0 / decidedN
        val pnls = subset.mapNotNull { s ->
            s.exitPrice?.let { ep ->
                if (s.side == "BUY") (ep - s.entry) / s.entry * 100
                else (s.entry - ep) / s.entry * 100
            }
        }
        val expectancy = if (pnls.isNotEmpty()) pnls.average() else 0.0
        return CandidateStats(minScore, subset.size, winRate, expectancy)
    }

    /**
     * هستهٔ pure بهینه‌سازی (بدون Context — قابل تست واحد).
     *
     * @param closed سیگنال‌های بسته‌شده (WIN/LOSS/EXP)
     * @param current پارامترهای فعال فعلی
     * @return گزارش بهینه‌سازی، یا null اگر داده کافی نیست
     */
    fun evaluate(closed: List<LoggedSignal>, current: SignalParams): OptReport? {
        if (closed.size < MIN_CLOSED) return null

        val decided = closed.filter { it.status == "WIN" || it.status == "LOSS" }
        if (decided.size < MIN_DECIDED) return null

        // تقسیم Walk-Forward: ۷۰٪ قدیمی‌تر = in-sample، ۳۰٪ جدیدتر = out-of-sample
        val sorted = decided.sortedBy { it.time }
        val cut = sorted.size * 7 / 10
        val inSample = sorted.subList(0, cut)
        val outSample = sorted.subList(cut, sorted.size)
        if (outSample.size < MIN_OUT_SAMPLE) return null

        val candidates = (listOf(60, 65, 70, 75, 80) + current.minScore).distinct().sorted()

        val inStats = candidates
            .mapNotNull { statsFor(inSample, it) }
            .filter { it.n >= MIN_SUBSET }
        if (inStats.isEmpty()) return null

        val best = inStats.maxByOrNull { it.expectancy } ?: return null

        val outBest = statsFor(outSample, best.minScore)
        val outCurrent = statsFor(outSample, current.minScore)

        // قاعدهٔ احتیاط: فقط اگر روی out-of-sample بدتر از آستانهٔ فعلی نباشد
        val applied = best.minScore != current.minScore &&
                outBest != null &&
                (outCurrent == null || outBest.expectancy >= outCurrent.expectancy)

        return OptReport(
            chosenMinScore = best.minScore,
            previousMinScore = current.minScore,
            inSampleSize = inSample.size,
            outSampleSize = outSample.size,
            inWinRate = best.winRate,
            outWinRate = outBest?.winRate ?: 0.0,
            inExpectancy = best.expectancy,
            outExpectancy = outBest?.expectancy ?: 0.0,
            applied = applied,
            ts = System.currentTimeMillis()
        )
    }

    /**
     * اجرای کامل: خواندن لاگ → evaluate → در صورت applied ذخیرهٔ پارامتر → ذخیرهٔ گزارش.
     * ایمن: هر خطایی → null (بهینه‌سازی هرگز نباید اپ را خراب کند).
     */
    suspend fun runIfNeeded(ctx: Context): OptReport? = withContext(Dispatchers.IO) {
        try {
            val logs = SignalLogger.load(ctx)
            val closed = logs.filter { it.status == "WIN" || it.status == "LOSS" || it.status == "EXP" }
            val current = ParamsStore.load(ctx)

            val report = evaluate(closed, current) ?: return@withContext null

            if (report.applied) {
                ParamsStore.save(
                    ctx,
                    current.copy(
                        minScore = report.chosenMinScore,
                        goldenScore = (report.chosenMinScore + 15).coerceAtMost(95)
                    )
                )
            }
            saveReport(ctx, report)
            report
        } catch (_: Exception) {
            null
        }
    }

    /**
     * خواندن آخرین گزارش بهینه‌سازی (برای داشبورد).
     */
    fun loadReport(ctx: Context): OptReport? = try {
        val json = ctx.getSharedPreferences(PREFS, 0).getString(REPORT_KEY, "") ?: ""
        if (json.isEmpty()) null else GSON.fromJson(json, OptReport::class.java)
    } catch (_: Exception) { null }

    private fun saveReport(ctx: Context, r: OptReport) {
        try {
            ctx.getSharedPreferences(PREFS, 0).edit()
                .putString(REPORT_KEY, GSON.toJson(r))
                .apply()
        } catch (_: Exception) { }
    }
}
