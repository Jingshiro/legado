package io.legado.app.ui.book.read.ai

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.lib.dialogs.alert
import io.legado.app.databinding.ActivityAiChatBinding
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.config.AiConfigDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.utils.applyBackgroundTint

class AiChatActivity : BaseActivity<ActivityAiChatBinding>(false) {

    override val binding by viewBinding(ActivityAiChatBinding::inflate)
    internal val viewModel by viewModels<AiChatViewModel>()
    private val adapter by lazy {
        ChatAdapter { displayPosition ->
            viewModel.deleteMessageAt(displayPosition)
        }
    }

    /** 是否为独立模式（从"我的"页面进入，无书籍上下文） */
    private val isStandalone: Boolean get() = intent.getBooleanExtra("isStandalone", false) || ReadBook.book == null

    override fun initTheme() {
        // 保持 Material 主题，不允许 BaseActivity 覆盖为 AppCompat 主题
        setTheme(R.style.AppTheme_Material)
        window.decorView.applyBackgroundTint(backgroundColor)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        bindEvent()
        observeData()
        setupKeyboardAdjustment()

        if (isStandalone) {
            // 独立模式：隐藏章节选择区域，直接初始化
            binding.layoutChapterRange.visibility = View.GONE
            binding.titleBar.title = getString(R.string.ai_assistant)
            viewModel.initMessages(0, 0)
        } else {
            val currentChapter = (ReadBook.durChapterIndex + 1).toString()
            binding.etChapterStart.setText(currentChapter)
            binding.etChapterEnd.setText(currentChapter)
            // 标题显示书名，让用户明确当前AI关联的书籍
            val bookName = ReadBook.book?.name
            if (!bookName.isNullOrBlank()) {
                binding.titleBar.title = bookName
            } else {
                binding.titleBar.title = getString(R.string.ai_assistant)
            }
            viewModel.initMessages(
                ReadBook.durChapterIndex + 1,
                ReadBook.durChapterIndex + 1
            )
            updateWordCount()
        }
    }

