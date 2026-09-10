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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.blur
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

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }

    val pages = remember {
        listOf(
            OBPage("لحظه درست رو شکار کن!", "هشدارهای زودهنگام پامپ و دامپ، قبل از حرکت بزرگ بازار", R.drawable.onb_p1),
            OBPage("شکار نهنگ‌ها", "ردپای خرید و فروش نهنگ‌ها رو قبل از حرکت بزرگ بازار دنبال کن", R.drawable.onb_p2),
            OBPage("تحلیل مثل حرفه‌ای‌ها", "۵ تایم‌فریم + ۸ اندیکاتور + نقاط دقیق ورود، استاپ و هدف", R.drawable.onb_p3),
            OBPage("رادار میم‌کوین‌ها", "کشف میم‌کوین‌های ترند قبل از پامپ؛ کارآگاه NEXT دنبال سوژه بعدیه", R.drawable.onb_p4),
            OBPage("جلوتر از بازار باش", "سیگنال‌های طلایی با معیارهای ۵۰ تریدر برتر دنیا", R.drawable.onb_p5)
        )
    }

    val infinite = rememberInfiniteTransition(label = "ob")
    val slow by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "slow")
    val mid by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "mid")
    val fast by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "fast")
    val pulse by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    val swing by infinite.animateFloat(-15f, 15f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "swing")
    val shimmer by infinite.animateFloat(-1f, 2f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "shim")
    val story by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "story")

    val sway = sin(story * TWO_PI_F)
    val swallow = ((story - 0.70f) / 0.25f).coerceIn(0f, 1f)
    val dive = sin(swallow * PI.toFloat())

    val c1 = lerpColor(Color(0xFF07090D), Color(0xFF0A1F2E), slow)
    val c2 = lerpColor(Color(0xFF0E2A1E), Color(0xFF1A0E2A), slow)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c1, c2, Color(0xFF07090D))))
    ) {

        Sparkles(fast, Modifier.fillMaxSize())

        Crossfade(targetState = page, animationSpec = tween(550)) { p ->
            Box(modifier = Modifier.fillMaxSize()) {

                // ۱) کل صفحه = خود تصویر (شارپِ محو) بدون برش محتوا
                Image(
                    painter = painterResource(pages[p].img),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(22.dp)
                        .graphicsLayer { alpha = 0.80f }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x4407090D))
                )

                // ۲) نسخه شارپ وسط + حرکت سینمایی + افکت‌ها
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.78f)
                        .align(Alignment.Center)
                ) {
                    Image(
                        painter = painterResource(pages[p].img),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = 1.01f + 0.02f * pulse
                                scaleX = s
                                scaleY = s
                                when (p) {
                                    0 -> {
                                        translationY = sway * 10f
                                        rotationZ = sway * 1.5f
                                    }
                                    1 -> translationY = dive * 46f
                                    2 -> alpha = 0.94f + 0.06f * sin(story * 50f * PI.toFloat())
                                    3 -> {
                                        val b = 1.01f + 0.015f * sin(story * TWO_PI_F)
                                        scaleX = b
                                        scaleY = b
                                    }
                                    4 -> translationY = -sin(story * TWO_PI_F) * 12f
                                }
                            }
                    )

                    when (p) {
                        0 -> RocketStory(story, Modifier.fillMaxSize())
                        1 -> WhaleStory(story, Modifier.fillMaxSize())
                        2 -> CockpitStory(story, Modifier.fillMaxSize())
                        3 -> {
                            SwingLight(swing, Modifier.fillMaxSize())
                            Smoke(mid, Modifier.fillMaxSize())
                            DetectiveStory(story, Modifier.fillMaxSize())
                        }
                        4 -> {
                            RuneRing(slow, fast, Modifier.fillMaxSize())
                            OrbStory(story, Modifier.fillMaxSize())
                        }
                    }
                }

                // ۳) scrim پایین
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE07090D)))
                        )
                )

                // ۴) عنوان و توضیح
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .padding(bottom = 176.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(pages[p].title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(pages[p].desc, fontSize = 14.sp, color = Color(0xFFC7D2DC), textAlign = TextAlign.Center, lineHeight = 22.sp)
                }
            }
        }

        // ---------- کنترل‌های پایین ----------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            Spacer(Modifier.height(14.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { if (page < pages.size - 1) page++ else onDone() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
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

