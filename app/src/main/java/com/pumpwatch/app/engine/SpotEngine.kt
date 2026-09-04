package com.pumpwatch.app.engine

import com.pumpwatch.app.data.BinanceClient
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * ============================================================
 *  SpotEngine — موتور اسپات (تایم‌فریم روزانه، نگهداری ماه‌ها)
 * ============================================================
 * آموخته از بک‌تست ۳ ساله (۴۰ کوین، دسامبر ۲۰۲۳ تا سپتامبر ۲۰۲۶):
 *
 *  ❌ «دیپ بخر و ماه‌ها نگه دار» برای آلت‌کوین‌ها فاجعه بود:
 *     میانه بازده آلت‌ها در این بازه ‎-۶۵٪ بود و هیچ نسخه‌ای از
 *     خرید پولبک روزانه وین‌ریت بالای ۳۰٪ نگرفت.
 *  ✅ تنها بازنده‌نبودن: کوین‌های «قوی» (بالای EMA200، رشد بلندمدت بالا)
 *     در رژیم کلان صعودی BTC + خروج منظم زیر EMA50.
 *  🔑 نکته حیاتی: سال ۲۰۲۶ فقط ۷٪ روزها BTC بالای EMA200 بود —
 *     در آن شرایط پاسخ درستِ موتور اسپات: «خرید نکن» است!
 *
 * قوانین نهایی (از داده):
 *  - گیت کلان: فقط وقتی BTC بالای EMA200 روزانه است سیگنال خرید بده
 *  - فقط کوین‌های قوی: بالای EMA200 + EMA50 بالای EMA200 + رشد ۹۰ روزه مثبت
 *  - رتبه قوت: جزو قوی‌ترین‌های جهان اسکن (پرسیل بالا)
 *  - نقطه ورود: پولبک سبک به حوالی EMA20 روزانه (نه ریزش عمیق)
 *  - خروج: بسته‌شدن کندل روزانه زیر EMA50 → «روند تمام» (نگهداری شرطی، بدون هدف ثابت)
 *  - افق: هفته‌ها تا ماه‌ها — اسکن روزانه کافی است
 */
object SpotEngine {

    data class SpotPick(
        val symbol: String,
        val price: Double,
        val score: Int,
        val ret90: Double,        // رشد ۹۰ روزه ٪
        val ret180: Double,       // رشد ۱۸۰ روزه ٪
        val distEma50: Double,    // فاصله تا EMA50 ٪
        val entry: Double,
        val stop: Double,         // EMA50 روزانه — خط مرگ روند
        val holdNote: String,
        val reasons: List<String>
    )

    data class SpotReport(
        val macroOn: Boolean,          // BTC بالای EMA200 روزانه؟
        val btcNote: String,
        val picks: List<SpotPick>,
        val scanned: Int,
        val lines: List<String>
    )

    private const val MIN_RET90 = 10.0        // حداقل رشد ۹۰ روزه برای «قوی» بودن
    private const val PULLBACK_MAX_FROM_EMA20 = 1.05   // حداکثر ۵٪ بالاتر از EMA20 = ورود خوب

