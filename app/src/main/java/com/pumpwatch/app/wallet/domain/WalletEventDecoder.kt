package com.pumpwatch.app.wallet.domain

import java.math.BigDecimal

/**
 * 🚀 Commit 49 (فاز ۴ برنامهٔ اجرایی): ورودی خالص برای decode رویداد EVM.
 * عمداً به لایهٔ data وابسته نیست تا روی JVM قابل تست بماند.
 */
data class EvmTokenTransfer(
    val wallet: String,
    val chainId: String,
    val contractAddress: String,
    val decimals: Int,
    val rawValue: String,
    val from: String,
    val to: String,
    val timestampMs: Long,
    val txHash: String
)

/**
 * 🚀 Commit 49: decoder رویدادهای کیف پول.
 * قانون صداقت: انتقال توکن هرگز BUY/SELL فرض نمی‌شود؛
 * فقط وقتی جهت مشخص است TRANSFER_IN/OUT ساخته می‌شود، وگرنه null.
 */
object WalletEventDecoder {

    fun evmTokenTransfer(t: EvmTokenTransfer): WalletEvent? {
        // مقدار خام باید عدد باشد؛ وگرنه رویداد ساخته نمی‌شود (نه صفر!)
        val raw = t.rawValue.toBigIntegerOrNull() ?: return null
        // آدرس کیف باید برای آن زنجیره معتبر باشد (استفاده از Commit 48)
        if (!AddressValidator.isValidForChain(t.chainId, t.wallet)) return null
        // contract هم باید فرمت EVM داشته باشد
        if (!AddressValidator.isValidForChain(t.chainId, t.contractAddress)) return null

        val kind = when {
            t.to.equals(t.wallet, true) -> EventKind.TRANSFER_IN
            t.from.equals(t.wallet, true) -> EventKind.TRANSFER_OUT
            else -> return null   // رویداد مربوط به این کیف نیست
        }

        val amount = BigDecimal(raw).movePointLeft(t.decimals.coerceIn(0, 36))

        return WalletEvent(
            id = "${t.txHash}:${t.contractAddress.lowercase()}:${kind.name}",
            wallet = t.wallet,
            asset = AssetId.evm(t.chainId, t.contractAddress),
            kind = kind,
            amount = amount,
            quoteAmountUsd = null,          // قیمت جداگانه با PriceQuote می‌آید (فاز ۵)
            executionPriceUsd = null,
            timestampMs = t.timestampMs,
            txHash = t.txHash,
            source = "blockscout",
            confidence = Confidence.MEDIUM  // انتقال مستقیم = اطمینان متوسط؛ قیمت نداریم
        )
    }
}
