package com.pumpwatch.app.wallet.domain

/**
 * 🚀 Commit 66+67 (فاز ۱ — gate هویت): رجیستری مرکزی زنجیره‌ها با تشخیص صحیح EVM.
 *
 * باگ قبلی (CONSTITUTION بند ۶):
 *   `detectChain` اولین match را برمی‌گرداند؛ چون همهٔ EVMها regex یکسان
 *   `^0x[0-9a-fA-F]{40}$` دارند، هر آدرس EVM همیشه "Ethereum" شناسایی می‌شد.
 *   این ذات پروتکل EVM است (آدرس روی Base/Polygon/Arbitrum با Ethereum یکسان است)
 *   و با regex قابل حل نیست.
 *
 * راه‌حل:
 *   - `detectChain(addr)` از روی آدرس خام، برای EVM فقط `EVM_GENERIC` برمی‌گرداند.
 *   - `resolveChain(chainIdHint, addr)` مسیر اصلی است: وقتی caller شبکه را می‌داند
 *     (که معمولاً می‌داند)، دقیق resolve می‌کند.
 *   - `isValidAddress(chainId, addr)` برای validation دقیق شبکه مشخص استفاده می‌شود.
 *   - ترتیب: غیر-EVM اول (regex منحصربه‌فرد)، EVM_GENERIC آخر (fallback).
 */
object ChainRegistry {

    private val EVM_PATTERN = Regex("^0x[0-9a-fA-F]{40}$")
    private val SOLANA_PATTERN = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
    private val SUI_PATTERN = Regex("^0x[0-9a-fA-F]{64}$")
    private val TON_PATTERN = Regex("^(EQ|UQ|kQ|0:)[0-9a-zA-Z_-]+$")

    /**
     * Chain مجازی برای «EVM بدون شبکهٔ مشخص».
     * وقتی از روی آدرس خام نمی‌توان شبکه را تشخیص داد (ذات EVM).
     */
    val EVM_GENERIC: Chain = Chain(
        chainId = "evm",
        name = "EVM (generic)",
        addressPattern = EVM_PATTERN,
        nativeAsset = AssetId.native("evm"),
        explorerUrl = ""
    )

    val chains: List<Chain> = listOf(
        // === زنجیره‌های غیر-EVM (regex منحصربه‌فرد، تشخیص قطعی از آدرس) ===
        Chain(
            chainId = "solana",
            name = "Solana",
            addressPattern = SOLANA_PATTERN,
            nativeAsset = AssetId.native("solana"),
            explorerUrl = "https://solscan.io/account/"
        ),
        Chain(
            chainId = "sui",
            name = "SUI",
            addressPattern = SUI_PATTERN,
            nativeAsset = AssetId.native("sui"),
            explorerUrl = "https://suiscan.xyz/mainnet/account/"
        ),
        Chain(
            chainId = "ton",
            name = "TON",
            addressPattern = TON_PATTERN,
            nativeAsset = AssetId.native("ton"),
            explorerUrl = "https://tonscan.org/address/"
        ),

        // === EVM_GENERIC: fallback برای هر آدرس 0x... که شبکه‌اش مشخص نیست ===
        EVM_GENERIC,

        // === زنجیره‌های EVM مشخص (فقط برای isValidAddress/resolveChain استفاده می‌شوند) ===
        Chain(
            chainId = "1",
            name = "Ethereum",
            addressPattern = EVM_PATTERN,
            nativeAsset = AssetId.native("1"),
            explorerUrl = "https://etherscan.io/address/"
        ),
        Chain(
            chainId = "8453",
            name = "Base",
            addressPattern = EVM_PATTERN,
            nativeAsset = AssetId.native("8453"),
            explorerUrl = "https://basescan.org/address/"
        ),
        Chain(
            chainId = "56",
            name = "BNB Chain",
            addressPattern = EVM_PATTERN,
            nativeAsset = AssetId.native("56"),
            explorerUrl = "https://bscscan.com/address/"
        ),
        Chain(
            chainId = "42161",
            name = "Arbitrum",
            addressPattern = EVM_PATTERN,
            nativeAsset = AssetId.native("42161"),
            explorerUrl = "https://arbiscan.io/address/"
        ),
        Chain(
            chainId = "10",
            name = "Optimism",
            addressPattern = EVM_PATTERN,
            nativeAsset = AssetId.native("10"),
            explorerUrl = "https://optimistic.etherscan.io/address/"
        ),
        Chain(
            chainId = "137",
            name = "Polygon",
            addressPattern = EVM_PATTERN,
            nativeAsset = AssetId.native("137"),
            explorerUrl = "https://polygonscan.com/address/"
        ),
        Chain(
            chainId = "100",
            name = "Gnosis",
            addressPattern = EVM_PATTERN,
            nativeAsset = AssetId.native("100"),
            explorerUrl = "https://gnosisscan.io/address/"
        ),
        Chain(
            chainId = "robinhood",
            name = "Robinhood",
            addressPattern = EVM_PATTERN,
            nativeAsset = AssetId.native("robinhood"),
            explorerUrl = "https://robinhoodchain.blockscout.com/address/"
        )
    )

    /**
     * تشخیص زنجیره از روی آدرس خام.
     *
     * ⚠️ برای EVM: فقط `EVM_GENERIC` برمی‌گرداند (ذات پروتکل: آدرس EVM
     *    روی همهٔ شبکه‌ها یکسان است). برای تشخیص دقیق شبکه، از [resolveChain]
     *    با chainIdHint استفاده کنید.
     *
     * ترتیب چک:
     *   1. زنجیره‌های غیر-EVM (regex منحصربه‌فرد) — تشخیص قطعی
     *   2. EVM_GENERIC — اگر آدرس EVM بود
     *   3. null — اگر هیچ‌کدام نبود
     */
    fun detectChain(address: String): Chain? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null

        // ۱. اول زنجیره‌های غیر-EVM را چک کن (regex منحصربه‌فرد، تشخیص قطعی)
        for (chain in chains) {
            if (chain.chainId in AssetId.EVM_CHAIN_IDS) continue
            if (chain.chainId == "evm") continue
            if (chain.isValidAddress(trimmed)) return chain
        }

        // ۲. اگر آدرس EVM بود، EVM_GENERIC برگردان (نه Ethereum)
        if (EVM_PATTERN.matches(trimmed)) return EVM_GENERIC

        return null
    }

    /**
     * تشخیص زنجیره با chainIdHint (مسیر اصلی و ترجیحی).
     *
     * اگر caller شبکه را می‌داند (API response، UI selection، context)،
     * این متد باید استفاده شود. فقط اگر hint نداشت، به detectChain fall back می‌کند.
     */
    fun resolveChain(chainIdHint: String?, address: String): Chain? {
        if (!chainIdHint.isNullOrBlank()) {
            getChain(chainIdHint)?.let { return it }
        }
        return detectChain(address)
    }

    /** آیا آدرس برای زنجیرهٔ مشخص معتبر است؟ */
    fun isValidAddress(chainId: String, address: String): Boolean {
        val chain = chains.firstOrNull { it.chainId == chainId } ?: return false
        return chain.isValidAddress(address)
    }

    /** گرفتن زنجیره از chainId */
    fun getChain(chainId: String): Chain? = chains.firstOrNull { it.chainId == chainId }

    /** لینک explorer برای آدرس (null اگر زنجیره شناخته‌شده نباشد یا EVM_GENERIC) */
    fun explorerLink(chainId: String, address: String): String? {
        val chain = getChain(chainId) ?: return null
        if (chain.explorerUrl.isBlank()) return null
        return "${chain.explorerUrl}$address"
    }
}
