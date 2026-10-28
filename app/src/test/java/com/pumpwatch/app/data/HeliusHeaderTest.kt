package com.pumpwatch.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HeliusHeaderTest {

    @Test
    fun heliusClient_must_not_leak_api_key_in_url() {
        val sourceFile = File("app/src/main/java/com/pumpwatch/app/data/WalletApi.kt")
        val sourceCode = sourceFile.readText()
        
        // اطمینان از حذف کلید از URL
        assertFalse(
            "URL نباید شامل پارامتر ?api-key= باشد (نشت در لاگ)", 
            sourceCode.contains("?api-key=")
        )
        
        // اطمینان از استفاده از هدر امن
        assertTrue(
            "باید از هدر x-api-key استفاده شود", 
            sourceCode.contains("x-api-key")
        )
        
        // اطمینان از وجود Interceptor
        assertTrue(
            "باید از OkHttpClient.Builder و addInterceptor استفاده شود", 
            sourceCode.contains("addInterceptor")
        )
    }
}
