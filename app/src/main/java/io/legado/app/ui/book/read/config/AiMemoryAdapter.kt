package io.legado.app.ui.book.read.config

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.databinding.ItemAiMemoryBinding
import io.legado.app.help.config.AiMemoryItem

class AiMemoryAdapter(
    private val onDelete: (AiMemoryItem) -> Unit
) : ListAdapter<AiMemoryItem, AiMemoryAdapter.MemoryViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val binding = ItemAiMemoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvChapterRange.text = item.chapterRange
        holder.binding.tvContentPreview.text = item.preview
        holder.binding.ivDelete.setOnClickListener {
            onDelete(item)
        }
    }

    class MemoryViewHolder(val binding: ItemAiMemoryBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AiMemoryItem>() {
            override fun areItemsTheSame(oldItem: AiMemoryItem, newItem: AiMemoryItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: AiMemoryItem, newItem: AiMemoryItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}