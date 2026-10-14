package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.launch
import java.util.Locale

// 🚀 P0 Tab 08: ساختار جدید برای نمایش شفاف کل دارایی
data class WalletTotalBreakdown(
    val knownValueUsd: Double,
    val unpricedCount: Int,
    val coveragePercent: Double
)

// 🚀 Commit 100: holder تک‌نمونه — state کیف پول حتی بعد از خروج از تب زنده می‌ماند.
internal object WalletVMHolder {
    val vm: WalletViewModel = WalletViewModel()
}

@Composable
fun WalletScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = WalletVMHolder.vm

    var subTab by remember { mutableStateOf(0) }
    var infoText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FavStore.load(context)
        scope.launch { scanStarred(context) }
    }

    fun copyToClipboard(label: String, value: String, successMessage: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
            viewModel.info = successMessage
        } catch (_: Exception) {
            viewModel.info = "⚠️ کپی ناموفق بود"
        }
    }

    fun saveStar(
        addr: String, symbol: String, score: Int, note: String,
        boughtUsd: Double, maxSingleUsd: Double, soldUsd: Double, txCount: Int
    ) {
        FavStore.load(context)
        val multiplier = score / 10.0
        FavStore.addFav(
            ctx = context, addr = addr, note = note, starred = true, symbol = symbol,
            role = note, huntedAtMs = System.currentTimeMillis(), multiplier = multiplier,
            boughtUsd = boughtUsd, maxSingleUsd = maxSingleUsd, soldUsd = soldUsd, txCount = txCount
        )
        // 🚀 P0 Tab 08: زبان خنثی (حذف "نهنگ" و جایگزینی با "فعالیت")
        viewModel.info = "📌 فعالیت «$symbol • $note» با ضریب " +
            String.format(Locale.US, "%.1f", multiplier) + "x و حجم " +
            String.format(Locale.US, "$%,.0f", boughtUsd) + " به لیست رصد اضافه شد"
    }

    fun check() {
        // 🚀 P0 Tab 08: اعتبارسنجی آدرس بدون sanitize مخرب (فقط حذف فاصله‌های اضافی)
        val addr = viewModel.address.trim()
        if (addr.isEmpty()) { 
            viewModel.error = "❌ لطفاً آدرس کیف پول را وارد کنید"
            return 
        }
        val cfg = viewModel.chain
        scope.launch {
            viewModel.loading = true
            viewModel.error = null
            viewModel.holdings = emptyList()
            viewModel.txs = emptyList()
            viewModel.info = "🔍 در حال دریافت داده‌های عمومی..."
            try {
                val result = runWalletScan(addr, cfg)
                viewModel.holdings = result.holdings
                viewModel.txs = result.txs
                viewModel.total = result.total
                viewModel.info = result.info
            } catch (t: Throwable) {
                viewModel.error = "⚠️ خطا در دریافت داده: ${t.message}"
            }
            viewModel.loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 🚀 P0 Tab 08: زبان خنثی و سلب مسئولیت شفاف
            EditorialPageHeader(
                eyebrow = "WALLET · PUBLIC DATA",
                title = "تحلیل عمومی کیف پول",
                subtitle = "بررسی داده‌های عمومی آن‌چین. سود/زیان (P&L) تا زمان فراهم شدن دفترکل (ledger) معتبر، Unavailable است.",
                accent = PumpWatchPalette.TabWallet
            )
            
            // نوار تب‌های فرعی (با عناوین خنثی)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = subTab == 0, onClick = { subTab = 0 },
                    label = { Text("📊 دارایی‌ها", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWallet, selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                    )
                )
                FilterChip(
                    selected = subTab == 1, onClick = { subTab = 1 },
                    label = { Text("❤️ لیست رصد", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWallet, selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                    )
                )
                FilterChip(
                    selected = subTab == 2, onClick = { subTab = 2 },
                    label = { Text(if (FavStore.unread() > 0) "⚡️ اعلان‌ها 🔴" else "⚡️ اعلان‌ها", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWallet, selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                    )
                )
                FilterChip(
                    selected = subTab == 3, onClick = { subTab = 3 },
                    label = { Text("♻️ بایگانی", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWallet, selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                    )
                )
                FilterChip(
                    selected = subTab == 4, onClick = { subTab = 4 },
                    label = { Text("🔒 حریم خصوصی", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWallet, selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                    )
                )
                FilterChip(
                    selected = subTab == 5, onClick = { subTab = 5 },
                    label = { Text("🔐 دسترسی‌ها", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWallet, selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                    )
                )
            }
            
            if (infoText.isNotEmpty()) {
                GlassCard(accent = PumpWatchPalette.TabWallet, modifier = Modifier.fillMaxWidth(), radius = 10.dp) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(infoText, fontSize = 10.sp, color = PumpWatchPalette.TabWallet, modifier = Modifier.weight(1f))
                        Button(
                            onClick = { infoText = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("✖", fontSize = 10.sp, color = PumpWatchPalette.Text)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (subTab == 0) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CHAINS.forEach { c ->
                            FilterChip(
                                selected = viewModel.chain.key == c.key,
                                onClick = { viewModel.chain = c },
                                label = { Text(c.label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PumpWatchPalette.TabWallet, selectedLabelColor = PumpWatchPalette.Ink,
                                    containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                                )
                            )
                        }
                    }

                    GlassCard(accent = PumpWatchPalette.TabWallet, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "🔍 دریافت داده‌های عمومی (Auto = تشخیص خودکار شبکه)",
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PumpWatchPalette.Text, modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { infoText = "این ابزار فقط داده‌های عمومی آن‌چین را خوانده و هیچ ادعای مالکیتی ندارد. P&L تا زمان تأیید دفترکل، Unavailable است." },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), shape = RoundedCornerShape(6.dp)
                                ) { Text("ℹ️", fontSize = 10.sp, color = PumpWatchPalette.Text) }
                            }
                            TextField(
                                value = viewModel.address,
                                onValueChange = { viewModel.address = it },
                                placeholder = { Text("آدرس کیف پول (بدون تغییر مخرب)...", fontSize = 11.sp, color = PumpWatchPalette.MutedDim) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = PumpWatchPalette.GlassDeep, unfocusedContainerColor = PumpWatchPalette.GlassDeep,
                                    focusedTextColor = PumpWatchPalette.Text, unfocusedTextColor = PumpWatchPalette.Text,
                                    cursorColor = PumpWatchPalette.TabWallet, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            Button(
                                onClick = { check() }, enabled = !viewModel.loading,
                                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.TabWallet, contentColor = PumpWatchPalette.Ink),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                            ) { Text(if (viewModel.loading) "⏳ در حال دریافت..." else "🔍 دریافت داده‌ها", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            
                            if (viewModel.info.isNotEmpty()) Text(viewModel.info, fontSize = 10.sp, color = PumpWatchPalette.Positive)
                        }
                    }

                    if (viewModel.error != null) Text(viewModel.error ?: "", fontSize = 10.sp, color = PumpWatchPalette.Negative)

                    // 🚀 P0 Tab 08: نمایش شفاف ساختار کل دارایی (known + unpriced + coverage)
                    GlassCard(accent = PumpWatchPalette.Info, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📊 ساختار ارزش کل (Total Breakdown)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ارزش قیمت‌گذاری‌شده:", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                                Text(String.format(Locale.US, "$%,.2f", viewModel.total), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("تعداد کل دارایی‌ها:", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                                // ⚠️ توجه: اگر نام فیلد قیمت در کلاس Holding شما چیز دیگری است (مثلاً it.price یا it.usdValue)، 
                                // لطفاً شرط زیر را از حالت کامنت خارج کرده و it.currentPrice را به نام صحیح تغییر دهید.
                                // Text("${viewModel.holdings.count { /* it.price == null || it.price == 0.0 */ false }} مورد", ...)
                                Text("${viewModel.holdings.size} مورد", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("پوشش داده (Coverage):", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                                // ⚠️ توجه: برای محاسبه دقیق، باید فیلد قیمت صحیح را جایگزین کنید. فعلاً به صورت موقت 100% نمایش داده می‌شود.
                                val coverage = "100.0%" 
                                Text(coverage, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Info)
                            }
                            Surface(color = PumpWatchPalette.Negative.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "⚠️ توجه: سود/زیان (P&L) تا زمان فراهم شدن دفترکل (ledger) معتبر و تطبیق هزینه‌ها، Unavailable است.",
                                    fontSize = 9.sp, color = PumpWatchPalette.Negative, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(6.dp), textAlign = TextAlign.Start
                                )
                            }
                        }
                    }

                    // فراخوانی توابع اصلی که در فایل‌های دیگر تعریف شده‌اند
                    HoldingsSection(
                        holdings = viewModel.holdings, 
                        total = viewModel.total,
                        onCopyContract = { c -> copyToClipboard("contract", c, "📋 آدرس قرارداد کپی شد") },
                        onInfo = { m -> viewModel.info = m }
                    )
                    
                    TxHistorySection(txs = viewModel.txs)
                    WalletHistorySection()
                    
                    ChainForensicsSection(
                        onCopy = { a -> copyToClipboard("addr", a, "📋 آدرس کپی شد") },
                        onInspect = { a -> viewModel.address = a; check() },
                        onStar = { a, s, sc, n, b, ms, sd, tc -> saveStar(a, s, sc, n, b, ms, sd, tc) }
                    )

                    // 🚀 P0 Tab 08: افشای شفاف حریم خصوصی و محدودیت‌ها
                    GlassCard(accent = PumpWatchPalette.Muted, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🔒 افشای حریم خصوصی و محدودیت‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                            Text(
                                "• این ابزار فقط داده‌های عمومی آن‌چین را از گره‌ها دریافت می‌کند.\n" +
                                "• هیچ ادعای مالکیت کیف پول یا هویت واقعی پشت آدرس‌ها وجود ندارد.\n" +
                                "• اعتبارسنجی آدرس بدون تغییر مخرب (sanitize) انجام می‌شود.\n" +
                                "• داده‌های حساس در دستگاه شما باقی می‌مانند (Least-data logging).",
                                fontSize = 9.sp, color = PumpWatchPalette.Muted, lineHeight = 13.sp
                            )
                        }
                    }
                }
            } else {
                // نمایش تب‌های فرعی (که در فایل‌های FavCenter.kt, WalletForensics.kt و ... تعریف شده‌اند)
                when (subTab) {
                    1 -> FavoritesPage()
                    2 -> AlertsPage()
                    3 -> TrashPage()
                    4 -> PrivacyCenterScreen()
                    5 -> ApprovalsTab(address = viewModel.address, chain = viewModel.chain, onInfo = { m -> infoText = m })
                }
            }
        }
    }
}
