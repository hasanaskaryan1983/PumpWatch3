package com.pumpwatch.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 139: تست ناوبری OnboardingScreen.
 *
 * ⏱️ نکتهٔ کلیدی: این صفحه انیمیشن‌های بی‌پایان دارد (ستاره‌ها، shimmer، Ken Burns).
 * با autoAdvance پیش‌فرض، waitForIdle هرگز تمام نمی‌شد و تست hang می‌کرد.
 * پس mainClock.autoAdvance = false و زمان را دستی جلو می‌بریم.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun start(onDone: () -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setContent { OnboardingScreen(onDone = onDone) }
        rule.mainClock.advanceTimeBy(500)
    }

    @Test
    fun `first page and next button are displayed initially`() {
        start()
        rule.onNodeWithText("لحظه درست رو شکار کن!").assertIsDisplayed()
        rule.onNodeWithText("بعدی ←").assertIsDisplayed()
    }

    @Test
    fun `navigating to last page changes button to final label`() {
        start()
        repeat(4) {
            rule.onNodeWithText("بعدی ←").performClick()
            rule.mainClock.advanceTimeBy(2000)  // زمان برای انیمیشن سوایپ pager
        }
        rule.onNodeWithText("سیگنال‌های امتیازدار").assertIsDisplayed()
        rule.onNodeWithText("بزن بریم! 🚀").assertIsDisplayed()
    }

    @Test
    fun `skip button invokes onDone`() {
        var done = false
        start(onDone = { done = true })
        rule.onNodeWithText("رد شدن و ورود مستقیم").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertTrue("Skip must invoke onDone", done)
    }

    @Test
    fun `final button on last page invokes onDone`() {
        var done = false
        start(onDone = { done = true })
        repeat(4) {
            rule.onNodeWithText("بعدی ←").performClick()
            rule.mainClock.advanceTimeBy(2000)
        }
        rule.onNodeWithText("بزن بریم! 🚀").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertTrue("Final button must invoke onDone", done)
    }
}
