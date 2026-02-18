package io.legado.app.ui.book.thought

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookThought
import io.legado.app.databinding.DialogBookThoughtBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookThoughtDialog() : BaseDialogFragment(R.layout.dialog_book_thought, true) {

    constructor(thought: BookThought, editPos: Int = -1) : this() {
        arguments = Bundle().apply {
            putInt("editPos", editPos)
            putParcelable("thought", thought)
        }
    }

    private val binding by viewBinding(DialogBookThoughtBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        val args = arguments ?: let {
            dismiss()
            return
        }
        @Suppress("DEPRECATION")
        val bookThought = args.getParcelable<BookThought>("thought")
        bookThought ?: let {
            dismiss()
            return
        }
        val editPos = args.getInt("editPos", -1)
        binding.tvFooterLeft.visible(editPos >= 0)
        binding.run {
            tvChapterName.text = bookThought.chapterName
            editSelectedText.setText(bookThought.selectedText)
            editThought.setText(bookThought.thought)
            tvShare.setOnClickListener {
                val thoughtText = editThought.text?.toString()?.trim().orEmpty()
                if (thoughtText.isEmpty()) {
                    context?.toastOnUi(R.string.cannot_empty)
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    kotlin.runCatching {
                        withContext(IO) {
                            ThoughtImageExporter.exportToLocal(requireContext(), bookThought, thoughtText)
                        }
                    }.onSuccess {
                        context?.toastOnUi(getString(R.string.thought_image_saved, it))
                    }.onFailure {
                        context?.toastOnUi(R.string.thought_image_save_failed)
                    }
                }
            }
            tvOk.setOnClickListener {
                val thoughtText = editThought.text?.toString()?.trim().orEmpty()
                if (thoughtText.isEmpty()) {
                    context?.toastOnUi(R.string.cannot_empty)
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    withContext(IO) {
                        appDb.bookThoughtDao.insert(
                            bookThought.copy(
                                thought = thoughtText,
                                updateTime = System.currentTimeMillis()
                            )
                        )
                    }
                    postEvent(EventBus.REFRESH_BOOK_THOUGHT, true)
                    context?.toastOnUi(R.string.thought_saved)
                    dismiss()
                }
            }
            tvFooterLeft.setOnClickListener {
                lifecycleScope.launch {
                    withContext(IO) {
                        appDb.bookThoughtDao.delete(bookThought)
                    }
                    postEvent(EventBus.REFRESH_BOOK_THOUGHT, true)
                    context?.toastOnUi(R.string.thought_deleted)
                    dismiss()
                }
            }
        }
    }
}
