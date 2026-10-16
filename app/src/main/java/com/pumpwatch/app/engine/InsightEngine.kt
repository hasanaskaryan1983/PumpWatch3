package com.pumpwatch.app.engine

import java.util.Locale

/**
 * 🚀 P2-3: موتور تولید بینش هوشمند عملکرد.
 * جدا شده از TradesScreen برای قابلیت تست واحد.
 */
object InsightEngine {

    fun generateSmartInsight(
        closedCount: Int,
        maxDD: Double,
        profitFactor: Double,
        winRate: Double,
        bestTier: String?,
        bestTierWinRate: Double?,
        expectancy: Double
    ): String {
        if (closedCount < 3) {
            return "📈 حداقل ۳ معامله بسته‌شده نیاز است تا بینش هوشمند تولید شود."
        }

        // ۱. بررسی ریسک (Max Drawdown)
        if (maxDD > 25.0) {
            return "⚠️ حداکثر افت سرمایه (Max DD) شما ${String.format(Locale.US, "%.1f", maxDD)}٪ است. پیشنهاد: حجم هر معامله را ۳۰٪ کاهش دهید."
        }

        // ۲. بررسی کیفیت استراتژی (Profit Factor بالا با وین‌ریت پایین)
        if (profitFactor >= 1.5 && winRate < 45.0) {
            return "💡 استراتژی شما با وجود وین‌ریت پایین، سودده است (PF > 1.5). به سیستم اعتماد کنید و اجازه دهید سودها رشد کنند."
        }

        // ۳. بررسی بهترین دسته (Tier)
        if (bestTier != null && (bestTierWinRate ?: 0.0) >= 60.0) {
            return "💡 شما در دسته «$bestTier» عملکرد بهتری دارید (وین‌ریت ${String.format(Locale.US, "%.0f", bestTierWinRate ?: 0.0)}٪). تمرکز خود را اینجا حفظ کنید."
        }

        // ۴. بررسی امید ریاضی (Expectancy)
        if (expectancy > 0.5) {
            return "✅ سیستم شما امید ریاضی مثبت قوی دارد. به قوانین ورود و خروج خود پایبند بمانید."
        }

        return "📊 در حال جمع‌آوری داده‌های بیشتر برای تحلیل دقیق‌تر الگوی معاملاتی شما..."
    }
}
