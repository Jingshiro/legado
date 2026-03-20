package io.legado.app.ui.book.read.ai

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
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
        // 让 initMessages 内部自行决定恢复缓存还是重新初始化
        viewModel.initMessages(ReadBook.curTextChapter?.getContent())
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
