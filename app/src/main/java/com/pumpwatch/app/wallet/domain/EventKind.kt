package com.pumpwatch.app.wallet.domain

/**
 * 🚀 Commit 47: انواع رویداد کیف پول.
 * هر چیزی که decode نشود UNKNOWN می‌ماند — هرگز BUY فرض نمی‌شود.
 */
enum class EventKind {
    BUY, SELL, SWAP, TRANSFER_IN, TRANSFER_OUT,
    MINT, BURN, BRIDGE, FEE, LP_ADD, LP_REMOVE,
    UNKNOWN
}
