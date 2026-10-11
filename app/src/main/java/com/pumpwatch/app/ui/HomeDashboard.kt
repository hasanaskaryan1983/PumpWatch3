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

// داشبورد استودیو — مطابق دو تصویر مرجع (رابط تخت). خودبسنده؛ به هیچ فایل دیگر پروژه وابسته نیست.

private val ORANGE = Color(0xFFE8833A)
private val GREEN  = Color(0xFF34D399)
private val TEAL   = Color(0xFF2DD4BF)
private val CYAN   = Color(0xFF22D3EE)
private val YELLOW = Color(0xFFFBBF24)
private const val PI_F = 3.14159265f

private val BG    = Color(0xFF081210)
private val CARD  = Color(0xFF0E1F1B)
private val MUTED = Color(0xFF8B949E)
private val DIM   = Color(0xFF6B7280)

@Composable
fun HomeDashboard(onTabClick: (String) -> Unit) {
    var futures by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().background(BG)) {
        TopHeader()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ModeSwitch(futures) { futures = it }
            TitleBlock(futures)
            HeroCard(futures)
            PulseCard(futures)
            if (futures) FuturesTools(onTabClick) else SpotTools(onTabClick)
            Spacer(Modifier.height(8.dp))
        }
        BottomBar()
    }
}

@Composable
private fun TopHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⚙️", fontSize = 18.sp, color = MUTED)
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(GREEN))
            Spacer(Modifier.width(6.dp))
            Text("بازار فعال", fontSize = 11.sp, color = MUTED)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("pump", fontSize = 18.sp, fontWeight = FontWeight.Black, color = GREEN)
            Text("dump", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ORANGE)
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
            val h = size.height * f
            drawRect(cs[i], topLeft = Offset(i * bw + bw * 0.15f, size.height - h),
                size = androidx.compose.ui.geometry.Size(bw * 0.7f, h))
        }
    }
}

@Composable
private fun ModeSwitch(futures: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(modifier = Modifier.fillMaxWidth().clip(shape).background(CARD).border(1.dp, Color.White.copy(alpha = 0.06f), shape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ModeSeg("اسپات", !futures) { onChange(false) }
        ModeSeg("فیوچرز", futures) { onChange(true) }
    }
}

@Composable
private fun RowScope.ModeSeg(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Box(modifier = Modifier.weight(1f).clip(shape).background(if (active) ORANGE else Color.Transparent).clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (active) Color.Black else MUTED, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun TitleBlock(futures: Boolean) {
    Column {
        Text(if (futures) "استودیوی فیوچرز" else "استودیوی بازار", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(if (futures) "داده و ابزارهای مدیریت ریسک، یکجا" else "داده، ابزار، دید بهتر برای تصمیم‌های هوشمندتر", fontSize = 12.sp, color = MUTED)
    }
}

@Composable
private fun HeroCard(futures: Boolean) {
    val shape = RoundedCornerShape(22.dp)
    val pts = remember { listOf(.10f,.20f,.15f,.28f,.23f,.36f,.31f,.46f,.41f,.54f,.49f,.63f,.58f,.71f,.66f,.80f,.75f,.90f,.85f,1f) }
    Box(modifier = Modifier.fillMaxWidth().shadow(14.dp, shape, clip = false, ambientColor = GREEN.copy(alpha = .30f), spotColor = GREEN.copy(alpha = .45f))) {
        Column(modifier = Modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF0E2A26).copy(alpha = .96f), Color(0xFF0A1512).copy(alpha = .98f)))).border(1.2.dp, Brush.linearGradient(listOf(GREEN.copy(alpha = .85f), GREEN.copy(alpha = .15f))), shape).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BtcBadge()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("BTC/USDT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (futures) "Perpetual · نمونهٔ طراحی" else "Bitcoin", color = MUTED, fontSize = 11.sp)
                }
                Chip(if (futures) "دادهٔ نمونه" else "داده بازار", GREEN)
                Spacer(Modifier.width(8.dp))
                Text("24h ▾", color = MUTED, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("۶۷,۴۲۰", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+۲,۴٪", color = GREEN, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp)); Text("▲", color = GREEN, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            ChartSpark(pts, GREEN, TEAL, Modifier.fillMaxWidth().height(80.dp))
            Spacer(Modifier.height(8.dp))
            Text("(نمونهٔ طراحی · داده واقعی نیست)", color = DIM, fontSize = 9.sp)
        }
    }
}

@Composable
private fun BtcBadge(modifier: Modifier = Modifier.size(38.dp)) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f; val cy = size.height / 2f; val rad = size.minDimension / 2f
        drawCircle(Color(0xFFF7931A), radius = rad, center = Offset(cx, cy))
        drawContext.canvas.nativeCanvas.apply {
            val p = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE; textSize = rad * 1.24f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawText("₿", cx, cy + rad * 0.44f, p)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    val s = RoundedCornerShape(20.dp)
    Box(Modifier.clip(s).background(color.copy(alpha = .12f)).border(1.dp, color.copy(alpha = .4f), s).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp)); Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
        drawPath(lp, line, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val ly = pad + (1f - points.last()) * uh
        drawCircle(line.copy(alpha = .3f), 8f, center = Offset(w, ly))
        drawCircle(line, 4f, center = Offset(w, ly))
    }
}

@Composable
private fun PulseCard(futures: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(CARD).border(1.dp, Color.White.copy(alpha = .06f), shape)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("نبض بازار", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            WaveMini(Modifier.weight(1f).height(34.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (futures) "لیکوئیدیشن · 01 · Funding" else "آرام امروز\nپرانرژی فردا", color = MUTED, fontSize = 9.sp, lineHeight = 12.sp)
            Spacer(Modifier.width(6.dp)); Text("❮", color = MUTED, fontSize = 12.sp)
        }
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
        drawPath(pg, GREEN, style = Stroke(2f, cap = StrokeCap.Round))
        drawPath(po, ORANGE, style = Stroke(2f, cap = StrokeCap.Round))
        for (k in 0..6) {
            val vx = w * k / 6f
            drawLine(ORANGE.copy(alpha = .5f), Offset(vx, mid - h * .32f), Offset(vx, mid + h * .32f), strokeWidth = 1.5f)
        }
    }
}

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
    SectionTitle("کشف فرصت", "ابزارهای فیوچرز شما برای معامله‌های هوشمندتر", GREEN, listOf(
        ToolBig("سیگنال", "📶", "از داده تا ایدهٔ معاملاتی", "futures_dash", GREEN),
        ToolBig("اسکنر", "🎯", "فرصت‌ها را پیدا کن", "futures_scan", GREEN)
    ), onTabClick)
    SectionTitle("پایش و تمرین", "مدیریت ریسک در عمل، بدون حدس و گمان", ORANGE, listOf(
        ToolBig("هشدار", "🔔", "هیچ موقعیتی را از دست نده", "alerts_fut", ORANGE),
        ToolBig("Paper", "🎓", "معامله آزمایشی", "paper", ORANGE)
    ), onTabClick)
    SectionTitle("تحلیل عملکرد", "از داده تا پیشرفت، حرفه‌ای معامله کن", CYAN, listOf(
        ToolBig("ژورنال", "📄", "از معاملاتت یاد بگیر", "journal", CYAN),
        ToolBig("بک‌تست", "🧪", "ایده‌ها را محک بزن", "backtest", CYAN)
    ), onTabClick)
}

