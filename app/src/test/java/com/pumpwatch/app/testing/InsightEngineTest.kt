package com.pumpwatch.app.testing

import com.pumpwatch.app.engine.InsightEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🚀 P2-3: تست واحد برای موتور بینش هوشمند.
 * اثبات می‌کند که پیام‌های درست در شرایط مختلف تولید می‌شوند.
 */
class InsightEngineTest {

    @Test
    fun `returns minimum trades message when closed count is less than 3`() {
        val result = InsightEngine.generateSmartInsight(
            closedCount = 2,
            maxDD = 10.0,
            profitFactor = 1.2,
            winRate = 50.0,
            bestTier = null,
            bestTierWinRate = null,
            expectancy = 0.3
        )
        assertEquals("📈 حداقل ۳ معامله بسته‌شده نیاز است تا بینش هوشمند تولید شود.", result)
    }

    @Test
    fun `returns max DD warning when drawdown exceeds 25 percent`() {
        val result = InsightEngine.generateSmartInsight(
            closedCount = 10,
            maxDD = 30.0,
            profitFactor = 1.2,
            winRate = 50.0,
            bestTier = null,
            bestTierWinRate = null,
            expectancy = 0.3
        )
        assert(result.contains("Max DD"))
        assert(result.contains("30.0"))
    }

    @Test
    fun `returns profit factor insight when PF is high but win rate is low`() {
        val result = InsightEngine.generateSmartInsight(
            closedCount = 10,
            maxDD = 15.0,
            profitFactor = 2.0,
            winRate = 40.0,
            bestTier = null,
            bestTierWinRate = null,
            expectancy = 0.3
        )
        assert(result.contains("سودده است"))
        assert(result.contains("PF > 1.5"))
    }

    @Test
    fun `returns best tier insight when a tier has win rate above 60`() {
        val result = InsightEngine.generateSmartInsight(
            closedCount = 10,
            maxDD = 15.0,
            profitFactor = 1.3,
            winRate = 50.0,
            bestTier = "DEX",
            bestTierWinRate = 70.0,
            expectancy = 0.3
        )
        assert(result.contains("DEX"))
        assert(result.contains("70"))
    }

    @Test
    fun `returns expectancy insight when expectancy is positive and high`() {
        val result = InsightEngine.generateSmartInsight(
            closedCount = 10,
            maxDD = 15.0,
            profitFactor = 1.3,
            winRate = 50.0,
            bestTier = "DEX",
            bestTierWinRate = 55.0,
            expectancy = 0.8
        )
        assert(result.contains("امید ریاضی مثبت"))
    }

    @Test
    fun `returns data collection message when no specific insight applies`() {
        val result = InsightEngine.generateSmartInsight(
            closedCount = 10,
            maxDD = 15.0,
            profitFactor = 1.3,
            winRate = 50.0,
            bestTier = "DEX",
            bestTierWinRate = 55.0,
            expectancy = 0.3
        )
        assertEquals("📊 در حال جمع‌آوری داده‌های بیشتر برای تحلیل دقیق‌تر الگوی معاملاتی شما...", result)
    }
}
