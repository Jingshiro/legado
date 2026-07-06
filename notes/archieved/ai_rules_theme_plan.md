# AI 接入：规则与主题配色管理 开发计划

> 创建时间：2026-07-06
> 状态：📋 规划中

---

## 背景与目标

在现有 AI 工具体系基础上，扩展以下两类能力：

1. **高亮与净化规则管理**：AI 可以查询、新建、修改替换/高亮规则，需知晓所有参数格式（包括高亮规则新增的 isHighlight 字段及 HTML 标签+捕获组格式）。
2. **主题配色管理**：AI 可以列出已有主题、新建或修改主题配色，需知晓 ThemeConfig.Config 的完整字段格式。

覆盖两条接入路径：
- **Web 端 Agent**（reading-skill-master SKILL.md）：调用 HTTP API。
- **App 内置 AI 助手**：通过 ToolRouter + AiToolDef 调用本地数据库。

---

## 现状分析

### 替换/高亮规则（现状）

**App 内 AI 工具**（ToolRouter.kt）：
- get_replace_rules 已实现，但返回字段**缺少** isHighlight、group、scopeTitle、scopeContent、excludeScope
- save_replace_rule 已实现，但输入参数**缺少**高亮相关字段，工具描述没有 replacement 格式说明
- delete_replace_rule 已实现，无需改动

**Web HTTP API**（HttpServer.kt + ReplaceRuleController）：已完整实现，字段完整

**缺口**：Web 端 reading-skill 文档没有记录完整字段（缺少高亮扩展字段）；App 内工具描述缺少 replacement HTML 格式说明。

### 主题配色（现状）

**App 内 AI 工具**：❌ 完全未实现
**Web HTTP API**：❌ 完全未实现

---

## 数据结构参考

### ReplaceRule 完整字段（含高亮扩展）

`
字段名             类型      默认值   说明
id                Long      系统生成  唯一 ID（修改时必填，新建时留空）
name              String    必填     规则名称
group             String?   null     分组名
pattern           String    必填     匹配模式（普通字符串或正则表达式）
replacement       String    必填     替换内容（空字符串=""表示删除匹配内容）
isRegex           Boolean   false    是否使用正则表达式
scope             String?   ""       作用范围（书源URL，空=全部书籍）
scopeTitle        Boolean   false    是否作用于章节标题
scopeContent      Boolean   true     是否作用于正文
excludeScope      String?   null     排除范围（书源URL，逗号分隔）
isEnabled         Boolean   true     是否启用
order             Int       0        执行顺序，数字越小越先执行
isHighlight       Boolean   false    是否为高亮规则（用 HTML 渲染替换内容）
`

**高亮规则 replacement 格式（isHighlight=true 时）**：
- 捕获组引用：（整个匹配）、（第1个括号）、（第N个括号）
- 支持 HTML 标签：
  - <b>/<strong>：加粗
  - <i>/<em>：斜体
  - <u>：下划线
  - <font color="#D32F2F">：颜色（必须用16进制色值）
  - <big>/<small>：放大/缩小字号
  - <font face="楷体">：字体
- 示例：<b><font color="#D32F2F"></font></b> 将匹配内容加粗并染红色
- 示例：正则 "([^"]+)" + replacement "<i></i>" 将引号内文字变斜体

### ThemeConfig.Config 完整字段

`
字段名               类型      默认值       说明
themeName           String    必填        主题名称（同名则覆盖）
isNightTheme        Boolean   false       true=夜间主题，false=日间主题
primaryColor        String    必填        主色（16进制，如 "#607D8B"）
accentColor         String    必填        强调色（如 "#BF360C"）
backgroundColor     String    必填        背景色（夜间模式必须为深色）
bottomBackground    String    必填        底部栏背景色
cardBackground      String?   "#F3EDF7"  卡片背景色
cardBackgroundAlpha Int       100        卡片透明度 0-100
transparentNavBar   Boolean   false       是否透明导航栏
backgroundImgPath   String?   null        背景图路径（null=纯色；http=网络图；本地文件路径）
backgroundImgBlur   Int       0          背景图模糊强度 0-25（0=不模糊）
`

