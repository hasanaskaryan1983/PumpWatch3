package com.pumpwatch.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 🚀 C-04: رگرسیون متد RPC در WalletForensics.
 * بررسی می‌کند که رشته "getParsedTransaction" حذف شده و "getTransaction" جایگزین شده است.
 */
class WalletForensicsRpcMethodTest {

    @Test
    fun rpcMethodMustBeGetTransaction() {
        // تلاش برای یافتن فایل در مسیرهای مختلف (محلی و CI)
        val possiblePaths = listOf(
            "app/src/main/java/com/pumpwatch/app/ui/WalletForensics.kt",
            System.getProperty("user.dir") + "/app/src/main/java/com/pumpwatch/app/ui/WalletForensics.kt",
            "/home/runner/work/PumpWatch3/PumpWatch3/app/src/main/java/com/pumpwatch/app/ui/WalletForensics.kt"
        )
        
        val file = possiblePaths.firstOrNull { File(it).exists() }?.let { File(it) }
        
        assertTrue("فایل WalletForensics.kt در هیچ‌یک از مسیرهای انتظار یافت نشد", file != null)
        
        val content = file!!.readText()
        assertTrue("رشته getParsedTransaction نباید در فایل وجود داشته باشد", !content.contains("\"getParsedTransaction\""))
        assertTrue("رشته getTransaction باید در فایل وجود داشته باشد", content.contains("\"getTransaction\""))
    }
}
