package io.legado.app.ui.book.toc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookThought
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.FragmentBookmarkThoughtBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.thought.BookThoughtDialog
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class BookmarkThoughtFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_bookmark_thought),
    TocViewModel.BookmarkThoughtCallBack,
    BookmarkThoughtAdapter.Callback {

    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentBookmarkThoughtBinding::bind)
    private val adapter by lazy { BookmarkThoughtAdapter(this) }
    private var collectJob: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.bookmarkThoughtCallBack = this
        initRecyclerView()
        viewModel.bookData.observe(this) {
            upBookmarkThought(null)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = UpLinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
    }

    override fun upBookmarkThought(searchKey: String?) {
        val book = viewModel.bookData.value ?: return
        collectJob?.cancel()
        collectJob = lifecycleScope.launch {
            val bookmarkFlow = if (searchKey.isNullOrBlank()) {
                appDb.bookmarkDao.flowByBook(book.name, book.author)
            } else {
                appDb.bookmarkDao.flowSearch(book.name, book.author, searchKey)
            }
            val thoughtFlow = if (searchKey.isNullOrBlank()) {
                appDb.bookThoughtDao.flowByBook(book.name, book.author)
            } else {
                appDb.bookThoughtDao.flowSearch(book.name, book.author, searchKey)
            }
            bookmarkFlow.combine(thoughtFlow) { bookmarks, thoughts ->
                mergeItems(bookmarks, thoughts)
            }.catch {
                AppLog.put("目录界面获取书签/想法数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                adapter.setItems(it)
            }
        }
    }

    private fun mergeItems(
        bookmarks: List<Bookmark>,
        thoughts: List<BookThought>
    ): List<BookmarkThoughtAdapter.TocMarkListItem> {
        data class ChapterBucket(
            val chapterName: String,
            val bookmarks: MutableList<Bookmark> = mutableListOf(),
            val thoughts: MutableList<BookThought> = mutableListOf()
        )

        val chapterMap = linkedMapOf<Int, ChapterBucket>()
        bookmarks.forEach { bookmark ->
            val bucket = chapterMap.getOrPut(bookmark.chapterIndex) {
                ChapterBucket(bookmark.chapterName)
            }
            bucket.bookmarks.add(bookmark)
        }
        thoughts.forEach { thought ->
            val bucket = chapterMap.getOrPut(thought.chapterIndex) {
                ChapterBucket(thought.chapterName)
            }
            bucket.thoughts.add(thought)
        }

        val listItems = mutableListOf<BookmarkThoughtAdapter.TocMarkListItem>()
        chapterMap.toSortedMap().forEach { (_, bucket) ->
            listItems.add(BookmarkThoughtAdapter.TocMarkListItem.ChapterHeader(bucket.chapterName))
            bucket.bookmarks.sortedBy { it.chapterPos }.forEach { bookmark ->
                listItems.add(BookmarkThoughtAdapter.TocMarkListItem.BookmarkItem(bookmark))
            }
            bucket.thoughts.sortedWith(
                compareBy<BookThought> { it.chapterPos }.thenBy { it.createTime }
            ).forEach { thought ->
                listItems.add(BookmarkThoughtAdapter.TocMarkListItem.ThoughtItem(thought))
            }
        }
        return listItems
    }

    override fun onBookmarkClick(bookmark: Bookmark) {
        activity?.run {
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("index", bookmark.chapterIndex)
                putExtra("chapterPos", bookmark.chapterPos)
            })
            finish()
        }
    }

    override fun onBookmarkLongClick(bookmark: Bookmark, pos: Int): Boolean {
        showDialogFragment(BookmarkDialog(bookmark, pos))
        return true
    }

    override fun onThoughtClick(thought: BookThought, pos: Int) {
        activity?.run {
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("index", thought.chapterIndex)
                putExtra("chapterPos", thought.chapterPos)
            })
            finish()
        }
    }

    override fun onThoughtLongClick(thought: BookThought, pos: Int): Boolean {
        showDialogFragment(BookThoughtDialog(thought, pos))
        return true
    }
}
