package com.pumpwatch.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.design.TabPalette

/**
 * 🚀 Commit 161: کارت هشدار با glassmorphism (هم‌خانواده SignalCard ولی برای alertهای بازاری).
 *
 * تفاوت با SignalCard:
 *   - Entry/Target/Stop ندارد (چون alert بازاری است، نه سیگنال معامله)
 *   - Severity دارد (خفیف/متوسط/شدید) به‌جای Confidence score
 *   - فقط title + detail + emoji + badge + source•age
 *
 * ⚠️ قانون صداقت: source•age باید از scanner واقعی بیاید، نه hardcode.
 * اگر timestamp در دسترس نیست، ageSec = -1 بفرست تا "—" نمایش دهد.
 */
data class AlertView(
    val symbol: String,
    val type: String,              // "FUNDING" | "MOVE24" | "OI_SPIKE" | "EMA_CROSS" | "BREAKOUT" | "SQUEEZE"
    val severity: Int,             // 1=خفیف, 2=متوسط, 3=شدید
    val title: String,             // "فاندینگ شدید مثبت"
    val detail: String,            // "+0.0542% — لانگ‌ها هزینه می‌دهند..."
    val rank: Int?,                // رتبه در ۱۰۰ برتر (nullable)
    val emoji: String,             // "💸" | "🌪️" | "🏦" | "➿" | "💥" | "🌀"
    val source: String,            // "Binance Futures"
    val ageSec: Long,              // سن داده به ثانیه (از شروع اسکن)
)

@Composable
fun AlertCard(a: AlertView, tabKey: String) {
    val accent = TabPalette.accent(tabKey)
    GlassCard(accent = accent, modifier = Modifier.fillMaxWidth()) {
        // سطر ۱: emoji + symbol + rank + severity badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(a.symbol, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE6EDF3))
            if (a.rank != null) {
                Text(" #${a.rank}", fontSize = 11.sp, color = Color(0xFF8B949E))
            }
            Spacer(Modifier.weight(1f))
            SeverityChip(a.severity)
        }

        Spacer(Modifier.height(8.dp))

        // سطر ۲: title با رنگ severity
        Text(
            a.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = severityColor(a.severity)
        )

        Spacer(Modifier.height(4.dp))

        // سطر ۳: detail
        Text(
            a.detail,
            fontSize = 11.sp,
            color = Color(0xFFB0B8C4),
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(10.dp))

        // سطر ۴: source•age (صداقت)
        SourceAgeLine(a.source, a.ageSec)
    }
}

@Composable
private fun SeverityChip(severity: Int) {
    val (label, color) = when (severity) {
        3 -> "🔥 شدید" to Color(0xFFFF5252)
        2 -> "⚠️ متوسط" to Color(0xFFFFC107)
        else -> "👀 خفیف" to Color(0xFF40C4FF)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun severityColor(sev: Int): Color = when (sev) {
    3 -> Color(0xFFFF5252)
    2 -> Color(0xFFFFC107)
    else -> Color(0xFF40C4FF)
}
