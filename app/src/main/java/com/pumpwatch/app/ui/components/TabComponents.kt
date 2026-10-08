package com.pumpwatch.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.design.TabPalette

/**
 * 🚀 Commit 161: کامپوننت‌های تب-محور — دو الگوی Picture 34 و Picture 35.
 *
 * ⚠️ قانون صداقت (مهم‌تر از زیبایی):
 * هیچ‌کدام از این کامپوننت‌ها عدد ساختگی نمی‌گیرند. فراخوان باید دادهٔ واقعی
 * از CoinApi / SignalLogger / AlertRulesStore / TradeStore بدهد؛ اگر داده نیست،
 * null بفرستد و کامپوننت «—» خاکستری نشان دهد — نه ۰، نه حدس.
 * (این دقیقاً همان چیزی است که گزارش‌ها به‌عنوان P0 علامت زده‌اند.)
 */

// ---------------- الگوی Picture 34: کارت سیگنال ----------------

data class SignalView(
    val symbol: String,                 // "BTC/USDT"
    val entry: Double?,                 // null ⇒ "—"
    val target: Double?,
    val stop: Double?,
    val confidence: Int?,               // 0..100 یا null
    val reasons: List<String>,          // ["MACD","RSI","Volume"]
    val spark: List<Float>,             // نقاط mini-chart (soft-max شده)
    val source: String,                 // "Binance"
    val ageSec: Long,
)

@Composable
fun SignalCard(s: SignalView, tabKey: String) {
    val accent = TabPalette.accent(tabKey)
    GlassCard(accent = accent, modifier = Modifier.fillMaxWidth()) {
        Text(s.symbol, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFE6EDF3))
        Spacer(Modifier.height(10.dp))
        PriceLine("Entry", s.entry, Color(0xFF00E676))
        PriceLine("Target", s.target, Color(0xFF22D3EE))
        PriceLine("Stop", s.stop, Color(0xFFFF5252))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConfidenceRing(score = s.confidence, accent = accent)
            Spacer(Modifier.width(16.dp))
            MiniSparkline(points = s.spark, color = Color(0xFF00E676), modifier = Modifier.weight(1f).height(56.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            s.reasons.forEach { ReasonChip(it, accent) }
        }
        Spacer(Modifier.height(10.dp))
        SourceAgeLine(s.source, s.ageSec)
    }
}

@Composable
private fun PriceLine(label: String, value: Double?, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color(0xFF8B949E))
        Text(
            value?.let { "$${String.format("%,.0f", it)}" } ?: "—",
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (value == null) Color(0xFF8B949E) else color,
        )
    }
}

@Composable
fun ConfidenceRing(score: Int?, accent: Color, size: Int = 64) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth()) {
            val stroke = 6.dp.toPx()
            val r = (this.size.minDimension - stroke) / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            // مسیر پس‌زمینه
            drawArc(Color.White.copy(alpha = 0.10f), -90f, 360f, false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
            // پرشدگی بر اساس score
            val frac = (score ?: 0).coerceIn(0, 100) / 100f
            if (frac > 0f) drawArc(
                brush = Brush.sweepGradient(listOf(accent, Color(0xFFA855F7))),
                startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            )
        }
        Text(
            score?.let { "$it/100" } ?: "—",
            fontSize = 13.sp, fontWeight = FontWeight.Black,
            color = if (score == null) Color(0xFF8B949E) else Color(0xFFE6EDF3),
        )
    }
}

@Composable
fun MiniSparkline(points: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width; val h = size.height
        val step = w / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v.coerceIn(0f, 1f) * h * 0.8f + h * 0.1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ReasonChip(text: String, accent: Color) {
    Box(
        Modifier
            .background(TabPalette.chip(accent), RoundedCornerShape(12.dp))
            .border(1.dp, TabPalette.border(accent), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 10.sp, color = Color(0xFFE6EDF3)) }
}

// ---------------- الگوی Picture 35: ردیف لیست ----------------

data class CoinRowView(
    val symbol: String,
    val price: Double?,                 // null ⇒ "—"
    val change24h: Double?,             // null ⇒ "—"
    val spark: List<Float>,
    val source: String,
    val ageSec: Long,
)

@Composable
fun CoinRow(c: CoinRowView, tabKey: String) {
    val accent = TabPalette.accent(tabKey)
    GlassCard(accent = accent, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), radius = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // نشان دایره‌ای با حرف اول
            Box(
                Modifier.size(34.dp).background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, TabPalette.border(accent), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(c.symbol.take(1), fontSize = 14.sp, fontWeight = FontWeight.Black, color = accent) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.symbol, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                Text(
                    c.price?.let { "$${String.format("%,.2f", it)}" } ?: "—",
                    fontSize = 11.sp, color = Color(0xFF8B949E),
                )
            }
            MiniSparkline(c.spark, color = if ((c.change24h ?: 0.0) >= 0) Color(0xFF00E676) else Color(0xFFFF5252),
                modifier = Modifier.width(56.dp).height(28.dp))
            Spacer(Modifier.width(10.dp))
            val ch = c.change24h
            Text(
                ch?.let { "%+.2f%%".format(it) } ?: "—",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = when {
                    ch == null -> Color(0xFF8B949E)
                    ch >= 0 -> Color(0xFF00E676)
                    else -> Color(0xFFFF5252)
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        SourceAgeLine(c.source, c.ageSec)
    }
}

// ---------------- کاشی آماری پایین هاب (Picture 36) ----------------

@Composable
fun StatTile(title: String, value: String, accent: Color, sub: String? = null) {
    GlassCard(accent = accent, modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Text(title, fontSize = 11.sp, color = Color(0xFF8B949E))
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFE6EDF3))
        if (sub != null) { Spacer(Modifier.height(4.dp)); Text(sub, fontSize = 10.sp, color = accent) }
    }
}
