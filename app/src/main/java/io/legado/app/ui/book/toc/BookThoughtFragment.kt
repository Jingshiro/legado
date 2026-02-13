package io.legado.app.ui.book.toc

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookThought
import io.legado.app.databinding.FragmentBookThoughtBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.thought.BookThoughtDialog
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class BookThoughtFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_book_thought),
    BookThoughtAdapter.Callback,
    TocViewModel.ThoughtCallBack {

    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentBookThoughtBinding::bind)
    private val layoutManager by lazy { UpLinearLayoutManager(requireContext()) }
    private val adapter by lazy { BookThoughtAdapter(requireContext(), this) }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.thoughtCallBack = this
        initRecyclerView()
        viewModel.bookData.observe(this) {
            upThought(null)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
    }

    override fun upThought(searchKey: String?) {
        val book = viewModel.bookData.value ?: return
        lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> appDb.bookThoughtDao.flowByBook(book.name, book.author)
                else -> appDb.bookThoughtDao.flowSearch(book.name, book.author, searchKey)
            }.catch {
                AppLog.put("目录界面获取想法数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                adapter.setItems(it)
            }
        }
    }

    override fun onClick(bookThought: BookThought, pos: Int) {
        showDialogFragment(BookThoughtDialog(bookThought, pos))
    }

    override fun onLongClick(bookThought: BookThought, pos: Int) {
        showDialogFragment(BookThoughtDialog(bookThought, pos))
    }
}
