package com.pumpwatch.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// داشبورد استودیو v4 — کارت BTC افقی (مثل مرجع 18)، پس‌زمینهٔ کوه/خورشید/جاده.
// APIها عیناً همان‌های سبزِ قبلی؛ فقط چیدمان و SkyBackdrop عوض شده.

private val ORANGE = Color(0xFFE8833A)
private val AMBER  = Color(0xFFFFB347)
private val GREEN  = Color(0xFF34D399)
private val TEAL   = Color(0xFF2DD4BF)
private val CYAN   = Color(0xFF22D3EE)
private val YELLOW = Color(0xFFFBBF24)
private val RED    = Color(0xFFF87171)
private const val PI_F = 3.14159265f

private val BG    = Color(0xFF070F0D)
private val CARD  = Color(0xFF0E1F1B)
private val MUTED = Color(0xFF8B949E)
private val DIM   = Color(0xFF6B7280)

@Composable
fun HomeDashboard(onTabClick: (String) -> Unit) {
    var futures by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(BG)) {
        SkyBackdrop(modifier = Modifier.fillMaxWidth().height(340.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader()
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                ModeSwitch(futures) { futures = it }
                TitleBlock(futures)
                UnifiedMarketCard(futures, onTabClick)
                if (futures) FuturesTools(onTabClick) else SpotTools(onTabClick)
                Spacer(Modifier.height(4.dp))
            }
            BottomBar()
        }
    }
}

// ---------- پس‌زمینه: کوه تیز + خورشید پشتش + جادهٔ نورانی (مثل مرجع) ----------
@Composable
private fun SkyBackdrop(modifier: Modifier = Modifier) {
    val stars = remember { List(46) { Triple(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat() * 0.9f) } }
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(
            Color(0xFF05090F), Color(0xFF0A121C), Color(0xFF14101E), Color(0xFF1C1410), Color(0xFF0C0A0A)
        )), size = size)
        stars.forEach { s ->
            drawCircle(Color.White.copy(alpha = 0.18f + s.third * 0.30f), radius = s.third,
                center = Offset(s.first * w, s.second * h * 0.42f))
        }
        // خورشید پشت کوه، مرکز-راست
        val sx = w * 0.62f; val sy = h * 0.46f
        drawCircle(Brush.radialGradient(listOf(AMBER.copy(alpha = 0.55f), ORANGE.copy(alpha = 0.18f), Color.Transparent)), radius = w * 0.30f, center = Offset(sx, sy))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A), AMBER, ORANGE.copy(alpha = 0.5f), Color.Transparent)), radius = w * 0.11f, center = Offset(sx, sy))
        drawCircle(AMBER, radius = w * 0.058f, center = Offset(sx, sy))
        // جادهٔ نورانی: از پایین-مرکز به سمت خورشید بال می‌رود
        val road = Path().apply {
            moveTo(w * 0.40f, h); lineTo(w * 0.52f, h * 0.74f); lineTo(w * 0.58f, h * 0.60f)
            lineTo(w * 0.60f, sy + w * 0.02f); lineTo(w * 0.64f, sy + w * 0.02f)
            lineTo(w * 0.66f, h * 0.60f); lineTo(w * 0.74f, h * 0.76f); lineTo(w * 0.86f, h); close()
        }
        drawPath(road, Brush.verticalGradient(listOf(AMBER.copy(alpha = 0.0f), AMBER.copy(alpha = 0.10f), ORANGE.copy(alpha = 0.22f))))
        // کوه‌ها: بلندترین مرکزی-راست، خورشید پشتش
        val peaks = Path().apply {
            moveTo(0f, h); lineTo(w * 0.10f, h * 0.70f); lineTo(w * 0.22f, h * 0.82f)
            lineTo(w * 0.34f, h * 0.58f); lineTo(w * 0.46f, h * 0.74f)
            lineTo(w * 0.60f, h * 0.34f); lineTo(w * 0.70f, h * 0.52f)   // قلهٔ مرکزی که خورشید پشتش است
            lineTo(w * 0.82f, h * 0.40f); lineTo(w * 0.92f, h * 0.62f); lineTo(w, h * 0.50f)
            lineTo(w, h); close()
        }
        drawPath(peaks, Color(0xFF070C0A))
        // مه پایین
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, BG.copy(alpha = 0.85f))),
            topLeft = Offset(0f, h * 0.72f), size = Size(w, h * 0.28f))
    }
}

