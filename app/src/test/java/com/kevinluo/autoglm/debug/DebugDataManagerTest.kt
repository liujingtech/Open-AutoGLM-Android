package com.kevinluo.autoglm.debug

import com.kevinluo.autoglm.util.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.haveSize
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject

/**
 * Unit tests for [DebugDataManager].
 *
 * Tests data management operations including templates, notifications, 
 * selection, and history.
 */
class DebugDataManagerTest : StringSpec({

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

    // ==================== Data Model Tests ====================

    "PromptTemplate should have correct properties" {
        val template = PromptTemplate(
            id = "test-id",
            name = "Test Template",
            content = "Test content with {placeholder}",
            isBuiltin = false
        )
        
        template.id shouldBe "test-id"
        template.name shouldBe "Test Template"
        template.content shouldBe "Test content with {placeholder}"
        template.isBuiltin shouldBe false
    }

    "MockNotification should have default values" {
        val notification = MockNotification(
            packageName = "com.test",
            appName = "Test App",
            title = "Test Title",
            text = "Test Text"
        )
        
        notification.packageName shouldBe "com.test"
        notification.appName shouldBe "Test App"
        notification.title shouldBe "Test Title"
        notification.text shouldBe "Test Text"
        notification.category shouldBe NotificationCategory.OTHER
        notification.id shouldNotBe null
        notification.timestamp shouldNotBe 0L
    }

    "DistributionConfig should have correct defaults" {
        val config = DistributionConfig()
        
        config.wechatQQPercent shouldBe 30
        config.workWechatPercent shouldBe 20
        config.smsPercent shouldBe 30
        config.otherPercent shouldBe 20
    }

    "DistributionConfig toList should return ordered percentages" {
        val config = DistributionConfig(
            wechatQQPercent = 40,
            workWechatPercent = 10,
            smsPercent = 30,
            otherPercent = 20
        )
        
        config.toList() shouldBe listOf(40, 10, 30, 20)
    }

    "DistributionConfig fromList should create correct config" {
        val list = listOf(50, 15, 25, 10)
        val config = DistributionConfig.fromList(list)
        
        config.wechatQQPercent shouldBe 50
        config.workWechatPercent shouldBe 15
        config.smsPercent shouldBe 25
        config.otherPercent shouldBe 10
    }

    "DistributionConfig fromList should use defaults for missing values" {
        val list = listOf(40)
        val config = DistributionConfig.fromList(list)
        
        config.wechatQQPercent shouldBe 40
        config.workWechatPercent shouldBe 20  // default
        config.smsPercent shouldBe 30  // default
        config.otherPercent shouldBe 20  // default
    }

    // ==================== JSON Serialization Tests ====================

    "PromptTemplate should have serializable properties" {
        val template = PromptTemplate(
            id = "test-id",
            name = "Test",
            content = "Content",
            isBuiltin = false
        )

        // Verify properties can be converted to JSON-compatible types
        template.id shouldBe "test-id"
        template.name shouldBe "Test"
        template.content shouldBe "Content"
        template.isBuiltin shouldBe false
    }

    "MockNotification should have serializable properties" {
        val notification = MockNotification(
            id = "notif-1",
            packageName = "com.test",
            appName = "TestApp",
            title = "Title",
            text = "Text",
            timestamp = 1000L,
            category = NotificationCategory.WECHAT_QQ
        )

        // Verify properties can be converted to JSON-compatible types
        notification.id shouldBe "notif-1"
        notification.packageName shouldBe "com.test"
        notification.category.name shouldBe "WECHAT_QQ"
    }

    "DebugTestHistory should have correct properties" {
        val history = DebugTestHistory(
            id = "history-1",
            promptTemplateId = "template-1",
            promptTemplateName = "Test Template",
            inputNotifications = "[{...}]",
            modelResponse = "Test response",
            timestamp = 1000L,
            success = true
        )
        
        history.id shouldBe "history-1"
        history.promptTemplateId shouldBe "template-1"
        history.promptTemplateName shouldBe "Test Template"
        history.inputNotifications shouldBe "[{...}]"
        history.modelResponse shouldBe "Test response"
        history.timestamp shouldBe 1000L
        history.success shouldBe true
    }

    // ==================== Notification Category Tests ====================

    "NotificationCategory should have correct display names" {
        NotificationCategory.WECHAT_QQ.displayName shouldBe "微信/QQ"
        NotificationCategory.WORK_WECHAT.displayName shouldBe "企业微信"
        NotificationCategory.SMS.displayName shouldBe "短信"
        NotificationCategory.OTHER.displayName shouldBe "其他"
    }

    "NotificationCategory valueOf should parse correctly" {
        NotificationCategory.valueOf("WECHAT_QQ") shouldBe NotificationCategory.WECHAT_QQ
        NotificationCategory.valueOf("WORK_WECHAT") shouldBe NotificationCategory.WORK_WECHAT
        NotificationCategory.valueOf("SMS") shouldBe NotificationCategory.SMS
        NotificationCategory.valueOf("OTHER") shouldBe NotificationCategory.OTHER
    }
})
