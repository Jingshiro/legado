package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAiMemoryBinding
import io.legado.app.databinding.ItemAiMemoryBinding
import io.legado.app.help.config.AiConfig
import io.legado.app.help.config.MemoryItem
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiMemoryDialog : BaseDialogFragment(R.layout.dialog_ai_memory) {

    private val binding by viewBinding(DialogAiMemoryBinding::bind)
    private lateinit var adapter: MemoryAdapter

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MemoryAdapter { key ->
            AiConfig.deleteMemory(key)
            refreshList()
        }
        binding.rvMemoryList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMemoryList.adapter = adapter

        refreshList()

        binding.btnClearAllMemory.setOnClickListener {
            AiConfig.clearAllMemory()
            refreshList()
            toastOnUi("已清空全部记忆")
        }
    }

    private fun refreshList() {
        val list = AiConfig.getMemoryList()
        if (list.isEmpty()) {
            binding.tvMemoryEmpty.visibility = View.VISIBLE
            binding.rvMemoryList.visibility = View.GONE
        } else {
            binding.tvMemoryEmpty.visibility = View.GONE
            binding.rvMemoryList.visibility = View.VISIBLE
            adapter.setData(list)
        }
    }

    // ---- 内部 Adapter ----
    private class MemoryAdapter(
        private val onDelete: (Long) -> Unit
    ) : RecyclerView.Adapter<MemoryAdapter.VH>() {

        private val items = mutableListOf<MemoryItem>()

        fun setData(list: List<MemoryItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemAiMemoryBinding.inflate(
                android.view.LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val preview = if (item.content.length > 15) {
                item.content.take(15) + "..."
            } else {
                item.content
            }
            val chapterLabel = if (item.start == item.end) {
                "第${item.start}章"
            } else {
                "第${item.start}章 - 第${item.end}章"
            }
            holder.binding.tvMemoryPreview.text = preview
            holder.binding.tvMemoryChapter.text = chapterLabel
            holder.binding.btnDeleteMemory.setOnClickListener {
                onDelete(item.key)
            }
        }

        override fun getItemCount() = items.size

        class VH(val binding: ItemAiMemoryBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