// ---------- هدر ----------
@Composable
private fun TopHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⚙️", fontSize = 16.sp, color = MUTED)
        Spacer(Modifier.width(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(GREEN))
            Spacer(Modifier.width(5.dp))
            Text("بازار فعال", fontSize = 10.sp, color = MUTED)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("dump", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ORANGE)
            Text("pump", fontSize = 16.sp, fontWeight = FontWeight.Black, color = GREEN)
            Spacer(Modifier.width(5.dp))
            MiniBars()
        }
    }
}

@Composable
private fun MiniBars(modifier: Modifier = Modifier.size(width = 20.dp, height = 16.dp)) {
    Canvas(modifier = modifier) {
        val bw = size.width / 4f
        val hs = floatArrayOf(0.4f, 0.7f, 0.5f, 1.0f)
        val cs = listOf(GREEN, GREEN, ORANGE, ORANGE)
        hs.forEachIndexed { i, f ->
            val hh = size.height * f
            drawRect(cs[i], topLeft = Offset(i * bw + bw * 0.15f, size.height - hh), size = Size(bw * 0.7f, hh))
        }
    }
}

// ---------- سوئیچ ----------
@Composable
private fun ModeSwitch(futures: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier = Modifier.fillMaxWidth().clip(shape).background(CARD.copy(alpha = 0.78f)).border(1.dp, Color.White.copy(alpha = 0.06f), shape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ModeSeg("اسپات", !futures) { onChange(false) }
        ModeSeg("فیوچرز", futures) { onChange(true) }
    }
}

