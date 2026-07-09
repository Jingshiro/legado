package io.legado.app.ui.replace.edit

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.ActivityReplaceEditBinding
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.help.book.FontManager
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.utils.GSON
import io.legado.app.utils.imeHeight
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 编辑替换规则
 */
class ReplaceEditActivity :
    VMBaseActivity<ActivityReplaceEditBinding, ReplaceEditViewModel>(),
    KeyboardToolPop.CallBack,
    ColorPickerDialogListener {

    companion object {
        private const val COLOR_PICKER_ID = 1001

        fun startIntent(
            context: Context,
            id: Long = -1,
            pattern: String? = null,
            isRegex: Boolean = false,
            scope: String? = null
        ): Intent {
            val intent = Intent(context, ReplaceEditActivity::class.java)
            intent.putExtra("id", id)
            intent.putExtra("pattern", pattern)
            intent.putExtra("isRegex", isRegex)
            intent.putExtra("scope", scope)
            return intent
        }

    }

    override val binding by viewBinding(ActivityReplaceEditBinding::inflate)
    override val viewModel by viewModels<ReplaceEditViewModel>()

    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        softKeyboardTool.attachToWindow(window)
        initView()
        viewModel.initData(intent) {
            upReplaceView(it)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.replace_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val view = window.decorView.findFocus()
            if (view is EditText) {
                result.data?.getStringExtra("text")?.let {
                    view.setText(it)
                }
                result.data?.getIntExtra("cursorPosition", -1)?.takeIf { it in 0 ..< view.text.length }?.let {
                    view.setSelection(it)
                }
            } else {
                toastOnUi(R.string.focus_lost_on_textbox)
            }
        }
    }
    private fun onFullEditClicked() {
        val view = window.decorView.findFocus()
        if (view is EditText) {
            val hint = findParentTextInputLayout(view)?.hint?.toString()
            val currentText = view.text.toString()
            val intent = Intent(this, CodeEditActivity::class.java).apply {
                putExtra("text", currentText)
                putExtra("title", hint)
                putExtra("cursorPosition", view.selectionStart)
            }
            textEditLauncher.launch(intent)
        }
        else {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_fullscreen_edit -> onFullEditClicked()
            R.id.menu_save -> viewModel.save(getReplaceRule()) {
                setResult(RESULT_OK)
                finish()
            }

            R.id.menu_copy_rule -> sendToClip(GSON.toJson(getReplaceRule()))
            R.id.menu_paste_rule -> viewModel.pasteRule {
                upReplaceView(it)
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        softKeyboardTool.dismiss()
    }

    private fun initView() {
        binding.ivHelp.setOnClickListener {
            showHelp("regexHelp")
        }
        binding.ivHelpReplaceTo.setOnClickListener {
            showHelp("replaceToHelp")
        }
        binding.root.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            softKeyboardTool.initialPadding = windowInsets.imeHeight
            windowInsets
        }
        // 高亮模式切换
        binding.cbHighlight.setOnCheckedChangeListener { _, isChecked ->
            binding.llStyleToolbar.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        // 样式工具栏按钮
        binding.tvBold.setOnClickListener { insertStyleTag("b") }
        binding.tvItalic.setOnClickListener { insertStyleTag("i") }
        binding.tvUnderline.setOnClickListener { insertStyleTag("u") }
        binding.tvColor.setOnClickListener { showColorPicker() }
        binding.tvFontSize.setOnClickListener { showFontSizeDialog() }
        binding.tvFontFamily.setOnClickListener { showFontFamilyDialog() }
        binding.tvCenter.setOnClickListener { insertStyleTag("div", " style=\"text-align: center;\"") }
        
        // 绑定主题多选框
        binding.etBindToThemes.setOnClickListener {
            val configList = ReadBookConfig.configList
            val options = mutableListOf<String>()
            val optionKeys = mutableListOf<String>()
            configList.forEach { config ->
                options.add("浅色模式 - ${config.name}")
                optionKeys.add("DAY_${config.name}")
                options.add("夜间模式 - ${config.name}")
                optionKeys.add("NIGHT_${config.name}")
                options.add("墨水屏模式 - ${config.name}")
                optionKeys.add("EINK_${config.name}")
            }
            val binds = binding.etBindToThemes.text.toString().split(",").filter { it.isNotBlank() }
            val checkedItems = BooleanArray(options.size) { i ->
                binds.contains(optionKeys[i])
            }
            alert("绑定阅读主题") {
                multiChoiceItems(options.toTypedArray(), checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                okButton {
                    val result = mutableListOf<String>()
                    for (i in checkedItems.indices) {
                        if (checkedItems[i]) {
                            result.add(optionKeys[i])
                        }
                    }
                    binding.etBindToThemes.setText(result.joinToString(","))
                }
                cancelButton()
            }
        }
    }

    private fun upReplaceView(replaceRule: ReplaceRule) = binding.run {
        etName.setText(replaceRule.name)
        etGroup.setText(replaceRule.group)
        etReplaceRule.setText(replaceRule.pattern)
        cbUseRegex.isChecked = replaceRule.isRegex
        cbHighlight.isChecked = replaceRule.isHighlight
        cbDotAll.isChecked = replaceRule.isDotAll
        etReplaceTo.setText(replaceRule.replacement)
        cbScopeTitle.isChecked = replaceRule.scopeTitle
        cbScopeContent.isChecked = replaceRule.scopeContent
        etScope.setText(replaceRule.scope)
        etExcludeScope.setText(replaceRule.excludeScope)
        etTimeout.setText(replaceRule.timeoutMillisecond.toString())
        etBindToThemes.setText(replaceRule.bindToThemes ?: "")
        // 根据高亮模式显示/隐藏工具栏
        llStyleToolbar.visibility = if (replaceRule.isHighlight) View.VISIBLE else View.GONE
    }

    private fun getReplaceRule(): ReplaceRule = binding.run {
        val replaceRule: ReplaceRule = viewModel.replaceRule ?: ReplaceRule()
        replaceRule.name = etName.text.toString()
        replaceRule.group = etGroup.text.toString()
        replaceRule.pattern = etReplaceRule.text.toString()
        replaceRule.isRegex = cbUseRegex.isChecked
        replaceRule.isHighlight = cbHighlight.isChecked
        replaceRule.isDotAll = cbDotAll.isChecked
        replaceRule.replacement = etReplaceTo.text.toString()
        replaceRule.scopeTitle = cbScopeTitle.isChecked
        replaceRule.scopeContent = cbScopeContent.isChecked
        replaceRule.scope = etScope.text.toString()
        replaceRule.excludeScope = etExcludeScope.text.toString()
        replaceRule.timeoutMillisecond = etTimeout.text.toString().ifEmpty { "3000" }.toLong()
        replaceRule.bindToThemes = etBindToThemes.text.toString()
        return replaceRule
    }

    /**
     * 在"替换为"输入框的光标位置插入样式标签
     * @param tag 标签名，如 "b", "i", "u", "font"
     * @param attr 标签属性，如 " color=\"red\"", " face=\"楷体\""
     */
    private fun insertStyleTag(tag: String, attr: String = "") {
        val editText = binding.etReplaceTo
        val start = editText.selectionStart
        val end = editText.selectionEnd
        val editable = editText.editableText
        val selectedText = if (start in 0..end && end <= editable.length) {
            editable.substring(start, end)
        } else {
            ""
        }
        val openTag = "<$tag$attr>"
        val closeTag = "</$tag>"
        val insertText = "$openTag$selectedText$closeTag"
        if (start < 0 || start >= editable.length) {
            editable.append(insertText)
        } else {
            editable.replace(start, end, insertText)
        }
        val cursorPos = start + openTag.length + selectedText.length
        editText.setSelection(cursorPos)
    }

    /**
     * 显示调色盘，插入 <font color="xxx"></font>
     */
    private fun showColorPicker() {
        ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_PRESETS)
            .setShowAlphaSlider(false)
            .setDialogId(COLOR_PICKER_ID)
            .show(this)
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        if (dialogId == COLOR_PICKER_ID) {
            val hex = String.format("#%06X", 0xFFFFFF and color)
            insertStyleTag("font", " color=\"$hex\"")
        }
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    /**
     * 显示字号选择对话框，插入 <span style="font-size:Npx"></span>
     */
    private fun showFontSizeDialog() {
        val options = listOf("12px", "14px", "16px", "18px", "20px", "24px", "自定义...")
        alert(title = getString(R.string.style_font_size)) {
            items(options) { _, index ->
                if (index < options.lastIndex) {
                    val px = options[index].removeSuffix("px")
                    insertFontSizeTag(px)
                } else {
                    showCustomFontSizeInput()
                }
            }
            cancelButton()
        }
    }

    private fun showCustomFontSizeInput() {
        val input = android.widget.EditText(this).apply {
            hint = "16"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        alert(title = getString(R.string.style_font_size)) {
            customView { input }
            okButton {
                val px = input.text.toString().trim()
                if (px.isNotEmpty()) {
                    insertFontSizeTag(px)
                }
            }
            cancelButton()
        }
    }

    private fun insertFontSizeTag(px: String) {
        val editText = binding.etReplaceTo
        val start = editText.selectionStart
        val end = editText.selectionEnd
        val editable = editText.editableText
        val selectedText = if (start in 0..end && end <= editable.length) {
            editable.substring(start, end)
        } else {
            ""
        }
        val openTag = "<span style=\"font-size:${px}px\">"
        val closeTag = "</span>"
        val insertText = "$openTag$selectedText$closeTag"
        if (start < 0 || start >= editable.length) {
            editable.append(insertText)
        } else {
            editable.replace(start, end, insertText)
        }
        val cursorPos = start + openTag.length + selectedText.length
        editText.setSelection(cursorPos)
    }

    /**
     * 显示字体选择对话框，插入 <font face="xxx"></font>
     */
    private fun showFontFamilyDialog() {
        val fontNames = FontManager.getAvailableFontNames()
        if (fontNames.isEmpty()) {
            toastOnUi("未找到字体文件，请在设置中配置字体目录")
            return
        }
        alert(title = getString(R.string.style_font_family)) {
            items(fontNames) { _, _, index ->
                insertStyleTag("font", " face=\"${fontNames[index]}\"")
            }
            cancelButton()
        }
    }

    override fun helpActions(): List<SelectItem<String>> {
        return arrayListOf(
            SelectItem("正则教程", "regexHelp"),
            SelectItem("高亮模式说明", "highlightHelp")
        )
    }

    override fun onHelpActionSelect(action: String) {
        when (action) {
            "regexHelp" -> showHelp("regexHelp")
            "highlightHelp" -> showHelp("highlightHelp")
        }
    }

    override fun sendText(text: String) {
        if (text.isEmpty()) return
        val view = window?.decorView?.findFocus()
        if (view is EditText) {
            var start = view.selectionStart
            var end = view.selectionEnd
            if (start > end) {
                val temp = start
                start = end
                end = temp
            }
            //获取EditText的文字
            val edit = view.editableText
            if (start < 0 || start >= edit.length) {
                edit.append(text)
            } else {
                //光标所在位置插入文字
                edit.replace(start, end, text)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onUndoClicked() {
        val editText = window.decorView.findFocus()
        if (editText is EditText) {
            editText.onTextContextMenuItem(android.R.id.undo)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRedoClicked() {
        val editText = window.decorView.findFocus()
        if (editText is EditText) {
            editText.onTextContextMenuItem(android.R.id.redo)
        }
    }

}
