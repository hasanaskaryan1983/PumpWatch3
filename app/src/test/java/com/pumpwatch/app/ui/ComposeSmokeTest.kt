package com.pumpwatch.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 138: smoke test برای harness تست Compose روی Robolectric.
 *
 * اگر این تست سبز شود یعنی:
 * - createComposeRule در محیط CI کار می‌کند
 * - manifest تست (ComponentActivity) درست merge شده
 * - تست‌های UI واقعی (کامیت ۱۳۹ و ۱۴۰) قابل اجرا هستند
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `compose harness renders text on robolectric`() {
        rule.setContent { Text("سلام پامپ‌واچ") }
        rule.onNodeWithText("سلام پامپ‌واچ").assertIsDisplayed()
    }
}