@Composable
private fun RowScope.ModeSeg(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(modifier = Modifier.weight(1f).clip(shape).background(if (active) ORANGE else Color.Transparent).clickable(onClick = onClick).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (active) Color.Black else MUTED, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable
private fun TitleBlock(futures: Boolean) {
    Column {
        Text(if (futures) "استودیوی فیوچرز" else "استودیوی بازار", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(if (futures) "داده و ابزارهای مدیریت ریسک، یکجا" else "داده، ابزار، دید بهتر برای تصمیم‌های هوشمندتر", fontSize = 10.sp, color = MUTED)
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
}

// ---------- کادر یکپارچه: BTC افقی + نبض + گیج/جستجو ----------
private data class MockCoin(val symbol: String, val price: String, val change: String, val up: Boolean)

@Composable
private fun UnifiedMarketCard(futures: Boolean, onTabClick: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val pts = remember { listOf(.10f,.20f,.15f,.28f,.23f,.36f,.31f,.46f,.41f,.54f,.49f,.63f,.58f,.71f,.66f,.80f,.75f,.90f,.85f,1f) }
    var q by remember { mutableStateOf("") }
    val coins = remember { listOf(
        MockCoin("BTC", "$67,420", "+2.4%", true), MockCoin("ETH", "$3,569", "+0.65%", true),
        MockCoin("SOL", "$154", "-1.2%", false), MockCoin("BNB", "$612", "+0.9%", true),
        MockCoin("XRP", "$0.54", "-0.3%", false), MockCoin("PEPE", "$0.000011", "+8.1%", true)
    ) }
    val res = if (q.isBlank()) emptyList() else coins.filter { it.symbol.contains(q, true) }

    Box(modifier = Modifier.fillMaxWidth().shadow(10.dp, shape, clip = false, ambientColor = GREEN.copy(alpha = .22f), spotColor = GREEN.copy(alpha = .36f))) {
        Column(modifier = Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0E2A26).copy(alpha = .90f), Color(0xFF0A1512).copy(alpha = .94f))))
            .border(1.2.dp, Brush.linearGradient(listOf(GREEN.copy(alpha = .78f), GREEN.copy(alpha = .10f))), shape)
            .padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {

            // ردیف بالا: هویت + چیپ
            Row(verticalAlignment = Alignment.CenterVertically) {
                BtcBadge()
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("BTC/USDT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(if (futures) "Perpetual · نمونهٔ طراحی" else "Bitcoin", color = MUTED, fontSize = 9.sp)
                }
                Chip(if (futures) "دادهٔ نمونه" else "داده بازار", GREEN)
                Spacer(Modifier.width(7.dp))
                Text("24h ▾", color = MUTED, fontSize = 10.sp)
            }
            // ردیف میانی: قیمت چپ + نمودار راست (افقی = فشرده)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("۶۷,۴۲۰", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("+۲,۴٪", color = GREEN, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.width(3.dp)); Text("▲", color = GREEN, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                ChartSpark(pts, GREEN, TEAL, Modifier.weight(1f).height(74.dp))
            }

            Hairline()

            // نبض (باریک)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نبض بازار", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(9.dp))
                WaveMini(Modifier.weight(1f).height(15.dp))
                Spacer(Modifier.width(9.dp))
                Text(if (futures) "Liq · 01 · Funding" else "متعادل", color = MUTED, fontSize = 8.sp)
                Spacer(Modifier.width(5.dp)); Text("❮", color = MUTED, fontSize = 10.sp)
            }

            Hairline()

            // گیج + تحلیل (یک ردیف) — نگه‌داشته‌شده از خواستهٔ قبلی
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    FgGauge(0.64f, GREEN)
                    Text("64", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(GREEN))
                        Spacer(Modifier.width(5.dp))
                        Text("طمع", color = GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(1.dp))
                    Text("روند صعودی ملایم؛ منتظر تأیید حجم برای شکست مقاومت.", color = MUTED, fontSize = 8.sp, lineHeight = 12.sp)
                }
            }
            // جستجو (باریک، کامل‌عرض)
            TextField(
                value = q, onValueChange = { q = it }, modifier = Modifier.fillMaxWidth().height(44.dp), singleLine = true,
                placeholder = { Text("🔍 جستجوی ارز (BTC, ETH, SOL)...", fontSize = 11.sp, color = MUTED) },
                shape = RoundedCornerShape(9.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0A1714), unfocusedContainerColor = Color(0xFF0A1714),
                    cursorColor = GREEN,
                    focusedIndicatorColor = GREEN.copy(alpha = .5f), unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = MUTED, unfocusedPlaceholderColor = MUTED
                )
            )
            if (res.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    res.take(3).forEach { c ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onTabClick("market") }.padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.symbol, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                            Text(c.price, color = MUTED, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text(c.change, color = if (c.up) GREEN else RED, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---------- توابع کمکی (عیناً سبزِ قبلی) ----------
@Composable
private fun BtcBadge(modifier: Modifier = Modifier.size(34.dp)) {
    Box(modifier = modifier.clip(CircleShape).background(Color(0xFFF7931A)), contentAlignment = Alignment.Center) {
        Text("₿", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    val s = RoundedCornerShape(20.dp)
    Box(Modifier.clip(s).background(color.copy(alpha = .12f)).border(1.dp, color.copy(alpha = .4f), s).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp)); Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChartSpark(points: List<Float>, line: Color, fill: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width; val h = size.height; val pad = h * 0.12f; val uh = h - pad * 2f
        val sx = w / (points.size - 1)
        val lp = Path(); val fp = Path()
        points.forEachIndexed { i, v ->
            val x = i * sx; val y = pad + (1f - v) * uh
            if (i == 0) { lp.moveTo(x, y); fp.moveTo(x, h); fp.lineTo(x, y) } else { lp.lineTo(x, y); fp.lineTo(x, y) }
        }
        fp.lineTo(w, h); fp.close()
        drawPath(fp, Brush.verticalGradient(listOf(fill.copy(alpha = .35f), Color.Transparent)))
        drawPath(lp, line, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val ly = pad + (1f - points.last()) * uh
        drawCircle(line.copy(alpha = .3f), 6f, center = Offset(w, ly))
        drawCircle(line, 3f, center = Offset(w, ly))
    }
}

@Composable
private fun WaveMini(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val mid = h / 2f; val n = 40
        val pg = Path(); val po = Path()
        for (i in 0..n) {
            val fx = i / n.toFloat() * w
            val gy = mid + sin(fx / w * PI_F * 3f) * (h * .30f)
            val oy = mid + sin(fx / w * PI_F * 3f + 1.2f) * (h * .22f)
            if (i == 0) { pg.moveTo(fx, gy); po.moveTo(fx, oy) } else { pg.lineTo(fx, gy); po.lineTo(fx, oy) }
        }
        drawPath(pg, GREEN, style = Stroke(1.4f, cap = StrokeCap.Round))
        drawPath(po, ORANGE, style = Stroke(1.4f, cap = StrokeCap.Round))
    }
}

@Composable
private fun FgGauge(frac: Float, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = size.width * 0.16f
        val d = size.width - stroke
        val top = Offset(stroke / 2f, stroke / 2f)
        val arc = Size(d, d)
        drawArc(Color.White.copy(alpha = 0.10f), 135f, 270f, false, top, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, 135f, 270f * frac, false, top, arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ---------- ابزارها (فشرده‌تر) ----------
@Composable
private fun SpotTools(onTabClick: (String) -> Unit) {
    SectionTitle("ابزارهای منتخب", "تحلیل، پایش و اقدام", ORANGE)
    ToolGroup("دیدن", "عمیق‌تر به بازار", GREEN, listOf(
        Tool("بازار", "📊", "market", true), Tool("واچ‌لیست", "⭐", "watchlist"), Tool("کیف پول", "👛", "wallet")
    ), onTabClick)
    ToolGroup("پایش", "از حرکت‌ها جلوتر", ORANGE, listOf(
        Tool("هشدار", "🔔", "alerts_spot"), Tool("نهنگ", "🐳", "whale"), Tool("تریدرها", "👥", "traders"), Tool("میم", "🐸", "meme")
    ), onTabClick)
    ToolGroup("تحلیل و عمل", "از داده تا اقدام", YELLOW, listOf(
        Tool("دستیار", "🤖", "assistant"), Tool("بک‌تست", "🧪", "backtest"), Tool("معامله", "🔁", "trades")
    ), onTabClick)
}

@Composable
private fun FuturesTools(onTabClick: (String) -> Unit) {
    SectionTitle("کشف فرصت", "معامله‌های هوشمندتر", GREEN)
    ToolRow(listOf(
        ToolBig("سیگنال", "📶", "داده تا ایده", "futures_dash", GREEN),
        ToolBig("اسکنر", "🎯", "فرصت‌ها را پیدا کن", "futures_scan", GREEN)
    ), onTabClick)
    SectionTitle("پایش و تمرین", "ریسک بدون حدس", ORANGE)
    ToolRow(listOf(
        ToolBig("هشدار", "🔔", "موقعیت از دست نده", "alerts_fut", ORANGE),
        ToolBig("Paper", "🎓", "معامله آزمایشی", "paper", ORANGE)
    ), onTabClick)
    SectionTitle("تحلیل عملکرد", "حرفه‌ای معامله کن", CYAN)
    ToolRow(listOf(
        ToolBig("ژورنال", "📄", "از معاملات یاد بگیر", "journal", CYAN),
        ToolBig("بک‌تست", "🧪", "ایده‌ها را محک بزن", "backtest", CYAN)
    ), onTabClick)
}

@Composable
private fun SectionTitle(title: String, sub: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(7.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(7.dp))
        Text(sub, color = MUTED, fontSize = 9.sp)
    }
}

@Composable
private fun ToolRow(tools: List<ToolBig>, onTabClick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        tools.forEach { ToolBig(it, onTabClick) }
    }
}

private data class Tool(val label: String, val emoji: String, val id: String, val active: Boolean = false)

@Composable
private fun ToolGroup(title: String, sub: String, accent: Color, tools: List<Tool>, onTabClick: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = Modifier.fillMaxWidth().clip(shape).background(CARD.copy(alpha = 0.86f)).border(1.dp, accent.copy(alpha = .20f), shape).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(sub, color = MUTED, fontSize = 8.sp)
            Spacer(Modifier.width(5.dp)); Text("❮", color = accent, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            tools.forEach { t -> ToolCell(t, accent, onTabClick) }
        }
    }
}

@Composable
private fun RowScope.ToolCell(t: Tool, accent: Color, onTabClick: (String) -> Unit) {
    val s = RoundedCornerShape(10.dp)
    Column(modifier = Modifier.weight(1f).clip(s).background(if (t.active) accent.copy(alpha = .16f) else Color(0xFF0A1714)).border(1.dp, if (t.active) accent.copy(alpha = .6f) else Color.White.copy(alpha = .05f), s).clickable { onTabClick(t.id) }.padding(vertical = 9.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t.emoji, fontSize = 18.sp)
        Text(t.label, color = if (t.active) accent else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private data class ToolBig(val label: String, val emoji: String, val desc: String, val id: String, val accent: Color)

@Composable
private fun RowScope.ToolBig(t: ToolBig, onTabClick: (String) -> Unit) {
    val s = RoundedCornerShape(12.dp)
    Row(modifier = Modifier.weight(1f).clip(s).background(CARD.copy(alpha = 0.86f)).border(1.dp, t.accent.copy(alpha = .26f), s).clickable { onTabClick(t.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(t.accent.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Text(t.emoji, fontSize = 14.sp) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(t.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(t.desc, color = MUTED, fontSize = 8.sp)
        }
        Text("❮", color = t.accent, fontSize = 11.sp)
    }
}

@Composable
private fun BottomBar() {
    val items = listOf("خانه" to "🏠", "دیتا" to "📊", "اخبار" to "📰", "پروفایل" to "👤")
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A1512).copy(alpha = 0.95f)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEachIndexed { i, (label, emoji) ->
            val active = i == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 16.sp, color = if (active) ORANGE else MUTED)
                Text(label, fontSize = 8.sp, color = if (active) ORANGE else MUTED, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
