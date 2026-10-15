package com.pumpwatch.app.engine

/**
 * 🚀 P1-1: اعتبارسنج مرکزی — توابع pure، بدون وابستگی به Android، قابل تست واحد.
 * قرارداد: بازگشت null یعنی «معتبر»؛ در غیر این صورت پیام خطای فارسی و دقیق.
 * درس P0: اعتبارسنجی بدون sanitize مخرب — فقط trim فاصله‌های اطراف.
 */
object Validators {

    private val EVM = Regex("^0x[0-9a-fA-F]{40}$")
    private val SOL = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
    private val TON = Regex("^(EQ|UQ)[0-9A-Za-z_-]{46}$")
    private val SYMBOL = Regex("^[A-Z0-9.-]{1,12}$")

    /** تشخیص خانوادهٔ زنجیره از روی الگوی آدرس (بدون شبکه) */
    fun detectChainFamily(addr: String): String = when {
        addr.startsWith("0x") -> "evm"
        TON.matches(addr) -> "ton"
        SOL.matches(addr) -> "solana"
        else -> "unknown"
    }

    fun validateWalletAddress(raw: String): String? {
        val a = raw.trim()
        if (a.isEmpty()) return "آدرس کیف پول خالی است"
        if (a.any { it.isWhitespace() }) return "آدرس نباید فاصله یا خط جدید در وسط داشته باشد"
        return when {
            a.startsWith("0x") ->
                if (EVM.matches(a)) null
                else "آدرس EVM باید «0x + دقیقاً ۴۰ کاراکتر hex» باشد (الان ${a.length} کاراکتر)"
            a.startsWith("EQ", true) || a.startsWith("UQ", true) ->
                if (TON.matches(a)) null
                else "آدرس TON باید «EQ/UQ + ۴۶ کاراکتر» باشد (الان ${a.length} کاراکتر)"
            SOL.matches(a) -> null
            a.length in 26..128 -> null // سایر شبکه‌ها (مثل BTC) — سخت‌گیر نمی‌شویم
            else -> "قالب آدرس شناخته‌شده نیست (طول: ${a.length}). شبکه‌های پشتیبانی: EVM، Solana، TON"
        }
    }

    fun validateContractMint(raw: String, chainFamily: String): String? {
        val m = raw.trim()
        if (m.isEmpty()) return null // اختیاری
        return when (chainFamily) {
            "evm" -> if (EVM.matches(m)) null else "کانترکت EVM باید «0x + ۴۰ کاراکتر hex» باشد"
            "solana" -> if (SOL.matches(m)) null else "mint سولانا باید base58 با ۳۲ تا ۴۴ کاراکتر باشد"
            else -> if (EVM.matches(m) || SOL.matches(m) || TON.matches(m)) null
                    else "قالب کانترکت/mint شناخته‌شده نیست"
        }
    }

    fun validateSymbol(raw: String): String? {
        val s = raw.trim().uppercase()
        if (s.isEmpty()) return "نماد را وارد کنید"
        if (!SYMBOL.matches(s)) return "نماد فقط حروف/اعداد انگلیسی، نقطه و خط تیره — حداکثر ۱۲ کاراکتر"
        return null
    }

    /** برای قوانین هشدار: «*» به معنی همهٔ نمادها مجاز است */
    fun validateRuleSymbol(raw: String): String? {
        val s = raw.trim().uppercase()
        if (s.isEmpty() || s == "*") return null
        return validateSymbol(s)
    }

    fun validateThreshold(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return "آستانه را وارد کنید"
        val v = t.toDoubleOrNull() ?: return "آستانه باید عدد باشد (مثلاً 65000 یا 0.05)"
        if (!v.isFinite()) return "آستانه باید عدد متناهی باشد"
        if (v < 0) return "آستانه نمی‌تواند منفی باشد"
        if (v > 1_000_000_000.0) return "آستانه غیرمنطقی بزرگ است"
        return null
    }

    fun validatePositivePrice(raw: String): String? {
        val v = raw.trim().toDoubleOrNull() ?: return "قیمت ورود باید عدد باشد"
        if (!v.isFinite() || v <= 0) return "قیمت ورود باید بزرگ‌تر از صفر باشد"
        return null
    }

    fun validatePercent(raw: String, min: Double = 0.1, max: Double = 90.0): String? {
        val v = raw.trim().toDoubleOrNull() ?: return "درصد باید عدد باشد"
        if (!v.isFinite()) return "درصد باید عدد متناهی باشد"
        if (v < min || v > max) return "درصد باید بین $min و $max باشد"
        return null
    }
}
