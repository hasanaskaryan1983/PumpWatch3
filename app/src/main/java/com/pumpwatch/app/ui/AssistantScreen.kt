private suspend fun analyzeCoin(
    coingeckoId: String?,
    symbol: String, 
    name: String, 
    rank: Int?, 
    isDex: Boolean, 
    chainName: String?
): CoinAnalysis {
    return try {
        var closes: List<Double> = emptyList()
        var volumes: List<Double> = emptyList()
        
        if (isDex) {
            // برای DEX‌ها: دریافت کندل از GeckoTerminal
            try {
                val pool = withContext(Dispatchers.IO) {
                    GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null }
                }
                if (pool != null) {
                    // دریافت داده‌های قیمت از GeckoTerminal
                    // نکته: GeckoTerminal API کندل تاریخی نمی‌ده، فقط قیمت فعلی
                    // پس از داده‌های محدود استفاده می‌کنیم
                    val price = pool.attributes?.priceUsd?.toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        // شبیه‌سازی کندل با داده‌های محدود
                        closes = listOf(price)
                        volumes = listOf(pool.attributes?.volume?.h24 ?: 0.0)
                    }
                }
            } catch (_: Exception) { }
        } else {
            // برای CEX‌ها: دریافت کندل از Binance
            try {
                val klines = withContext(Dispatchers.IO) {
                    BinanceClient.api.klines("${symbol}USDT", "1h", 100)
                }
                closes = klines.map { it[4].toDouble() }
                volumes = klines.map { it[5].toDouble() }
            } catch (_: Exception) { }
        }
        
        // محاسبه اندیکاتورها (فقط اگر کندل کافی داشته باشیم)
        var score = 50
        val indicators = mutableMapOf<String, String>()
        
        if (closes.size >= 35) {
            val rsi = calculateRSI(closes)
            val macdUp = calculateMACD(closes)
            val ema20 = calculateEMA(closes, 20)
            val ema50 = calculateEMA(closes, 50)
            val volumeAvg = volumes.average()
            val currentVolume = volumes.last()
            val volumeRatio = if (volumeAvg > 0) currentVolume / volumeAvg else 1.0
            
            // RSI
            when {
                rsi < 30 -> {
                    score += 15
                    indicators["RSI"] = "${rsi.toInt()} (اشباع فروش ✅)"
                }
                rsi > 70 -> {
                    score -= 15
                    indicators["RSI"] = "${rsi.toInt()} (اشباع خرید ❌)"
                }
                else -> indicators["RSI"] = "${rsi.toInt()} (نرمال ⚪)"
            }
            
            // MACD
            if (macdUp) {
                score += 15
                indicators["MACD"] = "صعودی ✅"
            } else {
                score -= 10
                indicators["MACD"] = "نزولی ❌"
            }
            
            // EMA
            val price = closes.last()
            when {
                price > ema20 && ema20 > ema50 -> {
                    score += 20
                    indicators["EMA"] = "روند صعودی ✅"
                }
                price < ema20 && ema20 < ema50 -> {
                    score -= 20
                    indicators["EMA"] = "روند نزولی ❌"
                }
                else -> indicators["EMA"] = "خنثی ⚪"
            }
            
            // حجم
            when {
                volumeRatio > 2.0 -> {
                    score += 10
                    indicators["حجم"] = "${String.format("%.1f", volumeRatio)}x (بالا 🔥)"
                }
                volumeRatio < 0.5 -> {
                    score -= 5
                    indicators["حجم"] = "${String.format("%.1f", volumeRatio)}x (پایین ⚠️)"
                }
                else -> indicators["حجم"] = "${String.format("%.1f", volumeRatio)}x (نرمال)"
            }
        } else {
            // برای DEX‌ها با داده محدود
            indicators["اندیکاتورها"] = "داده کندل کافی نیست (DEX)"
            indicators["قیمت فعلی"] = if (closes.isNotEmpty()) "$${closes.last()}" else "نامشخص"
        }
        
        // بررسی فعالیت نهنگ‌ها (برای همه)
        val whaleActivity = try {
            val pool = withContext(Dispatchers.IO) {
                GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null }
            }
            if (pool != null) {
                val buys = pool.attributes?.transactions?.h1?.buys ?: 0.0
                val sells = pool.attributes?.transactions?.h1?.sells ?: 0.0
                val total = buys + sells
                val volH1 = pool.attributes?.volume?.h1 ?: 0.0
                val volH24 = pool.attributes?.volume?.h24 ?: 0.0
                
                if (total > 0) {
                    val buyRatio = buys / total * 100
                    buildString {
                        append("فشار خرید ۱س: ${buyRatio.toInt()}٪")
                        when {
                            buyRatio > 60 -> append(" 🟢")
                            buyRatio < 40 -> append(" 🔴")
                            else -> append(" ")
                        }
                        append("\nحجم ۱س: $${String.format("%.0f", volH1)}")
                        append("\nحجم ۲۴س: $${String.format("%.0f", volH24)}")
                    }
                } else "بدون داده"
            } else "داده‌ای موجود نیست"
        } catch (_: Exception) {
            "بدون داده"
        }
        
        // توصیه نهایی
        val recommendation = when {
            isDex && closes.size < 35 -> "تحلیل محدود (DEX) ⚪"
            score >= 80 -> "خرید قوی 🟢"
            score >= 65 -> "خرید ✅"
            score >= 45 -> "صبر "
            score >= 30 -> "فروش 🔴"
            else -> "فروش قوی 🔴"
        }
        
        // دلیل انتخاب
        val reason = buildString {
            if (rank != null) append("رتبه بازار: #$rank • ")
            if (isDex && chainName != null) append("شبکه: $chainName • ")
            append("امتیاز: $score/100 • ")
            when {
                isDex && closes.size < 35 -> append("DEX — فقط داده نهنگی موجوده")
                score >= 65 -> append("هم‌راستایی اندیکاتورها + حجم بالا")
                score <= 35 -> append("ضعف اندیکاتورها + کاهش حجم")
                else -> append("بازار خنثی — منتظر شکست بمون")
            }
        }
        
        // امتیاز اعتبار
        val trustScore = when {
            rank != null && rank <= 10 -> 90
            rank != null && rank <= 50 -> 75
            rank != null && rank <= 100 -> 60
            rank != null && rank <= 500 -> 50
            rank != null -> 40
            isDex -> 30
            else -> 50
        }
        
        CoinAnalysis(
            symbol = symbol,
            name = name,
            coingeckoId = coingeckoId,
            rank = rank,
            score = score.coerceIn(0, 100),
            recommendation = recommendation,
            indicators = indicators,
            whaleActivity = whaleActivity,
            reason = reason,
            trustScore = trustScore,
            isDex = isDex,
            chainName = chainName
        )
    } catch (e: Exception) {
        CoinAnalysis(
            symbol = symbol,
            name = name,
            coingeckoId = coingeckoId,
            rank = rank,
            score = 50,
            recommendation = "صبر ⚪",
            indicators = mapOf("خطا" to "داده کافی نیست"),
            whaleActivity = "بدون داده",
            reason = "خطا در تحلیل: ${e.message}",
            trustScore = 50,
            isDex = isDex,
            chainName = chainName
        )
    }
}
