package com.kevinluo.autoglm.debug

import java.util.UUID

/**
 * 提示词模板
 *
 * @property id 唯一标识
 * @property name 模板名称
 * @property content 提示词内容
 * @property isBuiltin 是否为预置模板（预置模板不可删除）
 */
data class PromptTemplate(
    val id: String,
    val name: String,
    val content: String,
    val isBuiltin: Boolean = false
)

/**
 * 模拟通知数据
 *
 * @property id 唯一标识
 * @property packageName 应用包名
 * @property appName 应用名称
 * @property title 通知标题
 * @property text 通知内容
 * @property timestamp 时间戳
 * @property category 通知类别
 */
data class MockNotification(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: NotificationCategory = NotificationCategory.OTHER
)

/**
 * 通知类别枚举
 */
enum class NotificationCategory(val displayName: String) {
    WECHAT_QQ("微信/QQ"),
    WORK_WECHAT("企业微信"),
    SMS("短信"),
    OTHER("其他")
}

/**
 * 数据分布配置
 *
 * @property wechatQQPercent 微信/QQ 百分比 (0-100)
 * @property workWechatPercent 企业微信百分比 (0-100)
 * @property smsPercent 短信百分比 (0-100)
 * @property otherPercent 其他百分比 (自动计算)
 */
data class DistributionConfig(
    val wechatQQPercent: Int = 30,
    val workWechatPercent: Int = 20,
    val smsPercent: Int = 30,
    val otherPercent: Int = 20
) {
    fun toList(): List<Int> = listOf(wechatQQPercent, workWechatPercent, smsPercent, otherPercent)

    fun toDisplayString(): String = "微信/QQ:${wechatQQPercent}%, 企业微信:${workWechatPercent}%, 短信:${smsPercent}%, 其他:${otherPercent}%"

    companion object {
        fun fromList(list: List<Int>): DistributionConfig {
            return DistributionConfig(
                wechatQQPercent = list.getOrElse(0) { 30 },
                workWechatPercent = list.getOrElse(1) { 20 },
                smsPercent = list.getOrElse(2) { 30 },
                otherPercent = list.getOrElse(3) { 20 }
            )
        }
    }
}

/**
 * 测试历史记录
 *
 * @property id 唯一标识
 * @property promptTemplateId 使用的提示词模板ID
 * @property promptTemplateName 模板名称快照
 * @property inputNotifications 输入的通知数据（JSON格式）
 * @property modelResponse 模型响应
 * @property timestamp 测试时间
 * @property success 是否成功
 * @property systemPrompt 系统提示词
 * @property userPrompt 完整用户提示词（模板+数据）
 * @property modelName 模型名称
 * @property baseUrl API 基础 URL
 * @property temperature 温度参数
 * @property maxTokens 最大 token 数
 * @property notificationCount 输入通知数量
 * @property notificationDistribution 通知类型分布
 */
data class DebugTestHistory(
    val id: String = UUID.randomUUID().toString(),
    val promptTemplateId: String,
    val promptTemplateName: String,
    val inputNotifications: String,
    val modelResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true,
    // 新增字段 - 用于提示词优化
    val systemPrompt: String = "",
    val userPrompt: String = "",
    val modelName: String = "",
    val baseUrl: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val notificationCount: Int = 0,
    val notificationDistribution: String = ""
)
