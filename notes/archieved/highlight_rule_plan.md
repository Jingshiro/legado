# 高亮规则功能开发计划

> 创建时间：2026-07-01
> 更新时间：2026-07-04
> 状态：✅ 已完成

---

## 📌 功能概述

在现有替换规则基础上扩展"高亮规则"功能，支持对正则捕获组应用 HTML 样式（加粗、斜体、下划线、颜色等）。

### 核心能力

- 使用正则表达式匹配文本，支持 `$0`, `$1`, `$2` 等捕获组引用
- 对不同捕获组应用独立的 HTML 样式
- 支持丢弃不需要的捕获组（不输出）
- 通过 `<usehtml>` 标记走 HTML 渲染路径

### 使用场景

```
正则："[^"]+"
替换为：<b><font color="#D32F2F">$0</font></b>
高亮模式：开启

输入：小明说"你好啊"然后走了。
输出："你好啊" 加粗红色
```

---

## 🏗️ 实现方案

### 核心流程

```
ContentProcessor.getContent()
    ↓
检测 item.isHighlight
    ↓ (true)
applyHighlightRule() 用正则 findAll 匹配
    ↓
CssStyleParser.extractGroupStyles() 从替换模板提取各捕获组的 HTML 标签
    ↓
对每个匹配：用 wrapWithHtmlTags() 生成带标签的替换文本
    ↓
替换结果中的 HTML 行包裹 <usehtml>...<endhtml> 标记
    ↓
TextChapterLayout.setTypeHtml() 解析 HTML（通过 Html.fromHtml）
    ↓
extractIsBold/extractIsItalic/extractIsUnderline/extractTextColor 提取样式
    ↓
TextHtmlColumn 渲染带样式文本
```

### 关键设计决策

1. **使用原生 HTML 标签**（`<b>`, `<i>`, `<u>`, `<font color>`），不使用 `<span style="...">`
   - 原因：Android `Html.fromHtml()` 不支持 CSS 内联样式
2. **通过 `<usehtml>` 标记走 setTypeHtml 渲染路径**
   - 原因：setTypeText 是纯文本逐字符渲染，不会解析 HTML 标签
3. **按捕获组整体替换**，而非逐字符处理
   - 原因：性能优化，减少 TextHtmlColumn 对象数量

---

## 🔧 技术细节

### 支持的 HTML 标签

| 标签 | 效果 | 生成方式 |
|------|------|----------|
| `<b>` / `<strong>` | 加粗 | 工具栏 B 按钮 |
| `<i>` / `<em>` | 斜体 | 工具栏 I 按钮 |
| `<u>` | 下划线 | 工具栏 U 按钮 |
| `<font color="red">` | 颜色 | 工具栏颜色按钮（支持自定义十六进制色值） |
| `<big>` / `<small>` | 字号（相对大小） | 工具栏字号按钮 |
| `<font face="楷体">` | 字体 | 工具栏字体按钮（需配置字体目录） |

### 捕获组引用

| 引用 | 含义 |
|------|------|
| `$0` | 整个匹配到的文本 |
| `$1` | 第1个括号捕获的内容 |
| `$2` | 第2个括号捕获的内容 |
| `$N` | 第N个括号捕获的内容 |

---

## 📝 已完成任务

### Phase 1: 数据层 ✅

- `ReplaceRule.kt` — 添加 `isHighlight` 字段（`@ColumnInfo(defaultValue = "0")`）
- `AppDatabase.kt` — 版本 96→97，AutoMigration

### Phase 2: 样式解析器 ✅

- `CssStyleParser.kt` — 解析 HTML 标签样式，提取捕获组样式，wrapWithHtmlTags 生成标签
- `FontManager.kt` — 扫描本地字体目录和用户配置字体目录，按字体名缓存 Typeface

### Phase 3: 渲染层 ✅

- `TextHtmlColumn.kt` — 新增 mTypeface/mIsBold/mIsItalic/mIsUnderline 字段，draw 方法应用样式
- `TextChapterLayout.kt` — 新增 extractIsBold/extractIsItalic/extractIsUnderline/extractTypeface 方法

### Phase 4: 处理层 ✅

- `ContentProcessor.kt` — isHighlight 分支调用 applyHighlightRule，HTML 行包裹 `<usehtml>` 标记

### Phase 5: UI 层 ✅

- `activity_replace_edit.xml` — 高亮模式复选框、样式工具栏、替换为帮助按钮
- `ReplaceEditActivity.kt` — 工具栏事件、颜色/字号/字体选择对话框、自定义颜色输入
- `item_replace_rule.xml` — 高亮标识图标
- `ReplaceRuleAdapter.kt` — 显示高亮标识
- 帮助文档：`highlightHelp.md`、`replaceToHelp.md`

### Bug 修复 ✅

- 修复替换净化菜单 `checkable=true` 导致勾选状态双重切换（最终移除 checkable）
- 修复 `<span style="...">` 不被 Android Html.fromHtml 解析的问题
- 修复 `<usehtml>` 标记未包裹导致 HTML 标签被当纯文本显示
- 修复 FontManager 使用 appCtx 无法读取 content URI 权限的问题
- "替换净化"更名为"净化与高亮"

---

## 📊 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `ReplaceRule.kt` | 修改 | 添加 isHighlight 字段 |
| `AppDatabase.kt` | 修改 | 数据库版本 96→97，AutoMigration |
| `AppDatabase/97.json` | 新建 | Room schema 导出 |
| `CssStyleParser.kt` | 新建 | HTML 样式解析器 |
| `FontManager.kt` | 新建 | 字体管理器 |
| `TextHtmlColumn.kt` | 修改 | 添加字体/加粗/斜体/下划线字段 |
| `TextChapterLayout.kt` | 修改 | 添加 span 提取方法 |
| `ContentProcessor.kt` | 修改 | 高亮处理逻辑 + usehtml 包裹 |
| `ReadBookActivity.kt` | 修改 | 替换净化状态切换逻辑 |
| `activity_replace_edit.xml` | 修改 | 高亮模式 UI |
| `item_replace_rule.xml` | 修改 | 高亮标识图标 |
| `ReplaceEditActivity.kt` | 修改 | 工具栏交互逻辑 |
| `ReplaceRuleAdapter.kt` | 修改 | 列表高亮标识 |
| `ic_highlight.xml` | 新建 | 高亮标识 drawable |
| `highlightHelp.md` | 新建 | 高亮模式帮助文档 |
| `replaceToHelp.md` | 新建 | 替换为语法帮助文档 |
| `strings.xml` (values/values-zh) | 修改 | 新增字符串资源 |
| `book_read.xml` / `content_search.xml` / `book_cache.xml` | 修改 | 移除 menu checkable |
| `CHANGELOG.md` | 修改 | 更新日志 |

---

## 📋 待办：CSS 扩展支持

以下 CSS 属性目前不支持，可作为后续扩展方向：

### 块级样式（需要在 TextLine 级别处理）

| 属性 | 说明 | 实现思路 |
|------|------|----------|
| `background-image` | 背景图片 | TextLine 新增 BackgroundInfo，draw 时先绘制背景 |
| `background-size` | 背景缩放 | Bitmap.createScaledBitmap + drawBitmap |
| `background-position` | 背景定位 | 计算偏移量 |
| `background-repeat` | 背景平铺 | Canvas.drawBitmap 循环绘制 |）
