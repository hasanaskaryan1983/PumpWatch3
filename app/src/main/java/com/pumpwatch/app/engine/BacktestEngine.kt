package com.pumpwatch.app.engine

import com.pumpwatch.app.data.KlineCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 🚀 Sprint 13 (F6b): موتور بک‌تست استراتژی
 *
 * همان استراتژی ربات زنده (TradesScreen) را روی تاریخ اجرا می‌کند.
 *
 * منطق:
 * ۱. برای هر روز، از پنجرهٔ تاریخ تا آن روز امتیاز می‌دهیم (no look-ahead)
 * ۲. وقتی امتیاز >= entryThreshold و در پوزیشن نیستیم → باز می‌کنیم
 * ۳. استاپ = ATR × 2.5 (همان فرمول ربات)
 * ۴. تارگت = استاپ × 2
 * ۵. تریلینگ فعال (همان فرمول ربات)
 * ۶. حداکثر روز نگهداری (maxHoldDays) برای جلوگیری از قفل‌شدن سرمایه
 * ۷. سایز پوزیشن ثابت = ۱۰۰ دلار
 *
 * صداقت:
 * - فقط ارزهایی که در Binance لیست هستند و ۳۰۰ روز تاریخ دارند کار می‌کند
 * - DEX ها و ارزهای جدید بک‌تست نمی‌شوند (صادقانه در UI گفته می‌شود)
 * - هزینهٔ درخواست: برای ۱۰ ارز × ۱۲ ماه = ~۱۰ درخواست Binance (cache 60s)
 */
object BacktestEngine {

    data class BacktestTrade(
        val symbol: String,
        val entryDay: Int,           // روز از شروع بک‌تست
        val exitDay: Int,
        val entryPrice: Double,
        val exitPrice: Double,
        val pnlPct: Double,
        val pnlUsd: Double,
        val rMultiple: Double,
        val exitReason: String       // "STOP" | "TARGET" | "TIMEOUT"
    )

    data class BacktestResult(
        val symbol: String,
        val days: Int,
        val trades: List<BacktestTrade>,
        val equityCurve: List<Double>,   // موجودی هر روز
        val stats: Stats
    )

    data class Stats(
        val totalTrades: Int,
        val wins: Int,
        val losses: Int,
        val winRate: Double,
        val totalPnlPct: Double,
        val totalPnlUsd: Double,
        val avgR: Double,
        val bestR: Double,
        val worstR: Double,
        val maxDrawdownPct: Double,
        val profitFactor: Double
    )

    data class Config(
        val entryThreshold: Int = 50,
        val sizeUsd: Double = 100.0,
        val maxHoldDays: Int = 30,
        val atrStopMultiple: Double = 2.5,
        val targetMultiple: Double = 2.0,
        val trailingEnabled: Boolean = true
    )

