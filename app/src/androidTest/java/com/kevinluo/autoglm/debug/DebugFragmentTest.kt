package com.kevinluo.autoglm.debug

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinluo.autoglm.R
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [DebugFragment] using Espresso.
 *
 * Tests the debug feature's user interactions including:
 * - Template selection
 * - Notification selection
 * - Test execution
 * - History management
 */
@RunWith(AndroidJUnit4::class)
class DebugFragmentTest {

    // ==================== Fragment Launch Tests ====================

    @Test
    fun fragment_shouldLaunchSuccessfully() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then - verify key UI elements are displayed
        onView(withText("提示词模板"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun fragment_shouldDisplayTemplateSection() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.templateChipGroup))
            .check(matches(isDisplayed()))
    }

    @Test
    fun fragment_shouldDisplayTestDataSection() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withText("测试数据"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun fragment_shouldDisplayDataSelectionSection() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then - scroll to data selection section
        onView(withText("数据选择"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun fragment_shouldDisplayModelTestSection() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then - scroll to model test section
        onView(withText("模型测试"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun fragment_shouldDisplayTestHistorySection() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then - scroll to history section
        onView(withText("测试历史"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    // ==================== Initial State Tests ====================

    @Test
    fun runTestButton_shouldBeEnabledInitially() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.btnRunTest))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    @Test
    fun cancelButton_shouldBeDisabledInitially() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.btnCancelTest))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
            .check(matches(not(isEnabled())))
    }

    @Test
    fun progressBar_shouldBeHiddenInitially() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.progressBar))
            .check(matches(not(isDisplayed())))
    }

    // ==================== Button Click Tests ====================

    @Test
    fun selectAllButton_shouldBeClickable() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.btnSelectAll))
            .perform(scrollTo())
            .check(matches(isEnabled()))
    }

    @Test
    fun deselectAllButton_shouldBeClickable() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.btnDeselectAll))
            .perform(scrollTo())
            .check(matches(isEnabled()))
    }

    @Test
    fun randomSelectButton_shouldBeClickable() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.btnRandomSelect))
            .perform(scrollTo())
            .check(matches(isEnabled()))
    }

    @Test
    fun regenerateButton_shouldBeClickable() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.btnRegenerate))
            .perform(scrollTo())
            .check(matches(isEnabled()))
    }

    // ==================== Prompt Editor Tests ====================

    @Test
    fun promptEditText_shouldBeDisplayed() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.promptEditText))
            .check(matches(isDisplayed()))
    }

    // ==================== Slider Tests ====================

    @Test
    fun distributionSlider_shouldBeDisplayed() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.distributionSlider))
            .check(matches(isDisplayed()))
    }

    @Test
    fun rangeSlider_shouldBeDisplayed() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.rangeSlider))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    // ==================== RecyclerView Tests ====================

    @Test
    fun notificationRecyclerView_shouldBeDisplayed() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.notificationRecyclerView))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun historyRecyclerView_shouldBeDisplayed() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.historyRecyclerView))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    // ==================== Response Area Tests ====================

    @Test
    fun responseTextView_shouldBeDisplayed() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.responseTextView))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun responseScrollView_shouldBeDisplayed() {
        // When
        launchFragmentInContainer<DebugFragment>(themeResId = R.style.Theme_AutoGLM)

        // Then
        onView(withId(R.id.responseScrollView))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }
}
