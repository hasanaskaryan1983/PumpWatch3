package com.pumpwatch.app.data

/**
 * هویت استاندارد یک دارایی در سیستم
 * شامل تمام اطلاعات لازم برای شناسایی یکتا
 */
data class AssetIdentity(
    val providerId: String,          // شناسه در CoinGecko (مثلاً "bitcoin")
    val symbol: String,              // نماد (مثلاً "BTC")
    val name: String,                // نام کامل (مثلاً "Bitcoin")
    val chain: String? = null,       // شبکه (مثلاً "ethereum", "solana") - برای توکن‌ها
    val contractAddress: String? = null,  // آدرس قرارداد - برای توکن‌ها
    val venue: String? = null,       // صرافی/بازار (مثلاً "binance", "geckoterminal")
    val marketType: MarketType = MarketType.SPOT,  // نوع بازار
    val rank: Int? = null            // رتبه بازار
) {
    /**
     * کلید یکتا برای مقایسه و کش
     */
    fun uniqueKey(): String {
        val parts = mutableListOf(providerId)
        if (chain != null) parts.add(chain)
        if (contractAddress != null) parts.add(contractAddress)
        if (venue != null) parts.add(venue)
        return parts.joinToString(":")
    }
    
    /**
     * آیا این دارایی یک ارز بومی لایه  است؟
     */
    fun isNativeLayer1(): Boolean {
        return chain == null && contractAddress == null
    }
    
    /**
     * آیا این دارایی یک توکن است؟
     */
    fun isToken(): Boolean {
        return contractAddress != null
    }
}

enum class MarketType {
    SPOT,
    FUTURES,
    DEX_POOL
}

/**
 * Registry برای مدیریت تطبیق دارایی‌ها
 */
object AssetRegistry {
    private val registry = mutableMapOf<String, AssetIdentity>()
    
    fun register(identity: AssetIdentity) {
        registry[identity.uniqueKey()] = identity
    }
    
    fun findByIdentity(providerId: String, chain: String? = null): AssetIdentity? {
        return registry.values.firstOrNull { 
            it.providerId == providerId && it.chain == chain 
        }
    }
    
    fun findBySymbol(symbol: String, chain: String? = null): List<AssetIdentity> {
        return registry.values.filter { 
            it.symbol.equals(symbol, ignoreCase = true) && it.chain == chain 
        }
    }
    
    fun clear() {
        registry.clear()
    }
}
