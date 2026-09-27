package com.pumpwatch.app.wallet.domain

/**
 * 🚀 Commit 66+67 (فاز ۱ — gate هویت): هویت رسمی دارایی با حفظ case صحیح.
 *
 * قانون (CONSTITUTION بند ۶):
 *   - هرگز برای ارزش‌گذاری یا مقایسه از symbol به‌تنهایی استفاده نمی‌شود.
 *   - EVM: chainId + contract (case-insensitive → lowercase مجاز)
 *   - Solana: mint (Base58, case-sensitive → lowercase ممنوع)
 *   - SUI: coin type (case-sensitive → lowercase ممنوع)
 *   - TON: jetton master (case-sensitive → lowercase ممنوع)
 *   - دارایی native: contract = null و standard = "NATIVE"
 *
 * باگ قبلی: `lowercase()` بدون شرط روی همهٔ آدرس‌ها اعمال می‌شد →
 *   Solana/SUI/TON آدرس‌های متفاوت به یک کلید نگاشت می‌شدند (collision در کش).
 */
data class AssetId(
    val chainId: String,
    val contractAddress: String?,
    val standard: String? = null
) {
    val isNative: Boolean get() = contractAddress == null

    /**
     * کلید یکتا برای مقایسه/کش.
     *
     * - Native: "$chainId:native"
     * - EVM: lowercase (چون آدرس EVM case-insensitive است)
     * - غیر EVM (Solana/SUI/TON): exact-case حفظ می‌شود
     */
    fun key(): String = when {
        contractAddress == null -> "$chainId:native"
        chainId in EVM_CHAIN_IDS -> "$chainId:${contractAddress.lowercase()}"
        else -> "$chainId:$contractAddress"
    }

    companion object {
        /**
         * chainIdهای زنجیره‌های EVM — فقط این‌ها اجازه lowercase دارند.
         * شامل chainIdهای عددی استاندارد + شناسه‌های نامی که ممکن است استفاده شوند.
         */
        val EVM_CHAIN_IDS: Set<String> = setOf(
            "1",        // Ethereum
            "56",       // BSC (BNB Chain)
            "137",      // Polygon
            "8453",     // Base
            "42161",    // Arbitrum
            "10",       // Optimism
            "100",      // Gnosis
            "43114",    // Avalanche C-Chain
            "250",      // Fantom
            "25",       // Cronos
            "324",      // zkSync Era
            "59144",    // Linea
            "5000",     // Mantle
            "534352",   // Scroll
            "1101",     // Polygon zkEVM
            "81457",    // Blast
            "7777777",  // Zora
            "34443",    // Mode
            "204",      // opBNB
            "167000",   // Taiko
            "robinhood" // Robinhood (EVM-compatible)
        )

        fun native(chainId: String): AssetId = AssetId(chainId, null, "NATIVE")
        fun evm(chainId: String, contract: String): AssetId = AssetId(chainId, contract, "ERC20")
        fun solana(mint: String): AssetId = AssetId("solana", mint, "SPL")
        fun sui(coinType: String): AssetId = AssetId("sui", coinType, "SUI_COIN")
        fun ton(jettonMaster: String): AssetId = AssetId("ton", jettonMaster, "JETTON")
    }
}
