package com.pumpwatch.app.ui

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val OBGreen = Color(0xFF00E676)
private val OBTeal = Color(0xFF00BFA5)
private val OBGold = Color(0xFFFFC107)
private val OBGray = Color(0xFF8B949E)
private val OBRed = Color(0xFFFF5252)
private val OBPurple = Color(0xFFB388FF)
private val OBBackground = Color(0xFF07090D)

private const val TWO_PI_F = 6.2831853f

private data class OBPage(
    val title: String,
    val desc: String,
    val img: Int,
    val accent: Color
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = remember {
        listOf(
            OBPage("لحظه درست رو شکار کن!", "هشدارهای زودهنگام پامپ و دامپ، قبل از حرکت بزرگ بازار", R.drawable.onb_p1, OBGold),
            OBPage("شکار نهنگ‌ها", "ردپای خرید و فروش نهنگ‌ها رو قبل از حرکت بزرگ بازار دنبال کن", R.drawable.onb_p2, OBTeal),
            OBPage("تحلیل مثل حرفه‌ای‌ها", "۵ تایم‌فریم + ۸ اندیکاتور + نقاط دقیق ورود، استاپ و هدف", R.drawable.onb_p3, OBGreen),
            OBPage("رادار میم‌کوین‌ها", "کشف میم‌کوین‌های ترند قبل از پامپ؛ کارآگاه NEXT دنبال سوژه بعدیه", R.drawable.onb_p4, OBRed),
            OBPage("جلوتر از بازار باش", "سیگنال‌های طلایی با معیارهای ۵۰ تریدر برتر دنیا", R.drawable.onb_p5, OBPurple)
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    // ---------- انیمیشن‌های سراسری ----------
    val infinite = rememberInfiniteTransition(label = "ob")
    val slow by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "slow")
    val mid by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "mid")
    val fast by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "fast")
    val swing by infinite.animateFloat(-15f, 15f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "swing")
    val shimmer by infinite.animateFloat(-1f, 2f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "shim")
    val story by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "story")
    val floatY by infinite.animateFloat(-10f, 10f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OBBackground)
    ) {

        // ---------- پس‌زمینه: گرادیان تنفس‌دار + ستاره‌های چشمک‌زن ----------
        val bgShift = abs(sin(slow * PI.toFloat()))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerpColor(Color(0xFF0A1420), Color(0xFF0A1F2E), bgShift),
                            Color(0xFF07090D)
                        )
                    )
                )
        )
        Starfield(fast, Modifier.fillMaxSize())

        // ---------- Pager کارت‌های قهرمان ----------
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { p ->
            val page = pages[p]
            // offset این صفحه نسبت به مرکز: بین -1 و 1
            val offset = ((pagerState.currentPage - p) + pagerState.currentPageOffsetFraction)
                .coerceIn(-1f, 1f)
            val absOff = abs(offset)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(Modifier.height(64.dp))

                // ============ کارت تصویر ============
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // فضای باقیمانده به تصویر
                    contentAlignment = Alignment.Center
                ) {
                    val cardShape = RoundedCornerShape(28.dp)

                    // درخشش پشت کارت (هاله accent)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.30f)
                            .blur(38.dp)
                            .alpha(0.55f)
                            .graphicsLayer { translationY = floatY * 1.6f }
                            .background(page.accent, cardShape)
                    )

                    // خود کارت: تیلت سه‌بعدی + پارالاکس داخلی + زوم نفس‌کش
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.30f)
                            .graphicsLayer {
                                translationY = floatY
                                // تیلت سه‌بعدی نسبت به موقعیت سوایپ
                                rotationY = offset * 14f
                                rotationZ = offset * 2f
                                scaleX = 1f - absOff * 0.10f
                                scaleY = 1f - absOff * 0.10f
                                alpha = 1f - absOff * 0.35f
                                cameraDistance = 12f * density
                            }
                            .clip(cardShape)
                            .background(Color(0xFF0D1117)),
                        contentAlignment = Alignment.Center
                    ) {
                        // تصویر با پارالاکس داخلی و زوم نرم (Ken Burns)
                        val kb by rememberInfiniteTransition(label = "kb$p").animateFloat(
                            initialValue = 1.0f,
                            targetValue = 1.10f,
                            animationSpec = infiniteRepeatable(tween(6500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "kbv$p"
                        )
                        Image(
                            painter = painterResource(page.img),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = kb
                                    scaleY = kb
                                    // تصویر داخل کارت برخلاف جهت سوایپ حرکت می‌کند → پارالاکس
                                    translationX = offset * size.width * 0.12f
                                }
                        )

                        // برق کشویی روی عکس (Shine Sweep)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val sweepX = (shimmer * 1.5f - 0.25f) * size.width
                                    translationX = sweepX - size.width / 2f
                                    alpha = 0.35f
                                }
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.28f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // افکت‌های داستانی هر صفحه روي تصویر
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

                        // حاشیه نورانی بالای کارت
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, page.accent.copy(alpha = 0.8f), Color.Transparent)
                                    )
                                )
                                .align(Alignment.TopCenter)
                        )
                    }

                    // ذرات معلق دور کارت
                    FloatingParticles(fast, page.accent, Modifier.fillMaxSize())
                }

                // ============ عنوان و توضیح ============
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = 1f - absOff * 0.9f
                            translationY = absOff * 46f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // خط رنگی کوچک بالای عنوان
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(page.accent)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        page.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        page.desc,
                        fontSize = 14.sp,
                        color = Color(0xFFC7D2DC),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }

                Spacer(Modifier.height(22.dp))
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
                    val active = i == pagerState.currentPage
                    val wd by animateDpAsState(if (active) 28.dp else 8.dp, label = "dot$i")
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(wd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (active) pages[i].accent else Color(0xFF3A4450)
                            )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OBGreen),
                    shape = RoundedCornerShape(18.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp)
                ) {
                    Text(
                        if (pagerState.currentPage < pages.size - 1) "بعدی ←" else "بزن بریم! 🚀",
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

// ---------- ستاره‌های چشمک‌زن پس‌زمینه ----------
@Composable
private fun Starfield(fast: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        for (i in 0 until 26) {
            val a = sin((fast * 2f + i * 0.83f) * TWO_PI_F).coerceAtLeast(0f)
            val x = size.width * ((i * 0.618f) % 1f)
            val y = size.height * ((i * 0.381f) % 1f)
            drawCircle(Color.White.copy(alpha = a * 0.5f), 1.5f + (i % 3), Offset(x, y))
        }
    }
}

// ---------- ذرات معلق دور کارت ----------
@Composable
private fun FloatingParticles(fast: Float, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 0 until 10) {
            val t = (fast + i / 10f) % 1f
            val x = w * ((i * 0.37f + 0.1f) % 1f)
            val y = h * (1f - t)
            val col = if (i % 3 == 0) accent else Color.White
            drawCircle(col.copy(alpha = (1f - t) * 0.5f), 2.5f, Offset(x, y))
        }
    }
}

