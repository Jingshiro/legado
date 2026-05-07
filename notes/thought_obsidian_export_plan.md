# 想法导出到Obsidian开发计划

## 一、需求分析

### 功能目标
将阅读中记录的想法（thought）和对应的划线原文（selectedText）导出到Obsidian仓库中。

### 导出内容要求
- 书籍简介（如果数据中有则保留）
- 按章节分组的划线原文
- 感想内容
- 记录时间
- **不需要**：作者简介、书籍封面、出版信息（出版社、ISBN等）

### 技术实现
通过Obsidian的Local REST API插件实现导出。

---

## 二、Obsidian Local REST API插件分析

### 插件基本信息
- **GitHub仓库**: `coddingtonbear/obsidian-local-rest-api`
- **默认端口**: `localhost:27124`
- **功能**: 为Obsidian vault提供本地REST API接口

### 核心API端点
| 方法 | 端点 | 功能 |
|------|------|------|
| `PUT` | `/vault/{filename}` | 创建或更新文件 |
| `GET` | `/vault/{filename}` | 获取文件内容 |
| `DELETE` | `/vault/{filename}` | 删除文件 |
| `GET` | `/vault/` | 列出所有文件 |
| `POST` | `/search/simple/` | 简单搜索 |
| `GET` | `/` | API文档（Swagger） |

### 认证方式
需要在请求头中添加API Key：
```
Authorization: Bearer <API_KEY>
```

---

## 三、现有数据结构分析

### BookThought实体
```kotlin
data class BookThought(
    val id: Long = 0,
    val bookName: String = "",      // 书名
    val bookAuthor: String = "",    // 作者
    val chapterIndex: Int = 0,      // 章节索引
    val chapterPos: Int = 0,        // 章节内位置
    val chapterName: String = "",   // 章节名
    val selectedText: String = "",  // 划线原文
    val textHash: String = "",      // 文本哈希
    val thought: String = "",       // 想法内容
    val createTime: Long = System.currentTimeMillis(),  // 创建时间
    val updateTime: Long = System.currentTimeMillis()   // 更新时间
)
```

### BookThoughtDao关键方法
- `all`: 获取所有想法
- `getByBook(bookName, bookAuthor)`: 获取某本书的所有想法
- `getByChapter(...)`: 获取某章的想法
- `findByText(...)`: 根据原文查找想法

---

## 四、导出Markdown格式设计

### 批量导出格式（按书分组）

参考格式如下，包含：书籍简介（如有）、按章节分组的划线原文和感想。

```markdown
### 书籍简介

书籍简介内容（如果数据中有则保留，没有则省略此部分）

---

### 第一章 章节名

划线原文内容1

> 感想内容1

<font>2024-01-01 12:00:00</font>

---

划线原文内容2

> 感想内容2

<font>2024-01-01 12:00:00</font>

---

### 第二章 章节名

划线原文内容3

> 感想内容3

<font>2024-01-02 15:30:00</font>
```

### 格式说明
- **书籍简介**：如果书籍有简介信息则保留，否则省略整个部分
- **章节标题**：使用三级标题 `###`，格式为 `### 第X章 章节名`
- **划线原文**：直接展示原文内容，无特殊标记
- **感想**：使用引用格式 `> 感想内容`，紧跟在原文下方
- **时间戳**：使用 `<font>时间</font>` 格式（清洗模板通过正则 `/<font[^>]*>([\s\S]*?)<\/font>/` 提取）
- **分隔线**：每个书摘条目之间用 `---` 分隔
- **不需要的内容**：作者简介、书籍封面、出版信息（出版社、ISBN等）
- **兼容性**：此格式可被 `tpl_纸间书摘清洗.md` 模板自动识别和清洗

---

## 五、技术实现方案

### 5.1 配置管理
在AppConfig中添加Obsidian配置：
```kotlin
// 导出方式枚举
enum class ObsidianExportMethod {
    LOCAL_REST_API,  // 通过 Obsidian Local REST API 导出
    LOCAL_FILE       // 直接写入本地文件路径
}

// 新增配置项
var obsidianExportMethod: ObsidianExportMethod  // 导出方式
var obsidianApiUrl: String                       // API地址，如 http://localhost:27124
var obsidianApiKey: String                       // API密钥
var obsidianVaultPath: String                    // vault中的导出路径，如 "读书笔记/thoughts"
var obsidianLocalPath: String                    // 本地 Obsidian 仓库路径，Android 设备路径
                                                 // 如 "/storage/emulated/0/Documents/ObsidianVault"
                                                 // 或使用 SAF (Storage Access Framework) 获取的 URI
```