    private fun setupKeyboardAdjustment() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottomPadding = if (imeHeight > navBarHeight) imeHeight else navBarHeight
            binding.root.setPadding(0, 0, 0, bottomPadding)
            insets
        }
    }

    private fun initView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter

        // 底部输入栏使用磨砂半透明背景
        binding.bottomBar.setCardBackgroundColor(0xCC1A1A1A.toInt())

        // 标题栏使用暗色主题，文字和图标自动为白色
        binding.titleBar.setTitleTextColor(Color.WHITE)
        binding.titleBar.setColorFilter(Color.WHITE)
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
            if (viewModel.isGeneratingLiveData.value == true) {
                toastOnUi("正在生成中...")
                return@setOnClickListener
            }
            val text = binding.etInput.text.toString()
            if (text.isNotBlank()) {
                val start: Int
                val end: Int
                if (isStandalone) {
                    start = 0
                    end = 0
                } else {
                    start = binding.etChapterStart.text.toString().toIntOrNull()
                        ?: (ReadBook.durChapterIndex + 1)
                    end = binding.etChapterEnd.text.toString().toIntOrNull()
                        ?: (ReadBook.durChapterIndex + 1)
                }
                viewModel.sendMessage(text, start, end)
                binding.etInput.setText("")
            }
        }
    }

    private fun updateWordCount() {
        if (isStandalone) return
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
            val displayMsgs = msgs.filter { it.role != "system" && it.role != "tool" }
            adapter.submitList(displayMsgs)
            if (displayMsgs.isNotEmpty()) {
                binding.recyclerView.scrollToPosition(displayMsgs.size - 1)
            }
        }

        viewModel.wordCountLiveData.observe(this) { count ->
            if (isStandalone) return@observe
            val countStr = if (count >= 10000) String.format("%.1f万", count / 10000f) else count.toString()
            binding.tvWordCount.text = "字数: $countStr"
            if (count > 50000) {
                binding.tvWordCount.setTextColor(Color.RED)
            } else {
                binding.tvWordCount.setTextColor(Color.parseColor("#888888"))
            }
        }

        viewModel.isGeneratingLiveData.observe(this) { isGenerating ->
            if (isGenerating) {
                binding.btnSend.setIconResource(R.drawable.ic_stop_black_24dp)
            } else {
                binding.btnSend.setIconResource(R.drawable.ic_send)
            }
        }

        viewModel.confirmationLiveData.observe(this) { request ->
            if (request == null) return@observe
            alert(R.string.ai_confirm_title) {
                setMessage(request.description)
                yesButton {
                    viewModel.confirmAction(true)
                }
                noButton {
                    viewModel.confirmAction(false)
                }
            }
        }

        viewModel.batchConfirmationLiveData.observe(this) { request ->
            if (request == null) return@observe
            showBatchConfirmationDialog(request.descriptions) { confirmed ->
                viewModel.confirmBatchAction(confirmed)
            }
        }
    }

    /**
     * 展示批量确认对话框：用可滚动列表展示所有待执行的操作。
     */
    private fun showBatchConfirmationDialog(
        descriptions: List<String>,
        callback: (Boolean) -> Unit
    ) {
        val dp = resources.displayMetrics.density

        // 外层容器
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * dp).toInt(), (12 * dp).toInt(),
                (20 * dp).toInt(), (4 * dp).toInt()
            )
        }

        // 头部提示
        val header = TextView(this).apply {
            text = "以下 ${descriptions.size} 个操作将被执行，请确认是否继续："
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        container.addView(header)

        // 分隔线
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            )
            setBackgroundColor(Color.parseColor("#22888888"))
        })

        // 操作列表内容
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        descriptions.forEachIndexed { index, desc ->
            val item = TextView(this).apply {
                text = "  ${index + 1}. $desc"
                textSize = 15f
                setPadding(
                    (4 * dp).toInt(), (8 * dp).toInt(),
                    (4 * dp).toInt(), (8 * dp).toInt()
                )
            }
            listLayout.addView(item)
            // 除最后一项外添加小分隔线
            if (index < descriptions.size - 1) {
                listLayout.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                    ).apply { setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0) }
                    setBackgroundColor(Color.parseColor("#11888888"))
                })
            }
        }

        // 如果内容过长则包装在 ScrollView 内
        val maxHeightPx = (resources.displayMetrics.heightPixels * 0.45).toInt()
        val scrollView = ScrollView(this).apply {
            addView(listLayout)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // 限制最大高度避免对话框过长
            post {
                if (height > maxHeightPx) {
                    layoutParams = layoutParams.also {
                        (it as LinearLayout.LayoutParams).height = maxHeightPx
                    }
                }
            }
        }
        container.addView(scrollView)

        alert("确认批量整理") {
            setCustomView(container)
            positiveButton("确认执行") {
                callback(true)
            }
            negativeButton("全部拒绝") {
                callback(false)
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ai_chat_menu, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_ai_clear -> {
                if (viewModel.isGeneratingLiveData.value == true) {
                    toastOnUi("正在生成中，请稍候...")
                    return true
                }
                alert("清空对话") {
                    setMessage("确定要清空当前全部对话记录吗？")
                    yesButton {
                        viewModel.clearMessages()
                    }
                    noButton { }
                }
                return true
            }
            R.id.menu_ai_settings -> {
                showDialogFragment(AiConfigDialog())
                return true
            }
            R.id.menu_ai_summarize -> {
                val start = if (isStandalone) 0 else binding.etChapterStart.text.toString().toIntOrNull()
                    ?: (ReadBook.durChapterIndex + 1)
                val end = if (isStandalone) 0 else binding.etChapterEnd.text.toString().toIntOrNull()
                    ?: (ReadBook.durChapterIndex + 1)
                viewModel.saveSession(start, end)
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }
}
