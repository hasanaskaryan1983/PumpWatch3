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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.components.GlassCard // 🚀 اضافه شد
import com.pumpwatch.app.ui.design.TabPalette // 🚀 اضافه شد
import kotlinx.coroutines.launch
import java.util.Locale

private val VGreen = Color(0xFF00E676)
private val VRed = Color(0xFFFF5252)
private val VBlue = Color(0xFF40C4FF)
private val VGold = Color(0xFFFFC107)
private val VGray = Color(0xFF8B949E)

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
        viewModel.info = "❤️ نهنگ «$symbol • $note» با ضریب " +
            String.format(Locale.US, "%.1f", multiplier) + "x و خرید " +
            String.format(Locale.US, "$%,.0f", boughtUsd) + " به پرونده اضافه شد"
    }

    fun check() {
        val addr = viewModel.address.trim().replace(Regex("[^A-Za-z0-9]"), "")
        if (addr.isEmpty()) { viewModel.error = "❌ آدرس کیف پول رو وارد کن"; return }
        val cfg = viewModel.chain
        scope.launch {
            viewModel.loading = true
            viewModel.error = null
            viewModel.holdings = emptyList()
            viewModel.txs = emptyList()
            viewModel.total = 0.0
            viewModel.info = "🔍 در حال اسکن کیف پول..."
            try {
                val result = runWalletScan(addr, cfg)
                viewModel.holdings = result.holdings
                viewModel.txs = result.txs
                viewModel.total = result.total
                viewModel.info = result.info
            } catch (t: Throwable) {
                viewModel.error = "⚠️ خطا: ${t.message}"
            }
            viewModel.loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("👛 کارآگاه کیف پول", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("⚙️ موتورها", fontSize = 11.sp) })
                FilterChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("❤️ مورد پسند", fontSize = 11.sp) })
                FilterChip(selected = subTab == 2, onClick = { subTab = 2 },
                    label = { Text(if (FavStore.unread() > 0) "⚡️ هشدار 🔴" else "⚡️ هشدار", fontSize = 11.sp) })
                FilterChip(selected = subTab == 3, onClick = { subTab = 3 }, label = { Text("♻️ سطل", fontSize = 11.sp) })
                FilterChip(selected = subTab == 4, onClick = { subTab = 4 }, label = { Text("🔒 حریم", fontSize = 11.sp) })
                FilterChip(selected = subTab == 5, onClick = { subTab = 5 }, label = { Text("🔐 Approvals", fontSize = 11.sp) })
            }
            if (infoText.isNotEmpty()) {
                // 🚀 مهاجرت به GlassCard
                GlassCard(accent = TabPalette.Wallet, modifier = Modifier.fillMaxWidth(), radius = 10.dp) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(infoText, fontSize = 10.sp, color = TabPalette.Wallet, modifier = Modifier.weight(1f))
                        Button(onClick = { infoText = "" }, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), shape = RoundedCornerShape(6.dp)) { 
                            Text("✖", fontSize = 10.sp, color = Color(0xFFE6EDF3)) 
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .then(if (subTab == 0) Modifier.fillMaxSize() else Modifier.size(0.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CHAINS.forEach { c ->
                        FilterChip(selected = viewModel.chain.key == c.key, onClick = { viewModel.chain = c }, label = { Text(c.label, fontSize = 10.sp) })
                    }
                }

                // 🚀 مهاجرت به GlassCard
                GlassCard(accent = TabPalette.Wallet, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔍 موتور ۱: بررسی کیف پول مشکوک (Auto = تشخیص خودکار شبکه)",
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE6EDF3), modifier = Modifier.weight(1f))
                            Button(onClick = { infoText = "موتور ۱: آدرس کیف بده → موجودی فعلی همه توکن‌ها + کانترکت با کپی + تاریخ/قیمت اولین مشاهده + سود/زیان واقعی." },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), shape = RoundedCornerShape(6.dp)) { 
                                Text("ℹ️", fontSize = 10.sp, color = Color(0xFFE6EDF3)) 
                            }
                        }
                        TextField(value = viewModel.address, onValueChange = { viewModel.address = it },
                            placeholder = { Text("آدرس کیف پول...", fontSize = 11.sp, color = VGray) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                        Button(onClick = { check() }, enabled = !viewModel.loading,
                            colors = ButtonDefaults.buttonColors(containerColor = TabPalette.Wallet),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(if (viewModel.loading) "⏳ در حال اسکن..." else "🔍 بررسی کیف پول", fontSize = 12.sp, color = Color.Black)
                        }
                        if (viewModel.info.isNotEmpty()) Text(viewModel.info, fontSize = 10.sp, color = VGreen)
                    }
                }

                if (viewModel.error != null) Text(viewModel.error ?: "", fontSize = 10.sp, color = VRed)

                // ⚠️ توابع زیر (HoldingsSection, TxHistorySection, ...) را در ادامهٔ فایل خودتان نگه دارید.
                // آن‌ها را اینجا کپی نکردم تا فایل شما ناقص نشود. فقط کافی است importهای بالا را اضافه کنید.
                HoldingsSection(
                    holdings = viewModel.holdings,
                    total = viewModel.total,
                    onCopyContract = { c -> copyToClipboard("contract", c, "📋 کانترکت کپی شد") },
                    onInfo = { m -> viewModel.info = m }
                )

                TxHistorySection(txs = viewModel.txs)
                WalletHistorySection()

                ChainForensicsSection(
                    onCopy = { a -> copyToClipboard("addr", a, "📋 آدرس کپی شد") },
                    onInspect = { a -> viewModel.address = a; check() },
                    onStar = { a, s, sc, n, b, ms, sd, tc -> saveStar(a, s, sc, n, b, ms, sd, tc) }
                )

                Text("⚠️ داده‌های عمومی آن‌چین — توصیه مالی نیست.", fontSize = 9.sp, color = VGold)
            }

            if (subTab != 0) {
                Box(modifier = Modifier.fillMaxSize()) {
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
}
// ⬇️⬇️⬇️ بقیهٔ فایل خودتان (توابع HoldingsSection و ...) دقیقاً همین‌جا ادامه پیدا می‌کند ⬇️⬇️⬇️
