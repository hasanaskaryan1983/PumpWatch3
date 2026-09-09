package com.pumpwatch.app.engine

data class LoggedSignal(
    val symbol: String,
    val side: String, // "BUY" or "SELL"
    val entry: Double,
    val stop: Double,
    val target: Double,
    val status: String, // "OPEN", "WIN", "LOSS", "EXP"
    val exitPrice: Double? = null,
    val mode: String = "SPOT", // "SPOT" or "FUT"
    val score: Int = 0,
    // فیلدهای جدید برای Trailing Stop و آپدیت زنده
    val currentPrice: Double? = null,       // قیمت لحظه‌ای
    val highestPrice: Double = 0.0,         // بالاترین قیمتی که دیده (برای تریلینگ استاپ)
    val trailingStop: Double = 0.0,         // استاپ شناور (دنباله‌رو)
    val currentTarget: Double = 0.0         // هدف شناور (که با رشد قیمت جلو می‌ره)
) {
    // متد کمکی برای ساخت نسخه جدید با آپدیت قیمت
    fun updateLivePrice(price: Double): LoggedSignal {
        val newHighest = maxOf(highestPrice.takeIf { it > 0 } ?: entry, price)
        // منطق تریلینگ استاپ: استاپ جدید = حداکثر(استاپ قبلی، ۹۲٪ِ بالاترین قیمت)
        val newTrailingStop = maxOf(trailingStop.takeIf { it > 0 } ?: stop, newHighest * 0.92)
        
        // منطق هدف شناور: اگه قیمت از هدف فعلی رد شد، هدف رو ۱۵٪ ببر جلوتر
        val newTarget = if (price >= currentTarget.takeIf { it > 0 } ?: target) {
            (currentTarget.takeIf { it > 0 } ?: target) * 1.15
        } else {
            currentTarget.takeIf { it > 0 } ?: target
        }

        return this.copy(
            currentPrice = price,
            highestPrice = newHighest,
            trailingStop = newTrailingStop,
            currentTarget = newTarget
        )
    }
}
