package com.pumpwatch.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.PumpWatchPalette

// گزینه ۱ / قدم ۱: کارت مشترک همهٔ تب‌ها با زبان شیشه‌ای Nebula هماهنگ شد
// (همان GlassDeep + لبهٔ نازکِ کارت‌های بازار و نبض)، با ته‌رنگ ملایم رنگ هر تب.
// هالهٔ نئونی حذف شد. امضاها دست‌نخورده‌اند تا هیچ تبی نشکند.

@Composable
fun GlassCard(
    accent: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 17.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Surface(
        modifier = modifier,
        shape = shape,
        color = PumpWatchPalette.GlassDeep,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    accent.copy(alpha = 0.38f),
                    PumpWatchPalette.Border.copy(alpha = 0.28f)
                )
            )
        ),
        shadowElevation = 6.dp
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun SourceAgeLine(
    source: String = "",
    ageSec: Long = -1L,
    tabKey: String = "",
    modifier: Modifier = Modifier,
    prefix: String = "📡",
    dim: Color = PumpWatchPalette.MutedDim
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
