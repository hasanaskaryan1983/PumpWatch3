package com.pumpwatch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 🚀 Commit 166: GlassCard نئونی واقعی — هاله + بدنهٔ شیشه‌ای + لبهٔ درخشان
// drop-in: امضای accent/modifier/content حفظ شده، پس ۱۶ تب بدون تغییر کار می‌کنند.
@Composable
fun GlassCard(
    accent: Color,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            // ۱) هالهٔ بیرونی نئونی (رنگ تب) — clip=false تا بیرون کارت بدرخشد
            .shadow(elevation = 10.dp, shape = shape, clip = false, color = accent.copy(alpha = 0.55f))
    ) {
        Box(
            modifier = Modifier
                // ۲) بدنهٔ شیشه‌ای: بالا ته‌رنگ تب، پایین تیرهٔ عمقی
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.16f),
                            Color(0xFF0E1620).copy(alpha = 0.94f)
                        )
                    )
                )
                // ۳) لبهٔ گرادیانی: یک سر پررنگ، سر دیگر کمرنگ = حس نئون
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.22f))
                    ),
                    shape = shape
                )
                .padding(contentPadding)
        ) {
            content()
        }
    }
}
