package io.legado.app.ui.config

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityCloudBackupBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.utils.requestInputMethod
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class CloudBackupActivity :
    VMBaseActivity<ActivityCloudBackupBinding, CloudBackupViewModel>(),
    SelectActionBar.CallBack,
    CloudBackupAdapter.CallBack {

    override val viewModel by viewModels<CloudBackupViewModel>()
    override val binding by viewBinding(ActivityCloudBackupBinding::inflate)
    private val adapter by lazy { CloudBackupAdapter(this, this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        title = getString(R.string.cloud_backup_manage)
        initView()
        initEvent()
        viewModel.loadBackups()
    }

    private fun initView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.setCallBack(this)
    }

    private fun initEvent() {
        viewModel.backupFiles.observe(this) { files ->
            binding.refreshProgressBar.isAutoLoading = false
            binding.tvEmptyMsg.isGone = files.isNotEmpty()
            adapter.setItems(files)
        }
        viewModel.deleteDone.observe(this) { count ->
            toastOnUi(getString(R.string.delete_backups_success, count))
        }
        viewModel.renameDone.observe(this) {
            if (it) {
                toastOnUi(R.string.rename_success)
            }
        }
        viewModel.restoreDone.observe(this) {
            if (it) {
                toastOnUi(R.string.restore_success)
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cloud_backup, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_refresh -> {
                binding.refreshProgressBar.isAutoLoading = true
                viewModel.loadBackups()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun selectAll(selectAll: Boolean) {
        adapter.selectAll(selectAll)
    }

    override fun onClickSelectBarMainAction() {
        val selectedNames = adapter.selected.map { it.displayName }
        if (selectedNames.isEmpty()) return
        alert(
            title = getString(R.string.delete_alert),
            message = getString(R.string.sure_delete_backups, selectedNames.size)
        ) {
            yesButton {
                viewModel.deleteBackups(selectedNames)
                adapter.selected.clear()
            }
            noButton()
        }
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(adapter.selected.size, adapter.itemCount)
    }

    override fun onItemLongClick(item: WebDavFile) {
        val items = listOf(
            getString(R.string.restore_backup),
            getString(R.string.rename),
            getString(R.string.delete)
        )
        alert {
            setTitle(item.displayName)
            items(items) { _, index ->
                when (index) {
                    0 -> confirmRestore(item)
                    1 -> showRenameDialog(item)
                    2 -> confirmDeleteSingle(item)
                }
            }
        }
    }

    private fun confirmRestore(item: WebDavFile) {
        alert(
            title = getString(R.string.restore_backup),
            message = getString(R.string.sure_restore_backup)
        ) {
            yesButton {
                binding.refreshProgressBar.isAutoLoading = true
                viewModel.restoreBackup(item.displayName)
            }
            noButton()
        }
    }

    private fun confirmDeleteSingle(item: WebDavFile) {
        alert(
            title = getString(R.string.delete_alert),
            message = getString(R.string.sure_del_any, item.displayName)
        ) {
            yesButton {
                viewModel.deleteBackups(listOf(item.displayName))
                adapter.selected.remove(item)
            }
            noButton()
        }
    }

    private fun showRenameDialog(item: WebDavFile) {
        alert(title = getString(R.string.rename_backup)) {
            val alertBinding = io.legado.app.databinding.DialogEditTextBinding
                .inflate(layoutInflater).apply {
                    editView.setHint(R.string.input_new_name)
                    editView.setText(item.displayName)
                }
            customView { alertBinding.root }
            okButton {
                val newName = alertBinding.editView.text?.toString()
                if (!newName.isNullOrBlank() && newName != item.displayName) {
                    viewModel.renameBackup(item.displayName, newName)
                }
            }
            cancelButton()
        }.requestInputMethod()
    }

}
