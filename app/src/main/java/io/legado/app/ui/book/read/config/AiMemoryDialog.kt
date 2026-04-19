package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAiMemoryBinding
import io.legado.app.help.config.AiConfig
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiMemoryDialog : BaseDialogFragment(R.layout.dialog_ai_memory) {

    private val binding by viewBinding(DialogAiMemoryBinding::bind)
    private val adapter by lazy {
        AiMemoryAdapter { item ->
            val list = AiConfig.memoryList.toMutableList()
            list.remove(item)
            AiConfig.memoryList = list
            updateList()
            notifyConfigDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        bindEvent()
        updateList()
    }

    private fun initView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun bindEvent() {
        binding.btnClearAll.setOnClickListener {
            if (AiConfig.memoryList.isEmpty()) {
                toastOnUi("暂无记忆")
                return@setOnClickListener
            }
            AiConfig.memoryList = emptyList()
            updateList()
            notifyConfigDialog()
            toastOnUi("已清空")
        }
    }

    private fun updateList() {
        adapter.submitList(AiConfig.memoryList)
    }

    private fun notifyConfigDialog() {
        val fragments = parentFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is AiConfigDialog) {
                fragment.updateMemoryLength()
                break
            }
        }
    }
}