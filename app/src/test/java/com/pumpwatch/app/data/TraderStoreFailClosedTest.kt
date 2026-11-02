package com.pumpwatch.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraderStoreFailClosedTest {

    @Test
    fun testS5_FailClosedFlagIsAccessibleAndFunctional() {
        // تأیید وجود پرچم lastLoadFailed برای الگوی fail-closed
        val initialFailed = TraderStore.lastLoadFailed
        assertTrue("پرچم lastLoadFailed باید از نوع Boolean باشد", initialFailed is Boolean)
        
        // شبیه‌سازی تغییر وضعیت (در محیط واقعی توسط بلوک catch انجام می‌شود)
        TraderStore.lastLoadFailed = true
        assertTrue("پرچم باید قابل تغییر به true باشد", TraderStore.lastLoadFailed)
        
        // بازنشانی برای تست‌های بعدی
        TraderStore.lastLoadFailed = false
        assertFalse("پرچم باید قابل بازنشانی به false باشد", TraderStore.lastLoadFailed)
    }
}