### Android 存储权限考虑
- **REST API 方式**: 无需额外权限，通过网络请求
- **本地文件方式**: 需要存储权限
  - Android 10+：使用 SAF (Storage Access Framework) 让用户选择目录
  - 或申请 `MANAGE_EXTERNAL_STORAGE` 权限（适用于文件管理类应用）
  - 路径格式：`/storage/emulated/0/...` 或 content URI

### 5.2 网络请求层
使用现有的OkHttp客户端发送请求：
```kotlin
object ObsidianApi {
    private val client = OkHttpClient()

    suspend fun createOrUpdateFile(
        filePath: String,
        content: String,
        config: ObsidianConfig
    ): Result<Unit>

    suspend fun checkConnection(config: ObsidianConfig): Result<Boolean>
}
```

### 5.3 Markdown生成器
```kotlin
object ThoughtMarkdownGenerator {
    /**
     * 生成按书分组的批量导出内容
     * 格式：书籍简介（如有）-> 按章节分组的划线原文+感想
     * @param bookName 书名
     * @param bookIntro 书籍简介，从 Book.getDisplayIntro() 获取，可为空
     * @param thoughts 该书的所有想法列表
     */
    fun generateCollection(
        bookName: String,
        bookIntro: String?,
        thoughts: List<BookThought>
    ): String
}
```

### 5.4 导出服务
```kotlin
class ThoughtObsidianExporter {
    /**
     * 按书导出，每本书生成一个文件
     * 包含书籍简介（如有）和所有划线+感想
     * 书籍简介从 Book.getDisplayIntro() 获取
     * 根据配置选择导出方式（REST API 或本地文件）
     */
    suspend fun exportByBook(bookName: String, bookAuthor: String): Result<Unit>

    /**
     * 批量导出所有书籍
     * 遍历所有有想法的书籍，逐个调用 exportByBook
     */
    suspend fun exportAll(): Result<Unit>

    /**
     * 通过 REST API 导出
     */
    private suspend fun exportViaApi(filePath: String, content: String): Result<Unit>

    /**
     * 直接写入本地文件
     * @param localPath 本地 Obsidian 仓库根路径（Android 设备路径）
     * @param vaultPath vault 内相对路径
     * @param content 文件内容
     *
     * 注意：需要处理 Android 存储权限
     * - 使用 SAF 时，localPath 可能是 content URI
     * - 使用文件路径时，需要确保有存储权限
     */
    private suspend fun exportViaLocalFile(localPath: String, vaultPath: String, content: String): Result<Unit>

    /**
     * 获取书籍简介
     * 从 Book 实体中获取简介，优先使用 customIntro，否则使用 intro
     */
    private fun getBookIntro(bookName: String, bookAuthor: String): String?

    /**
     * 生成文件名
     * 格式：《书名》_时间戳.md
     * 冲突时添加 -1/-2 后缀
     */
    private fun generateFileName(bookName: String): String
}
```

### 5.5 UI集成
在现有UI中添加导出到Obsidian的选项：
- `BookmarkThoughtFragment`中添加"导出到Obsidian"按钮
- 新增`ObsidianExportDialog`用于配置和导出，包含：
  - 导出方式选择：Local REST API / 本地文件路径
  - REST API 配置（API地址、密钥）
  - 本地路径配置：
    - 使用 Android 目录选择器 (SAF) 让用户选择 Obsidian 仓库路径
    - 或手动输入 Android 设备路径（如 `/storage/emulated/0/Documents/ObsidianVault`）
  - vault 内导出路径配置
  - 导出按钮
- 一次性全量导出所有书籍的想法
- 处理存储权限请求（本地文件方式）

---

## 六、文件结构规划

