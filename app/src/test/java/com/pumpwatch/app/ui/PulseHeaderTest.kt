package com.pumpwatch.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های واحد برای نمایش صادقانهٔ دامیننس (P0-4 invariant).
 *
 * اصل: هرگز عدد جعلی 0.0 نمایش داده نمی‌شود —
 * یا دادهٔ تازه، یا کش با برچسب صریح، یا «ناموجود».
 */
class PulseHeaderTest {

    @Test
    fun `fresh dominance shows plain percent without warning`() {
        val t = dominanceText(54.3, 51.0)
        assertEquals("54.3%", t)
        assertFalse(t.contains("⚠️"))
        assertFalse(t.contains("cached"))
    }

    @Test
    fun `missing fresh with cached shows cached label`() {
        val t = dominanceText(null, 51.2)
        assertTrue(t.contains("(cached)"))
        assertTrue(t.contains("51.2%"))
        assertTrue(t.contains("⚠️"))
    }

    @Test
    fun `both null shows unavailable and never fake zero — P0-4 invariant`() {
        val t = dominanceText(null, null)
        assertEquals("⚠️ ناموجود", t)
        assertFalse("must never fabricate 0.0", t.contains("0.0"))
    }

    @Test
    fun `capChange fresh negative keeps sign and globe`() {
        val t = capChangeText(-1.25, null)
        assertTrue(t.contains("-1.25%"))
        assertTrue(t.contains("🌍"))
    }

    @Test
    fun `capChange both null shows unavailable`() {
        assertEquals("⚠️ ناموجود", capChangeText(null, null))
    }
}
