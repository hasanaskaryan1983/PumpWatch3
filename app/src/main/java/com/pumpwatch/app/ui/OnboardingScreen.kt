package com.pumpwatch.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OBGreen = Color(0xFF00E676)
private val OBTeal = Color(0xFF00BFA5)
private val OBGold = Color(0xFFFFC107)
private val OBGray = Color(0xFF8B949E)

private data class OBPage(val emoji: String, val title: String, val desc: String)
private data class FloatingParticle(val emoji: String, val xOffset: Float, val duration: Int, val delay: Int, val size: Float)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }

    val pages = listOf(
        OBPage("🚀", "لحظه درست رو شکار کن!", "هشدارهای زودهنگام پامپ و دامپ، قبل از حرکت بزرگ بازار"),
        OBPage("📊", "تحلیل مثل حرفه‌ای‌ها", "۵ تایم‌فریم + ۸ اندیکاتور + نقاط دقیق ورود، استاپ و هدف"),
        OBPage("🐸", "رادار میم‌کوین‌ها", "شناسایی میم‌کوین‌های ترند قبل از پامپ، با ردپای نهنگ‌ها"),
        OBPage("🏆", "جلوتر از بازار باش", "سیگنال‌های طلایی با معیارهای ۵۰ تریدر برتر دنیا")
    )

    // ---------- انیمیشن‌های سراسری ----------
    val infinite = rememberInfiniteTransition(label = "ob_infinite")

    val bounce by infinite.animateFloat(
        initialValue = -14f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce"
    )
    val rocketTilt by infinite.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tilt"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "glow"
    )
    val hueShift by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "hue"
    )
    val shimmer by infinite.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    val particles = remember {
        listOf(
            FloatingParticle("📈", 0.08f, 9000, 0, 22f),
            FloatingParticle("🐳", 0.22f, 11000, 500, 28f),
            FloatingParticle("💰", 0.38f, 10000, 1200, 20f),
            FloatingParticle("⚡", 0.55f, 8500, 800, 24f),
            FloatingParticle("🎯", 0.72f, 12000, 300, 22f),
            FloatingParticle("💎", 0.88f, 9500, 1500, 26f)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F14))) {

        // ---------- پس‌زمینه گرادیان متحرک ----------
        val c1 = lerpColor(Color(0xFF0B0F14), Color(0xFF0A1F2E), hueShift)
        val c2 = lerpColor(Color(0xFF0E2A1E), Color(0xFF1A0E2A), hueShift)
        val c3 = lerpColor(Color(0xFF0B0F14), Color(0xFF14101E), hueShift)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(c1, c2, c3)))
        )

        // ---------- ذرات شناور ----------
        particles.forEach { p ->
            val yAnim by infinite.animateFloat(
                initialValue = 1f, targetValue = -0.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(p.duration, easing = LinearEasing, delayMillis = p.delay),
                    repeatMode = RepeatMode.Restart
                ),
                label = "particle_${p.emoji}"
            )
            val fadeAlpha = when {
                yAnim > 0.85f -> (1f - yAnim) / 0.15f
                yAnim < 0.15f -> yAnim / 0.15f
                else -> 1f
            }
            Text(
                text = p.emoji,
                fontSize = p.size.sp,
                modifier = Modifier
                    .offset(x = (p.xOffset * 360).dp, y = (yAnim * 800).dp)
                    .graphicsLayer { alpha = fadeAlpha * 0.7f }
            )
        }

        // ---------- هاله‌های بزرگ پس‌زمینه ----------
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-120).dp, y = 60.dp)
                .graphicsLayer { alpha = glow * 0.15f }
                .background(OBGreen, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = 200.dp, y = 500.dp)
                .graphicsLayer { alpha = glow * 0.12f }
                .background(OBTeal, CircleShape)
        )

        // ---------- محتوای اصلی ----------
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ---------- موشک با هاله + چرخش ----------
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .graphicsLayer { alpha = glow * 0.3f }
                        .background(OBGreen, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .graphicsLayer { alpha = glow * 0.5f }
                        .background(OBTeal, CircleShape)
                )
                Text(
                    "🚀",
                    fontSize = 96.sp,
                    modifier = Modifier
                        .offset(y = bounce.dp)
                        .rotate(rocketTilt)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------- لوگو با گرادیان متحرک ----------
            Text(
                "PumpDump",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(OBGreen, OBTeal, OBGold, OBGreen),
                        startX = hueShift * 400f,
                        endX = hueShift * 400f + 400f
                    )
                )
            )
            Text("دستیار تریدر هوشمند تو 🤖", fontSize = 14.sp, color = OBGray)

            Spacer(Modifier.height(28.dp))

            // ---------- صفحات معرفی ----------
            Crossfade(targetState = page, animationSpec = tween(500)) { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pages[p].emoji, fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pages[p].title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        pages[p].desc,
                        fontSize = 14.sp,
                        color = Color(0xFFB7C1CC),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ---------- نقطه‌های صفحه ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .then(
                                if (i == page) Modifier.fillMaxWidth().weight(1.5f)
                                else Modifier.size(8.dp)
                            )
                            .background(
                                if (i == page) Brush.horizontalGradient(listOf(OBGreen, OBTeal))
                                else Brush.horizontalGradient(listOf(Color(0xFF3A4450), Color(0xFF3A4450))),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------- دکمه اصلی با shimmer ----------
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (page < pages.size - 1) page++
                        else {
                            // ✅ کلید درست = "onboarded" (مطابق MainActivity)
                            context.getSharedPreferences("pumpwatch_prefs", 0).edit()
                                .putBoolean("onboarded", true)
                                .apply()
                            onDone()
                        }
                    },
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
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.4f),
                                    Color.Transparent
                                ),
                                startX = shimmer * 600f - 200f,
                                endX = shimmer * 600f
                            )
                        )
                )
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = {
                context.getSharedPreferences("pumpwatch_prefs", 0).edit()
                    .putBoolean("onboarded", true)
                    .apply()
                onDone()
            }) {
                Text("رد شدن و ورود مستقیم", color = OBGray, fontSize = 12.sp)
            }
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
