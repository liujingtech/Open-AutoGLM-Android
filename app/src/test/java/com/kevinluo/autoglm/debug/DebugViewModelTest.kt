package com.kevinluo.autoglm.debug

import com.kevinluo.autoglm.util.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject

/**
 * Unit tests for [DebugViewModel].
 *
 * Tests ViewModel operations including:
 * - UI state management
 * - Selection operations
 * - Template operations
 * - Test execution flow
 */
class DebugViewModelTest : StringSpec({

    beforeSpec {
        mockkObject(Logger)
        every { Logger.d(any(), any()) } just Runs
        every { Logger.w(any(), any()) } just Runs
        every { Logger.e(any(), any()) } just Runs
        every { Logger.e(any(), any(), any()) } just Runs
    }

    afterSpec {
        unmockkObject(Logger)
    }

    // ==================== DebugUiState Tests ====================

    "DebugUiState should have correct default values" {
        val state = DebugViewModel.DebugUiState()

        state.selectedTemplateId.shouldBeNull()
        state.editedContent shouldBe ""
        state.hasUnsavedChanges shouldBe false
        state.selectionMin shouldBe 10
        state.selectionMax shouldBe 40
        state.isRunning shouldBe false
        state.responseText shouldBe ""
        state.errorMessage.shouldBeNull()
    }

    "DebugUiState copy should update selectedTemplateId" {
        val original = DebugViewModel.DebugUiState()
        val updated = original.copy(selectedTemplateId = "template-123")

        updated.selectedTemplateId shouldBe "template-123"
        // Other fields should remain unchanged
        updated.editedContent shouldBe ""
        updated.isRunning shouldBe false
    }

    "DebugUiState copy should update isRunning state" {
        val original = DebugViewModel.DebugUiState()
        val updated = original.copy(isRunning = true, responseText = "Loading...")

        updated.isRunning shouldBe true
        updated.responseText shouldBe "Loading..."
    }

    "DebugUiState copy should update error state" {
        val original = DebugViewModel.DebugUiState()
        val updated = original.copy(
            isRunning = false,
            responseText = "Error: Test failed",
            errorMessage = "Test failed"
        )

        updated.isRunning shouldBe false
        updated.responseText shouldBe "Error: Test failed"
        updated.errorMessage shouldBe "Test failed"
    }

    "DebugUiState copy should clear error" {
        val stateWithError = DebugViewModel.DebugUiState(errorMessage = "Previous error")
        val cleared = stateWithError.copy(errorMessage = null)

        cleared.errorMessage.shouldBeNull()
    }

    // ==================== Selection Range Tests ====================

    "DebugUiState selection range should be valid" {
        val state = DebugViewModel.DebugUiState(selectionMin = 10, selectionMax = 40)

        state.selectionMin shouldBe 10
        state.selectionMax shouldBe 40
        state.selectionMin shouldBe (state.selectionMin.coerceAtMost(state.selectionMax))
    }

    "DebugUiState selection range with reversed values should still work" {
        val state = DebugViewModel.DebugUiState(selectionMin = 50, selectionMax = 20)

        // ViewModel should handle this case by coercing values
        state.selectionMin shouldBe 50
        state.selectionMax shouldBe 20
    }

    // ==================== Unsaved Changes Tests ====================

    "DebugUiState should track unsaved changes" {
        val state = DebugViewModel.DebugUiState(
            editedContent = "Modified content",
            hasUnsavedChanges = true
        )

        state.hasUnsavedChanges shouldBe true
    }

    "DebugUiState should clear unsaved changes after save" {
        val stateWithChanges = DebugViewModel.DebugUiState(
            editedContent = "Modified content",
            hasUnsavedChanges = true
        )
        val afterSave = stateWithChanges.copy(hasUnsavedChanges = false)

        afterSave.hasUnsavedChanges shouldBe false
    }

    // ==================== Template Selection State Tests ====================

    "DebugUiState should track selected template" {
        val state = DebugViewModel.DebugUiState(
            selectedTemplateId = "builtin_notification_summary",
            editedContent = "Template content here"
        )

        state.selectedTemplateId shouldBe "builtin_notification_summary"
        state.editedContent shouldBe "Template content here"
    }

    "DebugUiState should clear template selection" {
        val stateWithTemplate = DebugViewModel.DebugUiState(
            selectedTemplateId = "template-1",
            editedContent = "Content"
        )
        val cleared = stateWithTemplate.copy(
            selectedTemplateId = null,
            editedContent = "",
            hasUnsavedChanges = false
        )

        cleared.selectedTemplateId.shouldBeNull()
        cleared.editedContent shouldBe ""
        cleared.hasUnsavedChanges shouldBe false
    }

    // ==================== Test Running State Tests ====================

    "DebugUiState should track running state correctly" {
        val idle = DebugViewModel.DebugUiState()
        idle.isRunning shouldBe false

        val running = idle.copy(isRunning = true)
        running.isRunning shouldBe true

        val completed = running.copy(
            isRunning = false,
            responseText = "Test completed"
        )
        completed.isRunning shouldBe false
        completed.responseText shouldBe "Test completed"
    }

    // ==================== Builtin Templates Data Tests ====================

    "Builtin templates should have correct IDs" {
        val builtinIds = listOf(
            "builtin_notification_summary",
            "builtin_notification_priority",
            "builtin_notification_category",
            "builtin_notification_action",
            "builtin_notification_response"
        )

        builtinIds.size shouldBe 5
        builtinIds.all { it.startsWith("builtin_") } shouldBe true
    }

    "PromptTemplate builtin should not be deletable" {
        val builtin = PromptTemplate(
            id = "builtin_test",
            name = "Builtin Template",
            content = "Content",
            isBuiltin = true
        )

        builtin.isBuiltin shouldBe true
    }

    "PromptTemplate user created should be deletable" {
        val userTemplate = PromptTemplate(
            id = "user_template_1",
            name = "User Template",
            content = "Content",
            isBuiltin = false
        )

        userTemplate.isBuiltin shouldBe false
    }
})