@Composable
private fun SectionTitle(title: String, sub: String, accent: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Text(sub, color = MUTED, fontSize = 10.sp)
    }
}

private data class Tool(val label: String, val emoji: String, val id: String, val active: Boolean = false)

@Composable
private fun ToolGroup(title: String, sub: String, accent: Color, tools: List<Tool>, onTabClick: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = Modifier.fillMaxWidth().clip(shape).background(CARD).border(1.dp, accent.copy(alpha = .25f), shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(sub, color = MUTED, fontSize = 9.sp)
            Spacer(Modifier.width(6.dp)); Text("❮", color = accent, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.forEach { t -> ToolCell(t, accent, onTabClick) }
        }
    }
}

@Composable
private fun RowScope.ToolCell(t: Tool, accent: Color, onTabClick: (String) -> Unit) {
    val s = RoundedCornerShape(12.dp)
    Column(modifier = Modifier.weight(1f).clip(s).background(if (t.active) accent.copy(alpha = .16f) else Color(0xFF0A1714)).border(1.dp, if (t.active) accent.copy(alpha = .6f) else Color.White.copy(alpha = .05f), s).clickable { onTabClick(t.id) }.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t.emoji, fontSize = 20.sp)
        Text(t.label, color = if (t.active) accent else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private data class ToolBig(val label: String, val emoji: String, val desc: String, val id: String, val accent: Color)

@Composable
private fun RowScope.ToolBig(t: ToolBig, onTabClick: (String) -> Unit) {
    val s = RoundedCornerShape(14.dp)
    Row(modifier = Modifier.weight(1f).clip(s).background(CARD).border(1.dp, t.accent.copy(alpha = .3f), s).clickable { onTabClick(t.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(t.accent.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Text(t.emoji, fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(t.desc, color = MUTED, fontSize = 9.sp)
        }
        Text("❮", color = t.accent, fontSize = 13.sp)
    }
}

@Composable
private fun BottomBar() {
    val items = listOf("خانه" to "🏠", "دیتا" to "📊", "اخبار" to "📰", "پروفایل" to "👤")
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A1512)).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEachIndexed { i, (label, emoji) ->
            val active = i == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 18.sp, color = if (active) ORANGE else MUTED)
                Text(label, fontSize = 9.sp, color = if (active) ORANGE else MUTED, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
