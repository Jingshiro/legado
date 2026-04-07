package io.legado.app.ui.book.read.ai

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityAiChatBinding
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.config.AiConfigDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiChatActivity : BaseActivity<ActivityAiChatBinding>(false) {

    override val binding by viewBinding(ActivityAiChatBinding::inflate)
    private val viewModel by viewModels<AiChatViewModel>()
    private val adapter by lazy { ChatAdapter() }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        bindEvent()
        observeData()
        setupKeyboardAdjustment()
        val currentChapter = (ReadBook.durChapterIndex + 1).toString()
        binding.etChapterStart.setText(currentChapter)
        binding.etChapterEnd.setText(currentChapter)

        viewModel.initMessages((ReadBook.durChapterIndex + 1), (ReadBook.durChapterIndex + 1))
        updateWordCount()
    }

    /**
     * 监听键盘弹起/收起，手动调整底部 padding，兼容全面屏及 Android 10+ 的 edge-to-edge 场景。
     */
    private fun setupKeyboardAdjustment() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            // 键盘弹出时 imeHeight > navBarHeight，底部额外留出键盘高度
            val bottomPadding = if (imeHeight > navBarHeight) imeHeight else navBarHeight
            binding.root.setPadding(0, 0, 0, bottomPadding)
            insets
        }
    }

    private fun initView() {
        binding.titleBar.title = getString(R.string.ai_companion)
        binding.recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter
    }

    private fun bindEvent() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateWordCount()
            }
        }
        binding.etChapterStart.addTextChangedListener(textWatcher)
        binding.etChapterEnd.addTextChangedListener(textWatcher)

        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString()
            if (text.isNotBlank()) {
                val start = binding.etChapterStart.text.toString().toIntOrNull() ?: (ReadBook.durChapterIndex + 1)
                val end = binding.etChapterEnd.text.toString().toIntOrNull() ?: (ReadBook.durChapterIndex + 1)
                viewModel.sendMessage(text, start, end)
                binding.etInput.setText("")
            }
        }
    }

    private fun updateWordCount() {
        val start = binding.etChapterStart.text.toString().toIntOrNull()
        val end = binding.etChapterEnd.text.toString().toIntOrNull()
        val bookUrl = ReadBook.book?.bookUrl
        val chapterSize = ReadBook.chapterSize
        if (start != null && end != null && start > 0 && end > 0 && bookUrl != null && chapterSize > 0) {
            viewModel.calculateWordCount(bookUrl, start, end)
        }
    }

    private fun observeData() {
        viewModel.messagesLiveData.observe(this) { msgs ->
            val displayMsgs = msgs.filter { it.role != "system" }
            adapter.submitList(displayMsgs)
            if (displayMsgs.isNotEmpty()) {
                binding.recyclerView.scrollToPosition(displayMsgs.size - 1)
            }
        }

        viewModel.wordCountLiveData.observe(this) { count ->
            val countStr = if (count >= 10000) String.format("%.1f万", count / 10000f) else count.toString()
            binding.tvWordCount.text = "字数: $countStr"
            if (count > 50000) {
                binding.tvWordCount.setTextColor(Color.RED)
            } else {
                binding.tvWordCount.setTextColor(Color.parseColor("#888888")) // Secondary color approximation
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ai_chat_menu, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_ai_settings -> {
                showDialogFragment(AiConfigDialog())
                return true
            }
            R.id.menu_ai_summarize -> {
                viewModel.summarizeAndMemory()
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }
}