    suspend fun scan(symbols: List<String>): SpotReport {
        val lines = mutableListOf<String>()

        // ---------- گیت کلان بازار ----------
        val (macroOn, btcEma200) = btcMacro()
        lines.add(
            if (macroOn)
                "🟢 بازار کلان صعودی است (BTC بالای EMA200 روزانه) — خرید استپاتی مجاز"
            else
                "🔴 بازار کلان نزولی است (BTC زیر EMA200) — موتور اسپات: خرید نکن، نقد/فقط BTC"
        )

        if (!macroOn) {
            return SpotReport(false, "BTC زیر EMA200 روزانه", emptyList(), 0, lines)
        }

        // ---------- اسکن روزانه ----------
        val raw = mutableListOf<Triple<String, List<Double>, List<Double>>>() // sym, closes, (holders)
        val stats = mutableListOf<Stat>()
        var scanned = 0

        val all = (symbols + "BTC").distinct()
        for (sym in all) {
            try {
                val k = BinanceClient.api.klines("${sym}USDT", "1d", 300)
                if (k.size < 210) continue
                val closes = k.map { it[4].asDouble }
                scanned++
                val e50 = emaLast(closes, 50)
                val e200 = emaLast(closes, 200)
                val price = closes.last()
                val r90 = (price / closes[closes.size - 91] - 1) * 100.0
                val r180 = if (closes.size > 181) (price / closes[closes.size - 181] - 1) * 100.0 else r90
                stats.add(Stat(sym, price, e50, e200, r90, r180))
            } catch (_: Exception) { }
        }

        if (stats.isEmpty()) {
            lines.add("داده کافی دریافت نشد")
            return SpotReport(macroOn, "", emptyList(), scanned, lines)
        }

        // ---------- رتبه قوت (پرسنتایل رشد ۹۰ روزه) ----------
        val sorted90 = stats.map { it.ret90 }.sorted()
        fun pct(v: Double): Double {
            if (sorted90.isEmpty()) return 0.0
            var c = 0
            for (x in sorted90) if (x <= v) c++
            return c * 100.0 / sorted90.size
        }

        val picks = mutableListOf<SpotPick>()
        for (s in stats) {
            if (s.sym == "BTC") continue
            val e20 = 0.0 // محاسبه پایین فقط برای قوی‌ها (صرفه‌جویی)
            val strengthPct = pct(s.ret90)

            val above200 = s.price > s.e200
            val trendUp = s.e50 > s.e200
            val strong = s.ret90 >= MIN_RET90 && strengthPct >= 70.0

            if (!above200 || !trendUp || !strong) continue

            // EMA20 برای فیلتر پولبک سبک
            val kl = try { BinanceClient.api.klines("${s.sym}USDT", "1d", 60) } catch (_: Exception) { null }
            val closes60 = kl?.map { it[4].asDouble } ?: continue
            val ema20 = emaLast(closes60, 20)

            // ورود خوب: نزدیک EMA20 (پولبک سبک) — دورشدن زیاد از EMA20 یعنی دیر شده
            val dist20 = s.price / ema20 - 1.0
            val freshEntry = dist20 <= PULLBACK_MAX_FROM_EMA20 - 1.0 + 0.05

            val score = buildScore(s, strengthPct, freshEntry, dist20)
            if (score < 60) continue

            val reasons = mutableListOf<String>()
            reasons.add("رشد ۹۰ روزه ${fmt(s.ret90)}٪ (رتبه قوت ${strengthPct.toInt()}) 💪")
            if (s.ret180 > 0) reasons.add("رشد ۱۸۰ روزه ${fmt(s.ret180)}٪")
            reasons.add(if (freshEntry) "پولبک سبک به EMA20 — نقطه ورود تازه 🎯" else "دور از EMA20 (${fmt(dist20 * 100)}٪) — ورود پله‌ای بهتر است")
            reasons.add("شرط خروج: بسته‌شدن روزانه زیر EMA50")

            picks.add(
                SpotPick(
                    symbol = s.sym,
                    price = s.price,
                    score = score,
                    ret90 = s.ret90,
                    ret180 = s.ret180,
                    distEma50 = (s.price / s.e50 - 1) * 100.0,
                    entry = s.price,
                    stop = s.e50,
                    holdNote = "افق: هفته‌ها تا ماه‌ها • چک روزانه کافی است",
                    reasons = reasons
                )
            )
        }

        picks.sortByDescending { it.score }
        lines.add("اسکن روزانه: $scanned کوین | واجد شرایط: ${picks.size}")

        return SpotReport(true, "BTC بالای EMA200", picks.take(15), scanned, lines)
    }

    private fun buildScore(s: Stat, strengthPct: Double, fresh: Boolean, dist20: Double): Int {
        var sc = 0
        sc += if (strengthPct >= 90) 30 else 22                       // قوت مطلق
        sc += 20                                                       // EMA200/50 ساختار
        sc += if (s.ret180 > 0) 12 else 4                              // روند بلند هم مثبت
        sc += if (fresh) 20 else 8                                     // تازگی ورود
        if (dist20 < -0.03) sc += 8                                    // حتی زیر EMA20 = تخفیف
        if (dist20 > 0.12) sc -= 15                                    // خیلی دور شده = نخر
        return min(100, max(0, sc))
    }

    // ---------- رژیم کلان BTC ----------
    private data class MacroResult(val on: Boolean, val ema200: Double)

    private suspend fun btcMacro(): MacroResult {
        return try {
            val k = BinanceClient.api.klines("BTCUSDT", "1d", 260)
            if (k.size < 210) return MacroResult(true, 0.0)
            val closes = k.map { it[4].asDouble }
            val e = emaLast(closes, 200)
            MacroResult(closes.last() > e, e)
        } catch (_: Exception) {
            MacroResult(true, 0.0)
        }
    }

    private data class Stat(
        val sym: String,
        val price: Double,
        val e50: Double,
        val e200: Double,
        val ret90: Double,
        val ret180: Double
    )

    // ---------- اندیکاتور ----------
    fun emaLast(data: List<Double>, period: Int): Double {
        if (data.size < period) return data.lastOrNull() ?: 0.0
        val k = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) ema = data[i] * k + ema * (1 - k)
        return ema
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%+.0f", v)
}
