package com.pumpwatch.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 8 / I1 — تست‌های Contract برای شکل سیمِ API ها.
 *
 * این تست‌ها دقیقاً همان ساختار JSON واقعی را که UI به آن وابسته است قفل می‌کنند:
 * - TonAPI: کلیدهای اکشن با حرف بزرگ ("TonTransfer" / "JettonTransfer")
 * - SUI RPC: مسیر result.data[].balanceChanges[].owner.AddressOwner و timestampMs
 *
 * اگر upstream شکل پاسخ را عوض کند، CI قبل از رسیدن به کاربر خطا می‌دهد.
 * (تست‌های End-to-End با MockWebServer: Sprint 9 — نیاز به تزریق baseUrl دارد)
 */
class ApiContractTest {

    private val gson = Gson()

    private val tonEventsJson = """
        {"events":[{"event_id":"e1","timestamp":1700000000,"actions":[
          {"type":"TonTransfer","TonTransfer":{"amount":"1500000000",
            "sender":{"address":"EQS1"},"receiver":{"address":"EQR1"}}},
          {"type":"JettonTransfer","JettonTransfer":{"amount":"250000",
            "sender":{"address":"EQS2"},"receiver":{"address":"EQR2"},
            "jetton":{"symbol":"METBOI","name":"Metboi","decimals":6,"address":"0:jet1"}}}
        ]}],"next_from":123}
    """.trimIndent()

    @Test
    fun `tonapi events payload maps with capitalized action keys`() {
        val ev = gson.fromJson(tonEventsJson, TonEvents::class.java)
        assertNotNull(ev.events)
        assertEquals(1, ev.events!!.size)
        val actions = ev.events!![0].actions!!
        assertEquals(2, actions.size)
        assertNotNull("کلید TonTransfer با حرف بزرگ باید map شود", actions[0].TonTransfer)
        assertNotNull("کلید JettonTransfer با حرف بزرگ باید map شود", actions[1].JettonTransfer)
    }

    @Test
    fun `ton transfer amount converts from nanoton`() {
        val ev = gson.fromJson(tonEventsJson, TonEvents::class.java)
        val t = ev.events!![0].actions!![0].TonTransfer!!
        assertEquals(1.5, tonAmount(t.amount, 9)!!, 1e-12)
        assertEquals("EQS1", t.sender?.address)
        assertEquals("EQR1", t.receiver?.address)
    }

    @Test
    fun `jetton transfer carries symbol and decimals for pricing`() {
        val ev = gson.fromJson(tonEventsJson, TonEvents::class.java)
        val j = ev.events!![0].actions!![1].JettonTransfer!!
        assertEquals("METBOI", j.jetton?.symbol)
        assertEquals(6, j.jetton?.decimals)
        assertEquals(0.25, tonAmount(j.amount, j.jetton!!.decimals!!)!!, 1e-12)
    }

    @Test
    fun `tonapi jettons balance payload maps`() {
        val json = """{"jettons":[{"balance":"1000000",
            "jetton":{"symbol":"X","name":"X Token","decimals":6,"address":"0:j"}}]}"""
        val jb = gson.fromJson(json, TonJettons::class.java)
        assertEquals(1, jb.jettons!!.size)
        assertEquals(1.0, tonAmount(jb.jettons!![0].balance, 6)!!, 1e-12)
    }

    @Test
    fun `tonapi account payload carries nanoton balance as long`() {
        val json = """{"balance":1500000000,"status":"active"}"""
        val acc = gson.fromJson(json, TonAccount::class.java)
        assertEquals(1500000000L, acc.balance)
        assertEquals(1.5, tonAmount(acc.balance?.toString(), 9)!!, 1e-12)
    }

    @Test
    fun `sui queryTransactionBlocks payload paths used by UI still exist`() {
        val json = """
            {"result":{"data":[{"timestampMs":"1700000000000","balanceChanges":[
              {"owner":{"AddressOwner":"0xabc"},"coinType":"0x2::sui::SUI","amount":"-1000000000"},
              {"owner":{"AddressOwner":"0xdef"},"coinType":"0x2::sui::SUI","amount":"1000000000"}
            ]}]}}
        """.trimIndent()
        val obj = gson.fromJson(json, JsonObject::class.java)
        val data = obj.getAsJsonObject("result").getAsJsonArray("data")
        assertEquals(1, data.size())
        val tx = data[0].asJsonObject
        assertEquals(1700000000000L, tx.get("timestampMs").asString.toLong())
        val bc = tx.getAsJsonArray("balanceChanges")
        val owner = bc[0].asJsonObject.get("owner").asJsonObject.get("AddressOwner").asString
        assertEquals("0xabc", owner)
        val amt = bc[0].asJsonObject.get("amount").asString
        assertEquals(-1.0, suiAmount(amt)!!, 1e-12)
    }

    @Test
    fun `sui getAllBalances payload paths used by UI still exist`() {
        val json = """{"result":[
            {"coinType":"0x2::sui::SUI","balance":"2000000000"},
            {"coinType":"0xabc::coin::COIN","balance":"500"}
        ]}"""
        val obj = gson.fromJson(json, JsonObject::class.java)
        val arr = obj.getAsJsonArray("result")
        assertEquals(2, arr.size())
        val first = arr[0].asJsonObject
        assertEquals("0x2::sui::SUI", first.get("coinType").asString)
        assertEquals(2.0, suiAmount(first.get("balance").asString)!!, 1e-12)
        assertTrue(arr[1].asJsonObject.get("balance").asString == "500")
    }
}
