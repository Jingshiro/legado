# 待办事项清单

> 项目开发工作索引。更新时间：2026-06-05

---

## 📋 活跃开发文档

*暂无活跃文档（当前阶段所有开发任务已完成）*

---

## 🔴 P0 — AI 工具（管理闭环）

> 优先级最高。AI 助手的核心能力补全，让 AI 能管理书源和替换规则。

| 工具 | 功能 | 风险 | 状态 |
|------|------|------|------|
| `get_replace_rules` | 获取文本替换规则列表 | 低 | ✅ |
| `save_replace_rule` | 创建/修改替换规则 | 中 | ✅ |
| `delete_replace_rule` | 删除替换规则 | 高 | ✅ |
| ~~`save_book_source`~~ | ~~导入书源（JSON/URL）~~ | ~~中~~ | 🚫 终止 |
| ~~`manage_webdav`~~ | ~~WebDAV 备份管理~~ | ~~高~~ | 🚫 终止 |

> 参考：`archieved/legado_new_tools_guide`

---

## 🟡 P1 — AI 工具（知识闭环）

> 让 AI 能读取和导出读书想法、阅读记录。

| 工具 | 功能 | 风险 | 状态 |
|------|------|------|------|
| `get_thoughts` | 获取读书想法列表 | 低 | ✅ |
| `export_to_obsidian` | 想法导出到 Obsidian（AI 工具入口） | 低 | ✅ |
| `get_detailed_reading_record` | 获取详细阅读记录 | 低 | ✅ |

> 参考：`archieved/legado_new_tools_guide`

---

## 🟢 P2 — Material Design 迁移

> UI 已经大部分 OK，核心的 Phase 1-3 布局和控件迁移已完成，高级样式微调暂缓。

| 阶段 | 内容 | 工作量 | 状态 |
|------|------|--------|------|
| ~~Phase 1~~ | ~~主题切换~~ | ~~1-2天~~ | ✅ |
| ~~Phase 2~~ | ~~自定义 View 迁移（10个 AppCompat 基类）~~ | ~~2-3天~~ | ✅ |
| ~~Phase 3a~~ | ~~自定义 TextInputLayout 评估（保留，动态着色需要）~~ | | ✅ |
| ~~Phase 3b~~ | ~~Toolbar → MaterialToolbar（30处）~~ | ~~1天~~ | ✅ |
| ~~Phase 3c~~ | ~~AppCompatImageView → ImageView（77处）~~ | ~~1天~~ | ✅ |
| ~~Phase 3d~~ | ~~CardView → MaterialCardView~~ | | ✅ |
| ~~Phase 3e~~ | ~~AppCompatSpinner 自动升级确认~~ | | ✅ |
| ~~Phase 3f~~ | ~~AppCompatTextView/Button 散落控件替换~~ | | ✅ |
| Phase 4 | 样式细节调优（色彩/ripple/Shape） | 3-5天 | ⏸ 暂缓 |
| Phase 5 | 进阶优化（Material3/Dynamic Color/Motion） | 按需 | ⏸ 暂缓 |

> 参考：`archieved/material_design_migration_plan`

---

## ⚪ 待确认

| 功能 | 说明 | 状态 |
|------|------|------|
| 阅读记录排除订阅源 | `archieved/implementation_plan_read_record` 中 ReadRssActivity/VideoPlayerActivity 排除 RSS 源详细记录 | ✅ |

---

## 📁 文档索引

### 已归档（已完成）

| 文档 | 路径 | 说明 |
|------|------|------|
| 云端备份管理 | `archieved/cloud_backup_management_plan` | ✅ 已完成 |
| 阅读记录修改 | `archieved/implementation_plan_read_record` | ✅ 已完成 |
| AI 助手开发记录 | `archieved/project_ai_assistant` | ✅ 已完成 |
| AI 助手优化 | `archieved/ai_assistant_optimization_plan` | ✅ 已完成 |
| 想法导出 Obsidian | `archieved/thought_obsidian_export_plan` | ✅ 已完成 |
| 藏书票 | `archieved/reading_receipt` | ✅ 已完成 |
| "我的"页面 MD3 | `archieved/my_page_md3_progress` | ✅ 已完成 |
| 高亮规则开发计划 | `archieved/highlight_rule_plan` | ✅ 已完成 |
| Legado 优化与增强计划 | `archieved/implementation_plan` | ✅ 已完成 |
| 规则与主题配色管理计划 | `archieved/ai_rules_theme_plan` | ✅ 已完成 |
| AI 工具实现指南 | `archieved/legado_new_tools_guide` | ✅ 已完成 |
| Material Design 迁移计划 | `archieved/material_design_migration_plan` | ✅ 已完成 |

