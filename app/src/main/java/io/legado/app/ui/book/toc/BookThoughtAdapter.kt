package io.legado.app.ui.book.toc

import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookThought
import io.legado.app.databinding.ItemBookThoughtBinding
import io.legado.app.utils.gone
import splitties.views.onLongClick

class BookThoughtAdapter(context: Context, val callback: Callback) :
    RecyclerAdapter<BookThought, ItemBookThoughtBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookThoughtBinding {
        return ItemBookThoughtBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookThoughtBinding,
        item: BookThought,
        payloads: MutableList<Any>
    ) {
        binding.tvChapterName.text = item.chapterName
        binding.tvSelectedText.gone(item.selectedText.isEmpty())
        binding.tvSelectedText.text = item.selectedText
        binding.tvThought.gone(item.thought.isEmpty())
        binding.tvThought.text = item.thought
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookThoughtBinding) {
        binding.root.setOnClickListener {
            getItem(holder.layoutPosition)?.let { thought ->
                callback.onClick(thought, holder.layoutPosition)
            }
        }
        binding.root.onLongClick {
            getItem(holder.layoutPosition)?.let { thought ->
                callback.onLongClick(thought, holder.layoutPosition)
            }
        }
    }

    interface Callback {
        fun onClick(bookThought: BookThought, pos: Int)
        fun onLongClick(bookThought: BookThought, pos: Int)
    }
}
