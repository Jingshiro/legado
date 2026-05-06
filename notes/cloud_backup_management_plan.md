# 云端备份文件管理功能开发计划

## 需求概述

在「我的 → 备份与恢复 → 恢复」路径中，将现有的简单选择弹窗替换为一个独立的云端备份管理页面，支持：
1. 列出 WebDAV 中所有备份文件（显示文件名、大小、修改时间）
2. 单个文件恢复
3. 单个文件重命名（通过 WebDAV MOVE 方法）
4. 多选文件后一键批量删除

---

## 项目技术背景

- **语言**: Kotlin
- **架构**: MVVM (ViewModel + LiveData + ViewBinding)
- **异步**: Kotlin Coroutines + 自定义 Coroutine 链式封装
- **UI 组件**: RecyclerView + 自定义 RecyclerAdapter 基类、SelectActionBar（多选操作栏）
- **WebDAV 库**: 自封装 `io.legado.app.lib.webdav.WebDav`，基于 OkHttp
- **参考实现**: `RemoteBookActivity` / `RemoteBookAdapter` / `RemoteBookViewModel`（远程书籍浏览页面，已有多选、全选、反选等完整模式）

---

## 开发步骤

### 第一步：WebDav 层 - 添加 MOVE 方法

**文件**: `app/src/main/java/io/legado/app/lib/webdav/WebDav.kt`

在 `delete()` 方法之后添加 `move()` 方法，用于重命名文件：
- WebDAV 协议中重命名通过 `MOVE` 方法实现
- 需要设置 `Destination` header 为目标 URL
- 需要设置 `Overwrite` header 为 `F`（不覆盖已存在文件）

```kotlin
/**
 * 重命名/移动文件
 * @param destUrl 目标完整 URL
 */
suspend fun move(destUrl: String): Boolean {
    val url = httpUrl ?: return false
    return kotlin.runCatching {
        webDavClient.newCallResponse {
            url(url)
            method("MOVE", null)
            addHeader("Destination", destUrl)
            addHeader("Overwrite", "F")
        }.use {
            checkResult(it)
        }
    }.onFailure {
        currentCoroutineContext().ensureActive()
        AppLog.put("WebDav移动失败\n${it.localizedMessage}", it)
    }.isSuccess
}
```

### 第二步：AppWebDav 层 - 添加删除和重命名方法

**文件**: `app/src/main/java/io/legado/app/help/AppWebDav.kt`

添加以下方法：

```kotlin
/**
 * 获取云端所有备份文件的详细信息（含大小、时间等）
 */
@Throws(Exception::class)
suspend fun getBackupFileList(): ArrayList<WebDavFile> {
    val files = arrayListOf<WebDavFile>()
    authorization?.let {
        var fileList = WebDav(rootWebDavUrl, it).listFiles()
        fileList = fileList.sortedWith { o1, o2 ->
            AlphanumComparator.compare(o1.displayName, o2.displayName)
        }.reversed()
        fileList.forEach { webDavFile ->
            if (webDavFile.displayName.startsWith("backup")) {
                files.add(webDavFile)
            }
        }
    } ?: throw NoStackTraceException("webDav没有配置")
    return files
}

/**
 * 删除云端备份文件
 */
suspend fun deleteBackup(name: String): Boolean {
    authorization?.let {
        return WebDav(rootWebDavUrl + name, it).delete()
    }
    return false
}

/**
 * 重命名云端备份文件
 */
suspend fun renameBackup(oldName: String, newName: String): Boolean {
    authorization?.let {
        val oldUrl = rootWebDavUrl + oldName
        val newUrl = rootWebDavUrl + newName
        return WebDav(oldUrl, it).move(newUrl)
    }
    return false
}
```

### 第三步：创建云端备份管理 Activity 界面

#### 3.1 布局文件

**新建文件**: `app/src/main/res/layout/activity_cloud_backup.xml`

参考 `activity_import_book.xml` 的结构：
- TitleBar（带返回按钮和标题）
- RefreshProgressBar（加载指示器）
- RecyclerView（备份文件列表）
- 空状态 TextView
- SelectActionBar（底部多选操作栏：全选、反选、删除按钮）