// ---------- صفحه ۱: موشک + زنگ ----------
@Composable
private fun RocketStory(story: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 0 until 14) {
            val t = (story + i / 14f) % 1f
            val x = w * (0.16f + 0.30f * t)
            val y = h * (0.78f - 0.34f * t) + sin((t * 6f + i) * 1.4f) * 8f
            drawCircle(OBGold.copy(alpha = (1f - t) * 0.7f), 3f + (1f - t) * 4f, Offset(x, y))
        }
        for (k in 0 until 3) {
            val ph = (story * 1.3f + k / 3f) % 1f
            drawCircle(
                color = OBGold.copy(alpha = (1f - ph) * 0.45f),
                radius = 24f + ph * 70f,
                center = Offset(w * 0.80f, h * 0.22f),
                style = Stroke(3f)
            )
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
            drawRect(OBGreen.copy(alpha = 0.75f), Offset(x - 5f, base - ch), Size(10f, ch))
        }
        for (i in 0 until 8) {
            val t = ((story - 0.30f - i * 0.025f) / 0.30f).coerceIn(0f, 1f)
            if (t <= 0f || swallow >= 1f) continue
            val x = w * (0.50f + i * 0.055f)
            val ease = swallow * swallow
            val px = x + (mouth.x - x) * ease
            val py = (-30f + t * (h * 0.70f + 30f)) + (mouth.y - (-30f + t * (h * 0.70f + 30f))) * ease
            drawRect(OBRed.copy(alpha = 0.85f * (1f - swallow)), Offset(px - 4f, py - 10f), Size(8f, 10f))
        }
    }
}

