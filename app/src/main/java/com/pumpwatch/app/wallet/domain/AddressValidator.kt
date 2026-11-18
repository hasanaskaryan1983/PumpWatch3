package com.pumpwatch.app.wallet.domain

/**
 * 🚀 Commit 48: توابع اعتبارسنجی آدرس برای استفاده در موتورها.
 */
object AddressValidator {

    /** آیا آدرس معتبر است (برای هر زنجیره‌ای)؟ */
    fun isValid(address: String): Boolean = ChainRegistry.detectChain(address) != null

    /** آیا آدرس برای زنجیرهٔ مشخص معتبر است؟ */
    fun isValidForChain(chainId: String, address: String): Boolean =
        ChainRegistry.isValidAddress(chainId, address)

    /** زنجیرهٔ آدرس را detect کن (null اگر معتبر نیست) */
    fun detectChain(address: String): String? = ChainRegistry.detectChain(address)?.chainId

    /** آدرس را trim و normalize کن (حذف فضاهای اضافی) */
    fun normalize(address: String): String = address.trim()

    /** ماسک کردن آدرس برای لاگ (مثال: 0x1234...5678) */
    fun mask(address: String): String {
        val trimmed = address.trim()
        return if (trimmed.length >= 10) {
            "${trimmed.take(6)}...${trimmed.takeLast(4)}"
        } else {
            "***"
        }
    }
}