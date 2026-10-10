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
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val r: Float, val a: Float)

data class TabItem(val id: String, val emoji: String, val label: String, val color: Color)
data class CoinItem(val symbol: String, val color: Color)

private const val PI_D: Double = Math.PI

@Composable
fun OrbitalHub(onTabClick: (String) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(30000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "outerRing"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(20000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "innerRing"
    )

    val tabs = remember {
        listOf(
            TabItem("market", "📊", "بازار", Color(0xFF22D3EE)),
            TabItem("alerts_spot", "🔔", "هشدار", Color(0xFFFB4D6D)),
            TabItem("whale", "🐳", "نهنگ", Color(0xFF06B6D4)),
            TabItem("meme", "🐸", "میم", Color(0xFFF59E0B)),
            TabItem("wallet", "👛", "کیف", Color(0xFFFFC107)),
            TabItem("assistant", "🤖", "دستیار", Color(0xFFA855F7)),
            TabItem("backtest", "🧪", "بک‌تست", Color(0xFF10B981)),
            TabItem("traders", "🏆", "تریدرها", Color(0xFFFF9500))
        )
    }

    val coins = remember {
        listOf(
            CoinItem("BTC", Color(0xFFF7931A)),
            CoinItem("ETH", Color(0xFF627EEA)),
            CoinItem("SOL", Color(0xFF14F195)),
            CoinItem("BNB", Color(0xFFF3BA2F)),
            CoinItem("XRP", Color(0xFF8B949E)),
            CoinItem("SUI", Color(0xFF4DA2FF)),
            CoinItem("ADA", Color(0xFF5F7CF9)),
            CoinItem("PEPE", Color(0xFF4C9F3B))
        )
    }

    val stars = remember {
        List(90) { Star(Random.nextFloat(), Random.nextFloat(), 1f + Random.nextFloat() * 1.8f, 0.15f + Random.nextFloat() * 0.5f) }
    }

    val density = LocalDensity.current
    val iconPx = with(density) { 26.dp.toPx() }
    val coinPx = with(density) { 16.dp.toPx() }
    val centerPx = with(density) { 44.dp.toPx() }
    val labelOffPx = with(density) { 36.dp.toPx() }
    val labelTextPx = with(density) { 11.sp.toPx() }
    val strokePx = with(density) { 1.5.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF141426), Color(0xFF101020), Color(0xFF0a0a14))))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = tap.x - cx
                        val dy = tap.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        val outerR = minOf(size.width, size.height) * 0.32f
                        if (abs(dist - outerR) < iconPx * 1.6f) {
                            val deg = atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI_D)
                            val adj = (deg - outerRotation.toDouble() + 360.0) % 360.0
                            val step = 360.0 / tabs.size
                            val idx = ((adj / step).toInt() % tabs.size + tabs.size) % tabs.size
                            onTabClick(tabs[idx].id)
                        }
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val minDim = minOf(size.width, size.height)

            // ستاره‌ها (ثابت، بدون سوسو زدن)
            stars.forEach { s ->
                drawCircle(Color.White.copy(alpha = s.a), radius = s.r, center = Offset(s.x * size.width, s.y * size.height))
            }

            // حلقه‌های مداری
            val outerR = minDim * 0.32f
            val innerR = minDim * 0.19f
            drawCircle(Color.White.copy(alpha = 0.16f), radius = outerR, style = Stroke(width = strokePx))
            drawCircle(Color.White.copy(alpha = 0.12f), radius = innerR, style = Stroke(width = strokePx))

            // تابع محلی: گره شیشه‌ای
            fun node(x: Float, y: Float, r: Float, color: Color) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent), center = Offset(x, y), radius = r * 2.1f),
                    radius = r * 2.1f, center = Offset(x, y)
                )
                drawCircle(color.copy(alpha = 0.26f), radius = r, center = Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.45f), radius = r, style = Stroke(width = strokePx), center = Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.18f), radius = r * 0.42f, center = Offset(x - r * 0.28f, y - r * 0.34f))
            }

            // تب‌ها (حلقه بیرونی)
            val step = 360f / tabs.size
            tabs.forEachIndexed { i, t ->
                val rad = ((i * step + outerRotation).toDouble()) * (PI_D / 180.0)
                val x = cx + (outerR.toDouble() * cos(rad)).toFloat()
                val y = cy + (outerR.toDouble() * sin(rad)).toFloat()
                node(x, y, iconPx, t.color)

                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        textSize = iconPx * 1.05f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(t.emoji, x, y + iconPx * 0.36f, p)
                }

                val lx = cx + ((outerR + labelOffPx).toDouble() * cos(rad)).toFloat()
                val ly = cy + ((outerR + labelOffPx).toDouble() * sin(rad)).toFloat()
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        textSize = labelTextPx
                        color = android.graphics.Color.argb(200, 230, 237, 243)
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(t.label, lx, ly + labelTextPx * 0.35f, p)
                }
            }

            // ارزها (حلقه درونی)
            val cStep = 360f / coins.size
            coins.forEachIndexed { i, c ->
                val rad = ((i * cStep + innerRotation).toDouble()) * (PI_D / 180.0)
                val x = cx + (innerR.toDouble() * cos(rad)).toFloat()
                val y = cy + (innerR.toDouble() * sin(rad)).toFloat()
                node(x, y, coinPx, c.color)

                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        textSize = coinPx * 0.62f
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawText(c.symbol, x, y + coinPx * 0.22f, p)
                }
            }

            // گوی مرکزی طلایی
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFE066), Color(0xFFFFC107), Color(0xFFF59E0B).copy(alpha = 0.7f), Color.Transparent),
                    center = Offset(cx, cy), radius = centerPx * 1.5f
                ),
                radius = centerPx * 1.5f, center = Offset(cx, cy)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFD700), Color(0xFFB8860B)), center = Offset(cx - centerPx * 0.3f, cy - centerPx * 0.3f), radius = centerPx * 1.4f),
                radius = centerPx, center = Offset(cx, cy)
            )
            drawCircle(Color.White.copy(alpha = 0.5f), radius = centerPx, style = Stroke(width = strokePx), center = Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.25f), radius = centerPx * 0.35f, center = Offset(cx - centerPx * 0.3f, cy - centerPx * 0.35f))

            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    textSize = centerPx * 1.0f
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawText("₿", cx, cy + centerPx * 0.35f, p)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.BottomCenter) {
            Text("روی آیکون‌های چرخان ضربه بزن", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
        }
    }
}
