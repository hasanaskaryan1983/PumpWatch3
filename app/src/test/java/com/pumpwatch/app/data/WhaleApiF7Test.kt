package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

fun normalizeSymbolTest(symbol: String, exchange: String): String {
    val cleaned = symbol.uppercase(Locale.US).trim()
    if (cleaned.isEmpty()) return cleaned

    val quotes = listOf("USDT", "USDC", "USD", "EUR", "GBP", "BUSD", "FDUSD", "TUSD", "DAI", "ETH", "BTC", "BNB", "SOL")
    
    // فقط نمادهایی که خطر شکستن اشتباه دارند
    val protectedSymbols = setOf(
        "USDC", "DAI", "FRAX", "TUSD", "BUSD", "FDUSD",
        "STETH", "WSTETH", "RETH", "CBETH", "JITOSOL", "JUPSOL"
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
        else -> cleaned
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
    fun `F7 - correctly adds default USDT to base assets like BTC`() {
        // این تست تضمین می‌کند که حذف BTC از protectedSymbols باعث شکست منطق پیش‌فرض نشده است
        assertEquals("BTC باید به BTCUSDT تبدیل شود", "BTCUSDT", normalizeSymbolTest("BTC", "BINANCE"))
        assertEquals("BTC در OKX باید به BTC-USDT تبدیل شود", "BTC-USDT", normalizeSymbolTest("BTC", "OKX"))
    }

    @Test
    fun `F7 - correctly splits valid trading pairs`() {
        assertEquals("STETHUSDT باید به درستی شناسایی شود", "STETHUSDT", normalizeSymbolTest("STETHUSDT", "BINANCE"))
        assertEquals("JITOSOL-USDC در OKX باید درست بماند", "JITOSOL-USDC", normalizeSymbolTest("JITOSOL-USDC", "OKX"))
    }
}
