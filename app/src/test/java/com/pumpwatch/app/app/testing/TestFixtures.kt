package com.pumpwatch.app.testing

import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.engine.AlertRule
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.RuleCondition

/**
 * 🚀 Commit 9: سازنده‌های دادهٔ ثابت برای تست‌ها —
 * بدون شبکه، بدون SharedPreferences، بدون Robolectric.
 */
object TestFixtures {

    fun coin(
        id: String = "bitcoin",
        symbol: String = "BTC",
        name: String = "Bitcoin",
        price: Double = 50_000.0,
        change24h: Double? = 2.0,
        rank: Int? = 1
    ): CoinMarket = CoinMarket(
        id = id,
        symbol = symbol,
        name = name,
        image = "",
        current_price = price,
        market_cap = 1_000_000_000.0,
        total_volume = 100_000_000.0,
        price_change_percentage_24h = change24h,
        market_cap_rank = rank,
        change1h = null,
        change7d = null,
        change1y = null,
        ath = null,
        atl = null,
        ath_change_percentage = null,
        atl_change_percentage = null,
        high24h = null,
        low24h = null
    )

    fun signal(
        symbol: String = "BTC",
        side: String = "BUY",
        status: String = "OPEN",
        time: Long = 0L,
        entry: Double = 50_000.0,
        mode: String = "SPOT"
    ): LoggedSignal = LoggedSignal(
        symbol = symbol,
        side = side,
        entry = entry,
        stop = entry * 0.95,
        target = entry * 1.10,
        status = status,
        time = time,
        mode = mode,
        score = 80
    )

    fun rule(
        id: String = "rule-1",
        symbol: String = "BTC",
        condition: RuleCondition = RuleCondition.PRICE_ABOVE,
        threshold: Double = 60_000.0,
        lastTriggeredTs: Long = 0L
    ): AlertRule = AlertRule(
        id = id,
        symbol = symbol,
        condition = condition,
        threshold = threshold,
        enabled = true,
        createdAt = 0L,
        lastTriggeredTs = lastTriggeredTs
    )
}
