package com.pumpwatch.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class EditorialDockTab(
    val id: String,
    val label: String,
    val iconRes: Int,
    val accent: Color
)

@Composable
fun CosmicBackdrop(accent: Color, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "cosmic-backdrop")
    val aura by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.19f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebula-aura"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PumpWatchPalette.Background, PumpWatchPalette.BackgroundLift, PumpWatchPalette.Background)))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 125.dp, y = (-118).dp)
                .size(350.dp)
                .graphicsLayer { scaleX = 0.94f + aura; scaleY = 0.94f + aura }
                .background(Brush.radialGradient(listOf(accent.copy(alpha = aura), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-190).dp, y = 110.dp)
                .size(390.dp)
                .background(Brush.radialGradient(listOf(PumpWatchPalette.Info.copy(alpha = aura * 0.56f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 170.dp, y = 130.dp)
                .size(320.dp)
                .background(Brush.radialGradient(listOf(PumpWatchPalette.Purple.copy(alpha = aura * 0.42f), Color.Transparent)), CircleShape)
        )
        content()
    }
}

@Composable
fun PumpDumpMark(accent: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pumpdump-mark")
    val glow by transition.animateFloat(
        initialValue = 0.46f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mark-glow"
    )
    Surface(
        color = PumpWatchPalette.GlassDeep,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f + glow * 0.35f)),
        shadowElevation = 10.dp,
        modifier = modifier.size(42.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), PumpWatchPalette.GlassDeep))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = glow * 0.22f), Color.Transparent)), CircleShape)
            )
            Text(
                "PD",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1).sp
            )
        }
    }
}

@Composable
fun EditorialTabDock(
    tabs: List<EditorialDockTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "tab-dock")
    val glow by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active-tab-glow"
    )
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { tab ->
                DockTabItem(
                    tab = tab,
                    selected = tab.id == selectedId,
                    glow = glow,
                    onClick = { onSelect(tab.id) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.DockTabItem(
    tab: EditorialDockTab,
    selected: Boolean,
    glow: Float,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.035f else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "tab-scale-${tab.id}"
    )
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .width(72.dp)
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(onClick = onClick),
        color = tab.accent.copy(alpha = if (selected) 0.17f else 0.07f),
        shape = shape,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    tab.accent.copy(alpha = if (selected) 0.96f else 0.50f),
                    Color.White.copy(alpha = if (selected) 0.30f else 0.10f),
                    tab.accent.copy(alpha = if (selected) 0.82f else 0.42f)
                )
            )
        ),
        shadowElevation = if (selected) 13.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.09f), PumpWatchPalette.GlassDeep.copy(alpha = 0.72f))
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(tab.accent.copy(alpha = if (selected) glow * 0.72f else 0.15f), Color.Transparent)
                        )
                    )
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 3.dp)
                        .size(width = 19.dp, height = 2.dp)
                        .clip(CircleShape)
                        .background(tab.accent)
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = null,
                    tint = tab.accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    tab.label,
                    color = if (selected) PumpWatchPalette.Text else PumpWatchPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EditorialPageHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    accent: Color = PumpWatchPalette.Accent,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 2.dp)
                        .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.08f))), CircleShape)
                )
                Text(
                    eyebrow,
                    color = accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                title,
                color = PumpWatchPalette.Text,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = PumpWatchPalette.Muted,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(start = 2.dp)) {
                trailing()
            }
        }
    }
}
