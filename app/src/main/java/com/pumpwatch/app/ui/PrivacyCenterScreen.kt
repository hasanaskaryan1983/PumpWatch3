package com.pumpwatch.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.KlineCache
import com.pumpwatch.app.data.SecureStorage
import com.pumpwatch.app.store.TradeStore

private val PrGreen = Color(0xFF00E676)
private val PrRed = Color(0xFFFF5252)
private val PrGold = Color(0xFFFFC107)
private val PrGray = Color(0xFF8B949E)
private val PrCard = Color(0xFF1A2230)
private val PrBlue = Color(0xFF40C4FF)

/**
 * 🚀 Sprint 14 (مرحله ۳ / Commit 7D): فهرست صادقانهٔ منابع داده اپ.
 * هر منبع + هدفش — همان چیزی که فرم Data Safety پلی‌استور می‌پرسد.
 * internal تا تست JVM قفلش کند.
 */
internal fun dataProviders(): List<Pair<String, String>> = listOf(
    "CoinGecko" to "قیمت، رتبهٔ بازار و نمودارهای تاریخی",
    "GeckoTerminal" to "استخرهای DEX، میم‌کوین‌ها و نقدینگی",
    "GoPlus" to "بررسی امنیتی قراردادها (honeypot/tax/lock)",
    "Bybit / OKX / Gate.io" to "کندل‌ها و جریان معاملات (زنجیرهٔ فال‌بک)",
    "Binance Futures" to "فاندینگ، Open Interest و aggTrades نهنگ‌ها",
    "سرویس‌های زنجیره‌ای TON / Sui" to "دادهٔ آن‌چین کیف‌پول‌های عمومی"
)

@Composable
private fun StatusRow(title: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 11.sp, color = PrGray, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) PrGreen else PrRed
        )
    }
}

@Composable
fun PrivacyCenterScreen() {
    val context = LocalContext.current
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    // 🚀 Sprint 14 (مرحلهٔ ۰ گزارش / Commit 8C): ورود به صفحهٔ متدولوژی
    var showMethodology by remember { mutableStateOf(false) }

    val keystoreOk = remember { SecureStorage.isKeystoreAvailable() }
    val insecure = remember { SecureStorage.isInsecureFallback(context) }
    val notifGranted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // 🚀 Commit 8C: صفحهٔ متدولوژی جای کل محتوا را می‌گیرد تا «صفحهٔ مستقل» باشد
    if (showMethodology) {
        MethodologyScreen(onBack = { showMethodology = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🔒 مرکز حریم خصوصی", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "این اپ غیرامانی است: هیچ کلید صرافی، seed یا دارایی نگه نمی‌دارد. " +
                "دادهٔ کیف‌پول فقط روی همین دستگاه است.",
            fontSize = 10.sp, color = PrGold, lineHeight = 16.sp
        )

        // ---------- وضعیت امنیتی ----------
        Card(colors = CardDefaults.cardColors(containerColor = PrCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🛡️ وضعیت امنیتی", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrBlue)
                StatusRow(
                    "رمزنگاری داده‌ها",
                    if (keystoreOk) "فعال (AES-256-GCM / Keystore)" else "در دسترس نیست!",
                    keystoreOk
                )
                StatusRow("پشتیبان‌گیری ابری", "غیرفعال (داده به Drive نمی‌رود)", true)
                StatusRow("ترافیک شبکه", "فقط HTTPS", true)
                StatusRow(
                    "مجوز نوتیفیکیشن",
                    if (notifGranted) "داده شده" else "داده نشده — هشدارها نمایش داده نمی‌شوند",
                    notifGranted
                )
                if (insecure) {
                    Text(
                        "⚠️ روی این دستگاه Keystore کار نمی‌کند؛ داده‌ها موقتاً ساده ذخیره شده‌اند. " +
                            "پس از رفع، با اولین ذخیرهٔ جدید دوباره رمزنگاری می‌شوند.",
                        fontSize = 9.sp, color = PrRed, lineHeight = 15.sp
                    )
                }
            }
        }

        // ---------- منابع داده ----------
        Card(colors = CardDefaults.cardColors(containerColor = PrCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("📡 منابع دادهٔ اپ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrBlue)
                dataProviders().forEach { (name, purpose) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrGray)
                        Text(purpose, fontSize = 9.sp, color = PrGray)
                    }
                }
                Text(
                    "هیچ‌کدام از این منابع دادهٔ هویتی شما را دریافت نمی‌کنند؛ فقط دادهٔ عمومی بازار.",
                    fontSize = 9.sp, color = PrGold
                )
            }
        }

        // ---------- 🚀 Commit 8C: درب ورود به متدولوژی ----------
        Button(
            onClick = { showMethodology = true },
            colors = ButtonDefaults.buttonColors(containerColor = PrBlue.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("📖 متدولوژی و ریسک: هر عدد از کجا می‌آید؟", fontSize = 11.sp) }

        // ---------- کنترل کاربر ----------
        Card(colors = CardDefaults.cardColors(containerColor = PrCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎛️ کنترل داده‌ها", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrBlue)

                Button(
                    onClick = {
                        ApiClient.clearMemoryCache()
                        KlineCache.clear()
                        statusMsg = "✅ کش حافظه پاک شد (دادهٔ دیسک دست‌نخورده)"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrBlue.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🧹 پاک‌کردن کش حافظه", fontSize = 11.sp) }

                Button(
                    onClick = {
                        if (!confirmExport) {
                            confirmExport = true
                            statusMsg = "⚠️ خروجی شامل تاریخچهٔ معاملات است. مطمئنی؟"
                        } else {
                            val json = Gson().toJson(TradeStore.load(context))
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "خروجی ledger (فقط به اپ قابل‌اعتماد بده)")
                            )
                            confirmExport = false
                            statusMsg = "📤 خروجی ساخته شد"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrGold.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (confirmExport) "مطمئنی؟ خروجی بگیر" else "📤 خروجی گرفتن از ledger", fontSize = 11.sp) }

                Button(
                    onClick = {
                        if (!confirmWipe) {
                            confirmWipe = true
                            statusMsg = "🚨 با تأیید دوم، همهٔ دادهٔ رمزنگاری‌شده برای همیشه پاک می‌شود!"
                        } else {
                            TradeStore.clearAll(context)
                            SecureStorage.wipeAll(context)
                            confirmWipe = false
                            statusMsg = "🗑️ همهٔ دادهٔ امن پاک شد"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (confirmWipe) PrRed else PrRed.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (confirmWipe) "تأیید نهایی: پاک کن!" else "🗑️ حذف همهٔ داده‌های امن", fontSize = 11.sp) }

                if (statusMsg.isNotEmpty()) {
                    Text(statusMsg, fontSize = 10.sp, color = PrGold, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "💡 این صفحه خودش هیچ داده‌ای ارسال نمی‌کند؛ فقط وضعیت واقعی دستگاه را نشان می‌دهد.",
            fontSize = 9.sp, color = PrGray
        )
    }
}
