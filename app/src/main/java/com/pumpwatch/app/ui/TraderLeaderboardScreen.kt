// ... (بخش importها و تعریف کلاس‌ها بدون تغییر)

@Composable
fun TraderLeaderboardScreen() {
    // ... (کدهای قبلی تا بخش LazyColumn بدون تغییر)

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(traders) { idx, t ->
                            val pos = positions[t.addr]
                            GlassCard(accent = PumpWatchPalette.TabLeaderboard, modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${idx + 1}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = PumpWatchPalette.Warning)
                                        Spacer(Modifier.width(6.dp))
                                        Text(TraderStore.short(t.addr), fontWeight = FontWeight.Black, fontSize = 13.sp, color = PumpWatchPalette.Info)
                                        Spacer(Modifier.weight(1f))
                                        Text("امتیاز: ${TraderStore.scoreOf(t)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = PumpWatchPalette.Info)
                                    }
                                    Text("${t.symbol} • ${t.note} • زنجیره: ${t.chain}", fontSize = 9.sp, color = PumpWatchPalette.Muted)

                                    Text("حجم خرید: ${fmtUsd(t.boughtUsd)} • بزرگ‌ترین تراکنش: ${fmtUsd(t.maxSingleUsd)} • حجم فروش: ${fmtUsd(t.soldUsd)} • ${t.txCount} تراکنش", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                    Text("تعداد پامپ: ${t.pumpsCount} • ضریب: ${String.format(Locale.US, "%.1f", t.multiplier)}x • آخرین فعالیت: ${if (t.lastSeenTs > 0) sdf.format(Date(t.lastSeenTs)) else "بررسی نشده"}", fontSize = 9.sp, color = PumpWatchPalette.Muted)

                                    when {
                                        t.mint.isNullOrBlank() -> Text("📊 موقعیت: بررسی نمی‌شود (کانترکت توکن ثبت نشده)", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                        pos == null -> Text("📊 موقعیت: بررسی نشده", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                        pos.amount > 0.0 -> Text("🟢 موقعیت باز: ${fmtAmt(pos.amount)} ${t.symbol} ≈ ${fmtUsd(pos.valueUsd)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Positive)
                                        else -> Text("🔴 بدون موقعیت باز", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Negative)
                                    }

                                    val entry = t.entryPrice
                                    if (pos != null && pos.amount > 0.0 && pos.priceUsd != null && entry != null && entry > 0.0) {
                                        val pnl = (pos.priceUsd - entry) / entry * 100.0
                                        Text(
                                            "تغییر قیمت تقریبی: ${String.format(Locale.US, "%+.1f", pnl)}% (⚠️ تقریبی — ورود آن‌چین تأیید نشده)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pnl >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative
                                        )
                                    } else if (pos != null && pos.amount > 0.0) {
                                        Text("تغییر قیمت: نامعلوم (قیمت ورود ثبت نشده)", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                    }

                                    // 🚀 P1-3: دکمه‌های اکشن با contentDescription دقیق و اندازهٔ لمسی استاندارد
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FilterChip(
                                            selected = t.alertOn,
                                            onClick = {
                                                try {
                                                    TraderStore.setAlert(context, t.addr, !t.alertOn)
                                                    refresh()
                                                } catch (_: Exception) { }
                                            },
                                            label = { Text("هشدار", fontSize = 10.sp) },
                                            modifier = Modifier.heightIn(min = 48.dp), // 🚀 P1-3: حداقل ارتفاع لمسی
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PumpWatchPalette.TabLeaderboard,
                                                selectedLabelColor = PumpWatchPalette.Ink,
                                                containerColor = PumpWatchPalette.Glass,
                                                labelColor = PumpWatchPalette.Muted
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val p = TraderStore.checkOpenPosition(context, t)
                                                        positions = positions + (t.addr to p)
                                                        info = when {
                                                            p == null -> "⚠️ بررسی موقعیت فقط برای Solana + با کانترکت است"
                                                            p.amount > 0.0 -> "🟢 موقعیت باز پیدا شد"
                                                            else -> "🔴 موقعیتی ندارد"
                                                        }
                                                    } catch (e: Exception) {
                                                        info = "⚠️ خطا: ${e.message}"
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.heightIn(min = 48.dp) // 🚀 P1-3
                                        ) { Text("📊 موقعیت", fontSize = 10.sp, fontWeight = FontWeight.Medium) }
                                        
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val act = TraderStore.checkNewActivity(context, t)
                                                        if (act != null) {
                                                            TraderStore.markSeen(context, t.addr, act.ts, act.txId)
                                                            info = "🔔 فعالیت جدید برای ${TraderStore.short(t.addr)} ثبت شد"
                                                        } else {
                                                            if (t.chain == "solana") {
                                                                TraderStore.markSeen(context, t.addr, max(t.lastSeenTs, System.currentTimeMillis()), t.lastTxId)
                                                                info = "✅ بررسی شد؛ فعالیت جدیدی نبود"
                                                            } else {
                                                                info = "⚠️ بررسی خودکار فعلاً فقط برای Solana است"
                                                            }
                                                        }
                                                        refresh()
                                                    } catch (e: Exception) {
                                                        info = "⚠️ خطا: ${e.message}"
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.heightIn(min = 48.dp) // 🚀 P1-3
                                        ) { Text("🔍 فعالیت", fontSize = 10.sp, fontWeight = FontWeight.Medium) }
                                        
                                        IconButton(
                                            onClick = {
                                                try {
                                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                                        .setPrimaryClip(ClipData.newPlainText("addr", t.addr))
                                                    info = "📋 آدرس کپی شد"
                                                } catch (_: Exception) { info = "⚠️ کپی ناموفق" }
                                            },
                                            modifier = Modifier.size(48.dp) // 🚀 P1-3: اندازهٔ لمسی استاندارد ۴۸dp
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "کپی آدرس کیف پول ${t.symbol}", tint = PumpWatchPalette.Info)
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                try {
                                                    TraderStore.remove(context, t.addr)
                                                    refresh()
                                                    info = "🗑 تریدر حذف شد"
                                                } catch (e: Exception) {
                                                    info = "⚠️ خطا در حذف: ${e.message}"
                                                }
                                            },
                                            modifier = Modifier.size(48.dp) // 🚀 P1-3
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف تریدر ${t.symbol} از لیست ردیابی", tint = PumpWatchPalette.Negative)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
