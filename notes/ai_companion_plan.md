# AI 阅读伴侣功能优化开发计划

## 一、 功能需求拆解与目标

本次优化的主要目标集中在 **AI 阅读伴侣** 模块，旨在改善交互体验及完善记忆管理功能：

### 1. 交互体验优化
- **发送按钮状态切换**：在 AI 回复生成期间，需将输入框右侧的“发送按钮”替换为“生成中/停止”按钮（表现为一个正方形图标）。此状态需通过监听 ViewModel 中的生成状态来动态更新 UI，防止重复发送。
- **保存反馈**：用户点击右上角的“保存/归纳记忆”按钮 (`menu_ai_summarize`) 时，成功保存后需在界面底部弹出 Toast 提示（“本次交流已保存”）。

### 2. 记忆管理功能（新增查看/删除功能）
- **入口调整**：在 `AiConfigDialog` (设置弹窗界面) 的“聊天记忆长度（参考）”信息下方，新增一个入口，用于打开“查看记忆”的弹窗或新界面。
- **功能细节**：
  - **数据结构调整**：目前的 `AiConfig.memory` 以单一字符串的形式保存。为了实现分条查看与删除，需重构底层的记忆存储方式（可引入 JSON 数组存储，包含：时间戳、章节范围、前 15 字预览、完整内容）。
  - **展示效果**：列表项需显示每条记录的**章节范围**（如：`1-2章` 或 `3章`）以及**内容节选（前 15 字）**。
  - **删除功能**：支持针对单条记录进行独立删除，同时保留一键清空全部记录的功能。

---

## 二、 涉及修改的文件及类结构

### 1. 交互体验优化（UI 及状态监听）
- **`app/src/main/java/io/legado/app/ui/book/read/ai/AiChatViewModel.kt`**
  - **修改点**：
    - 新增 `isGeneratingLiveData: MutableLiveData<Boolean>`，用于对外暴露当前的生成状态。
    - 在调用 AI 接口的执行流前后，更新 `isGeneratingLiveData` 的布尔值。
    - 修改 `summarizeAndMemory()` 函数，在成功写入记忆后，向 UI 层抛出一个单次事件（或利用回调/LiveData）以触发 Toast。
- **`app/src/main/java/io/legado/app/ui/book/read/ai/AiChatActivity.kt`**
  - **修改点**：
    - 在 `observeData()` 中监听 `isGeneratingLiveData`。
    - 当为 `true`（生成中）时，将 `binding.btnSend.setImageResource` 修改为正方形停止图标（如 `@drawable/ic_stop_black_24dp`），并修改/禁用点击事件或将其设为中断生成的逻辑。
    - 当为 `false` 时，恢复为发送图标 `@drawable/ic_send`。
    - 在右上角 `menu_ai_summarize` 触发执行后，弹出提示 `toastOnUi("本次交流已保存")`。

### 2. 记忆管理与存储架构重构
- **`app/src/main/java/io/legado/app/help/config/AiConfig.kt`**
  - **修改点**：
    - 维持原有接口，将内部的存储逻辑升级为解析/序列化结构化数据（如 `List<AiMemoryItem>`）。
    - *建议数据模型*：
      ```kotlin
      data class AiMemoryItem(
          val id: Long = System.currentTimeMillis(),
          val chapterRange: String, // 如 "1-3"
          val content: String // 完整的记忆内容
      ) {
          val preview: String get() = content.take(15) + if (content.length > 15) "..." else ""
      }
      ```
- **`app/src/main/res/layout/dialog_ai_config.xml`**
  - **修改点**：
    - 在 `tv_memory_length` 所在行下方或右侧增加一个 `Button`（例如 “查看记忆列表” `btn_view_memory`）。
- **`app/src/main/java/io/legado/app/ui/book/read/config/AiConfigDialog.kt`**
  - **修改点**：
    - 绑定新的 `btn_view_memory` 点击事件，打开新的弹窗 `AiMemoryDialog`。

### 3. 新增文件：记忆列表页面
- **`app/src/main/res/layout/dialog_ai_memory.xml`**
  - 弹窗布局：包含一个 `RecyclerView` 以及一个底部的“一键清空”按钮。
- **`app/src/main/res/layout/item_ai_memory.xml`**
  - 列表项布局：包含“章节标注(TextView)”、“前15字预览(TextView)”以及一个右侧的“删除按钮/图标”。
- **`app/src/main/java/io/legado/app/ui/book/read/config/AiMemoryDialog.kt`** (新增)
  - 继承自 `BaseDialogFragment`。
  - 负责加载并显示 `AiConfig` 中的结构化记忆列表。
  - 实现单条删除及清空功能，并同步保存回本地存储。
- **`app/src/main/java/io/legado/app/ui/book/read/config/AiMemoryAdapter.kt`** (新增)
  - 列表适配器，绑定 UI 数据。

---

## 三、 开发步骤与排期

1. **第一阶段：状态交互与轻量反馈优化**
   - 增加 `isGeneratingLiveData` 的支持。
   - 更换生成时的 `btn_send` UI 图标为 `ic_stop_black_24dp` 并在点击后提示用户。
   - 处理“归纳记忆”按钮的 Toast 飘字 `toastOnUi("本次交流已保存")`。
2. **第二阶段：核心存储的兼容重构**
   - 编写 `AiMemoryItem` 数据类。
   - 修改 `AiChatViewModel` 的归纳逻辑，将单纯的拼接改为存入 JSON 数组（带上当前章节的起止标识）。
   - 实现向前兼容（旧版纯文本记忆转换为一条单独记录处理）。
3. **第三阶段：查看与管理 UI 搭建**
   - 编写 `AiMemoryDialog` 和 RecyclerView Adapter。
   - 完善单条删除和清空逻辑，确保与 `AiConfig` 同步刷新。
4. **第四阶段：集成测试**
   - 在主界面校验 AI 会话中断、重发等交互。
   - 验证记忆拼接及 Token/字数计算是否符合预期。

---

## 四、 本次更新总结

本次对 AI 阅读伴侣功能进行了以下优化和完善：
1. **交互体验优化**：在用户发送对话后，成功增加了防止重复点击和生成中的状态提示（输入框右侧图标切换），并在记忆归纳完成后添加了直观的 Toast 提示。
2. **记忆数据结构重构**：将原本只支持单一字符串存储的记忆格式升级为列表（`List<AiMemoryItem>`）结构，并自动兼容处理了旧版本的纯文本记录，记录包含了时间戳、章节范围、截断预览以及完整内容。
3. **记忆查看与管理面板**：在系统设置面板（`AiConfigDialog`）中新增了“查看记忆列表”的入口，构建了全新的弹窗（`AiMemoryDialog`）通过 RecyclerView 将历史记忆分条列出。用户现在可以随时独立查阅某段对话，进行单条记忆删除以及一键清空全部记忆的操作，UI 交互自然、完整。