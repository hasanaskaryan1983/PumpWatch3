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

// داشبورد استودیو v3 — سه کارت در یک کادر، جمع‌وجور، پس‌زمینهٔ کهکشانی.
// توابع کمکی عیناً از نسخهٔ سبز قبلی؛ فقط چیدمان + SkyBackdrop عوض شده.

private val ORANGE = Color(0xFFE8833A)
private val GREEN  = Color(0xFF34D399)
private val TEAL   = Color(0xFF2DD4BF)
private val CYAN   = Color(0xFF22D3EE)
private val YELLOW = Color(0xFFFBBF24)
private val RED    = Color(0xFFF87171)
private val PURPLE = Color(0xFF7C5CFF)
private const val PI_F = 3.14159265f

private val BG    = Color(0xFF070F0D)
private val CARD  = Color(0xFF0E1F1B)
private val MUTED = Color(0xFF8B949E)
private val DIM   = Color(0xFF6B7280)

@Composable
fun HomeDashboard(onTabClick: (String) -> Unit) {
    var futures by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(BG)) {
        SkyBackdrop(modifier = Modifier.fillMaxWidth().height(360.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader()
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeSwitch(futures) { futures = it }
                TitleBlock(futures)
                UnifiedMarketCard(futures, onTabClick)
                if (futures) FuturesTools(onTabClick) else SpotTools(onTabClick)
                Spacer(Modifier.height(6.dp))
            }
            BottomBar()
        }
    }
}

// ---------- پس‌زمینهٔ کهکشانی/غروب ----------
@Composable
private fun SkyBackdrop(modifier: Modifier = Modifier) {
    val stars = remember {
        List(70) { Triple(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat() * 1.1f) }
    }
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        // آسمان: سرمه‌ای بالا → غروب میانی → تیره پایین
        drawRect(Brush.verticalGradient(listOf(
            Color(0xFF060B14), Color(0xFF0B1320), Color(0xFF1A1330), Color(0xFF2A1A22), Color(0xFF120E12)
        )), size = size)
        // کهکشان مورب (چند لکهٔ کم‌رنگ بنفش/آبی در یک قطر)
        val gx = floatArrayOf(0.10f, 0.22f, 0.34f, 0.46f, 0.58f, 0.70f)
        val gy = floatArrayOf(0.18f, 0.26f, 0.34f, 0.42f, 0.50f, 0.58f)
        val gc = listOf(PURPLE, Color(0xFF3B6BFF), PURPLE, Color(0xFF5A8CFF), PURPLE, Color(0xFF3B6BFF))
        gx.forEachIndexed { i, fx ->
            drawCircle(Brush.radialGradient(listOf(gc[i].copy(alpha = 0.10f), Color.Transparent)),
                radius = w * 0.22f, center = Offset(fx * w, gy[i] * h))
        }
        // ستاره‌ها
        stars.forEach { s ->
            drawCircle(Color.White.copy(alpha = 0.25f + s.third * 0.35f), radius = s.third,
                center = Offset(s.first * w, s.second * h * 0.7f))
        }
        // خورشید با هالهٔ دوگانه
        val sx = w * 0.72f; val sy = h * 0.40f
        drawCircle(Brush.radialGradient(listOf(ORANGE.copy(alpha = 0.45f), Color.Transparent)), radius = w * 0.34f, center = Offset(sx, sy))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC24D).copy(alpha = 0.85f), ORANGE.copy(alpha = 0.4f), Color.Transparent)), radius = w * 0.13f, center = Offset(sx, sy))
        drawCircle(Color(0xFFFFB347), radius = w * 0.065f, center = Offset(sx, sy))
        // سه لایه کوه + مه بینشان
        val far = Path().apply {
            moveTo(0f, h); lineTo(w * 0.14f, h * 0.50f); lineTo(w * 0.30f, h * 0.66f)
            lineTo(w * 0.48f, h * 0.40f); lineTo(w * 0.66f, h * 0.62f); lineTo(w * 0.82f, h * 0.46f)
            lineTo(w, h * 0.70f); lineTo(w, h); close()
        }
        drawPath(far, Color(0xFF0C1410))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0C1410).copy(alpha = 0.55f), Color.Transparent)),
            topLeft = Offset(0f, h * 0.60f), size = Size(w, h * 0.22f))
        val mid = Path().apply {
            moveTo(0f, h); lineTo(w * 0.20f, h * 0.64f); lineTo(w * 0.40f, h * 0.80f)
            lineTo(w * 0.58f, h * 0.58f); lineTo(w * 0.78f, h * 0.78f); lineTo(w, h * 0.62f)
            lineTo(w, h); close()
        }
        drawPath(mid, Color(0xFF080E0B))
        val near = Path().apply {
            moveTo(0f, h); lineTo(w * 0.26f, h * 0.80f); lineTo(w * 0.50f, h * 0.92f)
            lineTo(w * 0.74f, h * 0.78f); lineTo(w, h * 0.90f); lineTo(w, h); close()
        }
        drawPath(near, Color(0xFF040806))
    }
}

