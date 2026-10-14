package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
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
    
    // 🚀 P0 Tab 13: استفاده از وضعیت‌های صریح برای نمایش شفاف‌تر
    var loading by remember { mutableStateOf(FuturesHub.alertsLoading && FuturesHub.alertsList.isEmpty()) }
    var lastUpdate by remember { mutableStateOf(FuturesHub.alertsLastMs) }
    var futRules by remember { mutableStateOf<List<AlertRule>>(emptyList()) }

    fun scan(force: Boolean) {
        scope.launch {
            loading = true
            try {
                FuturesHub.scanAlerts(force)
                lastUpdate = FuturesHub.alertsLastMs
            } catch (e: Exception) {
                // خطا در لایه FuturesHub مدیریت می‌شود
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (FuturesHub.alertsList.isEmpty()) scan(false)
        futRules = AlertRulesStore.load(ctx).filter {
            it.enabled && (it.condition == RuleCondition.FUNDING_ABOVE || it.condition == RuleCondition.FUNDING_BELOW)
        }
    }

    val alerts = FuturesHub.alertsList
    val dataAgeSec = if (lastUpdate > 0L) (System.currentTimeMillis() - lastUpdate) / 1000L else -1L
    val isStale = dataAgeSec > 300 // 🚀 P0: Stale Gate - بیشتر از ۵ دقیقه

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 🚀 P0 Tab 13: صداقت محصول و خنثی‌سازی ادبیات
            EditorialPageHeader(
                eyebrow = "FUTURES · THRESHOLDS",
                title = "بررسی آستانه‌های فیوچرز",
                subtitle = "ارزیابی فاندینگ، تغییرات قیمت و حجم بر اساس داده‌های عمومی. این خروجی پژوهشی است و توصیه معاملاتی نیست.",
                accent = PumpWatchPalette.TabAlerts
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                HubRefreshRow(lastUpdate, loading, PumpWatchPalette.TabAlerts) { scan(true) }
            }
        }

        // 🚀 P0 Tab 13: نمایش شفاف محدوده و روش ارزیابی
        Surface(
            color = PumpWatchPalette.Muted.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                "📊 محدوده: ۱۰۰ ارز برتر با قرارداد Perp در بایننس • آستانهٔ فاندینگ: ۳ برابر میانگین مطلق شبکه",
                modifier = Modifier.padding(10.dp),
                fontSize = 9.sp, color = PumpWatchPalette.Muted, lineHeight = 15.sp
            )
        }

        if (futRules.isNotEmpty()) {
            Surface(
                color = PumpWatchPalette.Warning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("🔔 قوانین فاندینگ تعریف‌شده (${futRules.size}):", fontSize = 10.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                    futRules.take(3).forEach { r ->
                        Text("• ${r.symbol}: ${r.condition.label} ${String.format(Locale.US, "%.4f%%", r.threshold)}", fontSize = 9.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (futRules.size > 3) {
                        Text("... و ${futRules.size - 3} قانون دیگر", fontSize = 9.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }
            }
        }

        // 🚀 P0 Tab 13: نمایش وضعیت‌های Sealed Outcomes
        when {
            loading && alerts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PumpWatchPalette.TabAlerts)
                }
            }
            
            isStale -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Surface(color = PumpWatchPalette.Warning.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⏰ داده‌های بازار کهنه هستند (>۵ دقیقه)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning)
                            Text("برای جلوگیری از ارزیابی گمراه‌کننده، لطفاً داده‌ها را تازه‌سازی کنید.", fontSize = 11.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                            Button(
                                onClick = { scan(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Warning.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("تازه‌سازی فوری", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            
            alerts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "😴 در بازه زمانی فعلی، هیچ ارزی با آستانه‌های تعریف‌شده مطابقت نداشت. بازار در حالت عادی است.",
                        color = PumpWatchPalette.Muted, modifier = Modifier.padding(24.dp), fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }
            }
            
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("${alerts.size} مورد منطبق با آستانه", fontSize = 11.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                    }
                    items(alerts) { a ->
                        // 🚀 P0 Tab 13: خنثی‌سازی عنوان برای رعایت صداقت محصول
                        val neutralTitle = when (a.type) {
                            "FUNDING" -> "انحراف نرخ فاندینگ از میانگین"
                            "MOVE24" -> "تغییر قیمت قابل توجه در ۲۴ ساعت"
                            "OI_SPIKE" -> "تغییر حجم معاملات باز (OI)"
                            else -> a.title
                        }
                        
                        AlertCard(
                            a = AlertView(
                                symbol = a.base,
                                type = a.type,
                                severity = a.severity,
                                title = neutralTitle,
                                detail = a.detail,
                                rank = a.rank,
                                emoji = typeEmoji(a.type),
                                source = "Binance Futures",
                                ageSec = dataAgeSec
                            ),
                            tabKey = "alerts"
                        )
                    }
                }
            }
        }
    }
}
