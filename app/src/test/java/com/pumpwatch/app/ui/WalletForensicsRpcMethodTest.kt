package com.pumpwatch.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 🚀 C-04: رگرسیون متد RPC در WalletForensics.
 *
 * باگ قبلی: متد "getParsedTransaction" صدا زده می‌شد که در JSON-RPC سولانا وجود ندارد
 * (تابع کمکی web3.js است). RPC خطای "Method not found" می‌داد و deltas همیشه خالی بود.
 * متد درست "getTransaction" با encoding:"jsonParsed" است.
 */
class WalletForensicsRpcMethodTest {

    @Test
    fun rpcMethodMustBeGetTransaction() {
        val file = File("app/src/main/java/com/pumpwatch/app/ui/WalletForensics.kt")
        val content = file.readText()
        
        // متد اشتباه نباید وجود داشته باشد
        assertTrue(
            "متد getParsedTransaction نباید در WalletForensics استفاده شود",
            !content.contains("\"getParsedTransaction\"")
        )
        
        // متد درست باید وجود داشته باشد
        assertTrue(
            "متد getTransaction باید در WalletForensics استفاده شود",
            content.contains("\"getTransaction\"")
        )
    }
}
