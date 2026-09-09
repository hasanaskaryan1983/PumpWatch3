package com.pumpwatch.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.R
import kotlin.math.PI
import kotlin.math.sin

private val OBGreen = Color(0xFF00E676)
private val OBTeal = Color(0xFF00BFA5)
private val OBGold = Color(0xFFFFC107)
private val OBGray = Color(0xFF8B949E)
private val OBRed = Color(0xFFFF5252)
private val OBPurple = Color(0xFFB388FF)

private const val TWO_PI_F = 6.2831853f

private data class OBPage(val title: String, val desc: String, val img: Int)

// ================= صفحه اصلی Onboarding =================

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }

    val pages = remember {
        listOf(
            OBPage(
                "لحظه درست رو شکار کن!",
                "هشدارهای زودهنگام پامپ و دامپ، قبل از حرکت بزرگ بازار",
                R.drawable.onb_p1
            ),
            OBPage(
                "شکار نهنگ‌ها",
                "ردپای خرید و فروش نهنگ‌ها رو قبل از حرکت بزرگ بازار دنبال کن",
                R.drawable.onb_p2
            ),
            OBPage(
                "تحلیل مثل حرفه‌ای‌ها",
                "۵ تایم‌فریم + ۸ اندیکاتور + نقاط دقیق ورود، استاپ و هدف",
                R.drawable.onb_p3
            ),
            OBPage(
                "رادار میم‌کوین‌ها",
                "کشف میم‌کوین‌های ترند قبل از پامپ؛ کارآگاه NEXT دنبال سوژه بعدیه",
                R.drawable.onb_p4
            ),
            OBPage(
                "جلوتر از بازار باش",
                "سیگنال‌های طلایی با معیارهای ۵۰ تریدر برتر دنیا",
                R.drawable.onb_p5
            )
        )
    }

    // ---------- انیمیشن‌های سراسری ----------
    val infinite = rememberInfiniteTransition(label = "ob")
    val slow by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "slow")
    val mid by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "mid")
    val fast by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "fast")
    val pulse by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    val swing by infinite.animateFloat(-15f, 15f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "swing")
    val shimmer by infinite.animateFloat(-1f, 2f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "shim")

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07090D))) {

        // ---------- پس‌زمینه گرادیان متحرک ----------
        val c1 = lerpColor(Color(0xFF07090D), Color(0xFF0A1F2E), slow)
        val c2 = lerpColor(Color(0xFF0E2A1E), Color(0xFF1A0E2A), slow)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(c1, c2, Color(0xFF07090D))))
        )

        // ---------- محتوای هر صفحه ----------
        Crossfade(targetState = page, animationSpec = tween(550)) { p ->
            Box(modifier = Modifier.fillMaxSize()) {

                // تصویر قهرمان با تنفس آرام (Ken Burns)
                Image(
                    painter = painterResource(pages[p].img),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.06f + 0.03f * pulse
                            scaleY = 1.06f + 0.03f * pulse
                            translationY = -6f + 12f * mid
                        }
                )

                // scrim تاریک پایین برای خوانایی متن
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to Color(0xCC07090D)
                            )
                        )
                )

                // ---------- افکت‌های اختصاصی هر صفحه ----------
                when (p) {
                    0 -> {
                        FlowingCandles(mid, Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter))
                        BellGlow(pulse, Modifier.fillMaxSize())
                    }
                    1 -> FallingCandles(mid, Modifier.fillMaxSize())
                    2 -> {
                        Scanline(fast, Modifier.fillMaxSize())
                        FlowingCandles(mid, Modifier.fillMaxWidth().height(100.dp).align(Alignment.BottomCenter))
                    }
                    3 -> {
                        SwingLight(swing, Modifier.fillMaxSize())
                        Smoke(mid, Modifier.fillMaxSize())
                    }
                    4 -> {
                        RuneRing(slow, fast, Modifier.fillMaxSize())
                        OrbFlash(fast, Modifier.fillMaxSize())
                    }
                }

                Sparkles(fast, Modifier.fillMaxSize())

                // ---------- عنوان و توضیح ----------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp)
                        .padding(bottom = 168.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        pages[p].title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        pages[p].desc,
                        fontSize = 14.sp,
                        color = Color(0xFFC7D2DC),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // ---------- کنترل‌های پایین ----------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // نقطه‌های متحرک
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    val active = i == page
                    val wd by animateDpAsState(if (active) 26.dp else 8.dp, label = "dot$i")
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(wd)
                            .background(
                                if (active) Brush.horizontalGradient(listOf(OBGreen, OBTeal))
                                else Brush.horizontalGradient(listOf(Color(0xFF3A4450), Color(0xFF3A4450))),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // دکمه اصلی با shimmer
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (page < pages.size - 1) page++ else onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OBGreen),
                    shape = RoundedCornerShape(18.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp)
                ) {
                    Text(
                        if (page < pages.size - 1) "بعدی ←" else "بزن بریم! 🚀",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(18.dp))
                        .graphicsLayer { alpha = 0.5f }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent),
                                startX = shimmer * 600f - 200f,
                                endX = shimmer * 600f
                            )
                        )
                )
            }

            TextButton(onClick = onDone) {
                Text("رد شدن و ورود مستقیم", color = OBGray, fontSize = 12.sp)
            }
        }
    }
}

// ================= افکت‌های نقاشی‌شده (Canvas) =================

