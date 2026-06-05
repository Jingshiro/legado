package io.legado.app.ui.book.thought

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookThought
import io.legado.app.databinding.DialogBookThoughtBinding
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.postEvent
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
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = requireContext().getPrimaryTextColor(isLight)
        binding.run {
            tvTitle.setTextColor(textColor)
            tvChapterName.setTextColor(textColor)
            editSelectedText.setTextColor(textColor)
            editThought.setTextColor(textColor)
            tvUnderlineStyle.setTextColor(requireContext().getColor(R.color.accent))
        }
        val args = arguments ?: let {
            dismiss()
            return
        }
        @Suppress("DEPRECATION")
        var _bookThought = args.getParcelable<BookThought>("thought")
        _bookThought ?: let {
            dismiss()
            return
        }
        val editPos = args.getInt("editPos", -1)
        binding.tvFooterLeft.visible(editPos >= 0)
        binding.run {
            tvChapterName.text = _bookThought!!.chapterName
            editSelectedText.setText(_bookThought!!.selectedText)
            editThought.setText(_bookThought!!.thought)
            tvShare.setOnClickListener {
                val thoughtText = editThought.text?.toString()?.trim().orEmpty()
                if (thoughtText.isEmpty()) {
                    context?.toastOnUi(R.string.cannot_empty)
                    return@setOnClickListener
                }
                ShareThoughtDialog.newInstance(_bookThought!!, thoughtText)
                    .show(childFragmentManager, "shareThoughtDialog")
            }
            tvOk.setOnClickListener {
                val thoughtText = editThought.text?.toString()?.trim().orEmpty()
                lifecycleScope.launch {
                    withContext(IO) {
                        appDb.bookThoughtDao.insert(
                            _bookThought!!.copy(
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
                        appDb.bookThoughtDao.delete(_bookThought!!)
                    }
                    postEvent(EventBus.REFRESH_BOOK_THOUGHT, true)
                    context?.toastOnUi(R.string.thought_deleted)
                    dismiss()
                }
            }
            tvUnderlineStyle.setOnClickListener {
                val thought = _bookThought!!
                // 如果这条笔记还没设置过样式，用上次的默认样式
                val currentThoughtStyle = if (thought.underlineStyle == 0 && thought.underlineWeight == 2.5f && thought.underlineColor.isEmpty()) {
                    ThoughtUnderlineStyleDialog.lastUsedStyle()
                } else {
                    TextLine.ThoughtUnderlineStyle(thought.underlineStyle, thought.underlineWeight, thought.underlineColor)
                }
                ThoughtUnderlineStyleDialog(currentThoughtStyle) { newStyle ->
                    _bookThought = _bookThought!!.copy(
                        underlineStyle = newStyle.style,
                        underlineWeight = newStyle.weight,
                        underlineColor = newStyle.color,
                        updateTime = System.currentTimeMillis()
                    )
                    lifecycleScope.launch {
                        withContext(IO) {
                            appDb.bookThoughtDao.insert(_bookThought!!)
                        }
                        postEvent(EventBus.REFRESH_BOOK_THOUGHT, true)
                    }
                }.show(childFragmentManager, "underlineStyleDialog")
            }
        }
    }
}
