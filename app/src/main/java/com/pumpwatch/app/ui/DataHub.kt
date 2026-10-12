// ... (بخش‌های MarketHub و MemeHub دست‌نخورده باقی بمانند) ...

object FuturesHub {
    // ... (کدهای dashboardSignals, scannerResults, alertsList و توابع scan آنها دقیقاً مثل قبل باشند) ...
    
    // 🔧 اضافه‌شده: کش نمودار برای جلوگیری از اسکن مجدد هنگام جابجایی تب
    var chartSymbol: String = "BTCUSDT"; private set
    var chartTf: String = "4h"; private set
    var chartCandles: List<com.pumpwatch.app.data.FuturesCandle> = emptyList(); private set
    var chartRegimes: Map<String, com.pumpwatch.app.engine.FuturesScannerEngine.Regime> = emptyMap(); private set
    var chartLastMs: Long = 0L; private set
    var chartLoading: Boolean = false; private set

    suspend fun loadChart(sym: String, tf: String, loadFunc: suspend () -> Pair<List<com.pumpwatch.app.data.FuturesCandle>, Map<String, com.pumpwatch.app.engine.FuturesScannerEngine.Regime>>) {
        // اگر همان نماد و تایم‌فریم است و کمتر از ۵ دقیقه گذشته، از کش استفاده کن
        val fresh = System.currentTimeMillis() - chartLastMs < 5 * 60_000L
        if (chartSymbol == sym && chartTf == tf && chartCandles.isNotEmpty() && fresh) return
        
        chartLoading = true
        try {
            val (kl, regMap) = loadFunc()
            if (kl.isNotEmpty()) {
                chartSymbol = sym
                chartTf = tf
                chartCandles = kl
                chartRegimes = regMap
                chartLastMs = System.currentTimeMillis()
            }
        } finally {
            chartLoading = false
        }
    }
}