// ---------- صفحه ۱: موشک + زنگ ----------
@Composable
private fun RocketStory(story: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // جرقه‌های دنباله موشک
        for (i in 0 until 14) {
            val t = (story + i / 14f) % 1f
            val x = w * (0.16f + 0.30f * t)
            val y = h * (0.78f - 0.34f * t) + sin((t * 6f + i) * 1.4f) * 8f
            val a = (1f - t) * 0.7f
            drawCircle(OBGold.copy(alpha = a), 3f + (1f - t) * 4f, Offset(x, y))
        }
        // موج‌های زنگ
        for (k in 0 until 3) {
            val ph = (story * 1.3f + k / 3f) % 1f
            drawCircle(
                color = OBGold.copy(alpha = (1f - ph) * 0.45f),
                radius = 24f + ph * 70f,
                center = Offset(w * 0.80f, h * 0.22f),
                style = Stroke(3f)
            )
        }
        // کندل‌های سبز پامپ
        for (i in 0 until 7) {
            val g = (sin((story * 2f + i * 0.4f) * PI.toFloat()) + 1f) / 2f
            val x = w * (0.08f + i * 0.06f)
            val base = h * 0.92f
            val ch = h * (0.10f + 0.22f * g)
            val col = OBGreen.copy(alpha = 0.65f)
            drawLine(col, Offset(x, base - ch - 12f), Offset(x, base - ch), 3f)
            drawRect(col, Offset(x - 5f, base - ch), Size(10f, ch))
        }
        // خرده‌های قرمز سقوط
        for (i in 0 until 8) {
            val t = (story * 1.1f + i / 8f) % 1f
            val x = w * (0.55f + (i % 4) * 0.10f)
            val y = t * h * 0.70f
            val a = (1f - t) * 0.7f
            drawRect(OBRed.copy(alpha = a), Offset(x, y), Size(8f, 14f))
        }
    }
}

// ---------- صفحه ۲: پامپ → دامپ → بلع ----------
@Composable
private fun WhaleStory(story: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mouth = Offset(w * 0.72f, h * 0.45f)
        val swallow = ((story - 0.70f) / 0.25f).coerceIn(0f, 1f)

        for (i in 0 until 8) {
            val g = ((story - i * 0.02f) / 0.30f).coerceIn(0f, 1f)
            if (g <= 0f) continue
            val x = w * (0.10f + i * 0.055f)
            val base = h * 0.80f
            val ch = h * 0.10f + h * 0.26f * g
            val col = OBGreen.copy(alpha = 0.75f)
            drawLine(col, Offset(x, base - ch - 14f), Offset(x, base - ch), 3f)
            drawRect(col, Offset(x - 5f, base - ch), Size(10f, ch))
        }

        for (i in 0 until 8) {
            val t = ((story - 0.30f - i * 0.025f) / 0.30f).coerceIn(0f, 1f)
            if (t <= 0f || swallow >= 1f) continue
            val x = w * (0.50f + i * 0.055f)
            val landY = h * 0.70f
            val y = -30f + t * (landY + 30f)
            val ease = swallow * swallow
            val px = x + (mouth.x - x) * ease
            val py = y + (mouth.y - y) * ease
            val sc = 1f - swallow
            val ch = h * 0.09f * sc
            val col = OBRed.copy(alpha = 0.85f * sc)
            drawLine(col, Offset(px, py - ch - 12f * sc), Offset(px, py - ch), 3f * sc + 0.5f)
            drawRect(col, Offset(px - 5f * sc, py - ch), Size(10f * sc, ch))
        }

        if (swallow in 0.75f..1f) {
            val gq = (swallow - 0.75f) / 0.25f
            drawCircle(
                color = Color.White.copy(alpha = (1f - gq) * 0.6f),
                radius = 20f + gq * 70f,
                center = mouth,
                style = Stroke(4f)
            )
        }
    }
}

// ---------- صفحه ۳: کوکپیت تحلیل ----------
@Composable
private fun CockpitStory(story: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // خط اسکن
        val y = story * h
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, OBTeal.copy(alpha = 0.22f), Color.Transparent),
                startY = y - 40f,
                endY = y + 40f
            ),
            topLeft = Offset(0f, y - 40f),
            size = Size(w, 80f)
        )
        // پالس ENTRY
        val ph1 = (story * 1.5f) % 1f
        drawCircle(
            color = OBGreen.copy(alpha = (1f - ph1) * 0.55f),
            radius = 12f + ph1 * 46f,
            center = Offset(w * 0.435f, h * 0.52f),
            style = Stroke(3f)
        )
        // حلقه چرخان TARGET
        drawCircle(
            color = OBGold.copy(alpha = 0.6f),
            radius = 26f,
            center = Offset(w * 0.62f, h * 0.28f),
            style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f), story * 120f))
        )
        // جریان داده
        for (i in 0 until 20) {
            val xx = ((i / 20f + story) % 1f) * w
            val yy = h * 0.66f + sin((xx / w * 4f + story * 2f) * PI.toFloat()) * h * 0.05f
            drawLine(OBTeal.copy(alpha = 0.5f), Offset(xx, yy), Offset(xx + 10f, yy), 2f)
        }
    }
}

