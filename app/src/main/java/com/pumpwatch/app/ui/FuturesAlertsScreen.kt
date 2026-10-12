package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.engine.AlertRule
import com.pumpwatch.app.engine.AlertRulesStore
import com.pumpwatch.app.engine.RuleCondition
import com.pumpwatch.app.ui.components.AlertCard
import com.pumpwatch.app.ui.components.AlertView
import kotlinx.coroutines.launch
import java.util.Locale

private fun typeEmoji(type: String): String = when (type) {
    "FUNDING" -> "💸"
    "MOVE24" -> "🌪️"
    "OI_SPIKE" -> "🏦"
    "EMA_CROSS" -> "➿"
    "BREAKOUT" -> "💥"
    "SQUEEZE" -> "🌀"
    else -> "🔔"
}

@Composable
fun FuturesAlertsScreen() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    
    // 🔗 اتصال به هاب
    var alerts by remember { mutableStateOf(FuturesHub.alertsList) }
    var loading by remember { mutableStateOf(FuturesHub.alertsLoading && FuturesHub.alertsList.isEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf(FuturesHub.alertsLastMs) }
    
    var futRules by remember { mutableStateOf<List<AlertRule>>(emptyList()) }

    fun scan(force: Boolean) {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                alerts = FuturesHub.scanAlerts(force)
                lastUpdate = FuturesHub.alertsLastMs
            } catch (e: Exception) {
                errorMsg = "⚠️ خطا در اسکن: ${e.message?.take(80) ?: "نامشخص"}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (FuturesHub.alertsList.isEmpty()) scan(false)
        futRules = AlertRulesStore.load(ctx).filter {
            it.enabled && (it.condition == RuleCondition.FUNDING_ABOVE || it.condition == RuleCondition.FUNDING_BELOW)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorialPageHeader(
                eyebrow = "FUTURES · ALERTS",
                title = "هشدارهای فیوچرز",
                subtitle = "فاندینگ • حرکت ۲۴س • OI • شکست",
                accent = PumpWatchPalette.TabAlerts
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                HubRefreshRow(lastUpdate, loading, PumpWatchPalette.TabAlerts) { scan(true) }
            }
        }

        Text(
            "🎯 فقط ۱۰۰ ارز برتر ∩ perp بایننس • آستانهٔ فاندینگ نسبی (۳× میانگین)",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 9.sp, color = PumpWatchPalette.Muted, lineHeight = 15.sp
        )

        if (futRules.isNotEmpty()) {
            Surface(
                color = PumpWatchPalette.Warning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("🔔 قوانین فاندینگ فعال شما (${futRules.size}):", fontSize = 10.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                    futRules.take(3).forEach { r ->
                        Text("• ${r.symbol}: ${r.condition.label} ${String.format(Locale.US, "%.4f%%", r.threshold)}", fontSize = 9.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }

        if (loading && alerts.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = PumpWatchPalette.TabAlerts)
            }
        } else if (errorMsg != null && alerts.isEmpty()) {
            Text(errorMsg ?: "", color = PumpWatchPalette.Negative, modifier = Modifier.padding(24.dp))
        } else if (alerts.isEmpty()) {
            Text(
                "😴 هیچ هشدار فعالی نیست — بازار در حالت عادی است.",
                color = PumpWatchPalette.Muted, modifier = Modifier.padding(24.dp), fontSize = 12.sp
            )
        } else {
            val ageSec = if (lastUpdate > 0L) (System.currentTimeMillis() - lastUpdate) / 1000L else -1L
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("${alerts.size} هشدار فعال", fontSize = 11.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                }
                items(alerts) { a ->
                    AlertCard(
                        a = AlertView(
                            symbol = a.base,
                            type = a.type,
                            severity = a.severity,
                            title = a.title,
                            detail = a.detail,
                            rank = a.rank,
                            emoji = typeEmoji(a.type),
                            source = "Binance Futures",
                            ageSec = ageSec
                        ),
                        tabKey = "alerts"
                    )
                }
            }
        }
    }
}