// ---------- هدر ----------
@Composable
private fun TopHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⚙️", fontSize = 17.sp, color = MUTED)
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(GREEN))
            Spacer(Modifier.width(6.dp))
            Text("بازار فعال", fontSize = 11.sp, color = MUTED)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("dump", fontSize = 17.sp, fontWeight = FontWeight.Black, color = ORANGE)
            Text("pump", fontSize = 17.sp, fontWeight = FontWeight.Black, color = GREEN)
            Spacer(Modifier.width(6.dp))
            MiniBars()
        }
    }
}

@Composable
private fun MiniBars(modifier: Modifier = Modifier.size(width = 22.dp, height = 18.dp)) {
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
    val shape = RoundedCornerShape(13.dp)
    Row(modifier = Modifier.fillMaxWidth().clip(shape).background(CARD.copy(alpha = 0.80f)).border(1.dp, Color.White.copy(alpha = 0.06f), shape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ModeSeg("اسپات", !futures) { onChange(false) }
        ModeSeg("فیوچرز", futures) { onChange(true) }
    }
}

@Composable
private fun RowScope.ModeSeg(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier = Modifier.weight(1f).clip(shape).background(if (active) ORANGE else Color.Transparent).clickable(onClick = onClick).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (active) Color.Black else MUTED, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable
private fun TitleBlock(futures: Boolean) {
    Column {
        Text(if (futures) "استودیوی فیوچرز" else "استودیوی بازار", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(if (futures) "داده و ابزارهای مدیریت ریسک، یکجا" else "داده، ابزار، دید بهتر برای تصمیم‌های هوشمندتر", fontSize = 11.sp, color = MUTED)
    }
}

// ---------- خط جداکننده ----------
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
}

// ---------- کادر یکپارچهٔ بازار (BTC + نبض + گیج/جستجو) ----------
private data class MockCoin(val symbol: String, val price: String, val change: String, val up: Boolean)

@Composable
private fun UnifiedMarketCard(futures: Boolean, onTabClick: (String) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val pts = remember { listOf(.10f,.20f,.15f,.28f,.23f,.36f,.31f,.46f,.41f,.54f,.49f,.63f,.58f,.71f,.66f,.80f,.75f,.90f,.85f,1f) }
    var q by remember { mutableStateOf("") }
    val coins = remember { listOf(
        MockCoin("BTC", "$67,420", "+2.4%", true),
        MockCoin("ETH", "$3,569", "+0.65%", true),
        MockCoin("SOL", "$154", "-1.2%", false),
        MockCoin("BNB", "$612", "+0.9%", true),
        MockCoin("XRP", "$0.54", "-0.3%", false),
        MockCoin("PEPE", "$0.000011", "+8.1%", true)
    ) }
    val res = if (q.isBlank()) emptyList() else coins.filter { it.symbol.contains(q, true) }

    Box(modifier = Modifier.fillMaxWidth().shadow(12.dp, shape, clip = false, ambientColor = GREEN.copy(alpha = .25f), spotColor = GREEN.copy(alpha = .40f))) {
        Column(modifier = Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0E2A26).copy(alpha = .90f), Color(0xFF0A1512).copy(alpha = .94f))))
            .border(1.2.dp, Brush.linearGradient(listOf(GREEN.copy(alpha = .80f), GREEN.copy(alpha = .12f))), shape)
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── بخش ۱: BTC ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                BtcBadge()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("BTC/USDT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(if (futures) "Perpetual · نمونهٔ طراحی" else "Bitcoin", color = MUTED, fontSize = 10.sp)
                }
                Chip(if (futures) "دادهٔ نمونه" else "داده بازار", GREEN)
                Spacer(Modifier.width(8.dp))
                Text("24h ▾", color = MUTED, fontSize = 10.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("۶۷,۴۲", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Spacer(Modifier.weight(1f))
                Text("+۲,۴٪", color = GREEN, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp)); Text("▲", color = GREEN, fontSize = 11.sp)
            }
            ChartSpark(pts, GREEN, TEAL, Modifier.fillMaxWidth().height(56.dp))

            Hairline()

            // ── بخش ۲: نبض ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نبض بازار", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                WaveMini(Modifier.weight(1f).height(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (futures) "Liq · 01 · Funding" else "متعادل", color = MUTED, fontSize = 9.sp)
                Spacer(Modifier.width(6.dp)); Text("❮", color = MUTED, fontSize = 11.sp)
            }

            Hairline()

            // ── بخش ۳: گیج + تحلیل + جستجو ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    FgGauge(0.64f, GREEN)
                    Text("64", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(GREEN))
                        Spacer(Modifier.width(6.dp))
                        Text("طمع", color = GREEN, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("بازار متعادل — روند صعودی ملایم؛ منتظر تأیید حجم برای شکست مقاومت.", color = MUTED, fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
            TextField(
                value = q, onValueChange = { q = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("🔍 جستجوی ارز (BTC, ETH, SOL)...", fontSize = 12.sp, color = MUTED) },
                shape = RoundedCornerShape(10.dp),
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
                    res.take(4).forEach { c ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onTabClick("market") }.padding(vertical = 5.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.symbol, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                            Text(c.price, color = MUTED, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text(c.change, color = if (c.up) GREEN else RED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---------- توابع کمکی (عیناً از نسخهٔ سبز قبلی) ----------
@Composable
private fun BtcBadge(modifier: Modifier = Modifier.size(36.dp)) {
    Box(modifier = modifier.clip(CircleShape).background(Color(0xFFF7931A)), contentAlignment = Alignment.Center) {
        Text("₿", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    val s = RoundedCornerShape(20.dp)
    Box(Modifier.clip(s).background(color.copy(alpha = .12f)).border(1.dp, color.copy(alpha = .4f), s).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp)); Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Medium)
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
        drawCircle(line.copy(alpha = .3f), 7f, center = Offset(w, ly))
        drawCircle(line, 3.5f, center = Offset(w, ly))
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
        drawPath(pg, GREEN, style = Stroke(1.5f, cap = StrokeCap.Round))
        drawPath(po, ORANGE, style = Stroke(1.5f, cap = StrokeCap.Round))
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

// ---------- ابزارها (عیناً از نسخهٔ سبز قبلی) ----------
@Composable
private fun SpotTools(onTabClick: (String) -> Unit) {
    SectionTitle("ابزارهای منتخب", "مجموعه ابزارهای شما برای تحلیل، پایش و اقدام", ORANGE)
    ToolGroup("دیدن", "نگاه عمیق‌تر به بازار", GREEN, listOf(
        Tool("بازار", "📊", "market", true), Tool("واچ‌لیست", "⭐", "watchlist"), Tool("کیف پول", "👛", "wallet")
    ), onTabClick)
    ToolGroup("پایش", "از حرکت‌ها جلوتر باش", ORANGE, listOf(
        Tool("هشدار", "🔔", "alerts_spot"), Tool("نهنگ", "🐳", "whale"), Tool("تریدرها", "👥", "traders"), Tool("میم", "🐸", "meme")
    ), onTabClick)
    ToolGroup("تحلیل و عمل", "از داده تا اقدام", YELLOW, listOf(
        Tool("دستیار", "🤖", "assistant"), Tool("بک‌تست", "🧪", "backtest"), Tool("معامله", "🔁", "trades")
    ), onTabClick)
}

@Composable
private fun FuturesTools(onTabClick: (String) -> Unit) {
    SectionTitle("کشف فرصت", "ابزارهای فیوچرز شما برای معامله‌های هوشمندتر", GREEN)
    ToolRow(listOf(
        ToolBig("سیگنال", "📶", "از داده تا ایدهٔ معاملاتی", "futures_dash", GREEN),
        ToolBig("اسکنر", "🎯", "فرصت‌ها را پیدا کن", "futures_scan", GREEN)
    ), onTabClick)
    SectionTitle("پایش و تمرین", "مدیریت ریسک در عمل، بدون حدس و گمان", ORANGE)
    ToolRow(listOf(
        ToolBig("هشدار", "🔔", "هیچ موقعیتی را از دست نده", "alerts_fut", ORANGE),
        ToolBig("Paper", "🎓", "معامله آزمایشی", "paper", ORANGE)
    ), onTabClick)
    SectionTitle("تحلیل عملکرد", "از داده تا پیشرفت، حرفه‌ای معامله کن", CYAN)
    ToolRow(listOf(
        ToolBig("ژورنال", "📄", "از معاملاتت یاد بگیر", "journal", CYAN),
        ToolBig("بک‌تست", "🧪", "ایده‌ها را محک بزن", "backtest", CYAN)
    ), onTabClick)
}

@Composable
private fun SectionTitle(title: String, sub: String, accent: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 15.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Text(sub, color = MUTED, fontSize = 10.sp)
    }
}

@Composable
private fun ToolRow(tools: List<ToolBig>, onTabClick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.forEach { ToolBig(it, onTabClick) }
    }
}

private data class Tool(val label: String, val emoji: String, val id: String, val active: Boolean = false)

@Composable
private fun ToolGroup(title: String, sub: String, accent: Color, tools: List<Tool>, onTabClick: (String) -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Column(modifier = Modifier.fillMaxWidth().clip(shape).background(CARD.copy(alpha = 0.88f)).border(1.dp, accent.copy(alpha = .22f), shape).padding(11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(sub, color = MUTED, fontSize = 9.sp)
            Spacer(Modifier.width(6.dp)); Text("❮", color = accent, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.forEach { t -> ToolCell(t, accent, onTabClick) }
        }
    }
}

@Composable
private fun RowScope.ToolCell(t: Tool, accent: Color, onTabClick: (String) -> Unit) {
    val s = RoundedCornerShape(11.dp)
    Column(modifier = Modifier.weight(1f).clip(s).background(if (t.active) accent.copy(alpha = .16f) else Color(0xFF0A1714)).border(1.dp, if (t.active) accent.copy(alpha = .6f) else Color.White.copy(alpha = .05f), s).clickable { onTabClick(t.id) }.padding(vertical = 10.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(t.emoji, fontSize = 19.sp)
        Text(t.label, color = if (t.active) accent else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private data class ToolBig(val label: String, val emoji: String, val desc: String, val id: String, val accent: Color)

@Composable
private fun RowScope.ToolBig(t: ToolBig, onTabClick: (String) -> Unit) {
    val s = RoundedCornerShape(13.dp)
    Row(modifier = Modifier.weight(1f).clip(s).background(CARD.copy(alpha = 0.88f)).border(1.dp, t.accent.copy(alpha = .28f), s).clickable { onTabClick(t.id) }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(t.accent.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Text(t.emoji, fontSize = 15.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(t.desc, color = MUTED, fontSize = 9.sp)
        }
        Text("❮", color = t.accent, fontSize = 12.sp)
    }
}

@Composable
private fun BottomBar() {
    val items = listOf("خانه" to "🏠", "دیتا" to "📊", "اخبار" to "📰", "پروفایل" to "👤")
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A1512).copy(alpha = 0.95f)).padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEachIndexed { i, (label, emoji) ->
            val active = i == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 17.sp, color = if (active) ORANGE else MUTED)
                Text(label, fontSize = 9.sp, color = if (active) ORANGE else MUTED, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
