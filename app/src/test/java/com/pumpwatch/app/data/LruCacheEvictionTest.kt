package com.pumpwatch.app.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class LruCacheEvictionTest {

    @Test
    fun testTrueLruEvictsLeastRecentlyUsed() {
        // شبیه‌سازی دقیق منطق LRU استفاده شده در GlobalKlineCache
        val maxSize = 2
        val map = object : LinkedHashMap<String, Int>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>): Boolean = size > maxSize
        }

        map["A"] = 1
        map["B"] = 2
        
        // دسترسی به "A" تا به‌عنوان اخیراً استفاده‌شده标记 شود
        map["A"] 
        
        // درج "C"، باید "B" (کم‌استفاده‌ترین) حذف شود، نه "A"
        map["C"] = 3

        assertTrue("A should still exist because it was recently accessed", map.containsKey("A"))
        assertFalse("B should be evicted as it was least recently used", map.containsKey("B"))
        assertTrue("C should exist", map.containsKey("C"))
        assertEquals(2, map.size)
    }
}