---

## 开发任务

### Task 1：修复 get_replace_rules 返回字段（App）

文件：ToolRouter.kt - getReplaceRules()
改动：在返回 map 中补充 isHighlight、group、scopeTitle、scopeContent、excludeScope 字段
工作量：极小（5行）

---

### Task 2：完善 save_replace_rule 工具描述和参数（App）

文件：AiToolDef.kt + ToolRouter.kt
改动：
1. AiToolDef.kt 中的 description 补充高亮规则格式说明；itemProperties 新增 group、scopeTitle、scopeContent、excludeScope、isHighlight 参数
2. ToolRouter.kt batchSaveReplaceRule() 补充对应字段的读取和写入
工作量：小

---

### Task 3：新增 Web API 主题管理端点

新建文件：io.legado.app.api.controller.ThemeController.kt

新增端点：
| 方法 | 路径 | 功能 |
|------|------|------|
| GET | /getThemeConfigs | 返回全部主题列表 |
| POST | /saveThemeConfig | 新建或覆盖主题（body: ThemeConfig.Config JSON） |
| POST | /deleteThemeConfig | 删除主题（body: {"themeName":"..."} ） |
| POST | /applyThemeConfig | 应用主题并触发 UI 重建（body: {"themeName":"..."} ） |

HttpServer.kt 改动：GET 和 POST 路由各增加 2-3 行分发
工作量：中

---

### Task 4：新增 App 内 AI 工具 — 主题管理

文件：AiToolDef.kt + ToolRouter.kt

新增工具：
| 工具名 | 类型 | 说明 |
|--------|------|------|
| get_theme_configs | 只读 | 获取主题列表 |
| save_theme_config | 批量确认 | 新建或覆盖主题配色 |
| delete_theme_config | 批量确认（高风险） | 删除主题 |
| apply_theme_config | 批量确认 | 应用主题，触发 UI 重建 |

工作量：中

---

### Task 5：更新 reading-skill-master 文档

文件：
- d:\myprojects\reading-skill-master\SKILL.md：补充规则格式说明和主题 API 文档
- d:\myprojects\reading-skill-master\references\legado-api.md：补充完整数据模型和端点详情
工作量：小

---

## 改动文件汇总

| 文件 | 类型 | 说明 |
|------|------|------|
| ToolRouter.kt | 修改 | 补全字段返回；补全输入参数；新增 4 个主题管理函数 |
| AiToolDef.kt | 修改 | 完善 save_replace_rule；新增 4 个主题工具定义 |
| ThemeController.kt | 新建 | Web API 主题管理控制器 |
| HttpServer.kt | 修改 | 新增主题管理 GET/POST 路由（4条） |
| SKILL.md（reading-skill-master） | 修改 | 补充规则格式说明和主题 API 文档 |
| legado-api.md（references） | 修改 | 补充数据模型和端点详情 |

---

## 执行顺序

1. Task 1 - 修复 getReplaceRules 返回字段（5分钟）
2. Task 2 - 完善 save_replace_rule 描述和参数（15分钟）
3. Task 3 - 新建 ThemeController + HttpServer 路由（30分钟）
4. Task 4 - 新增 AI 主题工具（30分钟）
5. Task 5 - 更新 reading-skill-master 文档（15分钟）

---

## 待确认问题

1. apply_theme_config 的 Context 问题：ThemeConfig.applyDayNight(context) 需要 Context 参数，ToolRouter 当前只能拿 appCtx（Application Context）。用 appCtx 调用写 SharedPreferences 没问题，但如果 applyTheme 内部某处需要 Activity，则需要通过 postEvent(EventBus.RECREATE) 通知。建议：在 ThemeController 中直接用 appCtx，发 EventBus 重建。

2. 主题配色修改后是否立即生效：save_theme_config 只存储配置，不主动应用；需要用户或 AI 再调一次 apply_theme_config 才生效。这个两步流程是否符合预期？
