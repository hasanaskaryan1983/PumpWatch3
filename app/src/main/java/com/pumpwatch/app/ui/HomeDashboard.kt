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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// داشبورد استودیو v7 — نمای مداری حذف؛ نبض واقعی بالا؛ سوئیچ داخل هدر زیر لوگو.
// MarketPulseHeader هم‌پکیج است -> بدون import. importهای بی‌مصرفِ انیمیشن/math حذف شدند.

private val ORANGE = Color(0xFFE8833A)
private val GREEN  = Color(0xFF34D399)
private val TEAL   = Color(0xFF2DD4BF)
private val CYAN   = Color(0xFF22D3EE)
private val YELLOW = Color(0xFFFBBF24)

private val BG    = Color(0xFF070F0D)
private val CARD  = Color(0xFF0E1F1B)
private val MUTED = Color(0xFF8B949E)

@Composable
fun HomeDashboard(onTabClick: (String) -> Unit) {
    var futures by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().background(BG)) {
        TopHeader(futures) { futures = it }          // لوگو + سوئیچ، بالا
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            MarketPulseHeader()                       // نبض واقعی، اولین بلوک
            TitleBlock(futures)
            if (futures) FuturesTools(onTabClick) else SpotTools(onTabClick)
            Spacer(Modifier.height(4.dp))
        }
        BottomBar()
    }
}

// ---------- هدر: ردیف ۱ لوگو+وضعیت، ردیف ۲ سوئیچ ----------
@Composable
private fun TopHeader(futures: Boolean, onChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(Modifier.height(8.dp))
        ModeSwitch(futures, onChange)
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