// نوار کندل‌های روان افقی (صفحه ۱ و ۳)
@Composable
private fun FlowingCandles(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 26
        val w = size.width
        val h = size.height
        for (i in 0 until n) {
            val base = i.toFloat() / n
            val x = (((base - progress * 0.9f) % 1f) + 1f) % 1f * w
            val up = sin(i * 1.7f) * 0.5f + 0.5f
            val ch = h * (0.25f + 0.55f * up)
            val col = if (i % 3 != 1) OBGreen.copy(alpha = 0.75f) else OBRed.copy(alpha = 0.75f)
            drawLine(col, Offset(x, h - ch - 12f), Offset(x, h - ch), 3f)
            drawRoundRect(col, Offset(x - 5f, h - ch), Size(10f, ch), 3f)
        }
    }
}

// کندل‌های قرمز در حال سقوط (صفحه ۲ — نهنگ می‌بلعه‌شون)
@Composable
private fun FallingCandles(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 14
        val w = size.width
        val h = size.height
        for (i in 0 until n) {
            val base = i.toFloat() / n
            val speed = 0.7f + (i % 4) * 0.18f
            val t = ((base + progress * speed) % 1.2f) - 0.1f
            val x = w * (0.06f + 0.88f * ((i * 0.37f) % 1f))
            val y = t * h
            val ch = h * (0.05f + 0.05f * ((i * 0.53f) % 1f))
            val alpha = when {
                t < 0.1f -> t / 0.1f
                t > 0.85f -> (1.2f - t) / 0.35f
                else -> 1f
            } * 0.8f
            val col = OBRed.copy(alpha = alpha)
            drawLine(col, Offset(x, y - 10f), Offset(x, y), 3f)
            drawRoundRect(col, Offset(x - 5f, y), Size(10f, ch), 3f)
        }
    }
}

// خط اسکن عمودی (صفحه ۳)
@Composable
private fun Scanline(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val y = progress * size.height
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, OBTeal.copy(alpha = 0.22f), Color.Transparent),
                startY = y - 40f,
                endY = y + 40f
            ),
            topLeft = Offset(0f, y - 40f),
            size = Size(size.width, 80f)
        )
    }
}

// هاله پالس‌زن زنگ (صفحه ۱ — گوشه بالا-راست)
@Composable
private fun BellGlow(pulse: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * 0.80f, size.height * 0.24f)
        val r = size.width * 0.22f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(OBGold.copy(alpha = 0.10f + 0.22f * pulse), Color.Transparent),
                center = c,
                radius = r
            ),
            center = c,
            radius = r
        )
    }
}

// مخروط نور چراغ بازجویی که چپ‌وراست تاب می‌خوره (صفحه ۴)
@Composable
private fun SwingLight(swingDeg: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val px = size.width / 2f
        rotate(swingDeg, pivot = Offset(px, 0f)) {
            val path = Path().apply {
                moveTo(px, 0f)
                lineTo(px - size.width * 0.30f, size.height * 0.72f)
                lineTo(px + size.width * 0.30f, size.height * 0.72f)
                close()
            }
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.72f
                )
            )
        }
        drawCircle(OBGold.copy(alpha = 0.25f), 46f, Offset(px, 8f))
    }
}

// دود سیگار بالارونده (صفحه ۴ — سمت چپ میز)
@Composable
private fun Smoke(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width * 0.30f
        for (i in 0 until 10) {
            val t = (progress + i / 10f) % 1f
            val y = size.height * 0.86f - t * size.height * 0.55f
            val x = cx + sin((t * 5f + i) * 1.35f) * size.width * 0.05f
            val r = 6f + t * 42f
            val a = (1f - t) * 0.16f
            drawCircle(Color.White.copy(alpha = a), r, Offset(x, y))
        }
    }
}

// حلقه رون‌های چرخان دور گوی (صفحه ۵)
@Composable
private fun RuneRing(slow: Float, fast: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        val r = minOf(size.width, size.height) * 0.30f
        val rot = slow * 360f
        for (i in 0 until 28) {
            val a = (i * 12.857f + rot).toDouble() * PI / 180.0
            val col = if (i % 2 == 0) OBPurple else OBTeal
            drawLine(
                col.copy(alpha = 0.55f),
                Offset(cx + kotlin.math.cos(a).toFloat() * r, cy + sin(a).toFloat() * r),
                Offset(cx + kotlin.math.cos(a).toFloat() * (r + 14f), cy + sin(a).toFloat() * (r + 14f)),
                3f
            )
        }
        drawCircle(OBPurple.copy(alpha = 0.30f), r, Offset(cx, cy), style = Stroke(2f))
        drawCircle(
            OBTeal.copy(alpha = 0.25f),
            r * 0.86f,
            Offset(cx, cy),
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 22f), -fast * 36f)
            )
        )
    }
}

// فلش نور گوی (صفحه ۵)
@Composable
private fun OrbFlash(fast: Float, modifier: Modifier = Modifier) {
    val flick = sin(fast * TWO_PI_F * 3f).coerceAtLeast(0f) * 0.20f
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.42f)
        val r = size.width * 0.26f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = flick), Color.Transparent),
                center = c,
                radius = r
            ),
            center = c,
            radius = r
        )
    }
}

// ستاره‌های چشمک‌زن سراسری
@Composable
private fun Sparkles(fast: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        for (i in 0 until 12) {
            val a = sin((fast * 2f + i * 0.83f) * TWO_PI_F).coerceAtLeast(0f) * 0.7f
            val x = size.width * ((i * 0.618f) % 1f)
            val y = size.height * ((i * 0.381f) % 1f) * 0.7f
            drawCircle(Color.White.copy(alpha = a), 2.5f, Offset(x, y))
        }
    }
}

// ترکیب دو رنگ
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = 1f
    )
}
