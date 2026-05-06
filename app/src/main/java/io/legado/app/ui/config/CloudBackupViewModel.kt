package io.legado.app.ui.config

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.help.AppWebDav
import io.legado.app.lib.webdav.WebDavException
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

class CloudBackupViewModel(application: Application) : BaseViewModel(application) {

    val backupFiles = MutableLiveData<List<WebDavFile>>()
    val deleteDone = MutableLiveData<Int>()
    val renameDone = MutableLiveData<Boolean>()
    val restoreDone = MutableLiveData<Boolean>()

    fun loadBackups() {
        execute {
            AppWebDav.getBackupFileList()
        }.onSuccess {
            backupFiles.postValue(it)
        }.onError {
            AppLog.put("获取云端备份列表出错\n${it.localizedMessage}", it)
            context.toastOnUi("获取云端备份列表出错\n${it.localizedMessage}")
            backupFiles.postValue(emptyList())
        }
    }

    fun deleteBackups(names: List<String>) {
        execute {
            var successCount = 0
            names.forEach { name ->
                if (AppWebDav.deleteBackup(name)) {
                    successCount++
                }
            }
            successCount
        }.onSuccess {
            deleteDone.postValue(it)
            loadBackups()
        }.onError {
            AppLog.put("删除备份出错\n${it.localizedMessage}", it)
            context.toastOnUi("删除备份出错\n${it.localizedMessage}")
        }
    }

    fun renameBackup(oldName: String, newName: String) {
        execute {
            AppWebDav.renameBackup(oldName, newName)
        }.onSuccess {
            renameDone.postValue(true)
            loadBackups()
        }.onError {
            AppLog.put("重命名备份出错\n${it.localizedMessage}", it)
            if (it is WebDavException) {
                val msg = it.localizedMessage ?: ""
                if (msg.contains("405") || msg.contains("501")
                    || msg.contains("Method Not Allowed")
                    || msg.contains("Not Implemented")
                ) {
                    appCtx.toastOnUi(R.string.webdav_move_not_supported)
                } else {
                    appCtx.toastOnUi(
                        appCtx.getString(R.string.rename_backup_fail, it.localizedMessage)
                    )
                }
            } else {
                appCtx.toastOnUi(
                    appCtx.getString(R.string.rename_backup_fail, it.localizedMessage)
                )
            }
        }
    }

    fun restoreBackup(name: String) {
        execute {
            AppWebDav.restoreWebDav(name)
        }.onSuccess {
            restoreDone.postValue(true)
        }.onError {
            AppLog.put("WebDav恢复出错\n${it.localizedMessage}", it)
            context.toastOnUi("WebDav恢复出错\n${it.localizedMessage}")
        }
    }

}
