package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VGreen = Color(0xFF00E676)
private val VRed = Color(0xFFFF5252)
private val VGold = Color(0xFFFFC107)
private val VGray = Color(0xFF8B949E)
private val VCard = Color(0xFF1A2230)

@Composable
internal fun HoldingsSection(
    holdings: List<WalletHolding>,
    total: Double,
    onCopyContract: (String) -> Unit,
    onInfo: (String) -> Unit
) {
    if (holdings.isEmpty()) return

    val sdfBuy = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    val trackedCost = holdings.sumOf { h ->
        val e = h.buyPrice ?: 0.0
        val p = h.price ?: 0.0
        if (e > 0.0 && p > 0.0) e * h.amount else 0.0
    }

    val trackedValue = holdings.sumOf { h ->
        val e = h.buyPrice ?: 0.0
        val p = h.price ?: 0.0
        if (e > 0.0 && p > 0.0) p * h.amount else 0.0
    }

    val pnl = trackedValue - trackedCost
    val pnlPct = if (trackedCost > 0.0) (pnl / trackedCost) * 100.0 else 0.0
    val pnlSign = if (pnl >= 0.0) "+" else ""
    val pnlColor = if (pnl >= 0.0) VGreen else VRed

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = VCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("💰 ارزش کل", fontSize = 10.sp, color = VGray)
                        Text(
                            String.format(Locale.US, "$%,.2f", total),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = VGreen
                        )
                    }

                    if (trackedCost > 0.0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("📈 سود/زیان قابل محاسبه", fontSize = 10.sp, color = VGray)
                            Text(
                                pnlSign +
                                    String.format(Locale.US, "$%,.2f", pnl) +
                                    " (" +
                                    pnlSign +
                                    String.format(Locale.US, "%.1f", pnlPct) +
                                    "%)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = pnlColor
                            )
                        }
                    }
                }

                if (trackedCost <= 0.0) {
                    Text(
                        "❓ قیمت خرید معتبر برای هیچ توکنی موجود نیست؛ سود/زیان محاسبه نشد.",
                        fontSize = 9.sp,
                        color = VGold
                    )
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
        ) {
            items(holdings) { h ->
                val cur = h.price
                val entry = h.buyPrice

                Card(
                    colors = CardDefaults.cardColors(containerColor = VCard),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    h.symbol,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )

                                if (cur != null && cur > 0) {
                                    Text(
                                        "مقدار: " +
                                            String.format(Locale.US, "%.4f", h.amount) +
                                            " • قیمت: " +
                                            String.format(Locale.US, "$%.6f", cur),
                                        fontSize = 9.sp,
                                        color = VGray
                                    )

                                    if (entry != null && entry > 0) {
                                        val itemPnl = (cur - entry) * h.amount
                                        val itemPct = ((cur - entry) / entry) * 100.0
                                        val itemSign = if (itemPnl >= 0.0) "+" else ""
                                        val itemColor = if (itemPnl >= 0.0) VGreen else VRed

                                        Text(
                                            "سود/زیان: " +
                                                itemSign +
                                                String.format(Locale.US, "$%,.2f", itemPnl) +
                                                " (" +
                                                itemSign +
                                                String.format(Locale.US, "%.1f", itemPct) +
                                                "%)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = itemColor
                                        )
                                    } else {
                                        Text(
                                            "سود/زیان: ❓ قیمت خرید نامشخص",
                                            fontSize = 9.sp,
                                            color = VGray
                                        )
                                    }
                                } else {
                                    Text(
                                        "مقدار: " +
                                            String.format(Locale.US, "%.4f", h.amount) +
                                            " • قیمت: ❓ نامشخص",
                                        fontSize = 9.sp,
                                        color = VGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                String.format(Locale.US, "$%,.2f", h.value),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VGreen
                            )
                        }

                        val fts = h.firstBuyTs
                        if (fts != null) {
                            val entryText = if (entry != null && entry > 0) {
                                String.format(Locale.US, "$%.6f", entry)
                            } else {
                                "توی CoinGecko لیست نشده"
                            }

                            Text(
                                "🕐 اولین مشاهده: " +
                                    sdfBuy.format(Date(fts)) +
                                    " • قیمت آن روز: " +
                                    entryText,
                                fontSize = 9.sp,
                                color = VGold,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val contract = h.contract
                        if (contract != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "کانترکت: " + contract,
                                    fontSize = 8.sp,
                                    color = VGray,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = {
                                        onCopyContract(contract)
                                        onInfo("📋 کانترکت کپی شد")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VCard),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("📋 کپی", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TxHistorySection(txs: List<WalletTx>) {
    if (txs.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "📜 تاریخچه معاملات:",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
        ) {
            items(txs) { t ->
                val px = t.priceUsd

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
                            if (t.incoming) "🟢" else "🔴",
                            fontSize = 14.sp
                        )

                        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                (if (t.incoming) "خرید/ورود " else "فروش/خروج ") + t.symbol,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                "تاریخ: " +
                                    t.dateText +
                                    " • مقدار: " +
                                    String.format(Locale.US, "%.4f", t.amount),
                                fontSize = 9.sp,
                                color = VGray
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (px != null && px > 0) {
                                Text(
                                    "قیمت اون روز: " + String.format(Locale.US, "$%.6f", px),
                                    fontSize = 9.sp,
                                    color = VGold
                                )
                                Text(
                                    "ارزش: " + String.format(Locale.US, "$%.2f", t.amount * px),
                                    fontSize = 10.sp,
                                    color = if (t.incoming) VGreen else VRed
                                )
                            } else {
                                Text(
                                    "قیمت اون روز: ❓ نامشخص",
                                    fontSize = 9.sp,
                                    color = VGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "ارزش: ❓",
                                    fontSize = 10.sp,
                                    color = VGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
