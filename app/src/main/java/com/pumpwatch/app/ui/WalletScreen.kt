package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import kotlinx.coroutines.launch
import java.util.Locale

private val VGreen = Color(0xFF00E676)
private val VRed = Color(0xFFFF5252)
private val VBlue = Color(0xFF40C4FF)
private val VGold = Color(0xFFFFC107)
private val VGray = Color(0xFF8B949E)
private val VPurple = Color(0xFFCE93D8)
private val VOrange = Color(0xFFFFA726)
private val VCard = Color(0xFF1A2230)

@Composable
fun WalletScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var subTab by remember { mutableStateOf(0) }
    var infoText by remember { mutableStateOf("") }

    var chain by remember { mutableStateOf(CHAINS[0]) }
    var address by remember { mutableStateOf("") }
    var holdings by remember { mutableStateOf<List<WalletHolding>>(emptyList()) }
    var txs by remember { mutableStateOf<List<WalletTx>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0.0) }
    var info by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FavStore.load(context)
        scope.launch {
            scanStarred(context)
        }
    }

    fun copyToClipboard(label: String, value: String, successMessage: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
            info = successMessage
        } catch (_: Exception) {
            info = "⚠️ کپی ناموفق بود"
        }
    }

    fun saveStar(
        addr: String,
        symbol: String,
        score: Int,
        note: String,
        boughtUsd: Double,
        maxSingleUsd: Double,
        soldUsd: Double,
        txCount: Int
    ) {
        FavStore.load(context)
        val multiplier = score / 10.0
        FavStore.addFav(
            ctx = context,
            addr = addr,
            note = note,
            starred = true,
            symbol = symbol,
            role = note,
            huntedAtMs = System.currentTimeMillis(),
            multiplier = multiplier,
            boughtUsd = boughtUsd,
            maxSingleUsd = maxSingleUsd,
            soldUsd = soldUsd,
            txCount = txCount
        )
        info = "❤️ نهنگ «$symbol • $note» با ضریب " +
            String.format(Locale.US, "%.1f", multiplier) +
            "x و خرید " +
            String.format(Locale.US, "$%,.0f", boughtUsd) +
            " به پرونده اضافه شد"
    }

    fun check() {
        val addr = address.trim().replace(Regex("[^A-Za-z0-9]"), "")
        if (addr.isEmpty()) {
            error = "❌ آدرس کیف پول رو وارد کن"
            return
        }

        val cfg = chain
        scope.launch {
            loading = true
            error = null
            holdings = emptyList()
            txs = emptyList()
            total = 0.0
            info = "🔍 در حال اسکن کیف پول..."

            try {
                val result = runWalletScan(addr, cfg)
                holdings = result.holdings
                txs = result.txs
                total = result.total
                info = result.info
            } catch (t: Throwable) {
                error = "⚠️ خطا: ${t.message}"
            }

            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "👛 کارآگاه کیف پول",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = VGreen
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    label = { Text("⚙️ موتورها", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    label = { Text("❤️ مورد پسند", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = subTab == 2,
                    onClick = { subTab = 2 },
                    label = {
                        Text(
                            if (FavStore.unread() > 0) "⚡️ هشدار 🔴" else "⚡️ هشدار",
                            fontSize = 11.sp
                        )
                    }
                )
                FilterChip(
                    selected = subTab == 3,
                    onClick = { subTab = 3 },
                    label = { Text("♻️ سطل", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = subTab == 4,
                    onClick = { subTab = 4 },
                    label = { Text("🔒 حریم", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = subTab == 5,
                    onClick = { subTab = 5 },
                    label = { Text("🔐 Approvals", fontSize = 11.sp) }
                )
            }

            if (infoText.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VCard),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            infoText,
                            fontSize = 10.sp,
                            color = VGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { infoText = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = VCard),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("✖", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        when (subTab) {
            1 -> Box(modifier = Modifier.weight(1f)) {
                FavoritesPage()
            }

            2 -> Box(modifier = Modifier.weight(1f)) {
                AlertsPage()
            }

            3 -> Box(modifier = Modifier.weight(1f)) {
                TrashPage()
            }

            4 -> Box(modifier = Modifier.weight(1f)) {
                PrivacyCenterScreen()
            }

            5 -> Box(modifier = Modifier.weight(1f)) {
                ApprovalsTab(
                    address = address,
                    chain = chain,
                    onInfo = { msg -> infoText = msg }
                )
            }

            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CHAINS.forEach { c ->
                        FilterChip(
                            selected = chain.key == c.key,
                            onClick = { chain = c },
                            label = { Text(c.label, fontSize = 10.sp) }
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = VCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "🔍 موتور ۱: بررسی کیف پول مشکوک (Auto = تشخیص خودکار شبکه)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = VBlue,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    infoText = "موتور ۱: آدرس کیف بده → موجودی فعلی همه توکن‌ها + کانترکت با کپی + تاریخ/قیمت اولین مشاهده + سود/زیان واقعی."
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VCard),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("ℹ️", fontSize = 10.sp)
                            }
                        }

                        TextField(
                            value = address,
                            onValueChange = { address = it },
                            placeholder = { Text("آدرس کیف پول...", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = { check() },
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(containerColor = VBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (loading) "⏳ در حال اسکن..." else "🔍 بررسی کیف پول",
                                fontSize = 12.sp
                            )
                        }

                        if (info.isNotEmpty()) {
                            Text(info, fontSize = 10.sp, color = VGreen)
                        }
                    }
                }

                if (error != null) {
                    Text(error ?: "", fontSize = 10.sp, color = VRed)
                }

                HoldingsSection(
                    holdings = holdings,
                    total = total,
                    onCopyContract = { contract ->
                        copyToClipboard("contract", contract, "📋 کانترکت کپی شد")
                    },
                    onInfo = { msg ->
                        info = msg
                    }
                )

                TxHistorySection(txs = txs)

                WalletHistorySection()

                ChainForensicsSection(
                    onCopy = { addr ->
                        copyToClipboard("addr", addr, "📋 آدرس کپی شد")
                    },
                    onInspect = { addr ->
                        address = addr
                        check()
                    },
                    onStar = { addr, symbol, score, note, bought, maxSingle, sold, txCount ->
                        saveStar(addr, symbol, score, note, bought, maxSingle, sold, txCount)
                    }
                )

                Text(
                    "⚠️ داده‌های عمومی آن‌چین — توصیه مالی نیست.",
                    fontSize = 9.sp,
                    color = VGold
                )
            }
        }
    }
}