#### 3.2 列表项布局

**新建文件**: `app/src/main/res/layout/item_cloud_backup.xml`

每个备份文件项显示：
- CheckBox（多选框）
- 文件名（TextView）
- 文件大小（TextView）
- 修改时间（TextView）

参考 `item_import_book.xml` 的结构，但针对备份文件调整显示内容。

### 第四步：创建 ViewModel

**新建文件**: `app/src/main/java/io/legado/app/ui/config/CloudBackupViewModel.kt`

继承 `BaseViewModel`，功能：
- `backupFiles: MutableLiveData<List<CloudBackupItem>>` — 备份文件列表数据
- `loadBackups()` — 调用 `AppWebDav.getBackupFileList()` 加载数据
- `deleteBackups(names: List<String>, callback)` — 批量删除，逐个调用 `AppWebDav.deleteBackup()`
- `renameBackup(oldName: String, newName: String, callback)` — 重命名，调用 `AppWebDav.renameBackup()`
- `restoreBackup(name: String, callback)` — 恢复，调用 `AppWebDav.restoreWebDav()`

数据模型 `CloudBackupItem`：
```kotlin
data class CloudBackupItem(
    val displayName: String,
    val size: Long,
    val lastModify: Long
)
```

### 第五步：创建 Adapter

**新建文件**: `app/src/main/java/io/legado/app/ui/config/CloudBackupAdapter.kt`

继承 `RecyclerAdapter<CloudBackupItem, ItemCloudBackupBinding>`，功能：
- `selected: HashSet<CloudBackupItem>` — 已选中项集合
- 支持全选 / 反选
- 点击事件：选中/取消选中
- 长按事件：弹出操作菜单（恢复、重命名、删除）
- `CallBack` 接口：`onItemClick`, `onItemLongClick`, `upCountView`

### 第六步：创建 Activity

**新建文件**: `app/src/main/java/io/legado/app/ui/config/CloudBackupActivity.kt`

继承 `VMBaseActivity<ActivityCloudBackupBinding, CloudBackupViewModel>`，实现：
- `SelectActionBar.CallBack` — 全选、反选、批量删除
- `CloudBackupAdapter.CallBack` — 单项操作

核心功能：
1. **初始化**: 设置 RecyclerView、Adapter、SelectActionBar
2. **加载数据**: 在 `onActivityCreated` 中调用 `viewModel.loadBackups()`
3. **观察数据**: `viewModel.backupFiles.observe()` 更新列表
4. **批量删除**: `onClickSelectBarMainAction()` → 确认弹窗 → `viewModel.deleteBackups()` → 刷新列表
5. **单个恢复**: 长按菜单选"恢复" → 确认弹窗 → `viewModel.restoreBackup()`
6. **单个重命名**: 长按菜单选"重命名" → 输入弹窗 → `viewModel.renameBackup()` → 刷新列表
7. **单个删除**: 长按菜单选"删除" → 确认弹窗 → `viewModel.deleteBackup()` → 刷新列表

### 第七步：修改 BackupConfigFragment 的恢复入口

**文件**: `app/src/main/java/io/legado/app/ui/config/BackupConfigFragment.kt`

修改 `restore()` 方法和 `showRestoreDialog()`：
- 将现有的 selector 弹窗替换为启动 `CloudBackupActivity`
- 保留长按"恢复"从本地文件恢复的功能

```kotlin
fun restore() {
    // 直接启动云端备份管理页面
    startActivity(Intent(requireContext(), CloudBackupActivity::class.java))
}
```

删除不再需要的 `showRestoreDialog()` 和 `restoreWebDav()` 方法（已迁移到 ViewModel）。

### 第八步：注册 Activity 和添加字符串资源

#### 8.1 AndroidManifest.xml

**文件**: `app/src/main/AndroidManifest.xml`

在 `<application>` 中注册：
```xml
<activity
    android:name=".ui.config.CloudBackupActivity"
    android:exported="false"
    android:title="@string/cloud_backup_manage" />
```

