package com.pumpwatch.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.Blockscout
import com.pumpwatch.app.data.DexScreenerClient
import com.pumpwatch.app.data.GeckoPrice
import com.pumpwatch.app.data.SuiClient
import com.pumpwatch.app.data.TonClient
import com.pumpwatch.app.data.bestPriceUsd
import com.pumpwatch.app.data.solanaRaw
import com.pumpwatch.app.data.solanaTyped
import com.pumpwatch.app.data.suiAmount
import com.pumpwatch.app.data.tonAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val _holdings = MutableStateFlow<List<WalletHolding>>(emptyList())
    val holdings: StateFlow<List<WalletHolding>> = _holdings.asStateFlow()

    private val _txs = MutableStateFlow<List<WalletTx>>(emptyList())
    val txs: StateFlow<List<WalletTx>> = _txs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    private val _total = MutableStateFlow(0.0)
    val total: StateFlow<Double> = _total.asStateFlow()

    private val _info = MutableStateFlow("")
    val info: StateFlow<String> = _info.asStateFlow()

    fun checkWallet(addr: String, chainKey: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            _holdings.value = emptyList()
            _txs.value = emptyList()
            _info.value = "🔍 در حال اسکن کیف پول..."

            try {
                withContext(Dispatchers.IO) {
                    val kind = when {
                        chainKey == "auto" -> detectKind(addr)
                        chainKey == "solana" -> "solana"
                        chainKey == "ton" -> "ton"
                        chainKey == "sui" -> "sui"
                        else -> "evm"
                    }

                    when (kind) {
                        "solana" -> scanSolana(addr)
                        "ton" -> scanTon(addr)
                        "sui" -> scanSui(addr)
                        else -> scanEvm(addr, chainKey)
                    }
                }
            } catch (t: Throwable) {
                _errorMsg.value = "⚠️ خطا: ${t.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun scanSolana(addr: String) {
        val body = mapOf(
            "jsonrpc" to "2.0", "id" to 1,
            "method" to "getTokenAccountsByOwner",
            "params" to listOf(
                addr,
                mapOf("programId" to "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"),
                mapOf("encoding" to "jsonParsed")
            )
        )
        val res = solanaTyped(body)
        val raw = res?.result?.value?.mapNotNull { a ->
            val inf = a.account?.data?.parsed?.info ?: return@mapNotNull null
            val mint = inf.mint ?: return@mapNotNull null
            val amt = inf.tokenAmount?.uiAmountString?.toDoubleOrNull() ?: 0.0
            if (amt <= 0.0) null else Triple(mint, amt, a.pubkey ?: "")
        } ?: emptyList()

        val list = coroutineScope {
            raw.take(50).chunked(5).flatMap { chunk ->
                val part = chunk.map { (mint, amt, acc) ->
                    async(Dispatchers.IO) {
                        try {
                            val t = GeckoPrice.api.tokenInfo("solana", mint).data?.attributes
                            val px = t?.price_usd?.toDoubleOrNull()
                            WalletHolding(t?.symbol ?: mint.take(6), t?.name ?: "", amt, px, amt * (px ?: 0.0), contract = mint, dexChainId = "solana")
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()
                delay(200)
                part
            }.toMutableList()
        }

        _holdings.value = list.sortedByDescending { it.value }
        _total.value = list.sumOf { it.value }
        _info.value = "✅ Solana: ${list.size} توکن پیدا شد"
    }

    private suspend fun scanTon(addr: String) {
        val acc = try { TonClient.api.account(addr) } catch (_: Exception) { null }
        val jets = try { TonClient.api.jettons(addr) } catch (_: Exception) { null }
        
        if (acc == null && jets == null) {
            _info.value = "️ اتصال به TonAPI ناموفق بود"
            return
        }

        val coinsT = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
        val tonPx = coinsT.firstOrNull { it.symbol.equals("TON", true) }?.current_price
        val list = mutableListOf<WalletHolding>()

        val tonBal = tonAmount(acc?.balance?.toString(), 9) ?: 0.0
        if (tonBal > 0.0) {
            list.add(WalletHolding("TON", "Toncoin", tonBal, tonPx, tonBal * (tonPx ?: 0.0)))
        }

        for (jb in (jets?.jettons ?: emptyList())) {
            val meta = jb.jetton ?: continue
            val mint = meta.address ?: continue
            val dec = meta.decimals ?: 9
            val amt = tonAmount(jb.balance, dec) ?: continue
            if (amt <= 0.0) continue
            list.add(WalletHolding(meta.symbol ?: mint.take(6), meta.name ?: "", amt, null, 0.0, contract = mint, dexChainId = "ton"))
        }

        _holdings.value = list.sortedByDescending { it.value }
        _total.value = list.sumOf { it.value }
        _info.value = "✅ TON: ${list.size} توکن پیدا شد"
    }

    private suspend fun scanSui(addr: String) {
        val bal = SuiClient.balances(addr)
        val arr = bal?.getAsJsonArray("result")
        if (arr == null) {
            _info.value = "⚠️ اتصال به SUI RPC ناموفق بود"
            return
        }

        val coinsS = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
        val pairs = mutableListOf<Pair<String, String>>()
        for (el in arr) {
            val o = el.asJsonObject
            val ct = o.get("coinType")?.asString ?: continue
            val rb = o.get("balance")?.asString ?: continue
            if ((rb.toLongOrNull() ?: 0L) > 0) pairs.add(ct to rb)
        }

        val held = mutableListOf<WalletHolding>()
        coroutineScope {
            pairs.chunked(2).forEach { chunk ->
                val part = chunk.map { (ct, rb) ->
                    async(Dispatchers.IO) {
                        try {
                            if (ct == "0x2::sui::SUI") {
                                val amt = suiAmount(rb) ?: return@async null
                                WalletHolding("SUI", "Sui", amt, null, 0.0)
                            } else {
                                val md = SuiClient.coinMetadata(ct)?.getAsJsonObject("result")
                                val sym = md?.get("symbol")?.asString ?: ct.take(8)
                                var d = (md?.get("decimals")?.asInt ?: 9).coerceIn(0, 18)
                                var amt = rb.toDoubleOrNull() ?: return@async null
                                while (d > 0) { amt /= 10.0; d-- }
                                WalletHolding(sym, "", amt, null, 0.0, contract = ct, dexChainId = "sui")
                            }
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()
                for (h in part) if (h.amount > 0.0) held.add(h)
                if (pairs.size > 2) delay(500)
            }
        }

        val suiPx = coinsS.firstOrNull { it.symbol.equals("SUI", true) }?.current_price
        val finalList = held.map { h ->
            if (h.symbol == "SUI" && suiPx != null && suiPx > 0) h.copy(price = suiPx, value = h.amount * suiPx) else h
        }

        _holdings.value = finalList.sortedByDescending { it.value }
        _total.value = finalList.sumOf { it.value }
        _info.value = if (finalList.isEmpty()) "😴 این کیف SUI خالیه" else "✅ SUI: ${finalList.size} کوین پیدا شد"
    }

    private suspend fun scanEvm(addr: String, chainKey: String) {
        _info.value = "⚠️ اسکن EVM هنوز به ViewModel منتقل نشده"
    }

    private fun detectKind(a: String): String = when {
        a.startsWith("0x") && a.length == 42 -> "evm"
        a.startsWith("0x") && a.length >= 64 -> "sui"
        a.startsWith("EQ") || a.startsWith("UQ") || a.startsWith("kQ") || a.startsWith("0:") -> "ton"
        else -> "solana"
    }

    fun saveStar(addr: String, symbol: String, score: Int, note: String, boughtUsd: Double, maxSingleUsd: Double, soldUsd: Double, txCount: Int) {
        val context = getApplication<Application>()
        FavStore.load(context)
        val multiplier = score / 10.0
        FavStore.addFav(
            ctx = context,
            addr = addr,
            note = note,
            starred = true,
            symbol = symbol,
            role = note,
            huntedAtMs = System.currentTimeMillis(),
            multiplier = multiplier,
            boughtUsd = boughtUsd,
            maxSingleUsd = maxSingleUsd,
            soldUsd = soldUsd,
            txCount = txCount
        )
        _info.value = "❤️ نهنگ «$symbol • $note» با ضریب ${String.format("%.1f", multiplier)}x به پرونده اضافه شد"
    }
}
