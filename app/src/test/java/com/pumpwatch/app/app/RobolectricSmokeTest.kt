package com.pumpwatch.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 131: smoke test برای تأیید setup درست Robolectric.
 *
 * اگر این دو تست سبز شوند، یعنی:
 * - Context واقعی روی JVM در دسترس است
 * - SharedPreferences واقعی کار می‌کند
 * - همهٔ integration test های بعدی (۱۳۲ به بعد) قابل اجرا هستند
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobolectricSmokeTest {

    @Test
    fun `application context is available`() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        assertNotNull(ctx)
        assertEquals("com.pumpwatch.app", ctx.packageName)
    }

    @Test
    fun `shared preferences roundtrip works`() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val prefs = ctx.getSharedPreferences("smoke_test", 0)
        prefs.edit().putString("key", "value").apply()
        assertEquals("value", prefs.getString("key", null))
    }
}
