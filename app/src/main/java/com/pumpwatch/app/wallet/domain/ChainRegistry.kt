package com.pumpwatch.app.wallet.domain

/**
 * 🚀 Commit 48 (فاز ۳): رجیستری مرکزی همهٔ زنجیره‌های پشتیبانی‌شده.
 * این لیست تنها منبع حقیقت برای زنجیره‌ها و validators است.
 * ترتیب مهم است: detectChain اولین match را برمی‌گرداند، پس Ethereum اول است.
 */
object ChainRegistry {

    val chains: List<Chain> = listOf(
        // Ethereum (اول — تا detectChain اول این را برگرداند برای آدرس‌های EVM)
        Chain(
            chainId = "1",
            name = "Ethereum",
            addressPattern = Regex("^0x[0-9a-fA-F]{40}$"),
            nativeAsset = AssetId.native("1"),
            explorerUrl = "https://etherscan.io/address/"
        ),

        // Solana
        Chain(
            chainId = "solana",
            name = "Solana",
            addressPattern = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"),
            nativeAsset = AssetId.native("solana"),
            explorerUrl = "https://solscan.io/account/"
        ),

        // Base
        Chain(
            chainId = "8453",
            name = "Base",
            addressPattern = Regex("^0x[0-9a-fA-F]{40}$"),
            nativeAsset = AssetId.native("8453"),
            explorerUrl = "https://basescan.org/address/"
        ),

        // BSC
        Chain(
            chainId = "56",
            name = "BNB Chain",
            addressPattern = Regex("^0x[0-9a-fA-F]{40}$"),
            nativeAsset = AssetId.native("56"),
            explorerUrl = "https://bscscan.com/address/"
        ),

        // Arbitrum
        Chain(
            chainId = "42161",
            name = "Arbitrum",
            addressPattern = Regex("^0x[0-9a-fA-F]{40}$"),
            nativeAsset = AssetId.native("42161"),
            explorerUrl = "https://arbiscan.io/address/"
        ),

        // Optimism
        Chain(
            chainId = "10",
            name = "Optimism",
            addressPattern = Regex("^0x[0-9a-fA-F]{40}$"),
            nativeAsset = AssetId.native("10"),
            explorerUrl = "https://optimistic.etherscan.io/address/"
        ),

        // Polygon
        Chain(
            chainId = "137",
            name = "Polygon",
            addressPattern = Regex("^0x[0-9a-fA-F]{40}$"),
            nativeAsset = AssetId.native("137"),
            explorerUrl = "https://polygonscan.com/address/"
        ),

        // SUI
        Chain(
            chainId = "sui",
            name = "SUI",
            addressPattern = Regex("^0x[0-9a-fA-F]{64}$"),
            nativeAsset = AssetId.native("sui"),
            explorerUrl = "https://suiscan.xyz/mainnet/account/"
        ),

        // TON
        Chain(
            chainId = "ton",
            name = "TON",
            addressPattern = Regex("^(EQ|UQ|kQ|0:)[0-9a-zA-Z_-]+$"),
            nativeAsset = AssetId.native("ton"),
            explorerUrl = "https://tonscan.org/address/"
        ),

        // Gnosis
        Chain(
            chainId = "100",
            name = "Gnosis",
            addressPattern = Regex("^0x[0-9a-fA-F]{40}$"),
            nativeAsset = AssetId.native("100"),
            explorerUrl = "https://gnosisscan.io/address/"
        ),

        // Robinhood
        Chain(
            chainId = "robinhood",
            name = "Robinhood",
            addressPattern = Regex("^0x[0-9a-fA-F]{40}$"),
            nativeAsset = AssetId.native("robinhood"),
            explorerUrl = "https://robinhoodchain.blockscout.com/address/"
        )
    )

    /** پیدا کردن زنجیره از روی آدرس (اولین match) */
    fun detectChain(address: String): Chain? {
        val trimmed = address.trim()
        return chains.firstOrNull { it.isValidAddress(trimmed) }
    }

    /** آیا آدرس برای زنجیرهٔ مشخص معتبر است؟ */
    fun isValidAddress(chainId: String, address: String): Boolean {
        val chain = chains.firstOrNull { it.chainId == chainId } ?: return false
        return chain.isValidAddress(address)
    }

    /** گرفتن زنجیره از chainId */
    fun getChain(chainId: String): Chain? = chains.firstOrNull { it.chainId == chainId }

    /** لینک explorer برای آدرس */
    fun explorerLink(chainId: String, address: String): String? {
        val chain = getChain(chainId) ?: return null
        return "${chain.explorerUrl}$address"
    }
}