    /**
     * بک‌تست روی یک ارز — pure و قابل تست
     * @param candles کندل‌های روزانه (حداقل ۲۰۰ تا برای محاسبهٔ EMA200)
     */
    fun runSingle(symbol: String, candles: List<com.google.gson.JsonArray>, config: Config): BacktestResult {
        val closes = candles.map { it[4].asDouble }
        if (closes.size < 200) {
            return BacktestResult(symbol, 0, emptyList(), emptyList(),
                Stats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        }

        val trades = mutableListOf<BacktestTrade>()
        val equity = mutableListOf(config.sizeUsd)

        var cash = config.sizeUsd
        var inTrade = false
        var entryDay = 0
        var entryPrice = 0.0
        var stop = 0.0
        var target = 0.0
        var targetHit = false

        // از روز ۲۰۰ شروع می‌کنیم (تا EMA200 قابل محاسبه باشد)
        for (day in 200 until closes.size) {
            val price = closes[day]

            if (inTrade) {
                val daysHeld = day - entryDay

                // ۱) رسیدن به تارگت → قفل سود (حالت دونده)
                if (!targetHit && price >= target) {
                    targetHit = true
                    target = -1.0
                    val lock = entryPrice * (1 + (entryPrice - stop) / entryPrice)
                    if (lock > stop) stop = lock
                }

                // ۲) تریلینگ
                if (config.trailingEnabled) {
                    val dist = if (targetHit)
                        (entryPrice - stop) / entryPrice * 0.7
                    else
                        (entryPrice - stop) / entryPrice
                    val nt = price * (1 - dist)
                    if (nt > stop) stop = nt
                }

                // ۳) بررسی خروج
                val exitReason = when {
                    price <= stop -> "STOP"
                    targetHit && price >= entryPrice * 1.5 -> "TARGET" // در حالت دونده، خروج در +50%
                    daysHeld >= config.maxHoldDays -> "TIMEOUT"
                    else -> null
                }

                if (exitReason != null) {
                    val pnlPct = (price - entryPrice) / entryPrice * 100
                    val pnlUsd = config.sizeUsd * pnlPct / 100
                    val risk = abs(entryPrice - stop)
                    val r = if (risk > 0) (price - entryPrice) / risk else 0.0

                    trades.add(
                        BacktestTrade(
                            symbol = symbol,
                            entryDay = entryDay,
                            exitDay = day,
                            entryPrice = entryPrice,
                            exitPrice = price,
                            pnlPct = pnlPct,
                            pnlUsd = pnlUsd,
                            rMultiple = r,
                            exitReason = exitReason
                        )
                    )
                    cash += config.sizeUsd + pnlUsd
                    inTrade = false
                }
            } else {
                // امتیازدهی روی پنجرهٔ تاریخ (no look-ahead)
                val window = candles.subList(0, day + 1)
                val (score, atrPct) = ScoringEngine.scoreFromCandles(window)

                if (score >= config.entryThreshold && cash >= config.sizeUsd) {
                    // محاسبهٔ استاپ از ATR
                    val atrAbs = price * atrPct / 100
                    val risk = atrAbs * config.atrStopMultiple
                    entryDay = day
                    entryPrice = price
                    stop = price - risk
                    target = price + risk * config.targetMultiple
                    targetHit = false
                    inTrade = true
                    cash -= config.sizeUsd
                }
            }

            // equity روزانه = نقد + ارزش پوزیشن
            val posValue = if (inTrade) {
                config.sizeUsd * (1 + (price - entryPrice) / entryPrice)
            } else 0.0
            equity.add(cash + posValue)
        }

        // آمار
        val wins = trades.count { it.pnlPct > 0 }
        val losses = trades.size - wins
        val totalPnlPct = trades.sumOf { it.pnlPct }
        val totalPnlUsd = trades.sumOf { it.pnlUsd }
        val avgR = if (trades.isEmpty()) 0.0 else trades.map { it.rMultiple }.average()
        val bestR = trades.maxOfOrNull { it.rMultiple } ?: 0.0
        val worstR = trades.minOfOrNull { it.rMultiple } ?: 0.0

        // Max drawdown
        var peak = equity.firstOrNull() ?: 0.0
        var maxDD = 0.0
        for (e in equity) {
            if (e > peak) peak = e
            val dd = if (peak > 0) (peak - e) / peak * 100 else 0.0
            if (dd > maxDD) maxDD = dd
        }

        // Profit factor
        val grossProfit = trades.filter { it.pnlUsd > 0 }.sumOf { it.pnlUsd }
        val grossLoss = abs(trades.filter { it.pnlUsd < 0 }.sumOf { it.pnlUsd })
        val pf = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) Double.POSITIVE_INFINITY else 0.0

        val stats = Stats(
            totalTrades = trades.size,
            wins = wins,
            losses = losses,
            winRate = if (trades.isEmpty()) 0.0 else wins * 100.0 / trades.size,
            totalPnlPct = totalPnlPct,
            totalPnlUsd = totalPnlUsd,
            avgR = avgR,
            bestR = bestR,
            worstR = worstR,
            maxDrawdownPct = maxDD,
            profitFactor = pf
        )

        return BacktestResult(symbol, closes.size, trades, equity, stats)
    }

    /**
     * بک‌تست روی چند ارز — هم‌زمان برای کاهش زمان اجرا
     * @param symbols لیست نمادها (مثلاً ["BTC", "ETH", "SOL"])
     * @param days تعداد روز تاریخ (معمولاً 365)
     */
    suspend fun runMultiple(
        symbols: List<String>,
        days: Int = 365,
        config: Config = Config(),
        onProgress: (String) -> Unit = {}
    ): List<BacktestResult> = coroutineScope {
        symbols.mapIndexed { idx, sym ->
            async(Dispatchers.IO) {
                onProgress("دانلود ${sym.uppercase(Locale.US)} (${idx + 1}/${symbols.size})")
                try {
                    val candles = KlineCache.klines(
                        "${sym.uppercase(Locale.US)}USDT", "1d", days
                    )
                    onProgress("بک‌تست ${sym.uppercase(Locale.US)}...")
                    runSingle(sym, candles, config)
                } catch (_: Exception) {
                    BacktestResult(sym, 0, emptyList(), emptyList(),
                        Stats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
                }
            }
        }.awaitAll()
    }
}
