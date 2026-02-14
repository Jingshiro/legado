package io.legado.app.ui.book.toc

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.BookThought
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ItemTocChapterHeaderBinding
import io.legado.app.databinding.ItemTocMarkEntryBinding
import io.legado.app.utils.gone

class BookmarkThoughtAdapter(
    private val callback: Callback
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<TocMarkListItem>()

    fun setItems(data: List<TocMarkListItem>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TocMarkListItem.ChapterHeader -> VIEW_TYPE_HEADER
            is TocMarkListItem.BookmarkItem -> VIEW_TYPE_BOOKMARK
            is TocMarkListItem.ThoughtItem -> VIEW_TYPE_THOUGHT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemTocChapterHeaderBinding.inflate(inflater, parent, false)
            )

            else -> EntryViewHolder(
                ItemTocMarkEntryBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TocMarkListItem.ChapterHeader -> (holder as HeaderViewHolder).bind(item)
            is TocMarkListItem.BookmarkItem -> (holder as EntryViewHolder).bindBookmark(item.bookmark)
            is TocMarkListItem.ThoughtItem -> (holder as EntryViewHolder).bindThought(item.thought)
        }
    }

    override fun getItemCount(): Int = items.size

    private inner class HeaderViewHolder(
        private val binding: ItemTocChapterHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TocMarkListItem.ChapterHeader) {
            binding.tvChapterName.text = item.chapterName
        }
    }

    private inner class EntryViewHolder(
        private val binding: ItemTocMarkEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bindBookmark(bookmark: Bookmark) {
            binding.tvTypeTag.setText(R.string.bookmark)
            binding.tvPrimary.text = bookmark.bookText
            binding.tvSecondary.gone(bookmark.content.isEmpty())
            binding.tvSecondary.text = bookmark.content
            binding.root.setOnClickListener {
                callback.onBookmarkClick(bookmark)
            }
            binding.root.setOnLongClickListener {
                callback.onBookmarkLongClick(bookmark, bindingAdapterPosition)
            }
        }

        fun bindThought(thought: BookThought) {
            binding.tvTypeTag.setText(R.string.book_thought)
            binding.tvPrimary.text = thought.selectedText
            binding.tvSecondary.gone(thought.thought.isEmpty())
            binding.tvSecondary.text = thought.thought
            binding.root.setOnClickListener {
                callback.onThoughtClick(thought, bindingAdapterPosition)
            }
            binding.root.setOnLongClickListener {
                callback.onThoughtLongClick(thought, bindingAdapterPosition)
            }
        }
    }

    interface Callback {
        fun onBookmarkClick(bookmark: Bookmark)
        fun onBookmarkLongClick(bookmark: Bookmark, pos: Int): Boolean
        fun onThoughtClick(thought: BookThought, pos: Int)
        fun onThoughtLongClick(thought: BookThought, pos: Int): Boolean
    }

    sealed class TocMarkListItem {
        data class ChapterHeader(val chapterName: String) : TocMarkListItem()
        data class BookmarkItem(val bookmark: Bookmark) : TocMarkListItem()
        data class ThoughtItem(val thought: BookThought) : TocMarkListItem()
    }

    private companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_BOOKMARK = 1
        private const val VIEW_TYPE_THOUGHT = 2
    }
}
