package com.pumpwatch.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val r: Float, val a: Float)
data class TabItem(val id: String, val emoji: String, val label: String, val color: Color)
data class CoinItem(val symbol: String, val color: Color)

private const val PI_D: Double = Math.PI

@Composable
fun OrbitalHub(onTabClick: (String) -> Unit) {
    val inf = rememberInfiniteTransition(label = "rot")
    val outerRot by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart), label = "o")
    val innerRot by inf.animateFloat(360f, 0f,
        infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart), label = "i")

    // 🚀 Commit 165: ۱۰ تب — واچ‌لیست و معاملات اضافه شدند
    val tabs = remember { listOf(
        TabItem("market", "📊", "بازار", Color(0xFF22D3EE)),
        TabItem("watchlist", "⭐", "واچ‌لیست", Color(0xFF10B981)),
        TabItem("alerts_spot", "🔔", "هشدار", Color(0xFFFB4D6D)),
        TabItem("whale", "🐳", "نهنگ", Color(0xFF06B6D4)),
        TabItem("meme", "🐸", "میم", Color(0xFFF59E0B)),
        TabItem("wallet", "👛", "کیف", Color(0xFFFFC107)),
        TabItem("trades", "📈", "معاملات", Color(0xFF00E676)),
        TabItem("assistant", "🤖", "دستیار", Color(0xFFA855F7)),
        TabItem("backtest", "🧪", "بک‌تست", Color(0xFF10B981)),
        TabItem("traders", "🏆", "تریدرها", Color(0xFFFF9500))
    ) }
    val coins = remember { listOf(
        CoinItem("BTC", Color(0xFFF7931A)), CoinItem("ETH", Color(0xFF627EEA)),
        CoinItem("SOL", Color(0xFF14F195)), CoinItem("BNB", Color(0xFFF3BA2F)),
        CoinItem("XRP", Color(0xFF8B949E)), CoinItem("SUI", Color(0xFF4DA2FF)),
        CoinItem("ADA", Color(0xFF5F7CF9)), CoinItem("PEPE", Color(0xFF4C9F3B))
    ) }
    val stars = remember { List(90) { Star(Random.nextFloat(), Random.nextFloat(), 1f + Random.nextFloat()*1.6f, 0.12f + Random.nextFloat()*0.45f) } }

    val d = LocalDensity.current
    val iconPx = with(d) { 20.dp.toPx() }      // کوچک‌تر برای ۱۰ آیکون
    val coinPx = with(d) { 12.dp.toPx() }
    val centerPx = with(d) { 32.dp.toPx() }    // 🚀 گوی مرکزی کوچک‌تر
    val labelOff = with(d) { 24.dp.toPx() }
    val labelPx = with(d) { 10.sp.toPx() }
    val strokePx = with(d) { 1.5.dp.toPx() }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF16162B), Color(0xFF101020), Color(0xFF08080F))))) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { tap ->
                val cx = size.width/2f; val cy = size.height/2f
                val dx = tap.x - cx; val dy = tap.y - cy
                val minDim = minOf(size.width, size.height)
                val a = minDim*0.36f; val b = a*0.50f      // 🚀 حلقه بزرگ‌تر + کمتر مورب
                val nx = dx/a; val ny = dy/b
                val rr = sqrt(nx*nx + ny*ny)
                if (abs(rr - 1f) < 0.32f) {
                    val tDeg = atan2((dy/b).toDouble(), (dx/a).toDouble()) * (180.0/PI_D)
                    val adj = (tDeg - outerRot.toDouble() + 360.0) % 360.0
                    val step = 360.0/tabs.size
                    val idx = (((adj/step).roundToInt() % tabs.size) + tabs.size) % tabs.size
                    onTabClick(tabs[idx].id)
                }
            }
        }) {
            val cx = size.width/2f; val cy = size.height/2f
            val minDim = minOf(size.width, size.height)
            val a = minDim*0.36f; val b = a*0.50f          // 🚀 بیرونی
            val ia = minDim*0.21f; val ib = ia*0.50f       // درونی

            stars.forEach { s -> drawCircle(Color.White.copy(alpha=s.a), radius=s.r, center=Offset(s.x*size.width, s.y*size.height)) }

            drawOval(Color.White.copy(alpha=0.18f), style=Stroke(width=strokePx), topLeft=Offset(cx-a, cy-b), size=Size(a*2f, b*2f))
            drawOval(Color.White.copy(alpha=0.10f), style=Stroke(width=strokePx*2f), topLeft=Offset(cx-a, cy-b+2f), size=Size(a*2f, b*2f))
            drawOval(Color.White.copy(alpha=0.12f), style=Stroke(width=strokePx), topLeft=Offset(cx-ia, cy-ib), size=Size(ia*2f, ib*2f))

            fun node(x: Float, y: Float, r: Float, color: Color, depth: Float) {
                val sc = 0.72f + 0.28f*((depth+1f)/2f)
                val al = 0.55f + 0.45f*((depth+1f)/2f)
                val rr = r*sc
                drawCircle(brush=Brush.radialGradient(listOf(color.copy(alpha=0.32f*al), Color.Transparent), center=Offset(x,y), radius=rr*2.2f), radius=rr*2.2f, center=Offset(x,y))
                drawCircle(color.copy(alpha=0.30f*al), radius=rr, center=Offset(x,y))
                drawCircle(Color.White.copy(alpha=0.5f*al), radius=rr, style=Stroke(width=strokePx), center=Offset(x,y))
                drawCircle(Color.White.copy(alpha=0.22f*al), radius=rr*0.4f, center=Offset(x-rr*0.3f, y-rr*0.34f))
            }

            // تب‌ها (۱۰ تا) — دور اول، نزدیک بعد
            val step = 360f/tabs.size
            val order = tabs.indices.sortedBy { i -> sin(((i*step+outerRot).toDouble())*(PI_D/180.0)) }
            order.forEach { i ->
                val t = tabs[i]
                val rad = ((i*step+outerRot).toDouble())*(PI_D/180.0)
                val x = cx + (a.toDouble()*cos(rad)).toFloat()
                val y = cy + (b.toDouble()*sin(rad)).toFloat()
                val depth = sin(rad).toFloat()
                node(x, y, iconPx, t.color, depth)
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply { textSize=iconPx*1.0f; textAlign=android.graphics.Paint.Align.CENTER; isAntiAlias=true }
                    drawText(t.emoji, x, y+iconPx*0.34f, p)
                }
                val lx = cx + ((a+labelOff).toDouble()*cos(rad)).toFloat()
                val ly = cy + ((b+labelOff*0.45).toDouble()*sin(rad)).toFloat()
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply { textSize=labelPx; color=android.graphics.Color.argb((150+105*((depth+1f)/2f)).toInt(), 230,237,243); textAlign=android.graphics.Paint.Align.CENTER; isAntiAlias=true }
                    drawText(t.label, lx, ly+labelPx*0.35f, p)
                }
            }

            // ارزها (حلقه درونی)
            val cStep = 360f/coins.size
            val cOrder = coins.indices.sortedBy { i -> sin(((i*cStep+innerRot).toDouble())*(PI_D/180.0)) }
            cOrder.forEach { i ->
                val c = coins[i]
                val rad = ((i*cStep+innerRot).toDouble())*(PI_D/180.0)
                val x = cx + (ia.toDouble()*cos(rad)).toFloat()
                val y = cy + (ib.toDouble()*sin(rad)).toFloat()
                node(x, y, coinPx, c.color, sin(rad).toFloat())
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply { textSize=coinPx*0.62f; color=android.graphics.Color.WHITE; textAlign=android.graphics.Paint.Align.CENTER; isAntiAlias=true; typeface=android.graphics.Typeface.DEFAULT_BOLD }
                    drawText(c.symbol, x, y+coinPx*0.2f, p)
                }
            }

            // 🚀 گوی مرکزی کوچک‌تر (۳۲dp)
            drawCircle(brush=Brush.radialGradient(listOf(Color(0xFFFFE066),Color(0xFFFFC107),Color(0xFFF59E0B).copy(alpha=0.7f),Color.Transparent), center=Offset(cx,cy), radius=centerPx*1.6f), radius=centerPx*1.6f, center=Offset(cx,cy))
            drawCircle(brush=Brush.radialGradient(listOf(Color(0xFFFFD700),Color(0xFFB8860B)), center=Offset(cx-centerPx*0.3f,cy-centerPx*0.3f), radius=centerPx*1.4f), radius=centerPx, center=Offset(cx,cy))
            drawCircle(Color.White.copy(alpha=0.5f), radius=centerPx, style=Stroke(width=strokePx), center=Offset(cx,cy))
            drawCircle(Color.White.copy(alpha=0.25f), radius=centerPx*0.34f, center=Offset(cx-centerPx*0.3f,cy-centerPx*0.34f))
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply { textSize=centerPx*0.95f; color=android.graphics.Color.WHITE; textAlign=android.graphics.Paint.Align.CENTER; isAntiAlias=true; typeface=android.graphics.Typeface.DEFAULT_BOLD }
                drawText("₿", cx, cy+centerPx*0.33f, p)
            }
        }
        Box(Modifier.fillMaxSize().padding(bottom=58.dp), contentAlignment=Alignment.BottomCenter) {
            Text("روی آیکون‌های چرخان ضربه بزن", fontSize=12.sp, color=Color.White.copy(alpha=0.5f), textAlign=TextAlign.Center)
        }
    }
}
