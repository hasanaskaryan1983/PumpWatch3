package com.pumpwatch.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OBGreen = Color(0xFF00E676)
private val OBGold = Color(0xFFFFC107)
private val OBGray = Color(0xFF8B949E)

private data class OBPage(val emoji: String, val title: String, val desc: String)

// ---------- صفحه ورود پرانرژی ----------

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }
    var risk by remember { mutableStateOf("⚖️ متعادل") }
    var style by remember { mutableStateOf("📅 روزانه") }

    // ---------- انیمیشن‌های زنده ----------
    val infinite = rememberInfiniteTransition()
    val bounce by infinite.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val glow by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        )
    )

    val pages = listOf(
        OBPage("🚀", "لحظه درست رو شکار کن!", "هشدارهای زودهنگام پامپ و دامپ، قبل از حرکت بزرگ بازار"),
        OBPage("📊", "تحلیل مثل حرفه‌ای‌ها", "۵ تایم‌فریم + ۸ اندیکاتور + نقاط دقیق ورود، استاپ و هدف"),
        OBPage("🐸", "رادار میم‌کوین‌ها", "شناسایی میم‌کوین‌های ترند قبل از پامپ، با ردپای نهنگ‌ها"),
        OBPage("🏆", "جلوتر از بازار باش", "سیگنال‌های طلایی با معیارهای ۵۰ تریدر برتر دنیا")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B0F14),
                        Color(0xFF0E2A1E),
                        Color(0xFF0B0F14)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ---------- راکت زنده با هاله درخشان ----------
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(OBGreen.copy(alpha = glow * 0.25f), CircleShape)
                )
                Text(
                    "🚀",
                    fontSize = 90.sp,
                    modifier = Modifier.offset(y = bounce.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "PumpDump",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = OBGreen
            )
            Text(
                "دستیار تریدر هوشمند تو 🤖",
                fontSize = 14.sp,
                color = OBGray
            )

            Spacer(Modifier.height(24.dp))

            // ---------- صفحات معرفی ----------
            Crossfade(targetState = page) { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pages[p].emoji, fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pages[p].title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pages[p].desc,
                        fontSize = 14.sp,
                        color = Color(0xFFB7C1CC),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------- شخصی‌سازی ----------
            Text(
                "سبک تریدت رو انتخاب کن:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OBGold
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🐂 تهاجمی", "⚖️ متعادل", "🛡️ محافظه‌کار").forEach { r ->
                    FilterChip(
                        selected = risk == r,
                        onClick = { risk = r },
                        label = { Text(r, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("⚡ اسکالپ", "📅 روزانه", "🌊 سویینگ").forEach { s ->
                    FilterChip(
                        selected = style == s,
                        onClick = { style = s },
                        label = { Text(s, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ---------- نقطه‌های صفحه ----------
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 10.dp else 7.dp)
                            .background(
                                if (i == page) OBGreen else Color(0xFF3A4450),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- دکمه اصلی ----------
            Button(
                onClick = {
                    if (page < pages.size - 1) page++
                    else {
                        context.getSharedPreferences("pumpwatch_prefs", 0).edit()
                            .putString("risk_profile", risk)
                            .putString("trade_style", style)
                            .apply()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OBGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (page < pages.size - 1) "بعدی ←" else "بزن بریم! 🚀",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(onClick = onDone) {
                Text("رد شدن", color = OBGray)
            }
        }
    }
}
