package com.pumpwatch.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.design.TabPalette

/**
 * 🚀 Commit 161 + توسعه: کارت هشدار با glassmorphism.
 * ➕ فیلدهای اختیاری contractChain/contractAddr (پیش‌فرض null → صفحه‌های دیگر بدون تغییر).
 */
data class AlertView(
    val symbol: String,
    val type: String,
    val severity: Int,
    val title: String,
    val detail: String,
    val rank: Int?,
    val emoji: String,
    val source: String,
    val ageSec: Long,
    val contractChain: String? = null,
    val contractAddr: String? = null
)

@Composable
fun AlertCard(a: AlertView, tabKey: String) {
    val accent = TabPalette.accent(tabKey)
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    GlassCard(accent = accent, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(a.symbol, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE6EDF3))
                if (a.rank != null) {
                    Text(" #${a.rank}", fontSize = 11.sp, color = Color(0xFF8B949E))
                }
                Spacer(Modifier.weight(1f))
                SeverityChip(a.severity)
            }

            Spacer(Modifier.height(8.dp))

            Text(a.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = severityColor(a.severity))

            Spacer(Modifier.height(4.dp))

            Text(a.detail, fontSize = 11.sp, color = Color(0xFFB0B8C4), lineHeight = 16.sp)

            Spacer(Modifier.height(10.dp))

            SourceAgeLine(a.source, a.ageSec)

            // 🚀 نوار هویت قرارداد (جلوگیری از رفتن سراغ ارز اشتباه)
            if (a.contractAddr != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⛓️", fontSize = 10.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("${a.contractChain}: ", fontSize = 9.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                    Text(addrShort(a.contractAddr), fontSize = 9.sp, color = Color(0xFF58A6FF), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            try {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("contract", a.contractAddr))
                                copied = true
                            } catch (_: Exception) { }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107).copy(alpha = 0.2f), contentColor = Color(0xFFE6EDF3)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text(if (copied) "✅" else "📋", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            } else if (a.contractChain != null) {
                Spacer(Modifier.height(6.dp))
                Text("⛓️ ${a.contractChain}", fontSize = 9.sp, color = Color(0xFF8B949E))
            }
        }
    }
}

private fun addrShort(a: String): String = if (a.length > 16) "${a.take(6)}…${a.takeLast(4)}" else a

@Composable
private fun SeverityChip(severity: Int) {
    val (label, color) = when (severity) {
        3 -> "🔥 شدید" to Color(0xFFFF5252)
        2 -> "⚠️ متوسط" to Color(0xFFFFC107)
        else -> "👀 خفیف" to Color(0xFF40C4FF)
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

private fun severityColor(sev: Int): Color = when (sev) {
    3 -> Color(0xFFFF5252)
    2 -> Color(0xFFFFC107)
    else -> Color(0xFF40C4FF)
}
