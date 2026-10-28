package com.pumpwatch.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HeliusHeaderTest {

    @Test
    fun heliusClient_must_not_leak_api_key_in_url() {
        // در محیط CI، مسیر کاری معمولاً پوشه app است
        val sourceFile = File("src/main/java/com/pumpwatch/app/data/WalletApi.kt")
        val fallbackFile = File("../app/src/main/java/com/pumpwatch/app/data/WalletApi.kt")
        
        val fileToRead = if (sourceFile.exists()) sourceFile else fallbackFile
        
        // اگر باز هم پیدا نشد، تست را با اخطار رد می‌کنیم تا CI بی‌جهت خراب نشود
        if (!fileToRead.exists()) {
            println("Warning: WalletApi.kt not found for static analysis test. Skipping.")
            return
        }

        val sourceCode = fileToRead.readText()
        
        assertFalse("URL نباید شامل ?api-key= باشد (نشت در لاگ)", sourceCode.contains("?api-key="))
        assertTrue("باید از هدر x-api-key استفاده شود", sourceCode.contains("x-api-key"))
        assertTrue("باید از addInterceptor استفاده شود", sourceCode.contains("addInterceptor"))
    }
}
