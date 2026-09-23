package com.pumpwatch.app.data

import com.pumpwatch.app.wallet.gateway.ProviderGateway
import com.pumpwatch.app.wallet.gateway.ProviderResult
import kotlinx.coroutines.delay

/**
 * 🚀 Commit 53/54 (فاز ۷): درِ واحد همهٔ تماس‌های شبکه.
 * Commit 54: solanaTypedGateway اضافه شد (مصرف‌کننده: اسکن کیف‌ها).
 */
class GatewayProviders(
    private val gecko: GeckoPriceApi = GeckoPrice.api,
    private val blockscoutFactory: (String) -> BlockscoutApi = { Blockscout.api(it) },
    private val gateway: ProviderGateway = DefaultGateway.instance
) {
    suspend fun geckoTokenInfo(network: String, address: String): ProviderResult<GtTokenInfo?> =
        gateway.callSuspend("geckoterminal", "gt:token:$network:$address", ttlMs = 60_000) {
            gecko.tokenInfo(network, address)
        }

    suspend fun geckoPoolTrades(network: String, pool: String, before: Long?): ProviderResult<GtTrades?> =
        gateway.callSuspend("geckoterminal", "gt:trades:$network:$pool:$before", ttlMs = 30_000) {
            gecko.poolTrades(network, pool, before)
        }

    suspend fun evmTokenList(host: String, address: String): ProviderResult<BsTokenList?> =
        gateway.callSuspend("blockscout", "bs:list:$host:$address", ttlMs = 60_000) {
            blockscoutFactory(host).tokenList("account", "tokenlist", address)
        }

    suspend fun solanaRawGateway(key: String, body: Map<String, @JvmSuppressWildcards Any?>): ProviderResult<SolanaRawResponse?> =
        gateway.callSuspend("solana-rpc", "sol:$key", ttlMs = 20_000) {
            solanaRaw(body)
        }

    /** 🚀 Commit 54: نسخهٔ typed برای getTokenAccountsByOwner و مشابه */
    suspend fun solanaTypedGateway(key: String, body: Map<String, @JvmSuppressWildcards Any?>): ProviderResult<SolanaRpcResponse?> =
        gateway.callSuspend("solana-rpc", "solt:$key", ttlMs = 20_000) {
            solanaTyped(body)
        }
}

object DefaultGateway {
    val instance: ProviderGateway = ProviderGateway(sleepSuspend = { delay(it) })
}
