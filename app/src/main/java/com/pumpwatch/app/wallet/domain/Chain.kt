package com.pumpwatch.app.wallet.domain

/**
 * 🚀 Commit 48 (فاز ۳ برنامهٔ اجرایی): تعریف یک زنجیره با validator آدرس.
 * هر زنجیره شامل chainId، نام، pattern آدرس و دارایی native است.
 */
data class Chain(
    val chainId: String,
    val name: String,
    val addressPattern: Regex,
    val nativeAsset: AssetId,
    val explorerUrl: String? = null
) {
    /** آیا این آدرس برای این زنجیره معتبر است؟ */
    fun isValidAddress(address: String): Boolean = addressPattern.matches(address.trim())
}
