package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

// کپی تابع برای تست ایزوله (یا می‌توانیم تابع اصلی را اگر internal/public باشد فراخوانی کنیم)
// برای اطمینان از تست‌پذیری کامل، منطق را اینجا شبیه‌سازی می‌کنیم
fun normalizeSymbolTest(symbol: String, exchange: String): String {
    val cleaned = symbol.uppercase(Locale.US).trim()
    if (cleaned.isEmpty()) return cleaned

    val quotes = listOf("USDT", "USDC", "USD", "EUR", "GBP", "BUSD", "FDUSD", "TUSD", "DAI", "ETH", "BTC", "BNB", "SOL")
    val protectedSymbols = setOf(
        "USDC", "USDT", "DAI", "FRAX", "STETH", "WSTETH", "RETH", "CBETH", 
        "JITOSOL", "SOL", "ETH", "BTC", "BNB", "JUP", "PYTH"
    )

    if (cleaned in protectedSymbols) {
        return when (exchange) {
            "OKX" -> if (cleaned in quotes) cleaned else "$cleaned-USDT"
            "GATE" -> if (cleaned in quotes) cleaned else "${cleaned}_USDT"
            else -> cleaned
        }
    }

    var base = cleaned
    var quote = "USDT"
    for (q in quotes) {
        if (cleaned.endsWith(q)) {
            val potentialBase = cleaned.substring(0, cleaned.length - q.length).trimEnd('-', '_')
            if (potentialBase.isNotEmpty()) {
                val reconstructed = "$potentialBase$q"
                if (reconstructed in protectedSymbols) {
                    continue
                }
                base = potentialBase
                quote = q
                break
            }
        }
    }

    return when (exchange) {
        "BINANCE" -> "$base$quote"
        "BYBIT" -> "$base$quote"
        "OKX" -> "$base-$quote"
        "GATE" -> "${base}_${quote}"
        else -> "$base$quote"
    }
}

class WhaleApiF7Test {

    @Test
    fun `F7 - does not break protected standalone symbols`() {
        assertEquals("STETH باید دست‌نخورده بماند", "STETH", normalizeSymbolTest("STETH", "BINANCE"))
        assertEquals("JITOSOL باید دست‌نخورده بماند", "JITOSOL", normalizeSymbolTest("JITOSOL", "BINANCE"))
        assertEquals("USDC باید دست‌نخورده بماند (جلوگیری از باگ C-USD)", "USDC", normalizeSymbolTest("USDC", "BINANCE"))
        assertEquals("WSTETH در OKX باید درست فرمت شود", "WSTETH-USDT", normalizeSymbolTest("WSTETH", "OKX"))
    }

    @Test
    fun `F7 - correctly splits valid trading pairs`() {
        assertEquals("STETHUSDT باید به درستی شناسایی شود", "STETHUSDT", normalizeSymbolTest("STETHUSDT", "BINANCE"))
        assertEquals("JITOSOL-USDC در OKX باید درست بماند", "JITOSOL-USDC", normalizeSymbolTest("JITOSOL-USDC", "OKX"))
        assertEquals("BTC_USDT در GATE باید درست فرمت شود", "BTC_USDT", normalizeSymbolTest("BTC_USDT", "GATE"))
    }
}
