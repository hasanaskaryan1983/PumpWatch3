package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.engine.EraStore
import com.pumpwatch.app.ui.components.GlassCard
import java.util.Locale
import kotlin.math.abs

/**
 * 🚀 P4-1: آمار یک دوره (قبل یا بعد از نقطهٔ تغییر).
 */
data class EraStats(
    val count: Int,
    val winRate: Double,
    val profitFactor: Double,
    val avgR: Double,
    val totalPnlUsd: Double
)

/**
 * 🚀 P4-1 fix: محاسبهٔ محلی R-multiple — کاملاً مستقل از TradesScreen
 * تا دیگر به دسترسی internal/private وابسته نباشیم.
 */
private fun eraR(t: PaperTrade): Double {
    if (t.entry <= 0.0 || t.stopPct <= 0.0) return 0.0
    val risk = t.entry * t.stopPct / 100.0
    return (t.price - t.entry) / risk
}

/**
 * 🚀 P4-1: موتور مقایسهٔ دو دورهٔ عملکرد.
 * تابع خالص و کاملاً تست‌پذیر.
 */
object EraComparisonEngine {

    data class Comparison(
        val markLabel: String,
        val markTs: Long,
        val before: EraStats,
        val after: EraStats,
        val verdict: String,
        val verdictPositive: Boolean?
    )

    fun stats(trades: List<PaperTrade>): EraStats {
        if (trades.isEmpty()) return EraStats(0, 0.0, 0.0, 0.0, 0.0)
        val wins = trades.filter { it.pnl > 0 }
        val losses = trades.filter { it.pnl < 0 }
        val winRate = wins.size * 100.0 / trades.size
        val grossProfit = wins.sumOf { it.pnl * it.sizeUsd / 100.0 }
        val grossLoss = abs(losses.sumOf { it.pnl * it.sizeUsd / 100.0 })
        val pf = when {
            grossLoss > 0 -> grossProfit / grossLoss
            grossProfit > 0 -> Double.POSITIVE_INFINITY
            else -> 0.0
        }
        val rs = trades.map { eraR(it) }
        val avgR = if (rs.isEmpty()) 0.0 else rs.average()
        val totalPnlUsd = trades.sumOf { it.pnl * it.sizeUsd / 100.0 }
        return EraStats(trades.size, winRate, pf, avgR, totalPnlUsd)
    }

    fun compare(
        before: List<PaperTrade>,
        after: List<PaperTrade>,
        markLabel: String,
        markTs: Long
    ): Comparison {
        val b = stats(before)
        val a = stats(after)
        val (verdict, positive) = when {
            b.count < 3 || a.count < 3 ->
                "هنوز دادهٔ کافی برای مقایسهٔ قابل‌اتکا نیست (حداقل ۳ موقعیت بسته در هر دو دوره لازم است)." to null
            a.avgR > b.avgR + 0.05 ->
                "✅ تغییر عملکرد را بهبود داد: میانگین R از ${fmt(b.avgR)} به ${fmt(a.avgR)} رسید." to true
            a.avgR < b.avgR - 0.05 ->
                "⚠️ تغییر عملکرد را بدتر کرد: میانگین R از ${fmt(b.avgR)} به ${fmt(a.avgR)} افت کرد. شاید «بازگشت به بهترین تنظیمات» منطقی باشد." to false
            else ->
                "➖ تفاوت معناداری در میانگین R دیده نمی‌شود؛ به جمع‌آوری داده ادامه دهید." to null
        }
        return Comparison(markLabel, markTs, b, a, verdict, positive)
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%+.2f", v)
}

/**
 * 🚀 P4-1: کارت مقایسهٔ قبل/بعد در ژورنال.
 */
@Composable
fun EraComparisonCard(closed: List<PaperTrade>) {
    val ctx = LocalContext.current
    // کلید نوسازی: بعد از ثبت/پاک‌سازی نقطهٔ تغییر، UI تازه می‌شود
    var refreshKey by remember { mutableStateOf(0) }
    val marks = remember(refreshKey) { EraStore.load(ctx) }
    val last = marks.maxByOrNull { it.ts }

    GlassCard(accent = PumpWatchPalette.Purple, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📊 مقایسهٔ قبل/بعد از تغییر",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PumpWatchPalette.Text,
                    modifier = Modifier.weight(1f)
                )
                if (marks.isNotEmpty()) {
                    TextButton(onClick = { EraStore.clear(ctx); refreshKey++ }) {
                        Text("پاک‌سازی", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                    }
                }
            }

            if (last == null) {
                Text(
                    "با ثبت یک نقطهٔ تغییر، عملکرد ژورنال قبل و بعد از هر تغییر تنظیمات (فرمول، استراتژی و...) مقایسه می‌شود. تغییرات فرمول به‌صورت خودکار ثبت می‌شوند.",
                    fontSize = 9.sp,
                    color = PumpWatchPalette.Muted,
                    lineHeight = 14.sp
                )
            } else {
                val before = closed.filter { it.closeTime < last.ts }
                val after = closed.filter { it.closeTime >= last.ts }
                val cmp = EraComparisonEngine.compare(before, after, last.label, last.ts)

                Text("📍 آخرین نقطهٔ تغییر: ${cmp.markLabel}", fontSize = 9.sp, color = PumpWatchPalette.Warning)

                Row(modifier = Modifier.fillMaxWidth()) {
                    EraColumn("قبل", cmp.before)
                    EraColumn("بعد", cmp.after)
                }

                Text(
                    cmp.verdict,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = when (cmp.verdictPositive) {
                        true -> PumpWatchPalette.Positive
                        false -> PumpWatchPalette.Negative
                        null -> PumpWatchPalette.Muted
                    }
                )
            }

            Button(
                onClick = { EraStore.mark(ctx, "نقطهٔ تغییر دستی"); refreshKey++ },
                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("📍 ثبت نقطهٔ تغییر (از حالا تنظیم جدید را تست می‌کنم)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * 🚀 P4-1 fix: ستون آمار به‌صورت extension روی RowScope
 * تا Modifier.weight در دسترس باشد.
 */
@Composable
private fun RowScope.EraColumn(title: String, s: EraStats) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Text(title, fontSize = 9.sp, color = PumpWatchPalette.Muted)
        Text("${s.count} موقعیت", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
        Text("وین‌ریت ${String.format(Locale.US, "%.0f%%", s.winRate)}", fontSize = 9.sp, color = PumpWatchPalette.Muted)
        Text("PF ${if (s.profitFactor.isInfinite()) "∞" else String.format(Locale.US, "%.2f", s.profitFactor)}", fontSize = 9.sp, color = PumpWatchPalette.Muted)
        Text("میانگین R ${String.format(Locale.US, "%+.2f", s.avgR)}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
    }
}
