package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.engine.SignalAccuracyStats
import com.pumpwatch.app.engine.SignalLogger
import java.util.Locale

/**
 * 🚀 Sprint 15 (فاز ۴ / Commit 21): کارت کارنامهٔ دقت سیگنال‌ها
 *
 * این Composable کپسوله از هر جایی قابل استفاده است.
 * داده از SignalLogger.accuracyStats() می‌آید (pure, قابل‌تست).
 */

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val AGold = Color(0xFFFFC107)
private val ABlue = Color(0xFF40C4FF)
private val AGray = Color(0xFF8B949E)
private val ACard = Color(0xFF1A2230)

@Composable
fun SignalAccuracyCard(
    modifier: Modifier = Modifier,
    accent: Color = AGreen
) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<SignalAccuracyStats?>(null) }
    var byMode by remember { mutableStateOf<Map<String, SignalAccuracyStats>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val logs = SignalLogger.load(context)
        stats = SignalLogger.accuracyStats(logs)
        byMode = SignalLogger.accuracyByMode(logs)
    }

    Surface(
        color = ACard,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📊 کارنامهٔ دقت سیگنال‌ها", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (stats != null) {
                    val s = stats!!
                    Text(
                        "${s.totalDecided} تصمیم",
                        fontSize = 10.sp, color = AGray
                    )
                }
            }

            if (stats == null || stats!!.total == 0) {
                Text(
                    "هنوز سیگنالی ثبت نشده — با اولین اسکن، سیگنال‌ها ذخیره می‌شوند و بعد از ۲۴ ساعت نمره می‌گیرند.",
                    fontSize = 10.sp, color = AGray, lineHeight = 15.sp
                )
            } else {
                val s = stats!!

                // ---------- خط اصلی: وین‌ریت ----------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("وین‌ریت کل", fontSize = 10.sp, color = AGray)
                        Text(
                            String.format(Locale.US, "%.1f%%", s.winRate),
                            fontSize = 22.sp, fontWeight = FontWeight.Black,
                            color = when {
                                s.totalDecided < 10 -> AGray
                                s.winRate >= 55 -> AGreen
                                s.winRate >= 45 -> AGold
                                else -> ARed
                            }
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MiniStat("برد", s.wins.toString(), AGreen)
                            MiniStat("باخت", s.losses.toString(), ARed)
                            MiniStat("منقضی", s.expired.toString(), AGold)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ---------- تفکیک mode ----------
                if (byMode.isNotEmpty()) {
                    Text("به تفکیک مود:", fontSize = 10.sp, color = AGray, fontWeight = FontWeight.Bold)
                    byMode.forEach { (mode, m) ->
                        ModeRow(mode, m)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "⚠️ صداقت: این عدد از سیگنال‌های واقعی ثبت‌شده در دستگاه شماست (نه شبیه‌سازی). " +
                        "با کوچک بودن نمونه، عدد ناپایدار است — با ${s.totalDecided} تصمیم فعلی، بازهٔ اطمینان ۹۵٪ حدوداً " +
                        "±${marginOfError(s.totalDecided, s.winRate)}٪ است.",
                    fontSize = 9.sp, color = AGray, lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 8.sp, color = AGray)
    }
}

@Composable
private fun ModeRow(mode: String, m: SignalAccuracyStats) {
    val modeLabel = if (mode == "FUT") "⚡ فیوچرز" else "🏦 اسپات"
    val winColor = when {
        m.totalDecided < 5 -> AGray
        m.winRate >= 55 -> AGreen
        m.winRate >= 45 -> AGold
        else -> ARed
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(modeLabel, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${m.wins}W/${m.losses}L", fontSize = 10.sp, color = AGray)
            if (m.totalDecided > 0) {
                Text(
                    String.format(Locale.US, "%.1f%%", m.winRate),
                    fontSize = 11.sp, fontWeight = FontWeight.Black, color = winColor
                )
            } else {
                Text("—", fontSize = 11.sp, color = AGray)
            }
        }
    }
}

/**
 * 🚀 Commit 21: محاسبهٔ حاشیهٔ خطای ۹۵٪ برای وین‌ریت
 * فرمول Wilson score interval (نسخهٔ ساده‌شده):
 *   margin ≈ 1.96 * sqrt(p*(1-p)/n)
 * برای n کوچک، عدد بزرگ می‌شود → صداقت حفظ می‌شود.
 */
internal fun marginOfError(n: Int, winRatePct: Double): String {
    if (n < 2) return "؟"
    val p = winRatePct / 100.0
    val margin = 1.96 * kotlin.math.sqrt(p * (1 - p) / n) * 100.0
    return String.format(Locale.US, "%.0f", margin)
}
