package com.pumpwatch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🚀 Commit 166 (retry): GlassCard نئونی + بازگرداندن SourceAgeLine
// - امضای GlassCard با پارامتر radius حفظ شد (فراخوان‌های WalletScreen/TabComponents نمی‌شکنند)
// - shadow با ambientColor/spotColor (API واقعی)، نه color
// - SourceAgeLine دوباره تعریف شد (Unresolved reference رفع شد)

@Composable
fun GlassCard(
    accent: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            // ۱) هالهٔ نئونی بیرونی (رنگ تب) — clip=false تا بیرون کارت بتابد
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = accent.copy(alpha = 0.45f),
                spotColor = accent.copy(alpha = 0.65f)
            )
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
                // ۳) لبهٔ گرادیانی = حس نئون (یک سر پررنگ، سر دیگر کمرنگ)
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

// 🛡️ صداقت داده: «منبع • سن» — با پارامترهای default تا طیف فراخوان‌ها را بپوشاند
@Composable
fun SourceAgeLine(
    source: String = "",
    ageSec: Long = -1L,
    tabKey: String = "",
    modifier: Modifier = Modifier,
    prefix: String = "📡",
    dim: Color = Color(0xFF8B949E)
) {
    val ageText = when {
        ageSec < 0 -> "—"
        ageSec < 60 -> "${ageSec}s"
        ageSec < 3600 -> "${ageSec / 60}m"
        else -> "${ageSec / 3600}h"
    }
    val src = source.ifBlank { tabKey.ifBlank { "—" } }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(prefix, fontSize = 9.sp, color = dim)
        Text(src, fontSize = 9.sp, color = dim, fontWeight = FontWeight.Medium)
        Text("•", fontSize = 9.sp, color = dim)
        Text(ageText, fontSize = 9.sp, color = dim)
    }
}
