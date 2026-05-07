package io.legado.app.ui.book.thought

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogObsidianExportBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObsidianExportDialog : BaseDialogFragment(R.layout.dialog_obsidian_export) {

    private val binding by viewBinding(DialogObsidianExportBinding::bind)

    private val bookName: String? by lazy { arguments?.getString("bookName") }
    private val bookAuthor: String? by lazy { arguments?.getString("bookAuthor") }

    private val dirSelector = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            AppConfig.obsidianLocalDirUri = uri.toString()
            binding.editLocalPath.setText(uri.toString())
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        loadSavedConfig()
    }

    private fun initView() {
        binding.toolBar.apply {
            setBackgroundColor(primaryColor)
            inflateMenu(R.menu.obsidian_export)
            menu.applyTint(requireContext())
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { dismiss() }
            if (!bookName.isNullOrEmpty()) {
                title = getString(R.string.export_book_to_obsidian, bookName)
            }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_help -> showHelp("obsidian_api_tutorial")
                }
                true
            }
        }

        binding.rgExportMethod.setOnCheckedChangeListener { _, checkedId ->
            val isApi = checkedId == R.id.rb_rest_api
            binding.layoutApiConfig.visible(isApi)
            binding.layoutLocalConfig.visible(!isApi)
        }

        binding.btnSelectDir.setOnClickListener {
            dirSelector.launch {
                title = getString(R.string.select_obsidian_dir)
            }
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnExport.setOnClickListener {
            saveConfig()
            startExport()
        }
    }

    private fun loadSavedConfig() {
        when (AppConfig.obsidianExportMethod) {
            0 -> binding.rbRestApi.isChecked = true
            1 -> binding.rbLocalFile.isChecked = true
        }
        binding.editApiUrl.setText(AppConfig.obsidianApiUrl)
        binding.editApiKey.setText(AppConfig.obsidianApiKey)
        binding.editVaultPath.setText(AppConfig.obsidianVaultSubPath)
        AppConfig.obsidianLocalDirUri?.let {
            binding.editLocalPath.setText(it)
        }
    }

    private fun saveConfig() {
        AppConfig.obsidianExportMethod = if (binding.rbRestApi.isChecked) 0 else 1
        AppConfig.obsidianApiUrl = binding.editApiUrl.text.toString().trim()
        AppConfig.obsidianApiKey = binding.editApiKey.text.toString().trim()
        AppConfig.obsidianVaultSubPath = binding.editVaultPath.text.toString().trim()
    }

    private fun startExport() {
        binding.progressBar.visible(true)
        binding.btnExport.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(IO) {
                val name = bookName
                val author = bookAuthor
                if (!name.isNullOrEmpty() && !author.isNullOrEmpty()) {
                    ThoughtObsidianExporter.exportBook(name, author).map { 1 to 1 }
                } else {
                    ThoughtObsidianExporter.exportAll()
                }
            }
            binding.progressBar.visible(false)
            binding.btnExport.isEnabled = true
            result.onSuccess { (success, total) ->
                context?.toastOnUi(
                    getString(R.string.obsidian_export_result, success, total)
                )
                dismiss()
            }.onFailure { e ->
                context?.toastOnUi(
                    getString(R.string.obsidian_export_failed, e.localizedMessage)
                )
            }
        }
    }

    private fun testConnection() {
        val url = binding.editApiUrl.text.toString().trim()
        val key = binding.editApiKey.text.toString().trim()
        if (url.isBlank()) {
            context?.toastOnUi(R.string.obsidian_api_url_empty)
            return
        }
        lifecycleScope.launch {
            val result = withContext(IO) {
                ObsidianApi.checkConnection(url, key)
            }
            result.onSuccess { connected ->
                if (connected) {
                    context?.toastOnUi(R.string.obsidian_connection_success)
                } else {
                    context?.toastOnUi(R.string.obsidian_connection_failed)
                }
            }.onFailure { e ->
                context?.toastOnUi(
                    getString(R.string.obsidian_connection_failed_detail, e.localizedMessage)
                )
            }
        }
    }

    companion object {
        fun newInstance(bookName: String? = null, bookAuthor: String? = null): ObsidianExportDialog {
            val args = Bundle().apply {
                putString("bookName", bookName)
                putString("bookAuthor", bookAuthor)
            }
            return ObsidianExportDialog().apply {
                arguments = args
            }
        }
    }
}
