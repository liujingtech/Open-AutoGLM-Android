package com.kevinluo.autoglm.debug

import android.content.Context
import android.content.SharedPreferences
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 调试数据管理器（单例）
 *
 * 负责管理提示词模板、模拟通知数据、测试历史的持久化存储
 */
class DebugDataManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DebugDataManager"
        private const val PREFS_NAME = "debug_settings"

        // Keys
        private const val KEY_PROMPT_TEMPLATES = "prompt_templates"
        private const val KEY_MOCK_NOTIFICATIONS = "mock_notifications"
        private const val KEY_DISTRIBUTION_CONFIG = "distribution_config"
        private const val KEY_TEST_HISTORY = "test_history"
        private const val KEY_SELECTION_RANGE_MIN = "selection_range_min"
        private const val KEY_SELECTION_RANGE_MAX = "selection_range_max"
        private const val KEY_SELECTED_TEMPLATE_ID = "selected_template_id"

        // 最大历史记录数
        private const val MAX_HISTORY_COUNT = 50

        @Volatile
        private var instance: DebugDataManager? = null

        fun getInstance(context: Context): DebugDataManager =
            instance ?: synchronized(this) {
                instance ?: DebugDataManager(context.applicationContext).also { instance = it }
            }

        // 工程版提示词模板
        private val ENGINEERING_TEMPLATE_CONTENT = """# 角色
你是车载智能助手，专注于停车场景的手机通知汇总，输出需适配车机端显示和TTS语音播报。
你必须严格按照要求输出以下格式：
<tts>{think}</tts>
<tts>{tts}</tts>
<show>{show}</show>

其中：
- {think} 是对你分级、筛选、汇总逻辑的简短推理，不超过30字。
- {tts} 是本次要播放给用户的语音内容，只播报重要信息。
- {show} 是本次要显示给用户的完整结构化汇总。

# 重要度分级规则（必须严格执行）
1. 一级（必须TTS播报）
   - 工作类：企业微信待审批、会议提醒、工作通知
   - 财务类：账户变动、收款、还款、银行卡提醒
   - 安全类：异常登录、安全警告、违章提醒

2. 二级（TTS简要汇总，文本展示）
   - 社交类：微信、QQ个人消息、家人、重要群聊
   - 生活类：快递、外卖、取件码、水电煤缴费
   - 出行类：行程、停车、限行、导航相关

3. 三级（仅展示，不播报）
   - 营销类：促销、优惠、广告、活动推送
   - 娱乐类：音乐、视频、游戏、热点推荐
   - 低优先系统：存储提醒、非紧急更新

# TTS 语音约束
- 只播报一级 + 二级核心内容
- 口语化、简洁、自然、无特殊符号
- 不超过3句话，每句不超过15字
- 无重要消息时输出：暂无重要通知

# SHOW 展示约束
- 结构化、分条、分应用展示
- 每条内容简短，不超过20字
- 按重要度排序：一级 > 二级 > 三级
- 同类消息合并，不重复展示
- 不编造信息，不添加无关内容

# 禁止行为
- 禁止输出格式外的任何多余文字
- 禁止使用表情、特殊符号、markdown
- 禁止遗漏关键信息
- 禁止修改标签结构。

以下是需要处理的通知数据：
{notifications}"""

        // 预置提示词模板
        private val BUILTIN_TEMPLATES = listOf(
            PromptTemplate(
                id = "builtin_engineering",
                name = "工程版",
                content = ENGINEERING_TEMPLATE_CONTENT,
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_summary",
                name = "通知摘要",
                content = "请总结以下通知内容的关键信息：\n\n{notifications}",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_priority",
                name = "通知优先级",
                content = "请判断以下通知的紧急程度（高/中/低），并说明理由：\n\n{notifications}",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_category",
                name = "通知分类",
                content = "请将以下通知分类（如：社交、工作、购物、系统等）：\n\n{notifications}",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_action",
                name = "通知操作建议",
                content = "基于以下通知内容，建议用户应该采取什么行动：\n\n{notifications}",
                isBuiltin = true
            ),
            PromptTemplate(
                id = "builtin_notification_response",
                name = "智能回复",
                content = "基于以下消息通知，生成一个合适的回复建议：\n\n{notifications}",
                isBuiltin = true
            )
        )
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // StateFlows for reactive UI
    private val _promptTemplates = MutableStateFlow<List<PromptTemplate>>(emptyList())
    val promptTemplates: StateFlow<List<PromptTemplate>> = _promptTemplates.asStateFlow()

    private val _mockNotifications = MutableStateFlow<List<MockNotification>>(emptyList())
    val mockNotifications: StateFlow<List<MockNotification>> = _mockNotifications.asStateFlow()

    private val _testHistory = MutableStateFlow<List<DebugTestHistory>>(emptyList())
    val testHistory: StateFlow<List<DebugTestHistory>> = _testHistory.asStateFlow()

    private val _distributionConfig = MutableStateFlow(DistributionConfig())
    val distributionConfig: StateFlow<DistributionConfig> = _distributionConfig.asStateFlow()

    private val _selectedNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNotificationIds: StateFlow<Set<String>> = _selectedNotificationIds.asStateFlow()

    init {
        loadData()
    }

    // ==================== 数据加载 ====================

    private fun loadData() {
        loadTemplates()
        loadNotifications()
        loadDistributionConfig()
        loadHistory()
        loadSelectionRange()
    }

    private fun loadTemplates() {
        _promptTemplates.value = BUILTIN_TEMPLATES + loadUserTemplates()
    }

    private fun loadUserTemplates(): List<PromptTemplate> {
        val json = prefs.getString(KEY_PROMPT_TEMPLATES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                PromptTemplate(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    content = obj.getString("content"),
                    isBuiltin = false
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse user templates", e)
            emptyList()
        }
    }

    private fun loadNotifications() {
        val json = prefs.getString(KEY_MOCK_NOTIFICATIONS, null)
        if (json != null) {
            try {
                val array = JSONArray(json)
                val list = (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    MockNotification(
                        id = obj.getString("id"),
                        packageName = obj.getString("packageName"),
                        appName = obj.getString("appName"),
                        title = obj.getString("title"),
                        text = obj.getString("text"),
                        timestamp = obj.getLong("timestamp"),
                        category = NotificationCategory.valueOf(obj.optString("category", "OTHER"))
                    )
                }
                _mockNotifications.value = list
                return
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to parse notifications", e)
            }
        }
        // 如果没有存储的数据，生成新的
        regenerateNotifications()
    }

    private fun loadDistributionConfig() {
        val wechatQQ = prefs.getInt("${KEY_DISTRIBUTION_CONFIG}_wechat_qq", 30)
        val workWechat = prefs.getInt("${KEY_DISTRIBUTION_CONFIG}_work_wechat", 20)
        val sms = prefs.getInt("${KEY_DISTRIBUTION_CONFIG}_sms", 30)
        val other = prefs.getInt("${KEY_DISTRIBUTION_CONFIG}_other", 20)
        _distributionConfig.value = DistributionConfig(wechatQQ, workWechat, sms, other)
    }

    private fun loadHistory() {
        val json = prefs.getString(KEY_TEST_HISTORY, null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<DebugTestHistory>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DebugTestHistory(
                        id = obj.getString("id"),
                        promptTemplateId = obj.getString("promptTemplateId"),
                        promptTemplateName = obj.getString("promptTemplateName"),
                        inputNotifications = obj.getString("inputNotifications"),
                        modelResponse = obj.getString("modelResponse"),
                        timestamp = obj.getLong("timestamp"),
                        success = obj.getBoolean("success"),
                        // 新增字段（兼容旧数据）
                        systemPrompt = obj.optString("systemPrompt", ""),
                        userPrompt = obj.optString("userPrompt", ""),
                        modelName = obj.optString("modelName", ""),
                        baseUrl = obj.optString("baseUrl", ""),
                        temperature = obj.optDouble("temperature", 0.7).toFloat(),
                        maxTokens = obj.optInt("maxTokens", 4096),
                        notificationCount = obj.optInt("notificationCount", 0),
                        notificationDistribution = obj.optString("notificationDistribution", "")
                    )
                )
            }
            _testHistory.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load debug history", e)
        }
    }

    private fun loadSelectionRange() {
        val min = prefs.getInt(KEY_SELECTION_RANGE_MIN, 10)
        val max = prefs.getInt(KEY_SELECTION_RANGE_MAX, 40)
        // 存储到 StateFlow 中供 ViewModel 使用
    }

    // ==================== 模板管理 ====================

    fun getAllTemplates(): List<PromptTemplate> = _promptTemplates.value

    fun getTemplateById(id: String): PromptTemplate? = _promptTemplates.value.find { it.id == id }

    fun saveTemplate(template: PromptTemplate) {
        if (template.isBuiltin) {
            Logger.w(TAG, "Cannot save builtin template")
            return
        }
        val userTemplates = loadUserTemplates().toMutableList()
        val existingIndex = userTemplates.indexOfFirst { it.id == template.id }
        if (existingIndex >= 0) {
            userTemplates[existingIndex] = template
        } else {
            userTemplates.add(template)
        }
        saveUserTemplates(userTemplates)
        _promptTemplates.value = BUILTIN_TEMPLATES + userTemplates
    }

    fun deleteTemplate(templateId: String) {
        val userTemplates = loadUserTemplates().filter { it.id != templateId }
        saveUserTemplates(userTemplates)
        _promptTemplates.value = BUILTIN_TEMPLATES + userTemplates
    }

    fun generateTemplateId(): String = "prompt_${System.currentTimeMillis()}"

    private fun saveUserTemplates(templates: List<PromptTemplate>) {
        val array = JSONArray()
        templates.forEach { template ->
            val obj = JSONObject().apply {
                put("id", template.id)
                put("name", template.name)
                put("content", template.content)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PROMPT_TEMPLATES, array.toString()).apply()
    }

    // ==================== 通知数据管理 ====================

    fun regenerateNotifications(count: Int = 100) {
        val config = _distributionConfig.value
        val notifications = generateMockNotifications(count, config)
        _mockNotifications.value = notifications
        saveNotifications(notifications)
        _selectedNotificationIds.value = emptySet()
    }

    fun updateDistribution(config: DistributionConfig) {
        _distributionConfig.value = config
        prefs.edit().apply {
            putInt("${KEY_DISTRIBUTION_CONFIG}_wechat_qq", config.wechatQQPercent)
            putInt("${KEY_DISTRIBUTION_CONFIG}_work_wechat", config.workWechatPercent)
            putInt("${KEY_DISTRIBUTION_CONFIG}_sms", config.smsPercent)
            putInt("${KEY_DISTRIBUTION_CONFIG}_other", config.otherPercent)
            apply()
        }
    }

    fun updateSelectionRange(min: Int, max: Int) {
        prefs.edit().apply {
            putInt(KEY_SELECTION_RANGE_MIN, min)
            putInt(KEY_SELECTION_RANGE_MAX, max)
            apply()
        }
    }

    fun getSelectionRange(): Pair<Int, Int> {
        val min = prefs.getInt(KEY_SELECTION_RANGE_MIN, 10)
        val max = prefs.getInt(KEY_SELECTION_RANGE_MAX, 40)
        return Pair(min, max)
    }

    // ==================== 选择管理 ====================

    fun selectAllNotifications() {
        _selectedNotificationIds.value = _mockNotifications.value.map { it.id }.toSet()
    }

    fun deselectAllNotifications() {
        _selectedNotificationIds.value = emptySet()
    }

    fun randomSelectNotifications(min: Int, max: Int) {
        val notifications = _mockNotifications.value
        val count = (min..max).random()
        val shuffled = notifications.shuffled()
        _selectedNotificationIds.value = shuffled.take(count).map { it.id }.toSet()
    }

    fun toggleNotificationSelection(id: String) {
        val current = _selectedNotificationIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedNotificationIds.value = current
    }

    fun setSelectedNotifications(ids: Set<String>) {
        _selectedNotificationIds.value = ids
    }

    // ==================== 历史记录管理 ====================

    fun addHistory(history: DebugTestHistory) {
        val list = _testHistory.value.toMutableList()
        list.add(0, history)

        // 限制最大数量
        while (list.size > MAX_HISTORY_COUNT) {
            list.removeAt(list.size - 1)
        }

        _testHistory.value = list
        saveHistory(list)
    }

    fun getHistoryById(id: String): DebugTestHistory? = _testHistory.value.find { it.id == id }

    fun deleteHistory(historyId: String) {
        val list = _testHistory.value.filter { it.id != historyId }
        _testHistory.value = list
        saveHistory(list)
    }

    fun clearAllHistory() {
        _testHistory.value = emptyList()
        prefs.edit().remove(KEY_TEST_HISTORY).apply()
    }

    private fun saveHistory(list: List<DebugTestHistory>) {
        val array = JSONArray()
        list.forEach { history ->
            val obj = JSONObject().apply {
                put("id", history.id)
                put("promptTemplateId", history.promptTemplateId)
                put("promptTemplateName", history.promptTemplateName)
                put("inputNotifications", history.inputNotifications)
                put("modelResponse", history.modelResponse)
                put("timestamp", history.timestamp)
                put("success", history.success)
                // 新增字段
                put("systemPrompt", history.systemPrompt)
                put("userPrompt", history.userPrompt)
                put("modelName", history.modelName)
                put("baseUrl", history.baseUrl)
                put("temperature", history.temperature)
                put("maxTokens", history.maxTokens)
                put("notificationCount", history.notificationCount)
                put("notificationDistribution", history.notificationDistribution)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_TEST_HISTORY, array.toString()).apply()
    }

    // ==================== 选中模板管理 ====================

    fun getSelectedTemplateId(): String? = prefs.getString(KEY_SELECTED_TEMPLATE_ID, null)

    fun setSelectedTemplateId(id: String?) {
        prefs.edit().putString(KEY_SELECTED_TEMPLATE_ID, id).apply()
    }

    // ==================== 私有辅助方法 ====================

    private fun saveNotifications(notifications: List<MockNotification>) {
        val array = JSONArray()
        notifications.forEach { notification ->
            val obj = JSONObject().apply {
                put("id", notification.id)
                put("packageName", notification.packageName)
                put("appName", notification.appName)
                put("title", notification.title)
                put("text", notification.text)
                put("timestamp", notification.timestamp)
                put("category", notification.category.name)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_MOCK_NOTIFICATIONS, array.toString()).apply()
    }

    private fun generateMockNotifications(count: Int, config: DistributionConfig): List<MockNotification> {
        val notifications = mutableListOf<MockNotification>()
        val now = System.currentTimeMillis()

        // 计算每个类别的数量
        val wechatQQCount = (count * config.wechatQQPercent / 100)
        val workWechatCount = (count * config.workWechatPercent / 100)
        val smsCount = (count * config.smsPercent / 100)
        val otherCount = count - wechatQQCount - workWechatCount - smsCount

        // 微信/QQ 消息
        notifications.addAll(generateNotificationsForCategory(
            NotificationCategory.WECHAT_QQ, wechatQQCount, now
        ))

        // 企业微信消息
        notifications.addAll(generateNotificationsForCategory(
            NotificationCategory.WORK_WECHAT, workWechatCount, now
        ))

        // 短信
        notifications.addAll(generateNotificationsForCategory(
            NotificationCategory.SMS, smsCount, now
        ))

        // 其他
        notifications.addAll(generateNotificationsForCategory(
            NotificationCategory.OTHER, otherCount, now
        ))

        return notifications.sortedByDescending { it.timestamp }
    }

    private fun generateNotificationsForCategory(
        category: NotificationCategory,
        count: Int,
        baseTime: Long
    ): List<MockNotification> {
        val notifications = mutableListOf<MockNotification>()

        val configs = when (category) {
            NotificationCategory.WECHAT_QQ -> listOf(
                AppNotificationConfig("com.tencent.mm", "微信", listOf("张三", "李四", "王五", "工作群", "家庭群", "技术交流群")),
                AppNotificationConfig("com.tencent.mobileqq", "QQ", listOf("小明", "小红", "同学群", "游戏群"))
            )
            NotificationCategory.WORK_WECHAT -> listOf(
                AppNotificationConfig("com.tencent.wework", "企业微信", listOf("公司公告", "项目组", "部门群", "周报提醒"))
            )
            NotificationCategory.SMS -> listOf(
                AppNotificationConfig("com.android.messaging", "短信", listOf("验证码", "物流", "营销", "银行"))
            )
            NotificationCategory.OTHER -> listOf(
                AppNotificationConfig("com.taobao.taobao", "淘宝", listOf("物流更新", "促销活动")),
                AppNotificationConfig("com.jingdong.app.mall", "京东", listOf("订单状态", "优惠券")),
                AppNotificationConfig("com.sankuai.meituan", "美团", listOf("外卖状态", "优惠领取")),
                AppNotificationConfig("com.android.system", "系统", listOf("存储空间", "系统更新", "安全警告"))
            )
        }

        val messageTemplates = mapOf(
            "微信" to listOf(
                "{sender}: 你好，明天的会议几点开始？",
                "{sender}: 收到，我马上处理",
                "{sender}: [图片]",
                "{sender}: 晚上一起吃饭吗？",
                "{sender}: 文件已发送，请查收",
                "{sender}: 这个问题怎么解决？",
                "{sender}: 周末有空吗？"
            ),
            "QQ" to listOf(
                "{sender}: 在吗？",
                "{sender}: 游戏开黑吗？",
                "{sender}: 作业写完了吗？",
                "{sender}: [表情]"
            ),
            "企业微信" to listOf(
                "【{sender}】您有一个待审批的申请",
                "【{sender}】会议将于15分钟后开始",
                "【{sender}】本周周报已截止提交",
                "【{sender}】项目进度更新通知",
                "【{sender}】新任务已分配给您"
            ),
            "短信" to listOf(
                "【验证码】您的验证码是 ${java.util.Random().nextInt(900000) + 100000}，5分钟内有效。",
                "【物流】您的快递已到达${arrayOf("北京", "上海", "广州", "深圳").random()}转运中心。",
                "【营销】限时优惠！全场5折起，点击查看详情。",
                "【银行】您尾号${java.util.Random().nextInt(9000) + 1000}的账户于${java.util.Random().nextInt(23)}:${String.format("%02d", java.util.Random().nextInt(60))}支出${java.util.Random().nextInt(900) + 100}元。"
            ),
            "淘宝" to listOf(
                "您的包裹正在派送中，预计今日送达",
                "您关注的商品正在降价促销",
                "订单已发货，快递单号: SF${java.util.Random().nextInt(900000000) + 100000000}",
                "您有未使用的优惠券即将过期"
            ),
            "京东" to listOf(
                "订单已签收，感谢您的购买",
                "您关注的商品有货了",
                "京东PLUS会员即将到期"
            ),
            "美团" to listOf(
                "外卖已送达，祝您用餐愉快",
                "您有新的优惠券可用",
                "商家已接单，正在准备中"
            ),
            "系统" to listOf(
                "存储空间不足，请清理不必要的文件",
                "系统更新可用，建议连接WiFi后下载",
                "检测到异常登录，请确认是否为本人操作",
                "电池电量低，请及时充电"
            )
        )

        for (i in 0 until count) {
            val app = configs.random()
            val sender = app.senders.random()
            val templates = messageTemplates[app.appName] ?: listOf("来自${app.appName}的通知")
            val message = templates.random().replace("{sender}", sender)

            notifications.add(
                MockNotification(
                    id = UUID.randomUUID().toString(),
                    packageName = app.packageName,
                    appName = app.appName,
                    title = if (category == NotificationCategory.SMS || category == NotificationCategory.OTHER) {
                        app.appName
                    } else {
                        sender
                    },
                    text = message,
                    timestamp = baseTime - (0..86400000).random(), // 最近24小时内
                    category = category
                )
            )
        }

        return notifications
    }

    private data class AppNotificationConfig(
        val packageName: String,
        val appName: String,
        val senders: List<String>
    )
}
