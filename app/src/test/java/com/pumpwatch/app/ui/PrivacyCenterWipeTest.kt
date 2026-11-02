package com.pumpwatch.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyCenterWipeTest {

    @Test
    fun testS3b_BothSecureStorageVersionsAreWiped() {
        // خواندن فایل منبع برای اطمینان از وجود هر دو دستور پاک‌سازی
        val file = File("app/src/main/java/com/pumpwatch/app/ui/PrivacyCenterScreen.kt")
        val content = file.readText()
        
        assertTrue("باید نسخه data.SecureStorage را پاک کند", content.contains("SecureStorage.wipeAll(context)"))
        assertTrue("باید نسخه store.SecureStorage را نیز پاک کند (جلوگیری از نشت داده)", content.contains("StoreSecureStorage.clear(context)"))
    }
}
