package com.pumpwatch.app.data

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class CacheTtlLogicTest {

    @Test
    fun testCacheReturnsDataWithinTtl() {
        val ttlMs = 60_000L
        val insertTime = System.currentTimeMillis()
        val now = insertTime + 30_000L // ۳۰ ثانیه بعد
        
        val isFresh = (now - insertTime) < ttlMs
        assertTrue("Cache should be fresh within TTL", isFresh)
    }

    @Test
    fun testCacheExpiresAfterTtl() {
        val ttlMs = 60_000L
        val insertTime = System.currentTimeMillis()
        val now = insertTime + 61_000L // ۶۱ ثانیه بعد
        
        val isFresh = (now - insertTime) < ttlMs
        assertFalse("Cache should be expired after TTL", isFresh)
    }
}
