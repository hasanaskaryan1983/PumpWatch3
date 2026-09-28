package com.pumpwatch.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 84 (W4 + L1 + W5): تست pure برای findCoinByContractOrId.
 *
 * هدف: اثبات اینکه ticker collision حل شده و چک «لیست‌شده در CoinGecko»
 * فقط با contract match معتبر سبز می‌شود.
 */
class CoinLookupTest {

    /** helper ساده برای ساخت CoinMarket نمونه (فیلدهای زیاد را null می‌گذارد) */
    private fun coin(
        id: String,
        symbol: String,
        rank: Int? = null
    ): CoinMarket = CoinMarket(
        id = id,
        symbol = symbol,
        name = symbol,
        image = "",
        current_price = 1.0,
        market_cap = 1_000_000.0,
        total_volume = 100_000.0,
        price_change_percentage_24h = 0.0,
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

    @Test
    fun `contract match beats ticker match (W4 scenario)`() = runBlocking {
        // دو توکن با تیکر یکسان PEPE روی دو chain متفاوت
        val coins = listOf(
            coin(id = "pepe-erc20", symbol = "PEPE", rank = 25),
            coin(id = "pepe-base-scam", symbol = "PEPE", rank = null)
        )
        val platformMap = mapOf(
            "pepe-erc20" to mapOf("ethereum" to "0x6982508145454Ce325dDbE47a25d4ec3d2311933"),
            "pepe-base-scam" to mapOf("base" to "0xDEADBEEF00000000000000000000000000000000")
        )

        // کاربر استخری با contract اسکم را می‌بیند
        val (match, ambiguous) = findCoinByContractOrId(
            coins = coins,
            contract = "0xDEADBEEF00000000000000000000000000000000",
            chain = "base",
            ticker = "PEPE",
            platformMap = platformMap
        )

        assertNotNull("match should not be null", match)
        assertEquals("pepe-base-scam", match!!.id)
        assertFalse("contract match is unambiguous", ambiguous)
    }

    @Test
    fun `ticker fallback returns ambiguous flag (W5)`() = runBlocking {
        val coins = listOf(coin(id = "solana", symbol = "SOL", rank = 5))

        val (match, ambiguous) = findCoinByContractOrId(
            coins = coins,
            contract = null,
            chain = null,
            ticker = "SOL",
            platformMap = null
        )

        assertNotNull(match)
        assertEquals("solana", match!!.id)
        assertTrue("ticker-only match must be flagged ambiguous", ambiguous)
    }

    @Test
    fun `contract case insensitive match`() = runBlocking {
        val coins = listOf(coin(id = "usdt", symbol = "USDT", rank = 4))
        val platformMap = mapOf(
            "usdt" to mapOf("ethereum" to "0xdac17f958d2ee523a2206206994597c13d831ec7")
        )

        val (match, ambiguous) = findCoinByContractOrId(
            coins = coins,
            contract = "0xDAC17F958D2EE523A2206206994597C13D831EC7",  // uppercase
            chain = "ethereum",
            ticker = "USDT",
            platformMap = platformMap
        )

        assertNotNull(match)
        assertEquals("usdt", match!!.id)
        assertFalse(ambiguous)
    }

    @Test
    fun `chain alias match (eth to ethereum)`() = runBlocking {
        val coins = listOf(coin(id = "weth", symbol = "WETH", rank = 20))
        val platformMap = mapOf(
            "weth" to mapOf("ethereum" to "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2")
        )

        val (match, ambiguous) = findCoinByContractOrId(
            coins = coins,
            contract = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",
            chain = "eth",  // alias
            ticker = "WETH",
            platformMap = platformMap
        )

        assertNotNull(match)
        assertEquals("weth", match!!.id)
        assertFalse(ambiguous)
    }

    @Test
    fun `chain alias match (bsc to binance-smart-chain)`() = runBlocking {
        val coins = listOf(coin(id = "cake", symbol = "CAKE", rank = 120))
        val platformMap = mapOf(
            "cake" to mapOf("binance-smart-chain" to "0x0E09FaBB73Bd3Ade0a17ECC321fD13a19e81cE82")
        )

        val (match, ambiguous) = findCoinByContractOrId(
            coins = coins,
            contract = "0x0E09FaBB73Bd3Ade0a17ECC321fD13a19e81cE82",
            chain = "bsc",  // alias
            ticker = "CAKE",
            platformMap = platformMap
        )

        assertNotNull(match)
        assertEquals("cake", match!!.id)
        assertFalse(ambiguous)
    }

    @Test
    fun `no match returns null`() = runBlocking {
        val coins = listOf(coin(id = "bitcoin", symbol = "BTC", rank = 1))

        val (match, ambiguous) = findCoinByContractOrId(
            coins = coins,
            contract = "0xNonExistent",
            chain = "ethereum",
            ticker = "NONEXIST",
            platformMap = emptyMap()
        )

        assertNull(match)
        assertFalse(ambiguous)
    }

    @Test
    fun `empty coins returns null`() = runBlocking {
        val (match, ambiguous) = findCoinByContractOrId(
            coins = emptyList(),
            contract = "0xabc",
            chain = "eth",
            ticker = "TEST",
            platformMap = null
        )
        assertNull(match)
        assertFalse(ambiguous)
    }

    @Test
    fun `null platformMap falls back to ticker`() = runBlocking {
        val coins = listOf(coin(id = "bitcoin", symbol = "BTC", rank = 1))

        val (match, ambiguous) = findCoinByContractOrId(
            coins = coins,
            contract = "0xabc",
            chain = "eth",
            ticker = "BTC",
            platformMap = null
        )

        assertNotNull(match)
        assertEquals("bitcoin", match!!.id)
        assertTrue("no platformMap = ticker fallback = ambiguous", ambiguous)
    }

    @Test
    fun `same ticker different chain are distinguished (W4 scenario)`() = runBlocking {
        // سه PEPE روی سه chain متفاوت
        val coins = listOf(
            coin(id = "pepe", symbol = "PEPE", rank = 25),
            coin(id = "pepe-base", symbol = "PEPE", rank = null),
            coin(id = "pepe-arb", symbol = "PEPE", rank = null)
        )
        val platformMap = mapOf(
            "pepe" to mapOf("ethereum" to "0x6982508145454Ce325dDbE47a25d4ec3d2311933"),
            "pepe-base" to mapOf("base" to "0xAAAA"),
            "pepe-arb" to mapOf("arbitrum-one" to "0xBBBB")
        )

        // چک Arbitrum
        val (match1, amb1) = findCoinByContractOrId(coins, "0xBBBB", "arbitrum", "PEPE", platformMap)
        assertEquals("pepe-arb", match1?.id)
        assertFalse(amb1)

        // چک Base
        val (match2, amb2) = findCoinByContractOrId(coins, "0xAAAA", "base", "PEPE", platformMap)
        assertEquals("pepe-base", match2?.id)
        assertFalse(amb2)

        // چک Ethereum
        val (match3, amb3) = findCoinByContractOrId(coins, "0x6982508145454Ce325dDbE47a25d4ec3d2311933", "ethereum", "PEPE", platformMap)
        assertEquals("pepe", match3?.id)
        assertFalse(amb3)
    }
}
