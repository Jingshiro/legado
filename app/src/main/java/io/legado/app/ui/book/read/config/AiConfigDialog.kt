package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.Theme
import io.legado.app.databinding.DialogAiConfigBinding
import io.legado.app.help.config.AiConfig
import io.legado.app.ui.book.read.ai.AiChatViewModel
import io.legado.app.utils.applyTint
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.lib.dialogs.alert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiConfigDialog : BaseDialogFragment(R.layout.dialog_ai_config) {

    private val binding by viewBinding(DialogAiConfigBinding::bind)
    private val viewModel by viewModels<AiChatViewModel>()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initToolbar()
        initData()
        bindEvent()
    }

    fun updateMemoryLength() {
        binding.tvMemoryLength.text = "${AiConfig.memoryList.size} 条"
    }

    private fun initToolbar() {
        binding.titleBar.setTitleTextColor(Color.WHITE)
        binding.titleBar.toolbar.apply {
            inflateMenu(R.menu.ai_config)
            menu.applyTint(requireContext(), Theme.Dark)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_help -> showHelp("aiCompanionHelp")
                }
                true
            }
        }
    }

    private fun initData() {
        binding.etApiUrl.setText(AiConfig.apiUrl)
        binding.etApiKey.setText(AiConfig.apiKey)
        binding.etModel.setText(AiConfig.model)
        binding.etPersona.setText(AiConfig.persona)
        binding.etUserAvatar.setText(AiConfig.userAvatar)
        binding.etAiAvatar.setText(AiConfig.aiAvatar)
        binding.swtToolEnabled.isChecked = AiConfig.toolEnabled
        updateMemoryLength()
    }

    private fun bindEvent() {
        binding.btnSave.setOnClickListener {
            AiConfig.apiUrl = binding.etApiUrl.text?.toString() ?: ""
            AiConfig.apiKey = binding.etApiKey.text?.toString() ?: ""
            AiConfig.model = binding.etModel.text?.toString() ?: ""
            AiConfig.persona = binding.etPersona.text?.toString() ?: ""
            AiConfig.userAvatar = binding.etUserAvatar.text?.toString() ?: ""
            AiConfig.aiAvatar = binding.etAiAvatar.text?.toString() ?: ""
            AiConfig.toolEnabled = binding.swtToolEnabled.isChecked
            toastOnUi("配置已保存")
            dismiss()
        }

        binding.btnViewMemory.setOnClickListener {
            showDialogFragment(AiMemoryDialog())
        }

        binding.btnClearMemory.setOnClickListener {
            AiConfig.memoryList = emptyList()
            updateMemoryLength()
            toastOnUi("记忆已清空")
        }

        // 拉取模型列表
        binding.btnFetchModels.setOnClickListener {
            val apiUrl = binding.etApiUrl.text?.toString()?.trim() ?: ""
            val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (apiUrl.isBlank() || apiKey.isBlank()) {
                toastOnUi("请先填写 API URL 和 API Key")
                return@setOnClickListener
            }
            binding.btnFetchModels.isEnabled = false
            binding.btnFetchModels.text = "拉取中…"

            lifecycleScope.launch {
                try {
                    val models = viewModel.fetchModels(apiUrl, apiKey)
                    withContext(Dispatchers.Main) {
                        if (models.isEmpty()) {
                            toastOnUi("未获取到任何模型")
                        } else {
                            showModelPickerDialog(models)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        toastOnUi("拉取失败: ${e.message}")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        binding.btnFetchModels.isEnabled = true
                        binding.btnFetchModels.text = "拉取模型列表"
                    }
                }
            }
        }

        // 测试模型可用性
        binding.btnTestModel.setOnClickListener {
            val apiUrl = binding.etApiUrl.text?.toString()?.trim() ?: ""
            val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
            val model = binding.etModel.text?.toString()?.trim() ?: ""
            if (apiUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                toastOnUi("请先填写 API URL、API Key 和模型名称")
                return@setOnClickListener
            }
            binding.btnTestModel.isEnabled = false
            binding.btnTestModel.text = "测试中…"

            lifecycleScope.launch {
                try {
                    val reply = viewModel.testModel(apiUrl, apiKey, model)
                    withContext(Dispatchers.Main) {
                        alert("模型测试成功 ✓") {
                            setMessage("模型 $model 响应正常。\n\n回复内容：$reply")
                            okButton()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        alert("模型测试失败 ✗") {
                            setMessage("模型 $model 请求失败：\n${e.message}")
                            okButton()
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        binding.btnTestModel.isEnabled = true
                        binding.btnTestModel.text = "测试模型可用性"
                    }
                }
            }
        }
    }

    /**
     * 展示模型选择对话框，用户点击某个模型后自动填入输入框
     */
    private fun showModelPickerDialog(models: List<String>) {
        val items = models.toTypedArray()
        alert("选择模型（共 ${models.size} 个）") {
            items(models) { _, item, _ ->
                binding.etModel.setText(item)
                toastOnUi("已选择模型：$item")
            }
        }
    }
}