#### 8.2 字符串资源

**文件**: `app/src/main/res/values/strings.xml` 添加：
```xml
<string name="cloud_backup_manage">Cloud backup management</string>
<string name="rename">Rename</string>
<string name="rename_backup">Rename backup</string>
<string name="input_new_name">Please enter a new file name</string>
<string name="sure_delete_backups">Are you sure to delete the selected %d backup files?</string>
<string name="delete_backups_success">Successfully deleted %d backup files</string>
<string name="delete_backup_success">Backup file deleted</string>
<string name="rename_success">Renamed successfully</string>
<string name="restore_backup">Restore backup</string>
<string name="sure_restore_backup">Are you sure to restore this backup? Current data will be overwritten.</string>
```

**文件**: `app/src/main/res/values-zh/strings.xml` 添加：
```xml
<string name="cloud_backup_manage">云端备份管理</string>
<string name="rename">重命名</string>
<string name="rename_backup">重命名备份</string>
<string name="input_new_name">请输入新的文件名</string>
<string name="sure_delete_backups">确定删除选中的 %d 个备份文件吗？</string>
<string name="delete_backups_success">成功删除 %d 个备份文件</string>
<string name="delete_backup_success">备份文件已删除</string>
<string name="rename_success">重命名成功</string>
<string name="restore_backup">恢复备份</string>
<string name="sure_restore_backup">确定恢复此备份吗？当前数据将被覆盖。</string>
```

---

## 涉及文件清单

| 操作 | 文件路径 |
|------|----------|
| 修改 | `app/src/main/java/io/legado/app/lib/webdav/WebDav.kt` — 添加 `move()` 方法 |
| 修改 | `app/src/main/java/io/legado/app/help/AppWebDav.kt` — 添加 `getBackupFileList()`, `deleteBackup()`, `renameBackup()` |
| 修改 | `app/src/main/java/io/legado/app/ui/config/BackupConfigFragment.kt` — 修改恢复入口 |
| 新建 | `app/src/main/java/io/legado/app/ui/config/CloudBackupActivity.kt` |
| 新建 | `app/src/main/java/io/legado/app/ui/config/CloudBackupViewModel.kt` |
| 新建 | `app/src/main/java/io/legado/app/ui/config/CloudBackupAdapter.kt` |
| 新建 | `app/src/main/res/layout/activity_cloud_backup.xml` |
| 新建 | `app/src/main/res/layout/item_cloud_backup.xml` |
| 修改 | `app/src/main/AndroidManifest.xml` — 注册 Activity |
| 修改 | `app/src/main/res/values/strings.xml` — 添加英文字符串 |
| 修改 | `app/src/main/res/values-zh/strings.xml` — 添加中文字符串 |

---

## 关键设计决策

1. **独立 Activity vs 弹窗**: 选择独立 Activity，因为需要展示列表 + 多选操作栏，弹窗空间不足以承载这些交互
2. **复用现有组件**: 复用 `SelectActionBar`（多选操作栏）、`RecyclerAdapter` 基类、`VMBaseActivity` 基类、`alert`/`selector` 弹窗工具
3. **WebDAV MOVE 实现重命名**: WebDAV 标准协议中重命名通过 MOVE 方法实现，无需额外依赖
4. **批量删除策略**: 逐个删除（串行），删除完成后统一刷新列表和提示
5. **保留本地恢复**: 长按"恢复"按钮仍可从本地 zip 文件恢复，保持向后兼容

---

## 风险点

1. **WebDAV MOVE 兼容性**: 不同 WebDAV 服务器对 MOVE 方法的支持程度不同，坚果云等主流服务均支持
2. **大量备份文件**: 坚果云有文件数量限制（约 700+），已有提示逻辑，沿用即可
3. **重命名冲突**: 需检查目标文件名是否已存在，MOVE header 中 `Overwrite: F` 可防止覆盖
4. **网络异常处理**: 所有网络操作需在 IO 协程中执行，错误通过 Toast 和 AppLog 反馈
