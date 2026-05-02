package io.legado.app.ui.config

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.legado.app.help.config.ThemeExportHelper
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
class ThemeListDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { Adapter(requireContext()) }
    private var exportIndex = 0

    private val createThemeDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            val themeConfig = ThemeConfig.configList[exportIndex]
            lifecycleScope.launch {
                ThemeExportHelper.exportFullTheme(requireContext(), it, themeConfig)
            }
        }
    }

    private val openThemeDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                ThemeExportHelper.importFullTheme(requireContext(), it)
                initData()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.theme_list)
        initView()
        initMenu()
        initData()
    }

    private fun initView() = binding.run {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
    }

    private fun initMenu() = binding.run {
        toolBar.setOnMenuItemClickListener(this@ThemeListDialog)
        toolBar.inflateMenu(R.menu.theme_list)
        toolBar.menu.applyTint(requireContext())
    }

    fun initData() {
        adapter.setItems(ThemeConfig.configList)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_import -> {
                val actions = listOf(getString(R.string.import_from_clipboard), getString(R.string.import_from_file))
                requireContext().selector(getString(R.string.import_theme), actions) { _, i ->
                    if (i == 0) {
                        requireContext().getClipText()?.let {
                            if (ThemeConfig.addConfig(it)) {
                                initData()
                            } else {
                                toastOnUi("格式不对,添加失败")
                            }
                        }
                    } else {
                        openThemeDoc.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                }
            }
        }
        return true
    }

    fun delete(index: Int) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                ThemeConfig.delConfig(index)
                initData()
            }
            noButton()
        }
    }

    fun share(index: Int) {
        val actions = listOf(getString(R.string.theme_export_full), getString(R.string.theme_export_color_only))
        requireContext().selector(getString(R.string.theme_export_type_title), actions) { _, i ->
            if (i == 0) {
                exportIndex = index
                val themeName = ThemeConfig.configList[index].themeName
                createThemeDoc.launch("$themeName.zip")
            } else {
                val json = GSON.toJson(ThemeConfig.configList[index])
                requireContext().share(json, "主题分享")
            }
        }
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<ThemeConfig.Config, ItemThemeConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemThemeConfigBinding {
            return ItemThemeConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThemeConfigBinding,
            item: ThemeConfig.Config,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvName.text = item.themeName
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemThemeConfigBinding) {
            binding.apply {
                root.setOnClickListener {
                    ThemeConfig.applyConfig(context, ThemeConfig.configList[holder.layoutPosition])
                }
                ivShare.setOnClickListener {
                    share(holder.layoutPosition)
                }
                ivDelete.setOnClickListener {
                    delete(holder.layoutPosition)
                }
            }
        }

    }
}