package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAiConfigBinding
import io.legado.app.help.config.AiConfig
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiConfigDialog : BaseDialogFragment(R.layout.dialog_ai_config) {

    private val binding by viewBinding(DialogAiConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        bindEvent()
    }

    fun updateMemoryLength() {
        binding.tvMemoryLength.text = AiConfig.memory.length.toString()
    }

    private fun initData() {
        binding.etApiUrl.setText(AiConfig.apiUrl)
        binding.etApiKey.setText(AiConfig.apiKey)
        binding.etModel.setText(AiConfig.model)
        binding.etPersona.setText(AiConfig.persona)
        binding.etUserAvatar.setText(AiConfig.userAvatar)
        binding.etAiAvatar.setText(AiConfig.aiAvatar)
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
    }
}
