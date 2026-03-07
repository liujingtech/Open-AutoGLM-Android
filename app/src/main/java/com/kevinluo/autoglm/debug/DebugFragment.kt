package com.kevinluo.autoglm.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.util.showWithPrimaryButtons
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugFragment : Fragment() {

    private val viewModel: DebugViewModel by viewModels()
    private var historyAdapter: DebugHistoryAdapter? = null
    private var notificationAdapter: NotificationListAdapter? = null

    private var hasUnsavedChanges = false
    private var currentEditingContent = ""

    // Views
    private lateinit var templateChipGroup: ChipGroup
    private lateinit var promptEditText: TextInputEditText
    private lateinit var distributionSlider: Slider
    private lateinit var distributionLabel: TextView
    private lateinit var rangeSlider: RangeSlider
    private lateinit var totalCountText: TextView
    private lateinit var selectedCountText: TextView
    private lateinit var historyCountText: TextView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var notificationRecyclerView: RecyclerView
    private lateinit var responseTextView: TextView
    private lateinit var btnRunTest: MaterialButton
    private lateinit var btnCancelTest: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_debug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupViews()
        observeViewModel()
    }

    private fun initViews(view: View) {
        templateChipGroup = view.findViewById(R.id.templateChipGroup)
        promptEditText = view.findViewById(R.id.promptEditText)
        distributionSlider = view.findViewById(R.id.distributionSlider)
        distributionLabel = view.findViewById(R.id.distributionLabel)
        rangeSlider = view.findViewById(R.id.rangeSlider)
        totalCountText = view.findViewById(R.id.totalCountText)
        selectedCountText = view.findViewById(R.id.selectedCountText)
        historyCountText = view.findViewById(R.id.historyCountText)
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView)
        notificationRecyclerView = view.findViewById(R.id.notificationRecyclerView)
        responseTextView = view.findViewById(R.id.responseTextView)
        btnRunTest = view.findViewById(R.id.btnRunTest)
        btnCancelTest = view.findViewById(R.id.btnCancelTest)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupViews() {
        // 设置历史列表
        historyAdapter = DebugHistoryAdapter(
            history = emptyList(),
            onItemClick = { history -> showHistoryDetail(history) },
            onDeleteClick = { history -> confirmDeleteHistory(history) }
        )
        historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        historyRecyclerView.adapter = historyAdapter

        // 设置通知列表
        notificationAdapter = NotificationListAdapter(
            notifications = emptyList(),
            selectedIds = emptySet(),
            onItemToggle = { id, isSelected ->
                viewModel.toggleNotificationSelection(id, isSelected)
            }
        )
        notificationRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        notificationRecyclerView.adapter = notificationAdapter

        // 分布滑块
        distributionSlider.value = 30f
        updateDistributionLabel(30)
        distributionSlider.addOnChangeListener { _, value, _ ->
            updateDistributionLabel(value.toInt())
        }

        // 范围滑块
        rangeSlider.values = listOf(10f, 40f)
        rangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            viewModel.updateSelectionRange(values[0].toInt(), values[1].toInt())
        }

        // 按钮点击事件
        view?.findViewById<MaterialButton>(R.id.btnSaveTemplate)?.setOnClickListener {
            showSaveTemplateDialog()
        }

        view?.findViewById<MaterialButton>(R.id.btnDeleteTemplate)?.setOnClickListener {
            viewModel.uiState.value.selectedTemplateId?.let { templateId ->
                confirmDeleteTemplate(templateId)
            }
        }

        view?.findViewById<MaterialButton>(R.id.btnRegenerate)?.setOnClickListener {
            viewModel.regenerateNotifications()
            Toast.makeText(requireContext(), R.string.debug_regenerated, Toast.LENGTH_SHORT).show()
        }

        view?.findViewById<MaterialButton>(R.id.btnSelectAll)?.setOnClickListener {
            viewModel.selectAllNotifications()
        }

        view?.findViewById<MaterialButton>(R.id.btnDeselectAll)?.setOnClickListener {
            viewModel.deselectAllNotifications()
        }

        view?.findViewById<MaterialButton>(R.id.btnRandomSelect)?.setOnClickListener {
            viewModel.randomSelectNotifications()
        }

        btnRunTest.setOnClickListener {
            if (hasUnsavedChanges) {
                showUnsavedChangesDialog {
                    runTest()
                }
            } else {
                runTest()
            }
        }

        btnCancelTest.setOnClickListener {
            viewModel.cancelTest()
        }

        view?.findViewById<ImageButton>(R.id.btnClearHistory)?.setOnClickListener {
            confirmClearAllHistory()
        }

        // 提示词编辑监听
        promptEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentEditingContent = s?.toString() ?: ""
                val currentTemplate = viewModel.uiState.value.selectedTemplateId?.let {
                    viewModel.promptTemplates.value.find { t -> t.id == it }
                }
                hasUnsavedChanges = currentTemplate?.content != currentEditingContent
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.promptTemplates.collect { templates ->
                updateTemplateChips(templates)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mockNotifications.collect { notifications ->
                totalCountText.text = getString(R.string.debug_total_count_format, notifications.size)
                notificationAdapter?.updateData(notifications, viewModel.selectedNotificationIds.value)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedNotificationIds.collect { ids ->
                selectedCountText.text = getString(R.string.debug_selected_count_format, ids.size)
                notificationAdapter?.updateSelectedIds(ids)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.testHistory.collect { history ->
                historyAdapter?.updateData(history)
                historyCountText.text = getString(R.string.debug_history_count_format, history.size)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // 更新运行状态
                btnRunTest.isEnabled = !state.isRunning
                btnCancelTest.isEnabled = state.isRunning
                progressBar.visibility = if (state.isRunning) View.VISIBLE else View.GONE

                // 更新响应文本
                if (state.responseText.isNotEmpty()) {
                    responseTextView.text = state.responseText
                }

                // 显示错误
                state.errorMessage?.let { error ->
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateTemplateChips(templates: List<PromptTemplate>) {
        templateChipGroup.removeAllViews()

        templates.forEach { template ->
            val chip = Chip(requireContext()).apply {
                text = template.name
                isClickable = true
                isCheckable = true
                tag = template.id

                setOnClickListener {
                    if (hasUnsavedChanges) {
                        showUnsavedChangesDialog {
                            selectTemplate(template)
                        }
                    } else {
                        selectTemplate(template)
                    }
                }
            }
            templateChipGroup.addView(chip)
        }

        // 选中当前模板
        viewModel.uiState.value.selectedTemplateId?.let { selectedId ->
            val chipCount = templateChipGroup.childCount
            for (i in 0 until chipCount) {
                val chip = templateChipGroup.getChildAt(i) as? Chip
                if (chip?.tag == selectedId) {
                    chip.isChecked = true
                    break
                }
            }
        }
    }

    private fun selectTemplate(template: PromptTemplate) {
        viewModel.selectTemplate(template.id)
        promptEditText.setText(template.content)
        hasUnsavedChanges = false
        currentEditingContent = template.content

        // 更新选中状态
        val chipCount = templateChipGroup.childCount
        for (i in 0 until chipCount) {
            val chip = templateChipGroup.getChildAt(i) as? Chip
            chip?.isChecked = (chip.tag == template.id)
        }
    }

    private fun updateDistributionLabel(value: Int) {
        // 计算各类别百分比
        val wechatQQ = value
        val remaining = 100 - value
        val workWechat = remaining / 4
        val sms = remaining / 2
        val other = remaining - workWechat - sms

        distributionLabel.text = getString(
            R.string.debug_distribution_format,
            wechatQQ,
            workWechat,
            sms,
            other
        )

        viewModel.updateDistribution(DistributionConfig(wechatQQ, workWechat, sms, other))
    }

    private fun showSaveTemplateDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_task_template, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.templateNameInput)
        val contentInput = dialogView.findViewById<TextInputEditText>(R.id.templateDescriptionInput)

        contentInput.setText(currentEditingContent)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debug_save_template_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                val content = contentInput.text?.toString()?.trim() ?: ""

                if (name.isEmpty() || content.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.debug_template_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.saveTemplateAsNew(name, content)
                hasUnsavedChanges = false
                Toast.makeText(requireContext(), R.string.debug_template_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun confirmDeleteTemplate(templateId: String) {
        val template = viewModel.promptTemplates.value.find { it.id == templateId }
        if (template?.isBuiltin == true) {
            Toast.makeText(requireContext(), R.string.debug_cannot_delete_builtin, Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debug_delete_template)
            .setMessage(R.string.debug_delete_template_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                viewModel.deleteTemplate(templateId)
                Toast.makeText(requireContext(), R.string.debug_template_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun showUnsavedChangesDialog(onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debug_unsaved_changes)
            .setMessage(R.string.debug_unsaved_changes_message)
            .setPositiveButton(R.string.dialog_discard) { _, _ ->
                onConfirm()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun runTest() {
        val content = currentEditingContent
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), R.string.debug_prompt_empty, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.runTest()
    }

    private fun showHistoryDetail(history: DebugTestHistory) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(history.promptTemplateName)
            .setMessage("""
时间: ${dateFormat.format(Date(history.timestamp))}

输入数据:
${history.inputNotifications.take(500)}${if (history.inputNotifications.length > 500) "..." else ""}

模型响应:
${history.modelResponse}
            """.trimIndent())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmDeleteHistory(history: DebugTestHistory) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debug_delete_history)
            .setMessage(R.string.debug_delete_history_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                viewModel.deleteHistory(history.id)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun confirmClearAllHistory() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debug_clear_history)
            .setMessage(R.string.debug_clear_history_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                viewModel.clearAllHistory()
                Toast.makeText(requireContext(), R.string.debug_history_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    companion object {
        private const val TAG = "DebugFragment"
    }
}
