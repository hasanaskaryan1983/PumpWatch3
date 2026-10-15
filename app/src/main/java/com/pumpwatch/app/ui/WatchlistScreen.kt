// ... (بخش importها و تعریف کلاس‌ها بدون تغییر)

@Composable
private fun WatchlistCoinRow(
    coin: WatchlistCoin,
    priceText: String,
    changeText: String,
    changeColor: Color,
    coverageNote: String?,
    onAlertClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(coin.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                    Text(coin.name, fontSize = 10.sp, color = PumpWatchPalette.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(priceText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                    if (changeText.isNotEmpty()) {
                        Text(changeText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = changeColor)
                    }
                }
                // 🚀 P1-3: دکمه‌های اکشن با اندازهٔ لمسی ۴۸dp و توضیح دقیق
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onAlertClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Notifications, 
                            contentDescription = "مدیریت هشدارهای قیمت برای ${coin.symbol}", 
                            tint = if (coin.alerts.isNotEmpty()) PumpWatchPalette.Warning else PumpWatchPalette.Muted
                        )
                    }
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "حذف ${coin.symbol} از این دستهٔ واچ‌لیست", 
                            tint = PumpWatchPalette.Negative
                        )
                    }
                }
            }

            if (coverageNote != null) {
                Text("⚪ $coverageNote", fontSize = 8.sp, color = PumpWatchPalette.Muted)
            }

            if (coin.alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${coin.alerts.size} هشدار فعال", fontSize = 9.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                    coin.alerts.take(3).forEach { alert ->
                        val conditionText = when (alert.condition) {
                            AlertCondition.ABOVE -> "⬆️ > ${alert.price}"
                            AlertCondition.BELOW -> "⬇️ < ${alert.price}"
                            AlertCondition.CHANGE_UP -> "📈 +${alert.price}%"
                            AlertCondition.CHANGE_DOWN -> "📉 ${alert.price}%"
                        }
                        Text(conditionText, fontSize = 8.sp, color = if (alert.isActive) PumpWatchPalette.Positive else PumpWatchPalette.Muted)
                    }
                }
            }
        }
    }
}
