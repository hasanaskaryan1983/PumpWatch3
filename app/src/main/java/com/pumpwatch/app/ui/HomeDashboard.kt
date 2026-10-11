package com.pumpwatch.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// داشبورد استودیو v5 — نمای مداری (گوی BTC + حلقهٔ آیکون) به‌جای کارت BTC،
// روی همان غروب؛ تایپ/افکت هماهنگ با بقیهٔ داشبورد. بدون nativeCanvas/drawText روی Canvas.
// (import غلط matchParentSize حذف شد — این عضوِ BoxScope است و نیاز به import ندارد.)

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
    Column(modifier = Modifier.fillMaxSize().background(BG)) {
        TopHeader()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            OrbitHeader(onTabClick)
            ModeSwitch(futures) { futures = it }
            TitleBlock(futures)
            InsightCard(onTabClick)
            if (futures) FuturesTools(onTabClick) else SpotTools(onTabClick)
            Spacer(Modifier.height(4.dp))
        }
        BottomBar()
    }
}

// ---------- نمای مداری: غروب + حلقهٔ چرخان + گوی BTC + نوار قیمت ----------
private data class OTab(val id: String, val emoji: String, val label: String, val color: Color)

@Composable
private fun OrbitHeader(onTabClick: (String) -> Unit) {
    val inf = rememberInfiniteTransition(label = "orbit")
    val rot by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart), label = "r")
    val tabs = remember { listOf(
        OTab("market", "📊", "بازار", GREEN),
        OTab("alerts_spot", "🔔", "هشدار", ORANGE),
        OTab("whale", "🐳", "نهنگ", CYAN),
        OTab("meme", "🐸", "میم", YELLOW),
        OTab("wallet", "👛", "کیف", ORANGE),
        OTab("assistant", "🤖", "دستیار", TEAL),
        OTab("backtest", "🧪", "بک‌تست", GREEN),
        OTab("traders", "🏆", "تریدرها", ORANGE)
    ) }
    val stars = remember { List(42) { Triple(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat() * 0.9f) } }
    val density = LocalDensity.current
    val strokePx = with(density) { 1.5.dp.toPx() }
    val orbR = with(density) { 22.dp.toPx() }
    val colHalf = with(density) { 28.dp.toPx() }
    val orbTopHalf = with(density) { 20.dp.toPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp))) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val a = w * 0.40f
        val b = h * 0.26f

        // لایهٔ ۱: آسمان (ستاره + خورشید + کوه + مه)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(
                Color(0xFF05090F), Color(0xFF0A121C), Color(0xFF14101E), Color(0xFF1C1410), Color(0xFF0C0A0A)
            )), size = size)
            stars.forEach { s ->
                drawCircle(Color.White.copy(alpha = 0.16f + s.third * 0.28f), radius = s.third,
                    center = Offset(s.first * w, s.second * h * 0.46f))
            }
            val sx = w * 0.62f; val sy = h * 0.42f
            drawCircle(Brush.radialGradient(listOf(AMBER.copy(alpha = 0.50f), ORANGE.copy(alpha = 0.16f), Color.Transparent)), radius = w * 0.30f, center = Offset(sx, sy))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A), AMBER, ORANGE.copy(alpha = 0.5f), Color.Transparent)), radius = w * 0.10f, center = Offset(sx, sy))
            drawCircle(AMBER, radius = w * 0.052f, center = Offset(sx, sy))
            val road = Path().apply {
                moveTo(w * 0.40f, h); lineTo(w * 0.52f, h * 0.74f); lineTo(w * 0.58f, h * 0.62f)
                lineTo(w * 0.60f, sy + w * 0.02f); lineTo(w * 0.64f, sy + w * 0.02f)
                lineTo(w * 0.66f, h * 0.62f); lineTo(w * 0.74f, h * 0.76f); lineTo(w * 0.86f, h); close()
            }
            drawPath(road, Brush.verticalGradient(listOf(AMBER.copy(alpha = 0.0f), AMBER.copy(alpha = 0.10f), ORANGE.copy(alpha = 0.20f))))
            val peaks = Path().apply {
                moveTo(0f, h); lineTo(w * 0.10f, h * 0.70f); lineTo(w * 0.22f, h * 0.82f)
                lineTo(w * 0.34f, h * 0.58f); lineTo(w * 0.46f, h * 0.74f)
                lineTo(w * 0.60f, h * 0.34f); lineTo(w * 0.70f, h * 0.52f)
                lineTo(w * 0.82f, h * 0.40f); lineTo(w * 0.92f, h * 0.62f); lineTo(w, h * 0.50f)
                lineTo(w, h); close()
            }
            drawPath(peaks, Color(0xFF070C0A))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, BG.copy(alpha = 0.80f))),
                topLeft = Offset(0f, h * 0.74f), size = Size(w, h * 0.26f))
        }

        // لایهٔ ۲: حلقه + گوی (فقط شکل، بدون متن)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawOval(Color.White.copy(alpha = 0.14f), style = Stroke(width = strokePx),
                topLeft = Offset(cx - a, cy - b), size = Size(a * 2f, b * 2f))
            drawCircle(Brush.radialGradient(listOf(GREEN.copy(alpha = 0.30f), Color.Transparent)), radius = orbR * 2.6f, center = Offset(cx, cy))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE066), Color(0xFFFFC107), Color(0xFFB8860B)),
                center = Offset(cx - orbR * 0.3f, cy - orbR * 0.3f), radius = orbR * 1.4f), radius = orbR, center = Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.55f), radius = orbR, style = Stroke(width = strokePx), center = Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.22f), radius = orbR * 0.34f, center = Offset(cx - orbR * 0.3f, cy - orbR * 0.34f))
        }

        // ₿ مرکزی (overlay متن — امن)
        Text("₿", modifier = Modifier.align(Alignment.Center), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

        // نوار قیمت (گوشهٔ پایین-چپ، دور از آیکونِ پایین)
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(12.dp))
            .background(CARD.copy(alpha = 0.72f)).border(1.dp, GREEN.copy(alpha = 0.30f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("۶۷,۴۲۰", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+۲,۴٪", color = GREEN, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.width(3.dp)); Text("▲", color = GREEN, fontSize = 9.sp)
            }
        }

        // آیکون‌های چرخان (overlay متن + کلیک‌پذیر — امن)
        val step = 360f / tabs.size
        tabs.forEachIndexed { i, t ->
            val rad = ((i * step + rot).toDouble()) * (PI / 180.0)
            val x = cx + (a * cos(rad)).toFloat()
            val y = cy + (b * sin(rad)).toFloat()
            Box(modifier = Modifier.size(56.dp, 52.dp)
                .offset { IntOffset((x - colHalf).toInt(), (y - orbTopHalf).toInt()) }
                .clickable { onTabClick(t.id) }, contentAlignment = Alignment.TopCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(t.color.copy(alpha = 0.18f)).border(1.dp, t.color.copy(alpha = 0.50f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(t.emoji, fontSize = 16.sp)
                    }
                    Text(t.label, fontSize = 8.sp, color = MUTED, maxLines = 1)
                }
            }
        }
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

// ---------- کارت ادغامی: نبض + گیج + جستجو (بدون BTC) ----------
private data class MockCoin(val symbol: String, val price: String, val change: String, val up: Boolean)

@Composable
private fun InsightCard(onTabClick: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    var q by remember { mutableStateOf("") }
    val coins = remember { listOf(
        MockCoin("BTC", "$67,420", "+2.4%", true), MockCoin("ETH", "$3,569", "+0.65%", true),
        MockCoin("SOL", "$154", "-1.2%", false), MockCoin("BNB", "$612", "+0.9%", true),
        MockCoin("XRP", "$0.54", "-0.3%", false), MockCoin("PEPE", "$0.000011", "+8.1%", true)
    ) }
    val res = if (q.isBlank()) emptyList() else coins.filter { it.symbol.contains(q, true) }
    Column(modifier = Modifier.fillMaxWidth().clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF0E2A26).copy(alpha = 0.90f), Color(0xFF0A1512).copy(alpha = 0.94f))))
        .border(1.2.dp, Brush.linearGradient(listOf(GREEN.copy(alpha = 0.70f), GREEN.copy(alpha = 0.10f))), shape)
        .padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("نبض بازار", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(9.dp))
            WaveMini(Modifier.weight(1f).height(15.dp))
            Spacer(Modifier.width(9.dp))
            Text("متعادل", color = MUTED, fontSize = 8.sp)
            Spacer(Modifier.width(5.dp)); Text("❮", color = MUTED, fontSize = 10.sp)
        }
        Hairline()
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

// ---------- توابع کمکی (سبزِ قبلی) ----------
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

// ---------- ابزارها ----------
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
