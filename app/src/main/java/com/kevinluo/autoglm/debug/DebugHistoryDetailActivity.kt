package com.kevinluo.autoglm.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.kevinluo.autoglm.BaseActivity
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.util.Logger
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试测试历史详情页面
 *
 * 展示测试记录的完整信息，支持复制系统提示词、用户提示词、模型响应
 */
class DebugHistoryDetailActivity : BaseActivity() {

    private val viewModel: DebugViewModel by viewModels()
    private var historyId: String? = null
    private var history: DebugTestHistory? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // Views
    private lateinit var templateNameText: TextView
    private lateinit var timestampText: TextView
    private lateinit var statusText: TextView
    private lateinit var modelNameText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var maxTokensText: TextView
    private lateinit var baseUrlText: TextView
    private lateinit var notificationCountText: TextView
    private lateinit var notificationDistributionText: TextView
    private lateinit var systemPromptText: TextView
    private lateinit var userPromptText: TextView
    private lateinit var responseText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_history_detail)
        setupEdgeToEdgeInsets(R.id.rootLayout, applyTop = true, applyBottom = false)

        historyId = intent.getStringExtra(EXTRA_HISTORY_ID)

        initViews()
        setupListeners()
        loadData()
    }

    private fun initViews() {
        templateNameText = findViewById(R.id.templateNameText)
        timestampText = findViewById(R.id.timestampText)
        statusText = findViewById(R.id.statusText)
        modelNameText = findViewById(R.id.modelNameText)
        temperatureText = findViewById(R.id.temperatureText)
        maxTokensText = findViewById(R.id.maxTokensText)
        baseUrlText = findViewById(R.id.baseUrlText)
        notificationCountText = findViewById(R.id.notificationCountText)
        notificationDistributionText = findViewById(R.id.notificationDistributionText)
        systemPromptText = findViewById(R.id.systemPromptText)
        userPromptText = findViewById(R.id.userPromptText)
        responseText = findViewById(R.id.responseText)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.exportBtn).setOnClickListener {
            history?.let { exportAll(it) }
        }

        findViewById<ImageButton>(R.id.deleteBtn).setOnClickListener {
            confirmDelete()
        }

        findViewById<MaterialButton>(R.id.copySystemPromptBtn).setOnClickListener {
            history?.let { copyToClipboard("系统提示词", it.systemPrompt) }
        }

        findViewById<MaterialButton>(R.id.copyUserPromptBtn).setOnClickListener {
            history?.let { copyToClipboard("用户提示词", it.userPrompt) }
        }

        findViewById<MaterialButton>(R.id.copyResponseBtn).setOnClickListener {
            history?.let { copyToClipboard("模型响应", it.modelResponse) }
        }

        findViewById<MaterialButton>(R.id.copyInputDataBtn).setOnClickListener {
            history?.let { copyToClipboard("输入数据", it.inputNotifications) }
        }
    }

    private fun loadData() {
        val id = historyId ?: return
        lifecycleScope.launch {
            history = viewModel.getHistoryById(id)
            history?.let { bindData(it) }
        }
    }

    private fun bindData(h: DebugTestHistory) {
        // 基本信息
        templateNameText.text = h.promptTemplateName
        timestampText.text = dateFormat.format(Date(h.timestamp))
        statusText.text = if (h.success) "✓ 成功" else "✗ 失败"
        statusText.setTextColor(
            getColor(if (h.success) R.color.primary else R.color.error)
        )

        // 模型参数
        modelNameText.text = h.modelName.ifEmpty { "未记录" }
        temperatureText.text = h.temperature.toString()
        maxTokensText.text = h.maxTokens.toString()
        baseUrlText.text = h.baseUrl.ifEmpty { "未记录" }

        // 输入数据
        notificationCountText.text = "${h.notificationCount} 条"
        notificationDistributionText.text = h.notificationDistribution.ifEmpty { "未记录" }

        // 提示词和响应
        systemPromptText.text = h.systemPrompt.ifEmpty { "未记录" }
        userPromptText.text = h.userPrompt.ifEmpty { "未记录" }
        responseText.text = h.modelResponse
    }

    private fun copyToClipboard(label: String, content: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label 已复制", Toast.LENGTH_SHORT).show()
    }

    private fun exportAll(h: DebugTestHistory) {
        val content = """
========== 测试记录 ==========
模板: ${h.promptTemplateName}
时间: ${dateFormat.format(Date(h.timestamp))}
状态: ${if (h.success) "成功" else "失败"}

========== 模型配置 ==========
模型: ${h.modelName}
Temperature: ${h.temperature}
Max Tokens: ${h.maxTokens}
API: ${h.baseUrl}

========== 输入数据 ==========
通知数量: ${h.notificationCount}
分布: ${h.notificationDistribution}

========== 系统提示词 ==========
${h.systemPrompt}

========== 用户提示词 ==========
${h.userPrompt}

========== 模型响应 ==========
${h.modelResponse}
""".trimIndent()

        copyToClipboard("测试记录", content)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.debug_delete_history)
            .setMessage(R.string.debug_delete_history_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                historyId?.let { viewModel.deleteHistory(it) }
                Toast.makeText(this, R.string.debug_history_deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    companion object {
        private const val TAG = "DebugHistoryDetailActivity"
        const val EXTRA_HISTORY_ID = "history_id"

        fun start(context: Context, historyId: String) {
            val intent = Intent(context, DebugHistoryDetailActivity::class.java).apply {
                putExtra(EXTRA_HISTORY_ID, historyId)
            }
            context.startActivity(intent)
        }
    }
}
