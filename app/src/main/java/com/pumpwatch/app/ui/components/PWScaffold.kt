package com.pumpwatch.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.design.TabPalette
import kotlin.random.Random

/**
 * 🚀 Commit 161: پوستهٔ مشترک همهٔ تب‌ها — همان ظاهر Picture 34/35.
 *
 * سه تصمیم فنی صادقانه:
 * 1) blur واقعیِ پس‌زمینه در Compose موبایل پرهزینه است و RenderEffect.blur
 *    فقط API 31+ کار می‌کند → «شیشه» را با alpha + گرادیان + glow لبه می‌سازیم
 *    (در GlassCard کامیت ۱۶۰). نتیجه: تقلید باکیفیت بدون قربانی‌کردن فریم‌ریت.
 * 2) کهکشان پس‌زمینه برداری است (چند radial gradient + ذرات ستاره با seed ثابت)
 *    نه بیت‌مپ → حجم APK بالا نمی‌رود (نگرانی A12 گزارش چهارم).
 * 3) رنگ سربرگ از TabPalette می‌آید؛ کلید تب = هویت پایدار، برچسب فارسی = فقط UI.
 */
@Composable
fun PWScaffold(
    tabKey: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = TabPalette.accent(tabKey)
    Box(modifier = modifier.fillMaxSize()) {
        GalaxyBackground()
        Column(Modifier.fillMaxSize()) {
            // سربرگ شیشه‌ای
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0E1622).copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFE6EDF3),
                )
                // نقطهٔ رنگ امضایی کنار عنوان (اشاره به هویت تب)
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .background(accent, androidx.compose.foundation.shape.CircleShape)
                        .size(6.dp)
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                content = content,
            )
        }
    }
}

/**
 * کهکشان برداری: سه هالهٔ آبی/بنفش + ~۹۰ ستاره با seed ثابت.
 * remember می‌کنیم تا هر recomposition ستاره‌ها را جابه‌جا نکند.
 */
@Composable
private fun GalaxyBackground() {
    val stars = remember {
        val r = Random(42)
        List(90) { Triple(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 1.2f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        // پایهٔ تیره
        drawRect(Color(0xFF070B12))
        // هالهٔ آبی چپ
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF1E3A8A).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.45f),
                radius = size.width * 0.7f,
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * 0.15f, size.height * 0.45f),
        )
        // هالهٔ بنفش راست
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF6D28D9).copy(alpha = 0.40f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.3f),
                radius = size.width * 0.6f,
            ),
            radius = size.width * 0.6f,
            center = Offset(size.width * 0.9f, size.height * 0.3f),
        )
        // هالهٔ فیروزه‌ای پایین
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF0E7490).copy(alpha = 0.30f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.85f),
                radius = size.width * 0.8f,
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.5f, size.height * 0.85f),
        )
        // ستاره‌ها
        stars.forEach { (fx, fy, rad) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.25f + rad * 0.25f),
                radius = rad,
                center = Offset(fx * size.width, fy * size.height),
            )
        }
    }
}
