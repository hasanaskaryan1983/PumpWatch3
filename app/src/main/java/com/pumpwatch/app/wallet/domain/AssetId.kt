package com.pumpwatch.app.wallet.domain

/**
 * 🚀 Commit 47 (فاز ۲ برنامهٔ اجرایی): هویت رسمی دارایی.
 * قانون: هرگز برای ارزش‌گذاری یا مقایسه از symbol به‌تنهایی استفاده نمی‌شود.
 * EVM: chainId + contract؛ Solana: mint؛ SUI: coin type؛ TON: jetton master.
 * دارایی native: contract = null و standard = "NATIVE".
 */
data class AssetId(
    val chainId: String,
    val contractAddress: String?,
    val standard: String? = null
) {
    val isNative: Boolean get() = contractAddress == null

    /** کلید یکتا برای مقایسه/کش؛ برای EVM بدون حساسیت به حالت حروف */
    fun key(): String =
        if (contractAddress == null) "$chainId:native"
        else "$chainId:${contractAddress.lowercase()}"

    companion object {
        fun native(chainId: String): AssetId = AssetId(chainId, null, "NATIVE")
        fun evm(chainId: String, contract: String): AssetId = AssetId(chainId, contract, "ERC20")
        fun solana(mint: String): AssetId = AssetId("solana", mint, "SPL")
        fun sui(coinType: String): AssetId = AssetId("sui", coinType, "SUI_COIN")
        fun ton(jettonMaster: String): AssetId = AssetId("ton", jettonMaster, "JETTON")
    }
}