```
app/src/main/java/io/legado/app/
├── help/
│   ├── config/
│   │   └── AppConfig.kt                    # 添加Obsidian配置项
│   └── storage/
│       └── SafHelper.kt                    # 新增：SAF 存储访问帮助类
├── data/
│   └── entities/
│       └── ObsidianConfig.kt               # 新增：Obsidian配置实体
├── network/
│   └── ObsidianApi.kt                      # 新增：Obsidian API客户端
├── ui/book/thought/
│   ├── ObsidianExportDialog.kt             # 新增：导出配置对话框
│   ├── ThoughtObsidianExporter.kt          # 新增：导出核心逻辑
│   └── ThoughtMarkdownGenerator.kt         # 新增：Markdown生成器
└── res/
    ├── layout/
    │   └── dialog_obsidian_export.xml       # 新增：导出对话框布局
    └── values/
        └── strings.xml                      # 添加相关字符串
```

---

### 七、实现步骤 (已完成)

### 第一阶段：基础架构 (已完成)
1. 创建`ObsidianConfig`（已整合至`AppConfig`）
2. 在`AppConfig`中添加配置项 (已完成)
3. 实现`ObsidianApi`网络客户端 (已完成，包含路径编码修复)
4. 实现存储访问 (已复用项目现有的 `FileDoc` 机制)

### 第二阶段：核心功能 (已完成)
5. 实现`ThoughtMarkdownGenerator`生成器 (已完成)
6. 实现`ThoughtObsidianExporter`导出器 (已完成，支持全量与单本导出)
7. 实现文件名生成和冲突处理逻辑 (已完成)
8. 添加错误处理和重试机制 (已完成)

### 第三阶段：UI集成 (已完成)
9. 创建`ObsidianExportDialog`配置对话框 (已完成，支持全量/单本模式)
10. 实现导出方式切换和对应配置项显示 (已完成)
11. 集成 Android 目录选择器 (已完成，复用 `HandleFileContract`)
12. 在现有UI中添加导出入口:
    - **全量导出**: "我的" -> "导出到 Obsidian"
    - **单本导出**: 目录界面 -> "书签/想法" Tab -> 菜单 -> "导出到 Obsidian"
13. 添加导出进度和结果提示 (已完成)
14. 处理存储权限请求 (已完成)

---

## 十一、最终实现说明 (2026-05-07)

1. **API路径编码**: 修复了 `ObsidianApi` 中文件路径未编码的问题，使用 `URLEncoder` 处理特殊字符。
2. **导出逻辑**: `ThoughtObsidianExporter.exportBook` 现在支持按需加载数据库中的想法。
3. **单本导出入口**: 在 `TocActivity` 的 `menu_group_bookmark` 分组中新增了导出菜单项。
4. **全量导出入口**: 在 `MyPreferenceFragment` (位于 `pref_main.xml`) 中保留了全量导出入口。


---

## 八、已确认方案

1. **文件命名规则**: `《书名》_时间戳.md`（如 `《意料之内》_20260507091031.md`）

2. **目录结构**: 一本书一个文件，所有书摘放在同一目录

3. **冲突处理**: 文件已存在时在末尾添加 `-1`/`-2` 后缀

4. **书籍简介来源**: 从 `Book.getDisplayIntro()` 获取（优先返回用户自定义简介 `customIntro`，否则返回书源简介 `intro`）

5. **导出范围**: 一次性全量导出，不支持筛选

6. **导出方式**: 支持两种方式
   - **Local REST API**: 通过 Obsidian Local REST API 插件导出（需要插件运行）
   - **本地文件**: 直接写入 Android 设备上的 Obsidian 仓库路径（需要存储权限）

---

## 九、预估工作量

- 基础架构：约2-3小时
- 核心功能：约3-4小时
- UI集成：约2-3小时
- 测试优化：约2小时

**总计：约10-12小时**

---

## 十、参考资料

- [Obsidian Local REST API GitHub](https://github.com/coddingtonbear/obsidian-local-rest-api)
- [Obsidian API文档](https://obsidian.md/plugins?id=obsidian-local-rest-api)
- 现有代码参考：
  - `BookThought.kt` - 数据实体
  - `BookThoughtDao.kt` - 数据访问
  - `ThoughtImageExporter.kt` - 现有导出功能
  - `ShareThoughtDialog.kt` - 分享功能UI