// ---------- صفحه ۳: کوکپیت تحلیل ----------
@Composable
private fun CockpitStory(story: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val y = story * h
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, OBTeal.copy(alpha = 0.25f), Color.Transparent),
                startY = y - 40f, endY = y + 40f
            ),
            topLeft = Offset(0f, y - 40f), size = Size(w, 80f)
        )
        val ph1 = (story * 1.5f) % 1f
        drawCircle(OBGreen.copy(alpha = (1f - ph1) * 0.55f), 12f + ph1 * 46f,
            center = Offset(w * 0.435f, h * 0.52f), style = Stroke(3f))
        drawCircle(OBGold.copy(alpha = 0.6f), 26f, Offset(w * 0.62f, h * 0.28f),
            style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f), story * 120f)))
    }
}

// ---------- صفحه ۴: اتاق کارآگاه ----------
@Composable
private fun DetectiveStory(story: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val flick = ((sin(story * 30f * PI.toFloat()) + 1f) / 2f).coerceIn(0f, 1f)
        val drop = if (sin(story * 7f * PI.toFloat()) > 0.96f) 0.3f else 1f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(OBTeal.copy(alpha = (0.10f + 0.18f * flick) * drop), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.07f), radius = w * 0.20f
            ),
            center = Offset(w * 0.5f, h * 0.07f), radius = w * 0.20f
        )
        for (i in 0 until 12) {
            val ang = (i * 30f).toDouble() * PI / 180.0
            val ex = w * 0.5f + kotlin.math.cos(ang).toFloat() * w * 0.44f
            val ey = h * 0.42f + sin(ang).toFloat() * h * 0.34f
            val wave = (sin((story * 3f - i / 12f) * TWO_PI_F) + 1f) / 2f
            drawLine(OBRed.copy(alpha = 0.15f + 0.35f * wave), Offset(w * 0.5f, h * 0.42f), Offset(ex, ey), 2f)
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
        val flick = sin(story * 6f * PI.toFloat()).coerceAtLeast(0f) * 0.18f
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = flick), Color.Transparent), center = c, radius = w * 0.30f),
            center = c, radius = w * 0.30f
        )
        for (s in 0 until 2) {
            val cx = if (s == 0) w * 0.13f else w * 0.87f
            val fl = (sin(story * 25f * PI.toFloat() + s * 2f) + 1f) / 2f
            drawCircle(
                brush = Brush.radialGradient(listOf(OBGold.copy(alpha = 0.15f + 0.20f * fl), Color.Transparent),
                    center = Offset(cx, h * 0.60f), radius = 60f),
                center = Offset(cx, h * 0.60f), radius = 60f
            )
        }
        for (i in 0 until 16) {
            val t = (story + i / 16f) % 1f
            val ang = (i * 22.5f + story * 90f).toDouble() * PI / 180.0
            val r = w * 0.18f + t * w * 0.14f
            val col = if (i % 2 == 0) OBPurple else Color.White
            drawCircle(col.copy(alpha = (1f - t) * 0.6f), 2.5f,
                Offset(c.x + kotlin.math.cos(ang).toFloat() * r, c.y + sin(ang).toFloat() * r * 0.6f - t * 40f))
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
        val sinA = sin(ang).toFloat()
        val x2 = px + (-halfW) * cosA - bottomY * sinA
        val y2 = (-halfW) * sinA + bottomY * cosA
        val x3 = px + halfW * cosA - bottomY * sinA
        val y3 = halfW * sinA + bottomY * cosA
        val path = Path().apply {
            moveTo(px, 0f); lineTo(x2, y2); lineTo(x3, y3); close()
        }
        drawPath(path, Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
            start = Offset(px, 0f), end = Offset(px, bottomY)
        ))
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
            drawCircle(Color.White.copy(alpha = (1f - t) * 0.16f), 4f + t * 26f, Offset(x, y))
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
            drawLine(col.copy(alpha = 0.55f),
                Offset(cx + kotlin.math.cos(a).toFloat() * r, cy + sin(a).toFloat() * r),
                Offset(cx + kotlin.math.cos(a).toFloat() * (r + 10f), cy + sin(a).toFloat() * (r + 10f)), 3f)
        }
        drawCircle(OBPurple.copy(alpha = 0.30f), r, Offset(cx, cy), style = Stroke(2f))
        drawCircle(OBTeal.copy(alpha = 0.25f), r * 0.86f, Offset(cx, cy),
            style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 22f), -fast * 36f)))
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