// ---------- صفحه ۴: اتاق کارآگاه ----------
@Composable
private fun DetectiveStory(story: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // فلیکر نئون NEXT
        val flick = ((sin(story * 30f * PI.toFloat()) + 1f) / 2f).coerceIn(0f, 1f)
        val drop = if (sin(story * 7f * PI.toFloat()) > 0.96f) 0.3f else 1f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(OBTeal.copy(alpha = (0.10f + 0.18f * flick) * drop), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.07f),
                radius = w * 0.20f
            ),
            center = Offset(w * 0.5f, h * 0.07f),
            radius = w * 0.20f
        )
        // پالس نخ‌های قرمز
        for (i in 0 until 12) {
            val ang = (i * 30f).toDouble() * PI / 180.0
            val ex = w * 0.5f + kotlin.math.cos(ang).toFloat() * w * 0.44f
            val ey = h * 0.42f + kotlin.math.sin(ang).toFloat() * h * 0.34f
            val wave = (sin((story * 3f - i / 12f) * TWO_PI_F) + 1f) / 2f
            drawLine(OBRed.copy(alpha = 0.15f + 0.35f * wave), Offset(w * 0.5f, h * 0.42f), Offset(ex, ey), 2f)
        }
        // غبار معلق
        for (i in 0 until 10) {
            val t = (story * 0.6f + i / 10f) % 1f
            val x = w * ((i * 0.37f) % 1f)
            val yy = h * (1f - t)
            drawCircle(Color.White.copy(alpha = (1f - t) * 0.12f), 2f, Offset(x, yy))
        }
    }
}

// ---------- صفحه ۵: گوی پیشگو ----------
@Composable
private fun OrbStory(story: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w * 0.5f, h * 0.45f)
        // فلش نور گوی
        val flick = sin(story * 6f * PI.toFloat()).coerceAtLeast(0f) * 0.18f
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = flick), Color.Transparent), center = c, radius = w * 0.30f),
            center = c,
            radius = w * 0.30f
        )
        // فلیکر شمع‌های دو طرف
        for (s in 0 until 2) {
            val cx = if (s == 0) w * 0.13f else w * 0.87f
            val fl = (sin(story * 25f * PI.toFloat() + s * 2f) + 1f) / 2f
            drawCircle(
                brush = Brush.radialGradient(listOf(OBGold.copy(alpha = 0.15f + 0.20f * fl), Color.Transparent), center = Offset(cx, h * 0.60f), radius = 60f),
                center = Offset(cx, h * 0.60f),
                radius = 60f
            )
        }
        // جرقه‌های بالارونده دور گوی
        for (i in 0 until 16) {
            val t = (story + i / 16f) % 1f
            val ang = (i * 22.5f + story * 90f).toDouble() * PI / 180.0
            val r = w * 0.18f + t * w * 0.14f
            val x = c.x + kotlin.math.cos(ang).toFloat() * r
            val yy = c.y + kotlin.math.sin(ang).toFloat() * r * 0.6f - t * 40f
            val col = if (i % 2 == 0) OBPurple else Color.White
            drawCircle(col.copy(alpha = (1f - t) * 0.6f), 2.5f, Offset(x, yy))
        }
    }
}

@Composable
private fun SwingLight(swingDeg: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val px = size.width / 2f
        val bottomY = size.height * 0.95f
        val halfW = size.width * 0.30f
        val ang = swingDeg * PI / 180.0
        val cosA = kotlin.math.cos(ang).toFloat()
        val sinA = kotlin.math.sin(ang).toFloat()
        val x2 = px + (-halfW) * cosA - bottomY * sinA
        val y2 = 0f + (-halfW) * sinA + bottomY * cosA
        val x3 = px + halfW * cosA - bottomY * sinA
        val y3 = 0f + halfW * sinA + bottomY * cosA
        val path = Path().apply {
            moveTo(px, 0f)
            lineTo(x2, y2)
            lineTo(x3, y3)
            close()
        }
        drawPath(
            path,
            Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                start = Offset(px, 0f),
                end = Offset(px, bottomY)
            )
        )
        drawCircle(OBGold.copy(alpha = 0.25f), 30f, Offset(px, 4f))
    }
}

@Composable
private fun Smoke(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width * 0.22f
        for (i in 0 until 10) {
            val t = (progress + i / 10f) % 1f
            val y = size.height * 0.95f - t * size.height * 0.7f
            val x = cx + sin((t * 5f + i) * 1.35f) * size.width * 0.04f
            val r = 4f + t * 26f
            val a = (1f - t) * 0.16f
            drawCircle(Color.White.copy(alpha = a), r, Offset(x, y))
        }
    }
}

@Composable
private fun RuneRing(slow: Float, fast: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.5f
        val r = minOf(size.width, size.height) * 0.34f
        val rot = slow * 360f
        for (i in 0 until 28) {
            val a = (i * 12.857f + rot).toDouble() * PI / 180.0
            val col = if (i % 2 == 0) OBPurple else OBTeal
            drawLine(
                col.copy(alpha = 0.55f),
                Offset(cx + kotlin.math.cos(a).toFloat() * r, cy + sin(a).toFloat() * r),
                Offset(cx + kotlin.math.cos(a).toFloat() * (r + 10f), cy + sin(a).toFloat() * (r + 10f)),
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

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = 1f
    )
}
