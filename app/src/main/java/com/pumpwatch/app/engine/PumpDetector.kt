package com.pumpwatch.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object PumpDetector {
    
    /**
     * تشخیص زودهنگام پامپ
     * @return امتیاز پامپ (0-100) - هرچه بالاتر، احتمال پامپ بیشتر
     */
    fun detectEarlyPump(
        closes: List<Double>,
        volumes: List<Double>
    ): Int {
        if (closes.size < 50 || volumes.size < 50) return 0
        
        val price = closes.last()
        val prevPrice = closes[closes.size - 2]
        
        // 1. Volume Spike (افزایش ناگهانی حجم)
        val avgVol = volumes.dropLast(1).takeLast(20).average()
        val currentVol = volumes.last()
        val volSpike = if (avgVol > 0) (currentVol / avgVol - 1) * 100 else 0.0
        
        // 2. Price Acceleration (شتاب قیمت)
        val priceChange1h = if (prevPrice > 0) (price - prevPrice) / prevPrice * 100 else 0.0
        val priceChange4h = if (closes.size >= 5) {
            val p4h = closes[closes.size - 5]
            if (p4h > 0) (price - p4h) / p4h * 100 else 0.0
        } else 0.0
        
        // 3. Breakout Detection (شکست مقاومت)
        val high20 = closes.takeLast(20).maxOrNull() ?: price
        val isBreakout = price > high20 * 0.98 && priceChange1h > 2
        
        // 4. Momentum (شتاب روند)
        val e5 = emaLast(closes, 5)
        val e20 = emaLast(closes, 20)
        val momentum = if (e20 > 0) (e5 - e20) / e20 * 100 else 0.0
        
        // محاسبه امتیاز پامپ
        var pumpScore = 0
        
        // Volume Spike: 30 امتیاز
        if (volSpike > 100) pumpScore += 30
        else if (volSpike > 50) pumpScore += 20
        else if (volSpike > 25) pumpScore += 10
        
        // Price Acceleration: 30 امتیاز
        if (priceChange1h > 5) pumpScore += 30
        else if (priceChange1h > 3) pumpScore += 20
        else if (priceChange1h > 1) pumpScore += 10
        
        // Breakout: 20 امتیاز
        if (isBreakout) pumpScore += 20
        
        // Momentum: 20 امتیاز
        if (momentum > 3) pumpScore += 20
        else if (momentum > 1) pumpScore += 10
        
        return pumpScore.coerceIn(0, 100)
    }
    
    /**
     * بررسی اینکه آیا ارز در سقف قیمتی هست
     * @return true اگر در سقف هست (نباید سیگنال خرید داد)
     */
    fun isAtTop(
        closes: List<Double>,
        volumes: List<Double>
    ): Boolean {
        if (closes.size < 50) return false
        
        val price = closes.last()
        val e20 = emaLast(closes, 20)
        val rsi = rsiOf(closes)
        
        // فاصله از EMA20
        val distanceFromEma = if (e20 > 0) (price - e20) / e20 * 100 else 0.0
        
        // تغییر قیمت 24 ساعته
        val price24hAgo = closes[closes.size - 24]
        val change24h = if (price24hAgo > 0) (price - price24hAgo) / price24hAgo * 100 else 0.0
        
        // تغییر قیمت 4 ساعته
        val price4hAgo = closes[closes.size - 4]
        val change4h = if (price4hAgo > 0) (price - price4hAgo) / price4hAgo * 100 else 0.0
        
        // RSI اشباع
        val isOverbought = rsi > 75
        
        // پامپ شدید
        val isPumped = change24h > 20 || change4h > 10
        
        // فاصله زیاد از EMA
        val isExtended = distanceFromEma > 8
        
        // اگه 2 تا از 3 شرط برقرار باشه = در سقف
        var topSignals = 0
        if (isOverbought) topSignals++
        if (isPumped) topSignals++
        if (isExtended) topSignals++
        
        return topSignals >= 2
    }
    
    /**
     * محاسبه امتیاز نهایی با فیلترهای ضد سقف
     */
    fun calculateFinalScore(
        baseScore: Int,
        closes: List<Double>,
        volumes: List<Double>,
        pumpScore: Int
    ): Int {
        var score = baseScore
        
        // اگه در سقف هست، امتیاز رو کم کن
        if (isAtTop(closes, volumes)) {
            score -= 40  // جریمه سنگین
        }
        
        // اگه پامپ زودهنگام تشخیص داده شد، امتیاز اضافه کن
        if (pumpScore >= 60) {
            score += 15  // پاداش تشخیص زودهنگام
        }
        
        return score.coerceIn(-100, 100)
    }
    
    // توابع کمکی
    private fun emaLast(data: List<Double>, period: Int): Double {
        if (data.size < period) return data.lastOrNull() ?: 0.0
        val k = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) ema = data[i] * k + ema * (1 - k)
        return ema
    }
    
    private fun rsiOf(data: List<Double>, period: Int = 14): Double {
        if (data.size <= period) return 50.0
        var g = 0.0
        var l = 0.0
        for (i in 1..period) {
            val d = data[i] - data[i - 1]
            if (d > 0) g += d else l -= d
        }
        var ag = g / period
        var al = l / period
        for (i in period + 1 until data.size) {
            val d = data[i] - data[i - 1]
            ag = (ag * (period - 1) + max(d, 0.0)) / period
            al = (al * (period - 1) + max(-d, 0.0)) / period
        }
        if (al == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + ag / al)
    }
}
