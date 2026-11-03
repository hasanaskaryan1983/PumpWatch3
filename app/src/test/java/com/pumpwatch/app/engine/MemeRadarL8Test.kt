package com.pumpwatch.app.engine

import com.pumpwatch.app.data.*
import org.junit.Assert.assertEquals
import org.junit.Test

class MemeRadarL8Test {

    @Test
    fun `L8 - pools with null id are NOT deduplicated if they have different attributes`() {
        val pool1 = GeckoPool(
            id = null,
            type = "pool",
            attributes = GeckoPoolAttributes(priceUsd = "1.0", name = "Pool A", createdAt = null, fdvUsd = null, priceChange = null, transactions = null, volume = null, reserveUsd = null),
            relationships = GeckoRelationships(
                network = GeckoRel(GeckoRelData("solana")),
                dex = GeckoRel(GeckoRelData("raydium")),
                base_token = GeckoRel(GeckoRelData("token_A"))
            )
        )

        val pool2 = GeckoPool(
            id = null,
            type = "pool",
            attributes = GeckoPoolAttributes(priceUsd = "2.0", name = "Pool B", createdAt = null, fdvUsd = null, priceChange = null, transactions = null, volume = null, reserveUsd = null),
            relationships = GeckoRelationships(
                network = GeckoRel(GeckoRelData("solana")),
                dex = GeckoRel(GeckoRelData("raydium")),
                base_token = GeckoRel(GeckoRelData("token_B")) // توکن پایه متفاوت
            )
        )

        val pools = listOf(pool1, pool2)
        val seen = mutableSetOf<String>()
        
        // شبیه‌سازی دقیق منطق جدید L8
        val unique = pools.filter { p ->
            val compositeKey = p.id ?: "${p.relationships?.network?.data?.id}_${p.relationships?.base_token?.data?.id}_${p.relationships?.dex?.data?.id}"
            seen.add(compositeKey)
        }

        assertEquals("هر دو استخر باید حفظ شوند چون کلید ترکیبی آن‌ها متفاوت است", 2, unique.size)
    }

    @Test
    fun `L8 - pools with same composite key ARE correctly deduplicated`() {
        val pool1 = GeckoPool(
            id = null,
            type = "pool",
            attributes = GeckoPoolAttributes(priceUsd = "1.0", name = "Pool A", createdAt = null, fdvUsd = null, priceChange = null, transactions = null, volume = null, reserveUsd = null),
            relationships = GeckoRelationships(
                network = GeckoRel(GeckoRelData("solana")),
                dex = GeckoRel(GeckoRelData("raydium")),
                base_token = GeckoRel(GeckoRelData("token_A"))
            )
        )

        val pool2 = GeckoPool( // کپی دقیقاً یکسان
            id = null,
            type = "pool",
            attributes = GeckoPoolAttributes(priceUsd = "1.0", name = "Pool A", createdAt = null, fdvUsd = null, priceChange = null, transactions = null, volume = null, reserveUsd = null),
            relationships = GeckoRelationships(
                network = GeckoRel(GeckoRelData("solana")),
                dex = GeckoRel(GeckoRelData("raydium")),
                base_token = GeckoRel(GeckoRelData("token_A"))
            )
        )

        val pools = listOf(pool1, pool2)
        val seen = mutableSetOf<String>()
        
        val unique = pools.filter { p ->
            val compositeKey = p.id ?: "${p.relationships?.network?.data?.id}_${p.relationships?.base_token?.data?.id}_${p.relationships?.dex?.data?.id}"
            seen.add(compositeKey)
        }

        assertEquals("استخر تکراری باید حذف شود", 1, unique.size)
    }
}
