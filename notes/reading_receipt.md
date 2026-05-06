# 藏书票开发计划

## 一、 数据库与实体类修改

1. **`Book` 实体类 (`io.legado.app.data.entities.Book.kt`)**:
   - 新增字段: `addTime: Long = System.currentTimeMillis()` （若已有代表添加时间的字段可用，否则新增；兼容旧数据设默认值为当前时间或0）。
   - 新增字段: `preReadNote: String? = null`。
   - 新增字段: `finishTime: Long = 0L`。
   - 新增字段: `postReadNote: String? = null`。
   - 新增字段: `bookRating: Float = 0f`。

2. **数据库升级 (`io.legado.app.data.AppDatabase.kt`)**:
   - 将 `@Database(version = 94, ...)` 升级为 95。
   - 在 `autoMigrations` 数组中添加 `AutoMigration(from = 94, to = 95)` 以支持新字段的自动合并。

3. **笔记统计**:
   - 通过调用 `appDb.bookmarkDao()` 中的方法（或类似机制）根据 `bookUrl` 动态统计该书籍关联的笔记（Bookmark/Thought）数量，无需增加实体类字段。

## 二、 页面管理与状态流转修改 (排版与翻页引擎)

为了使藏书票完美融入 Legado 现有的翻页动画体系（如仿真、平移等），它需要作为一种特殊形态的 `TextPage` 进行处理。

1. **卷首/卷尾判定 (`ChapterProvider` / `TextPageFactory` 等提供页面的地方)**:
   - **卷首票**: 当当前处于第一章且为第一页时，拦截向前的翻页操作。将原本的 Toast “已经是第一页”行为替换为：返回一个特殊的 `TextPage`（可扩充字段标记如 `isBookplateStart = true` 或赋予特殊的 index）。
   - **卷尾票**: 在原有的 `onReadFinish()`（完读监听）逻辑执行完成后，当用户继续向后翻页时，返回标记为 `isBookplateEnd = true` 的特殊 `TextPage`。注意：此虚拟页不应计入书籍总页数或影响正常阅读进度计算。

2. **状态更新与持久化**:
   - **触发完读时机**: 第一次到达卷尾票时，检查当前 `Book.finishTime`。如果未设置（`<= 0`），则自动获取当前系统时间并更新 `Book` 实体写入数据库，正式标记为已完读状态。

## 三、 UI 渲染与交互 (ReadView 及 Canvas 绘制)

为了还原参考图的小票质感且无缝衔接阅读界面，采用 Canvas 直接绘制。

1. **独立渲染 (`BookplateDrawer`)**:
   - 在 `ReadView` (或负责绘制页面内容的 `PageDelegate`) 的 `onDraw` 中，判定当前是否为藏书票特殊页。
   - 如果是，则跳过常规文本排版绘制，调用封装好的 `BookplateDrawer.draw(canvas, book, type, bounds)`。
   - 绘制细节：利用 `Paint` 和 `Path` 绘制锯齿状边缘边框、等宽字体(`Typeface.MONOSPACE`)排版、以及虚线形式的分割线，确保位于页面中间且占据 3/4 左右空间。

2. **事件拦截与交互映射 (`ReadView.onTouchEvent`)**:
   - 在 `ReadView` 的点击事件处理逻辑中（如 `onSingleTapUp`），判断当前展示的页面是否为藏书票。
   - 如果是，根据用户的点击坐标 `(X, Y)` 与绘制时记录的各个热区（`RectF`）进行碰撞检测：
     - **读前记录区域**: 点击后唤起 Legado 的文本编辑 Dialog（例如复用 `ContentEditDialog`），编辑后保存至 `Book.preReadNote` 并通知界面重绘。
     - **读后记录 / 评分区域**:
       - 若当前为**卷首票（未完读）**：拦截点击，并触发底部飘字提示 `Toast("请完读后再记录")`。
       - 若当前为**卷尾票或回顾状态（已完读）**：允许点击唤起编辑 Dialog，或处理星星评分区域的点击逻辑，修改 `Book.bookRating` / `postReadNote` 并持久化到数据库。

## 四、 开发步骤分解

1. **第一阶段**：更新 `Book` 实体、修改 `AppDatabase` 触发 Room 迁移（版本 94 -> 95）。
2. **第二阶段**：梳理翻页逻辑。在 `ChapterProvider` 及相关上下文中定义并注入“藏书票虚拟页”，使其支持卷首向前翻页和卷尾向后翻页的触发。
3. **第三阶段**：建立 `BookplateDrawer`，在 `ReadView` 的绘制链条中劫持虚拟页的渲染，使用纯 Canvas 完成小票 UI 及文本内容的绘制与排版。
4. **第四阶段**：在 `ReadView` 处理触摸事件拦截，计算坐标映射，完成编辑交互与打分反馈逻辑，测试卷首拦截及已读状态自动刷新。
