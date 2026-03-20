package io.legado.app.ui.book.read.ai

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
        // 让 initMessages 内部自行决定恢复缓存还是重新初始化
        viewModel.initMessages(ReadBook.curTextChapter?.getContent())
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
        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                binding.etInput.setText("")
            }
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
