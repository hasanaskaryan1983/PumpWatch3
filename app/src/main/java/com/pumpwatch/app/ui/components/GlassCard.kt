package com.pumpwatch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.design.TabPalette

@Composable
fun GlassCard(
    accent: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(TabPalette.glow(accent), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension / 1.2f,
                    ),
                    cornerRadius = CornerRadius(radius.toPx()),
                )
            }
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF1A2230).copy(alpha = 0.72f),
                        Color(0xFF0E1622).copy(alpha = 0.55f),
                    )
                ),
                shape = shape,
            )
            .border(1.dp, TabPalette.border(accent), shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SourceAgeLine(source: String, ageSec: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(source, fontSize = 9.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Text("•", fontSize = 9.sp, color = Color(0xFF8B949E))
        Spacer(Modifier.width(4.dp))
        Text(formatAge(ageSec), fontSize = 9.sp, color = Color(0xFF8B949E))
    }
}

private fun formatAge(sec: Long): String = when {
    sec < 0 -> "—"
    sec < 60 -> "${sec}s"
    sec < 3600 -> "${sec / 60}m"
    sec < 86400 -> "${sec / 3600}h"
    else -> "${sec / 86400}d"
}
