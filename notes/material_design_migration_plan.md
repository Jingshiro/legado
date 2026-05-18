# Material Design 全面迁移计划

## 背景

当前 app 基础主题为 `Theme.AppCompat.DayNight.NoActionBar`，部分界面已使用 Material 组件（MaterialCardView、FAB、MaterialButton 等），但未全面迁移。此前 AI 助手界面因混用 MaterialCardView 与 AppCompat 主题导致崩溃，说明需要系统性地完成迁移。

## 现状概览

| 项目 | 数量 |
|------|------|
| Activity | 30 |
| Fragment | 102 |
| 布局 XML | 229 |
| 自定义 View（继承 AppCompat） | 16 |
| Material 库版本 | 1.13.0（无需升级） |

## 迁移策略：分阶段推进

采用渐进式迁移，每阶段独立可验证，避免大规模改动引入难以排查的回归。

---

### Phase 1：主题切换（1-2 天）

**目标**：将基础主题从 AppCompat 切换到 MaterialComponents，确保编译通过、无崩溃。

**改动范围**：
- `styles.xml` / `values-night/styles.xml`
  - `Base.AppTheme` parent: `Theme.AppCompat.DayNight.NoActionBar` → `Theme.MaterialComponents.DayNight.NoActionBar`
  - `AppTheme.AppBarOverlay.Light` parent: `ThemeOverlay.AppCompat.Light` → `ThemeOverlay.MaterialComponents.Light`
  - `AppTheme.AppBarOverlay.Dark` parent: `ThemeOverlay.AppCompat.Dark` → `ThemeOverlay.MaterialComponents.Dark`
  - `AppTheme.PopupOverlay` parent: 同上
  - `Style.Toolbar.NoPadding` parent: `Base.Widget.AppCompat.Toolbar` → `Base.Widget.MaterialComponents.Toolbar`
  - `Style.PopupMenu` parent: `Widget.AppCompat.PopupMenu` → `Widget.MaterialComponents.PopupMenu`
  - `ToolbarTitle` parent: `TextAppearance.Widget.AppCompat.Toolbar.Title` → `TextAppearance.MaterialComponents.Headline6`
  - `Activity.Permission` parent: 同步切换
  - 合并 `AppTheme.Material`（已有的 AI 助手专用主题）到统一主题，删除冗余定义
- `AndroidManifest.xml`
  - 移除 AiChatActivity 的 `android:theme="@style/AppTheme.Material"`（不再需要单独主题）

**验证**：全量构建 + 启动主要界面确认无崩溃。

**风险**：主题切换后部分组件样式可能略有变化（颜色、间距），属于正常现象，在后续阶段逐步调整。

---

### Phase 2：自定义 View 迁移（2-3 天）

**目标**：将 16 个继承 AppCompat 的自定义 View 切换到标准/ Material 基类。

| 自定义 View | 当前基类 | 迁移目标 |
|------------|---------|---------|
| ThemeCheckBox | AppCompatCheckBox | CheckBox / MaterialCheckBox |
| ThemeEditText | AppCompatEditText | EditText / TextInputEditText |
| ThemeSeekBar | AppCompatSeekBar | SeekBar |
| BadgeView | AppCompatTextView | TextView |
| AccentBgTextView | AppCompatTextView | TextView |
| BatteryView | AppCompatTextView | TextView |
| AutoCompleteTextView | AppCompatAutoCompleteTextView | AutoCompleteTextView |
| ScrollMultiAutoCompleteTextView | AppCompatMultiAutoCompleteTextView | MultiAutoCompleteTextView |
| PhotoView | AppCompatImageView | ImageView |
| ImageButton | AppCompatImageView | ImageView |
| FilletImageView | AppCompatImageView | ImageView |
| CoverImageView | AppCompatImageView | ImageView |
| CircleImageView | AppCompatImageView | ImageView |
| DynamicFrameLayout | 内部使用 AppCompat 控件 | 替换为标准控件 |
| TintHelper | 引用 AppCompatEditText | 更新引用类型 |

**注意**：AppCompat 基类在 MaterialComponents 主题下仍然可用，此阶段是代码规范化而非功能性修复。可按优先级分批处理。

**验证**：编译通过 + 自定义 View 相关界面逐一检查。

---

### Phase 3：布局 XML 组件替换（5-7 天，工作量最大）

**目标**：将布局中的 AppCompat 控件替换为 Material 等价物。

#### 3a. TextInputLayout 替换（148 处）

当前使用自定义 `io.legado.app.ui.widget.text.TextInputLayout`，需评估：
- 阅读该自定义类源码，理解其与 Material TextInputLayout 的差异
- 如果功能兼容，直接在布局中替换为 `com.google.android.material.textfield.TextInputLayout`
- 如果有自定义逻辑，考虑让自定义类继承 Material TextInputLayout

#### 3b. Toolbar → MaterialToolbar（31 处）

- `androidx.appcompat.widget.Toolbar` → `com.google.android.material.appbar.MaterialToolbar`
- MaterialToolbar 原生支持 elevation、主题色适配，无需额外样式

#### 3c. AppCompatImageView → ImageView（73 处）

- AppCompatImageView 在 MaterialComponents 主题下功能等同于 ImageView
- 批量替换为标准 `ImageView`，自定义 View 保持不变（Phase 2 已处理）

#### 3d. CardView → MaterialCardView（~26 处）

- `androidx.cardview.widget.CardView` → `com.google.android.material.card.MaterialCardView`
- 补充 stroke、ripple 等 Material 属性

#### 3e. AppCompatSpinner → Material 自动升级（11 处）

- MaterialComponents 主题下 AppCompatSpinner 会自动获得 Material 样式
- 仅需确认样式正确，无需手动替换

#### 3f. 其他零散控件（4 处）

- AppCompatTextView (3处)、AppCompatButton (1处) → 替换为标准控件

**验证**：每完成一个子批次，构建 + 截图对比。

---

### Phase 4：样式细节调优（3-5 天）

**目标**：利用 Material Design 的设计规范优化视觉表现。

- 统一色彩系统：使用 Material color roles（primary, onPrimary, surface, onSurface 等）
- 统一组件样式：按钮、卡片、对话框的圆角、阴影、间距
- 适配 Material ripple 触摸反馈
- 对话框样式统一（当前混用自定义 dialog 和 MaterialAlertDialogBuilder）
- 考虑引入 Shape theming（圆角统一管理）

**验证**：UI 走查 + 深色模式验证。

---

### Phase 5：进阶优化（可选，按需）

- 从 MaterialComponents 迁移到 Material3（`Theme.Material3.*`）
- 引入 Dynamic Color（Android 12+）
- 使用 Material Motion 转场动画
- BottomSheet 替代部分弹窗

---

## 工作量预估

| 阶段 | 工作量 | 说明 |
|------|--------|------|
| Phase 1 主题切换 | 1-2 天 | 改动集中，风险可控 |
| Phase 2 自定义 View | 2-3 天 | 逐个替换，需回归测试 |
| Phase 3 布局替换 | 5-7 天 | 229 个布局，工作量最大 |
| Phase 4 样式调优 | 3-5 天 | 视觉层面，需设计对齐 |
| Phase 5 进阶优化 | 按需 | 可后续版本迭代 |
| **合计** | **11-17 天** | 不含 Phase 5 |

## 建议

1. **从 Phase 1 开始**，主题切换是后续所有工作的前提
2. Phase 3 建议按功能模块分批（如：书源编辑、阅读界面、设置页面），每批独立提交
3. 每个 Phase 完成后做一轮全量回归测试
4. Phase 4 的视觉调优可以和设计师同步进行，不必阻塞功能开发
