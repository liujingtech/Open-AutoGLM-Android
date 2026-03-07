package com.kevinluo.autoglm.debug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevinluo.autoglm.model.ChatMessage
import com.kevinluo.autoglm.model.ModelClient
import com.kevinluo.autoglm.model.ModelResult
import com.kevinluo.autoglm.model.NetworkError
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DebugViewModel(application: Application) : AndroidViewModel(application) {

    private val debugDataManager = DebugDataManager.getInstance(application)
    private val settingsManager = SettingsManager.getInstance(application)

    data class DebugUiState(
        val selectedTemplateId: String? = null,
        val editedContent: String = "",
        val hasUnsavedChanges: Boolean = false,
        val selectionMin: Int = 10,
        val selectionMax: Int = 40,
        val isRunning: Boolean = false,
        val responseText: String = "",
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState

    // Exposed data from manager
    val promptTemplates = debugDataManager.promptTemplates
    val mockNotifications = debugDataManager.mockNotifications
    val testHistory = debugDataManager.testHistory
    val distributionConfig = debugDataManager.distributionConfig
    val selectedNotificationIds = debugDataManager.selectedNotificationIds

    // ==================== 模板操作 ====================

    fun selectTemplate(templateId: String) {
        val template = debugDataManager.getTemplateById(templateId)
        _uiState.value = _uiState.value.copy(
            selectedTemplateId = templateId,
            editedContent = template?.content ?: "",
            hasUnsavedChanges = false
        )
    }

    // ==================== 数据选择操作 ====================

    fun selectAllNotifications() {
        debugDataManager.selectAllNotifications()
    }

    fun deselectAllNotifications() {
        debugDataManager.deselectAllNotifications()
    }

    fun randomSelectNotifications() {
        val min = _uiState.value.selectionMin
        val max = _uiState.value.selectionMax
        val actualMin = min.coerceAtMost(max)
        val actualMax = max.coerceAtLeast(min)
        debugDataManager.randomSelectNotifications(actualMin, actualMax)
    }

    fun toggleNotificationSelection(id: String, isSelected: Boolean) {
        debugDataManager.toggleNotificationSelection(id)
    }

    fun updateSelectionRange(min: Int, max: Int) {
        debugDataManager.updateSelectionRange(min, max)
        _uiState.value = _uiState.value.copy(
            selectionMin = min,
            selectionMax = max
        )
    }

    // ==================== 数据生成操作 ====================

    fun regenerateNotifications() {
        debugDataManager.regenerateNotifications()
    }

    fun updateDistribution(config: DistributionConfig) {
        debugDataManager.updateDistribution(config)
    }

    // ==================== 模型测试操作 ====================

    fun runTest() {
        val templateId = _uiState.value.selectedTemplateId
        if (templateId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先选择提示词模板")
            return
        }

        val selectedIds = selectedNotificationIds.value
        if (selectedIds.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先选择测试数据")
            return
        }

        val template = debugDataManager.getTemplateById(templateId)
        if (template == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "模板不存在")
            return
        }

        val selectedNotifications = mockNotifications.value.filter { it.id in selectedIds }
        val notificationsJson = buildNotificationsJson(selectedNotifications)
        val prompt = template.content.replace("{notifications}", notificationsJson)

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            responseText = "",
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val modelConfig = settingsManager.getModelConfig()
                Logger.d(TAG, "Model config: baseUrl=${modelConfig.baseUrl}, modelName=${modelConfig.modelName}, apiKeyLength=${modelConfig.apiKey.length}")

                // 检查 API key 是否有效
                if (modelConfig.apiKey == "EMPTY" || modelConfig.apiKey.length < 10) {
                    _uiState.value = _uiState.value.copy(
                        isRunning = false,
                        responseText = "错误: API Key 未配置或无效，请在设置页面保存 API Key",
                        errorMessage = "API Key 未配置"
                    )
                    return@launch
                }

                Logger.d(TAG, "Prompt length: ${prompt.length} chars")

                // 简化：直接调用现有的 ModelClient
                val client = ModelClient(modelConfig)
                val messages = listOf(
                    ChatMessage.System("你是一个帮助用户处理通知消息的智能助手。请根据用户的要求处理通知数据。"),
                    ChatMessage.User(prompt)
                )

                Logger.d(TAG, "Sending request with ${messages.size} messages")
                when (val result = client.request(messages)) {
                    is ModelResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isRunning = false,
                            responseText = result.response.rawContent
                        )
                        // 保存到历史记录
                        debugDataManager.addHistory(
                            DebugTestHistory(
                                promptTemplateId = templateId,
                                promptTemplateName = template.name,
                                inputNotifications = notificationsJson,
                                modelResponse = result.response.rawContent,
                                success = true
                            )
                        )
                    }
                    is ModelResult.Error -> {
                        val error = result.error
                        val errorDetail = when (error) {
                            is NetworkError.ServerError -> "ServerError(${(error as NetworkError.ServerError).statusCode}): ${error.message}"
                            is NetworkError.Timeout -> "Timeout: ${error.message}"
                            is NetworkError.ConnectionFailed -> "ConnectionFailed: ${error.message}"
                            is NetworkError.ParseError -> "ParseError: ${error.message}"
                        }
                        Logger.e(TAG, "Test failed: $errorDetail")
                        val errorInfo = when (error) {
                            is NetworkError.ServerError -> "服务器错误(${(error as NetworkError.ServerError).statusCode}): ${error.message}"
                            is NetworkError.Timeout -> "请求超时: ${error.message}"
                            is NetworkError.ConnectionFailed -> "连接失败: ${error.message}"
                            is NetworkError.ParseError -> "解析错误: ${error.message}"
                        }
                        _uiState.value = _uiState.value.copy(
                            isRunning = false,
                            responseText = "错误: $errorInfo",
                            errorMessage = errorInfo
                        )
                        // 保存失败记录
                        debugDataManager.addHistory(
                            DebugTestHistory(
                                promptTemplateId = templateId,
                                promptTemplateName = template.name,
                                inputNotifications = notificationsJson,
                                modelResponse = "错误: ${error.message}",
                                success = false
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Test failed", e)
                val error = e.message ?: "未知错误"
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    responseText = "错误: $error",
                    errorMessage = error
                )

                // 保存失败记录
                debugDataManager.addHistory(
                    DebugTestHistory(
                        promptTemplateId = templateId,
                        promptTemplateName = template.name,
                        inputNotifications = notificationsJson,
                        modelResponse = "错误: $error",
                        success = false
                    )
                )
            }
        }
    }

    fun cancelTest() {
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    // ==================== 历史操作 ====================

    fun deleteHistory(historyId: String) {
        debugDataManager.deleteHistory(historyId)
    }

    fun clearAllHistory() {
        debugDataManager.clearAllHistory()
    }

    // ==================== 辅助方法 ====================

    private fun buildNotificationsJson(notifications: List<MockNotification>): String {
        val jsonArray = notifications.map { notification ->
            """{
  "package": "${notification.packageName}",
  "appName": "${notification.appName}",
  "title": "${notification.title}",
  "text": "${notification.text}",
  "time": ${notification.timestamp}
}"""
        }.joinToString(",\n")
        return "[\n$jsonArray\n]"
    }

    // ==================== 模板保存删除操作 ====================

    fun saveTemplateAsNew(name: String, content: String) {
        val template = PromptTemplate(
            id = debugDataManager.generateTemplateId(),
            name = name,
            content = content,
            isBuiltin = false
        )
        debugDataManager.saveTemplate(template)
    }

    fun deleteTemplate(templateId: String) {
        debugDataManager.deleteTemplate(templateId)
    }

    companion object {
        private const val TAG = "DebugViewModel"
    }
}
