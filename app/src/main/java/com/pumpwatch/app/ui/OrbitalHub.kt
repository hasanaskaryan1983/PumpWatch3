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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

//  Commit 162: هاب مداری واقعی — منظومه شمسی با حلقه‌های شفاف و آیکون‌های واقعی

data class TabItem(val id: String, val emoji: String, val label: String, val color: Color)
data class CoinItem(val symbol: String, val color: Color)

@Composable
fun OrbitalHub(onTabClick: (String) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRing"
    )
    
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRing"
    )

    val tabs = remember {
        listOf(
            TabItem("market", "", "بازار", Color(0xFF22D3EE)),
            TabItem("alerts_spot", "", "هشدار", Color(0xFFFB4D6D)),
            TabItem("whale", "", "نهنگ", Color(0xFF06B6D4)),
            TabItem("meme", "", "میم", Color(0xFFF59E0B)),
            TabItem("wallet", "", "کیف", Color(0xFFFFC107)),
            TabItem("assistant", "", "دستیار", Color(0xFFA855F7)),
            TabItem("backtest", "", "بک‌تست", Color(0xFF10B981)),
            TabItem("traders", "", "تریدرها", Color(0xFFFF9500))
        )
    }

    val coins = remember {
        listOf(
            CoinItem("BTC", Color(0xFFF7931A)),
            CoinItem("ETH", Color(0xFF627EEA)),
            CoinItem("SOL", Color(0xFF00FFA3)),
            CoinItem("BNB", Color(0xFFF3BA2F)),
            CoinItem("XRP", Color(0xFF23292F)),
            CoinItem("SUI", Color(0xFF4DA2FF)),
            CoinItem("ADA", Color(0xFF0033AD)),
            CoinItem("PEPE", Color(0xFF4C9F3B))
        )
    }

    val stars = remember {
        List(100) { 
            Triple(
                (0..1000).random() / 1000f,
                (0..1000).random() / 1000f,
                (0.5f..2f).random()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF16213e),
                        Color(0xFF0f0f1e)
                    ),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY
                )
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val dx = tapOffset.x - centerX
                        val dy = tapOffset.y - centerY
                        val distance = sqrt(dx * dx + dy * dy)
                        val minDim = minOf(size.width, size.height)
                        val outerRadius = minDim * 0.32f
                        val iconSize = 28.dp.toPx()
                        
                        if (abs(distance - outerRadius) < iconSize * 1.5f) {
                            var angle = atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)
                            angle = (angle - outerRotation + 360) % 360
                            val angleStep = 360f / tabs.size
                            val index = ((angle / angleStep).roundToInt() % tabs.size + tabs.size) % tabs.size
                            onTabClick(tabs[index].id)
                        }
                    }
                }
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val minDim = minOf(size.width, size.height)

            // ستاره‌ها
            stars.forEach { (x, y, r) ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f + (0..70).random() / 100f * 0.5f),
                    radius = r.dp.toPx() * 0.5f,
                    center = Offset(x * size.width, y * size.height)
                )
            }

            // حلقه بیرونی
            val outerRadius = minDim * 0.32f
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = outerRadius,
                style = Stroke(width = 2.dp.toPx())
            )

            // حلقه درونی
            val innerRadius = minDim * 0.18f
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = innerRadius,
                style = Stroke(width = 2.dp.toPx())
            )

            // آیکون‌های تب (حلقه بیرونی)
            val angleStep = 360f / tabs.size
            tabs.forEachIndexed { i, tab ->
                val angle = (i * angleStep + outerRotation) * (PI / 180.0f)
                val x = centerX + (outerRadius * cos(angle).toFloat())
                val y = centerY + (outerRadius * sin(angle).toFloat())
                val iconSize = 28.dp.toPx()

                // Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tab.color.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(x, y),
                        radius = iconSize * 1.5f
                    ),
                    radius = iconSize * 1.5f,
                    center = Offset(x, y)
                )

                // دایره آیکون
                drawCircle(
                    color = tab.color.copy(alpha = 0.9f),
                    radius = iconSize,
                    center = Offset(x, y)
                )

                // حاشیه سفید
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = iconSize,
                    style = Stroke(width = 1.5.dp.toPx()),
                    center = Offset(x, y)
                )

                // Emoji
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = iconSize * 1.2f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(tab.emoji, x, y + iconSize * 0.35f, paint)
                }

                // برچسب متنی
                val labelX = centerX + ((outerRadius + 40.dp.toPx()) * cos(angle).toFloat())
                val labelY = centerY + ((outerRadius + 40.dp.toPx()) * sin(angle).toFloat())
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = 11.sp.toPx()
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawText(tab.label, labelX, labelY, paint)
                }
            }

            // ارزها (حلقه درونی)
            val coinAngleStep = 360f / coins.size
            coins.forEachIndexed { i, coin ->
                val angle = (i * coinAngleStep + innerRotation) * (PI / 180.0f)
                val x = centerX + (innerRadius * cos(angle).toFloat())
                val y = centerY + (innerRadius * sin(angle).toFloat())
                val coinSize = 20.dp.toPx()

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(coin.color.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(x, y),
                        radius = coinSize * 1.3f
                    ),
                    radius = coinSize * 1.3f,
                    center = Offset(x, y)
                )

                drawCircle(
                    color = coin.color.copy(alpha = 0.85f),
                    radius = coinSize,
                    center = Offset(x, y)
                )

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = coinSize * 0.9f
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawText(coin.symbol, x, y + coinSize * 0.3f, paint)
                }
            }

            // گوی مرکزی طلایی با ₿
            val centerSize = 60.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFE066),
                        Color(0xFFFFD700),
                        Color(0xFFFFA500),
                        Color(0xFFFF8C00).copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = centerSize
                ),
                radius = centerSize,
                center = Offset(centerX, centerY)
            )

            // حاشیه درخشان گوی مرکزی
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = centerSize,
                style = Stroke(width = 2.dp.toPx()),
                center = Offset(centerX, centerY)
            )

            // نماد ₿ در مرکز
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    textSize = centerSize * 0.9f
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawText("₿", centerX, centerY + centerSize * 0.3f, paint)
            }
        }

        // متن راهنما
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                "روی آیکون‌های چرخان ضربه بزن",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
