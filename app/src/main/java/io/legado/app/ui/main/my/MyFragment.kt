package io.legado.app.ui.main.my

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.FragmentMyConfigBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.AppWebDav
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.WebService
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.about.ReadRecordWebActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.read.ai.AiChatActivity
import io.legado.app.ui.main.my.ReadingSkillActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.config.CloudBackupActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.cardBackground
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class MyFragment() : BaseFragment(R.layout.fragment_my_config), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentMyConfigBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initQuickActions()
        initSettingsList()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
    }

    private fun initQuickActions() {
        // 备份恢复 - 短按打开云端备份，长按本地备份
        binding.btnBackupRestore.setOnClickListener {
            startActivity<CloudBackupActivity>()
        }
        binding.btnBackupRestore.setOnLongClickListener {
            startLocalBackup()
            true
        }

        // WebDAV - 弹出设置对话框
        binding.btnWebdav.setOnClickListener {
            showWebDavDialog()
        }

        // Web 服务 - 切换开关
        updateWebServiceState()
        binding.btnWebService.setOnClickListener {
            if (WebService.isRun) {
                WebService.stop(requireContext())
                updateWebServiceState()
            } else {
                val showWarning = requireContext().defaultSharedPreferences
                    .getBoolean(PreferKey.showWebServiceWarning, true)
                if (showWarning) {
                    showWebServiceWarningDialog()
                } else {
                    WebService.start(requireContext())
                    updateWebServiceState()
                }
            }
        }
        binding.btnWebService.setOnLongClickListener {
            if (!WebService.isRun) {
                return@setOnLongClickListener false
            }
            requireContext().selector(arrayListOf("复制地址", "浏览器打开")) { _, i ->
                when (i) {
                    0 -> requireContext().sendToClip(WebService.hostAddress)
                    1 -> requireContext().openUrl(WebService.hostAddress)
                }
            }
            true
        }

        // 阅读记录 - 打开内置可视化页面
        binding.btnReadRecord.setOnClickListener {
            startActivity<ReadRecordWebActivity>()
        }

        // 监听 WebService 状态变化
        observeEventSticky<String>(EventBus.WEB_SERVICE) {
            updateWebServiceState()
        }

        // 设置快捷按钮底色透明度比用户自定义值高 10，按下状态用主题强调色
        val buttonViews = listOf(
            binding.btnBackupRestore, binding.btnWebdav,
            binding.btnWebService, binding.btnReadRecord
        )
        val ctx = requireContext()
        val cardBg = ctx.cardBackground
        val alpha = android.graphics.Color.alpha(cardBg)
        val adjustedAlpha = (alpha + 25).coerceAtMost(255)
        val normalColor = ColorUtils.withAlpha(cardBg, adjustedAlpha / 255f)
        val pressedColor = ctx.accentColor
        val corner = 12f * ctx.resources.displayMetrics.density
        buttonViews.forEach { view ->
            val pressed = GradientDrawable().apply {
                setColor(pressedColor)
                cornerRadius = corner
            }
            val normal = GradientDrawable().apply {
                setColor(normalColor)
                cornerRadius = corner
            }
            view.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), normal)
            }
        }
    }

    private fun updateWebServiceState() {
        val isRunning = WebService.isRun
        binding.tvWebService.text = if (isRunning) "已开启" else getString(R.string.web_service)
        binding.ivWebService.alpha = if (isRunning) 1.0f else 0.6f
    }

    private fun showWebDavDialog() {
        val ctx = requireContext()
        val url = ctx.defaultSharedPreferences.getString(PreferKey.webDavUrl, "") ?: ""
        val account = ctx.defaultSharedPreferences.getString(PreferKey.webDavAccount, "") ?: ""
        val password = ctx.defaultSharedPreferences.getString(PreferKey.webDavPassword, "") ?: ""
        val subDir = ctx.defaultSharedPreferences.getString(PreferKey.webDavDir, "") ?: ""
        val deviceName = ctx.defaultSharedPreferences.getString(PreferKey.webDavDeviceName, "") ?: ""

        val density = ctx.resources.displayMetrics.density
        val hPadding = (16 * density).toInt()
        val vPadding = (8 * density).toInt()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPadding, vPadding, hPadding, 0)
        }

        val etUrl = android.widget.EditText(ctx).apply {
            hint = getString(R.string.web_dav_url)
            setText(url)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val etAccount = android.widget.EditText(ctx).apply {
            hint = getString(R.string.web_dav_account)
            setText(account)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val etPassword = android.widget.EditText(ctx).apply {
            hint = getString(R.string.web_dav_pw)
            setText(password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etSubDir = android.widget.EditText(ctx).apply {
            hint = getString(R.string.sub_dir)
            setText(subDir)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val etDeviceName = android.widget.EditText(ctx).apply {
            hint = getString(R.string.webdav_device_name)
            setText(deviceName)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        layout.addView(etUrl)
        layout.addView(etAccount)
        layout.addView(etPassword)
        layout.addView(etSubDir)
        layout.addView(etDeviceName)

        // 底部按钮容器
        val buttonLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(hPadding, vPadding, hPadding, vPadding)
        }

        val btnSave = com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.save_config)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * density).toInt() }
        }

        val btnUpload = com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.upload_to_cloud)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        buttonLayout.addView(btnSave)
        buttonLayout.addView(btnUpload)
        layout.addView(buttonLayout)

        val dialog = alert(R.string.web_dav_set) {
            customView { layout }
        }

        btnSave.setOnClickListener {
            saveWebDavConfig(etUrl, etAccount, etPassword, etSubDir, etDeviceName)
            ctx.toastOnUi(R.string.save_config)
            dialog.dismiss()
        }

        btnUpload.setOnClickListener {
            saveWebDavConfig(etUrl, etAccount, etPassword, etSubDir, etDeviceName)
            dialog.dismiss()
            lifecycleScope.launch(IO) {
                try {
                    AppWebDav.upConfig()
                    Backup.backupLocked(requireContext(), null)
                    ctx.toastOnUi(R.string.backup_success)
                } catch (e: Throwable) {
                    ensureActive()
                    ctx.toastOnUi("上传失败: ${e.localizedMessage}")
                }
            }
        }

        dialog.show()
    }

    private fun saveWebDavConfig(
        etUrl: android.widget.EditText,
        etAccount: android.widget.EditText,
        etPassword: android.widget.EditText,
        etSubDir: android.widget.EditText,
        etDeviceName: android.widget.EditText
    ) {
        requireContext().putPrefString(PreferKey.webDavUrl, etUrl.text.toString())
        requireContext().putPrefString(PreferKey.webDavAccount, etAccount.text.toString())
        requireContext().putPrefString(PreferKey.webDavPassword, etPassword.text.toString())
        requireContext().putPrefString(PreferKey.webDavDir, etSubDir.text.toString())
        requireContext().putPrefString(PreferKey.webDavDeviceName, etDeviceName.text.toString())
    }

    private fun startLocalBackup() {
        val backupPath = AppConfig.backupPath
        if (backupPath.isNullOrEmpty()) {
            requireContext().toastOnUi("请先在备份与恢复设置中设置备份路径")
            return
        }
        lifecycleScope.launch(IO) {
            try {
                Backup.backupLocked(requireContext(), backupPath)
                requireContext().toastOnUi(R.string.backup_success)
            } catch (e: Throwable) {
                ensureActive()
                requireContext().toastOnUi("备份失败: ${e.localizedMessage}")
            }
        }
    }

    private fun initSettingsList() {
        // 书源管理
        binding.itemBookSourceManage.setOnClickListener {
            startActivity<BookSourceActivity>()
        }

        // TXT 目录规则
        binding.itemTxtTocRule.setOnClickListener {
            startActivity<TxtTocRuleActivity>()
        }

        // 替换净化
        binding.itemReplaceManage.setOnClickListener {
            startActivity<ReplaceRuleActivity>()
        }

        // 字典规则
        binding.itemDictRule.setOnClickListener {
            startActivity<DictRuleActivity>()
        }

        // 主题模式
        binding.itemThemeMode.setOnClickListener {
            showThemeModeDialog()
        }

        // 备份与恢复
        binding.itemBackupRestore.setOnClickListener {
            startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.BACKUP_CONFIG)
            }
        }

        // 主题设置
        binding.itemThemeSetting.setOnClickListener {
            startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.THEME_CONFIG)
            }
        }

        // 其它设置
        binding.itemOtherSetting.setOnClickListener {
            startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.OTHER_CONFIG)
            }
        }

        // 书签与想法
        binding.itemBookmark.setOnClickListener {
            startActivity<AllBookmarkActivity>()
        }

        // 文件管理
        binding.itemFileManage.setOnClickListener {
            startActivity<FileManageActivity>()
        }

        // 阅读 Skill
        binding.itemReadingSkill.setOnClickListener {
            startActivity<ReadingSkillActivity>()
        }

        // AI 助手
        binding.itemAiAssistant.setOnClickListener {
            startActivity<AiChatActivity> {
                putExtra("isStandalone", true)
            }
        }

        // 关于
        binding.itemAbout.setOnClickListener {
            startActivity<AboutActivity>()
        }

        // 退出
        binding.itemExit.setOnClickListener {
            activity?.finish()
        }
    }

    private fun showWebServiceWarningDialog() {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        val hPadding = (20 * density).toInt()
        val vPadding = (8 * density).toInt()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPadding, vPadding, hPadding, 0)
        }
        val tvMsg = android.widget.TextView(ctx).apply {
            text = getString(R.string.web_service_security_warning_msg)
            setTextColor(ctx.getColor(R.color.primaryText))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * density).toInt() }
        }
        val checkBox = android.widget.CheckBox(ctx).apply {
            text = getString(R.string.do_not_show_again)
        }
        layout.addView(tvMsg)
        layout.addView(checkBox)
        alert(R.string.web_service_security_warning) {
            customView { layout }
            okButton {
                if (checkBox.isChecked) {
                    ctx.putPrefBoolean(PreferKey.showWebServiceWarning, false)
                }
                WebService.start(ctx)
                updateWebServiceState()
            }
            cancelButton()
        }.show()
    }

    private fun showThemeModeDialog() {
        val modes = resources.getStringArray(R.array.theme_mode)
        val modeValues = resources.getStringArray(R.array.theme_mode_v)
        val currentIndex = modeValues.indexOf(AppConfig.themeMode)

        alert(R.string.theme_mode) {
            singleChoiceItems(modes, currentIndex) { _, which ->
                requireContext().putPrefString(PreferKey.themeMode, modeValues[which])
                ThemeConfig.applyDayNight(requireContext())
            }
            yesButton {}
        }.show()
    }
}
