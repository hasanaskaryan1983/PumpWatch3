package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🚀 Commit 68: تست‌های `normalizeSymbol` — پوشش سناریوهای ممیزی WHA4.
 *
 * هدف: ثابت کنیم که Suffix Parsing، جایگزینی replace عمومی را به‌درستی حل می‌کند.
 */
class WhaleApiTest {

    // === باگ اصلی قبلی: BTC--USDT ===

    @Test
    fun `normalizeSymbol - BTC-USDT input produces correct output for all exchanges`() {
        // باگ قبلی: replace عمومی → BTC--USDT (دو خط تیره)
        // راه‌حل جدید: Suffix Parsing → BTC-USDT (یک خط تیره)
        assertEquals("BTCUSDT", normalizeSymbol("BTC-USDT", "BINANCE"))
        assertEquals("BTCUSDT", normalizeSymbol("BTC-USDT", "BYBIT"))
        assertEquals("BTC-USDT", normalizeSymbol("BTC-USDT", "OKX"))
        assertEquals("BTC_USDT", normalizeSymbol("BTC-USDT", "GATE"))
    }

    @Test
    fun `normalizeSymbol - BTCUSDT input works correctly`() {
        assertEquals("BTCUSDT", normalizeSymbol("BTCUSDT", "BINANCE"))
        assertEquals("BTCUSDT", normalizeSymbol("BTCUSDT", "BYBIT"))
        assertEquals("BTC-USDT", normalizeSymbol("BTCUSDT", "OKX"))
        assertEquals("BTC_USDT", normalizeSymbol("BTCUSDT", "GATE"))
    }

    @Test
    fun `normalizeSymbol - BTC_USDT input works correctly`() {
        assertEquals("BTCUSDT", normalizeSymbol("BTC_USDT", "BINANCE"))
        assertEquals("BTC-USDT", normalizeSymbol("BTC_USDT", "OKX"))
        assertEquals("BTC_USDT", normalizeSymbol("BTC_USDT", "GATE"))
    }

    // === سناریوهای مهم: نمادهای پیشونددار ===

    @Test
    fun `normalizeSymbol - 1000PEPEUSDT preserves 1000 prefix`() {
        // این مهم‌ترین تست است: با replace قدیمی، "USDT" اول حذف می‌شد،
        // بعد "USD" از "USDT" باقی‌مانده دوباره replace می‌شد → 1000PEPE--USDT
        // با Suffix Parsing: 1000PEPE به‌درستی به‌عنوان base شناسایی می‌شود
        assertEquals("1000PEPEUSDT", normalizeSymbol("1000PEPEUSDT", "BINANCE"))
        assertEquals("1000PEPEUSDT", normalizeSymbol("1000PEPEUSDT", "BYBIT"))
        assertEquals("1000PEPE-USDT", normalizeSymbol("1000PEPEUSDT", "OKX"))
        assertEquals("1000PEPE_USDT", normalizeSymbol("1000PEPEUSDT", "GATE"))
    }

    @Test
    fun `normalizeSymbol - 1000SHIBUSDT works`() {
        assertEquals("1000SHIBUSDT", normalizeSymbol("1000SHIBUSDT", "BINANCE"))
        assertEquals("1000SHIB-USDT", normalizeSymbol("1000SHIBUSDT", "OKX"))
    }

    // === سناریوهای مختلف quote ===

    @Test
    fun `normalizeSymbol - USDC quote works`() {
        assertEquals("BTCUSDC", normalizeSymbol("BTCUSDC", "BINANCE"))
        assertEquals("BTC-USDC", normalizeSymbol("BTCUSDC", "OKX"))
    }

    @Test
    fun `normalizeSymbol - ETH quote works`() {
        assertEquals("STETHETH", normalizeSymbol("STETHETH", "BINANCE"))
        assertEquals("STETH-ETH", normalizeSymbol("STETHETH", "OKX"))
    }

    @Test
    fun `normalizeSymbol - USDT priority over USD prevents mismatch`() {
        // USDT باید قبل از USD چک شود تا USDT به US+DT تجزیه نشود
        assertEquals("BTCUSDT", normalizeSymbol("BTCUSDT", "BINANCE"))
        // اگر USDT با USDT شروع نمی‌شد و فقط USD داشتیم:
        assertEquals("PEPEUSD", normalizeSymbol("PEPEUSD", "BINANCE"))
    }

    // === سناریوهای edge case ===

    @Test
    fun `normalizeSymbol - lowercase input is normalized to uppercase`() {
        assertEquals("BTCUSDT", normalizeSymbol("btcusdt", "BINANCE"))
        assertEquals("BTCUSDT", normalizeSymbol("btc-usdt", "BINANCE"))
    }

    @Test
    fun `normalizeSymbol - whitespace is trimmed`() {
        assertEquals("BTCUSDT", normalizeSymbol("  BTCUSDT  ", "BINANCE"))
        assertEquals("BTCUSDT", normalizeSymbol(" BTC-USDT ", "BINANCE"))
    }

    @Test
    fun `normalizeSymbol - input without quote defaults to USDT`() {
        // اگر کاربر فقط "BTC" بدهد، فرض می‌کنیم USDT است
        assertEquals("BTCUSDT", normalizeSymbol("BTC", "BINANCE"))
        assertEquals("BTC-USDT", normalizeSymbol("BTC", "OKX"))
    }

    @Test
    fun `normalizeSymbol - empty input returns empty`() {
        assertEquals("", normalizeSymbol("", "BINANCE"))
        assertEquals("", normalizeSymbol("   ", "BINANCE"))
    }

    // === سناریوی صرافی ناشناخته ===

    @Test
    fun `normalizeSymbol - unknown exchange returns cleaned input`() {
        assertEquals("BTCUSDT", normalizeSymbol("btcusdt", "UNKNOWN"))
        assertEquals("BTC-USDT", normalizeSymbol("BTC-USDT", "UNKNOWN"))
    }
}
