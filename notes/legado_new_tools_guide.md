# Legado AI Tools — 新增工具实现指南 v1.0

> **文档用途**：本文档供 AI agent 直接阅读并按指引完成工具实现。  
> **不含** Kotlin 源代码，只描述工具定义、参数规范、数据来源和实现要点。  
> **阅读前置**：已有工具的实现模式已建立，新工具必须保持一致的命名、确认、分页、精简返回等约定。

---

## 全局约定（必读，所有新工具均适用）

### 命名规范
- 函数名：`snake_case`，动词开头（get / search / save / delete / mark / set / manage / export）
- 参数名：`camelCase`，与 legado API 请求字段保持一致

### 返回格式约定
- 所有返回值均为 **精简 JSON**，剔除大型字段（如封面 base64、完整书源 JSON 字符串等）
- 返回结构统一包含：`{ "success": true, "data": {...} }` 或 `{ "success": false, "error": "原因" }`
- 列表类返回附带 `total` 字段
- 超过 100 条的查询支持 `offset` + `limit` 分页（默认 limit=20）

### 写操作确认分类
| 类型 | 适用场景 | 行为 |
|------|----------|------|
| **批量确认** | 需用户确认的操作（含高风险不可撤销）| 合并弹窗，一次性列出所有待操作项，统一确认 |
| **静默写入** | 低风险、用户主动触发 | 直接执行，无需确认 |

### 已有工具参考
现有工具模式（以 `get_bookshelf` 为例）：
- 只读工具：直接调 HTTP API，清洗返回字段
- 写工具（低风险）：调 API → 静默执行 → 返回结果
- 写工具（高风险）：调用前展示待变更内容 → 等待确认 → 执行

---

## 第一批：核心阅读体验（优先级 P0，建议首先实现）

---

### 工具 1：`get_book_content`

**用途**：获取指定书籍指定章节的正文内容，用于摘要、查询、AI 分析。

#### 参数定义（JSON Schema）

```json
{
  "name": "get_book_content",
  "description": "获取指定书籍某章节的正文内容。内容默认截断为前2000字，可调节。",
  "parameters": {
    "type": "object",
    "properties": {
      "bookUrl": {
        "type": "string",
        "description": "书籍的唯一标识 URL（从 get_bookshelf 获取）"
      },
      "chapterIndex": {
        "type": "integer",
        "description": "章节索引，从 0 开始"
      },
      "maxChars": {
        "type": "integer",
        "description": "返回最大字符数，默认 2000，最大 8000",
        "default": 2000
      }
    },
    "required": ["bookUrl", "chapterIndex"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "bookName": "斗破苍穹",
    "chapterTitle": "第一章 陨落的天才",
    "chapterIndex": 0,
    "contentLength": 3521,
    "content": "（正文前2000字...）",
    "truncated": true
  }
}
```

#### 对应 API

```
GET /getBookContent?url={bookUrl}&index={chapterIndex}
```

- 返回正文为纯文本字符串
- **截断逻辑**：取 content 前 `maxChars` 个字符，若截断则 `truncated=true`
- `contentLength` 填写原始总长度，方便 AI 判断是否需要翻页

#### 实现要点

1. `bookUrl` 需做 URL encode（含特殊字符）
2. `maxChars` 上限建议写死为 8000，防止单次消耗过多 token
3. 若章节未缓存，legado 会实时抓取，响应时间可能较长（建议超时设为 30s）
4. 返回中保留 `chapterTitle`：`/getBookContent` 接口只返回正文纯文本，章节标题需从数据库章节记录（`bookChapterDao.getChapter(bookUrl, index)`）中读取，该记录在调用内容接口前已存在于数据库

---

### 工具 2：`search_online_book`

**用途**：通过书源在线搜书，返回可加入书架的书籍列表。

#### 参数定义（JSON Schema）

```json
{
  "name": "search_online_book",
  "description": "使用 legado 书源在线搜索书籍，返回匹配结果列表。",
  "parameters": {
    "type": "object",
    "properties": {
      "keyword": {
        "type": "string",
        "description": "搜索关键词（书名、作者均可）"
      },
      "limit": {
        "type": "integer",
        "description": "返回结果数量上限，默认 10，最大 30",
        "default": 10
      },
      "timeout": {
        "type": "integer",
        "description": "等待搜索结果的超时秒数，默认 10",
        "default": 10
      }
    },
    "required": ["keyword"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "keyword": "斗破苍穹",
    "total": 15,
    "returned": 10,
    "books": [
      {
        "name": "斗破苍穹",
        "author": "天蚕土豆",
        "bookUrl": "https://...",
        "origin": "书源名称",
        "intro": "简介前100字...",
        "kind": "玄幻",
        "wordCount": "340万字"
      }
    ]
  }
}
```

#### 对应 API

```
WebSocket: ws://{host}/searchBook
发送消息：{ "key": "搜索关键词" }
持续接收 JSON 数组，直到连接关闭
```

#### 实现要点

1. **WebSocket 适配**：工具层需将 WS 流式接收适配为同步返回——建立连接后收集消息，到达 `timeout` 秒或收到足够数量后关闭并返回
2. 消息到达是流式的，每次收到一条或一批；实现时用 buffer 累积，达到 `limit` 条即可提前关闭
3. 返回字段精简：不返回 `coverUrl`（base64 或大图 URL）、完整 `bookSource` 对象
4. `intro` 截断为前 100 字
5. `total` 填写实际收到的总条数（不是书源数），`returned` 为实际返回条数

---

### 工具 3：`save_book_progress`

**用途**：保存当前阅读进度（章节位置），用于多端同步或手动校正进度。

#### 参数定义（JSON Schema）

```json
{
  "name": "save_book_progress",
  "description": "保存指定书籍的阅读进度（章节索引和章节内位置）。",
  "parameters": {
    "type": "object",
    "properties": {
      "bookUrl": {
        "type": "string",
        "description": "书籍唯一标识 URL"
      },
      "durChapterIndex": {
        "type": "integer",
        "description": "当前阅读章节索引（0-based）"
      },
      "durChapterPos": {
        "type": "integer",
        "description": "章节内字符位置，默认 0",
        "default": 0
      },
      "durChapterTitle": {
        "type": "string",
        "description": "章节标题（可选，辅助校验）"
      }
    },
    "required": ["bookUrl", "durChapterIndex"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "bookName": "斗破苍穹",
    "bookUrl": "https://...",
    "durChapterIndex": 100,
    "durChapterPos": 0,
    "savedAt": "2025-01-15T10:30:00"
  }
}
```

#### 对应 API

```
POST /saveBookProgress
Content-Type: application/json
Body: BookProgress 对象
```

#### BookProgress 实体字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `bookUrl` | String | 书籍 URL（主键） |
| `durChapterIndex` | Int | 当前章节索引 |
| `durChapterPos` | Int | 章节内位置 |
| `durChapterTitle` | String? | 章节标题（可选） |
| `syncTime` | Long | 时间戳，实现时填当前时间 |

#### 实现要点

1. 写操作分类：**静默写入**（用户主动调用，视为已确认）
2. 发送前构造完整 `BookProgress` 对象，`syncTime` 用当前毫秒时间戳
3. 返回中回显书名（需先查书架获取），方便 AI 确认操作对象
4. 若 `bookUrl` 不在书架中，返回 `success: false` 并说明原因

---

### 工具 4：`rate_book`

**用途**：给书架中的书籍打分（0-5 分，支持小数）。

#### 参数定义（JSON Schema）

```json
{
  "name": "rate_book",
  "description": "给书架中的书籍打评分，0-5 分（支持 0.5 步进）。",
  "parameters": {
    "type": "object",
    "properties": {
      "bookUrl": {
        "type": "string",
        "description": "书籍唯一标识 URL"
      },
      "rating": {
        "type": "number",
        "description": "评分，0.0 到 5.0 之间",
        "minimum": 0.0,
        "maximum": 5.0
      }
    },
    "required": ["bookUrl", "rating"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "bookName": "斗破苍穹",
    "bookUrl": "https://...",
    "previousRating": 0.0,
    "newRating": 4.5
  }
}
```

#### 对应数据实体

```
Book.bookRating: Float  // 0.0 ~ 5.0
```

**无直接 API**，需通过 `saveBook` 类接口更新整个 Book 对象。

#### 实现要点

1. 操作流程：
   - 步骤 1：调 `/getBookshelf` 查找目标书，获取完整 Book 对象
   - 步骤 2：修改 `bookRating` 字段
   - 步骤 3：调 `POST /saveBook`（或等效接口）提交整个 Book 对象
2. 写操作分类：**静默写入**（评分是低风险操作）
3. 返回中回显修改前后的评分（`previousRating`）
4. `rating` 值做校验：若超出 0-5 范围，返回 error 不执行
5. 建议支持按书名模糊匹配作为 `bookUrl` 的替代方案（通过预查）

---

### 工具 5：`mark_book_status`

**用途**：标记书籍阅读状态（未读完、读完、N 刷）。

#### 参数定义（JSON Schema）

```json
{
  "name": "mark_book_status",
  "description": "标记书籍的阅读状态（首次读完、二刷、三刷等）。",
  "parameters": {
    "type": "object",
    "properties": {
      "bookUrl": {
        "type": "string",
        "description": "书籍唯一标识 URL"
      },
      "status": {
        "type": "integer",
        "description": "阅读状态：0=未读完, 1=首次读完, 2=二刷中, 3=二刷完, 4=三刷中, 5=三刷完（以此类推）",
        "enum": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
      }
    },
    "required": ["bookUrl", "status"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "bookName": "斗破苍穹",
    "bookUrl": "https://...",
    "previousStatus": 0,
    "newStatus": 1,
    "statusLabel": "首次读完"
  }
}
```

#### 对应数据实体

```
Book.readIteration: Int
  0 = 未读完
  1 = 读完（首刷）
  2 = 二刷中
  3 = 二刷完
  4 = 三刷中
  5 = 三刷完
  ...（偶数=刷完，奇数=刷中，0特指未读完）
```

#### 实现要点

1. 写操作分类：**批量确认**（无论操作单本还是多本，均需弹窗确认；若 AI 在同一轮对话中需要修改多本书的状态，应合并为一次弹窗展示所有待变更项，统一确认，而非每本书分别弹一次）
2. 操作流程同 `rate_book`：先查完整 Book 对象 → 修改 `readIteration` → 保存
3. 返回中附 `statusLabel`（中文描述），方便 AI 向用户回报
4. `statusLabel` 映射表：`{0:"未读完", 1:"首次读完", 2:"二刷中", 3:"二刷完", ...}`（按规律生成）
5. 确认弹窗文案示例（单本）：「确认将《斗破苍穹》标记为"首次读完"？」
6. 确认弹窗文案示例（多本）：`「确认对以下书籍进行状态标记？· 《斗破苍穹》→ 首次读完 · 《三体》→ 二刷完」`

---

### 工具 6：`set_book_note`

**用途**：让 AI 针对书籍的指定章节写阅读感想，以 `BookThought`（想法）形式写入——等同于用户长按原文片段后写想法。支持同时为多个章节写感想。所有由 AI 写入的感想末尾强制追加标注 `——由AI助手生成`。

> ⚠️ **注意**：此工具已调整功能定位。原"阅读前/完读感想（preReadNote/postReadNote）"功能因对应 UI 已下线，改由此工具以 BookThought 形式记录感想。

#### 参数定义（JSON Schema）

```json
{
  "name": "set_book_note",
  "description": "为书籍指定章节写阅读感想，以 BookThought（想法）形式写入。支持同时为多个章节写感想。AI 写入的所有内容末尾会自动追加「——由AI助手生成」标注。调用前请先用 get_book_content 获取章节内容，再针对内容写感想。",
  "parameters": {
    "type": "object",
    "properties": {
      "bookUrl": {
        "type": "string",
        "description": "书籍唯一标识 URL（从 get_bookshelf 获取）"
      },
      "notes": {
        "type": "array",
        "description": "感想列表，每条对应一个章节。支持一次写多个章节。",
        "items": {
          "type": "object",
          "properties": {
            "chapterIndex": {
              "type": "integer",
              "description": "章节索引，从 0 开始（与 get_book_content 的 chapterIndex 一致）"
            },
            "selectedText": {
              "type": "string",
              "description": "本条感想关联的原文片段（建议取章节内的关键段落，最长 500 字），留空则使用章节标题作为关联文本"
            },
            "thought": {
              "type": "string",
              "description": "AI 的阅读感想内容（不需要手动追加标注，系统会自动加）"
            }
          },
          "required": ["chapterIndex", "thought"]
        }
      }
    },
    "required": ["bookUrl", "notes"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "bookName": "斗破苍穹",
    "total": 2,
    "written": 2,
    "failed": 0,
    "thoughts": [
      {
        "chapterIndex": 5,
        "chapterName": "第5章 萧炎的觉醒",
        "thoughtId": 1747395012345,
        "selectedText": "萧炎猛然起身……",
        "thought": "这一章节是萧炎性格转变的关键……——由AI助手生成"
      }
    ]
  }
}
```

#### 对应数据实体

```kotlin
// BookThought 完整字段
data class BookThought(
    val id: Long = 0,            // 自增主键
    val bookName: String,        // 书名
    val bookAuthor: String,      // 作者
    val chapterIndex: Int,       // 章节索引（0-based）
    val chapterPos: Int = 0,     // 章节内字符位置（AI 写入时填 0）
    val chapterName: String,     // 章节标题
    val selectedText: String,    // 关联的原文片段
    val textHash: String,        // selectedText.hashCode().toString()
    val thought: String,         // 感想内容
    val createTime: Long,        // 创建时间戳
    val updateTime: Long         // 更新时间戳
)
```

#### 实现要点

1. 写操作分类：**静默写入**（感想由 AI 主动生成，视为自动化操作）
2. 调用前置条件：应先通过 `get_book_content` 获取目标章节内容，基于实际内容撰写感想
3. `selectedText` 填写规则：
   - 若用户指定了具体片段，使用用户指定片段（限 500 字）
   - 若未指定，从章节缓存内容取前 200 字；若章节未缓存，使用章节标题
4. `thought` 强制后缀：写入前在内容末尾追加 `\n——由AI助手生成`，不允许 AI 省略此标注
5. `textHash` = `selectedText.hashCode().toString()`（与 legado 原生逻辑保持一致）
6. `chapterPos` 设为 0（AI 无法感知精确字符位置）
7. 若同一章节的同一 `selectedText` 已存在想法，则追加新想法（insert 新记录），不覆盖
8. 返回每条写入结果，包含 `thoughtId`（数据库自增 id）供后续查询
---

## 第二批：管理闭环（优先级 P1）

---

### 工具 7：`get_replace_rules`

**用途**：获取所有文本替换规则列表（用于查看、分析、批量修改前的预览）。

#### 参数定义（JSON Schema）

```json
{
  "name": "get_replace_rules",
  "description": "获取 legado 中所有文本替换规则列表。",
  "parameters": {
    "type": "object",
    "properties": {
      "offset": {
        "type": "integer",
        "description": "分页偏移量，默认 0",
        "default": 0
      },
      "limit": {
        "type": "integer",
        "description": "每页数量，默认 50，最大 100",
        "default": 50
      },
      "enabledOnly": {
        "type": "boolean",
        "description": "true=只返回已启用的规则，默认 false",
        "default": false
      }
    },
    "required": []
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "total": 23,
    "offset": 0,
    "limit": 50,
    "rules": [
      {
        "id": "rule_001",
        "name": "去广告",
        "pattern": "本书来源.*?\\n",
        "replacement": "",
        "isRegex": true,
        "scope": "all",
        "isEnabled": true,
        "order": 1
      }
    ]
  }
}
```

#### 对应 API

```
GET /getReplaceRules
```

#### ReplaceRule 关键字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | 规则唯一 ID |
| `name` | String | 规则名称 |
| `pattern` | String | 匹配模式 |
| `replacement` | String | 替换内容 |
| `isRegex` | Boolean | 是否正则 |
| `scope` | String | 生效范围（书籍 URL 或 "all"） |
| `isEnabled` | Boolean | 是否启用 |
| `order` | Int | 执行顺序 |

#### 实现要点

1. 纯只读操作，直接转发 API 响应
2. 字段精简：保留上述关键字段，剔除时间戳等冗余信息
3. `enabledOnly` 在客户端过滤（API 不一定支持此参数）

---

### 工具 8：`save_replace_rule`

**用途**：创建新替换规则或修改已有规则，支持单条或批量传入。

#### 参数定义（JSON Schema）

```json
{
  "name": "save_replace_rule",
  "description": "创建或修改文本替换规则，支持批量传入。rules 数组中每条：有 id 则更新，无 id 则新建。",
  "parameters": {
    "type": "object",
    "properties": {
      "rules": {
        "type": "array",
        "description": "规则列表，至少包含一条。单条操作时数组长度为 1。",
        "items": {
          "type": "object",
          "properties": {
            "id": {
              "type": "string",
              "description": "规则 ID（修改时必填，新建时留空）"
            },
            "name": {
              "type": "string",
              "description": "规则名称"
            },
            "pattern": {
              "type": "string",
              "description": "匹配模式（普通字符串或正则表达式）"
            },
            "replacement": {
              "type": "string",
              "description": "替换内容，留空字符串表示删除匹配内容"
            },
            "isRegex": {
              "type": "boolean",
              "description": "是否使用正则表达式，默认 false",
              "default": false
            },
            "scope": {
              "type": "string",
              "description": "生效范围：'all' 表示全部书籍，或填写 bookUrl 表示仅对该书生效",
              "default": "all"
            },
            "isEnabled": {
              "type": "boolean",
              "description": "是否启用，默认 true",
              "default": true
            },
            "order": {
              "type": "integer",
              "description": "执行顺序，数字越小越先执行，默认 0",
              "default": 0
            }
          },
          "required": ["name", "pattern", "replacement"]
        }
      }
    },
    "required": ["rules"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "created": 2,
    "updated": 1,
    "failed": 0,
    "rules": [
      { "id": "rule_024", "name": "去广告", "action": "created", "isEnabled": true },
      { "id": "rule_025", "name": "标点修复", "action": "created", "isEnabled": true },
      { "id": "rule_001", "name": "旧规则", "action": "updated", "isEnabled": true }
    ]
  }
}
```

#### 对应 API

```
POST /saveReplaceRule
Content-Type: application/json
Body: ReplaceRule 对象（逐条循环调用，每条一次请求）
```

> **注意**：当前 `/saveReplaceRule` 接口每次只接受单个 ReplaceRule 对象。工具层接收 `rules` 数组后需循环逐条调用，全部完成后汇总结果返回。

#### 实现要点

1. 写操作分类：**批量确认**（合并展示所有待新建/修改的规则，一次性确认后逐条提交）
2. 新建时需生成唯一 `id`（UUID 格式，或由 API 自动分配时留空）
3. 修改时先查原规则，在确认弹窗中展示 `before` → `after` 对比
4. 正则表达式校验：`isRegex=true` 时，在发送前做简单语法检查，避免无效规则入库
5. 任一条失败不中断整体流程，失败原因记录在对应条目的 `error` 字段

---

### 工具 9：`delete_replace_rule`

**用途**：删除指定替换规则（高风险，批量确认）。

#### 参数定义（JSON Schema）

```json
{
  "name": "delete_replace_rule",
  "description": "删除指定的文本替换规则。此操作不可撤销，执行前将列出所有待删除规则并一次性确认。",
  "parameters": {
    "type": "object",
    "properties": {
      "ids": {
        "type": "array",
        "items": { "type": "string" },
        "description": "要删除的规则 ID 列表（支持一次传多个）"
      }
    },
    "required": ["ids"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "deleted": ["rule_001", "rule_002"],
    "failed": [],
    "totalDeleted": 2
  }
}
```

#### 对应 API

```
POST /deleteReplaceRule
Content-Type: application/json
Body: { "ids": ["rule_001"] }
```

#### 实现要点

1. 写操作分类：**批量确认**（不可撤销，高风险）
2. 执行前展示规则名称列表，确认弹窗：`「确认删除以下 2 条替换规则？此操作不可撤销：[去广告, 标点修复]」`
3. 支持批量传入 `ids`，一次整体确认后统一执行
4. 失败的 id 记录在 `failed` 数组，不中断整体流程

---

### 工具 10：`save_book_source`

**用途**：导入一个或多个书源到 legado，支持 JSON 字符串或 URL 两种输入方式。

#### 参数定义（JSON Schema）

```json
{
  "name": "save_book_source",
  "description": "导入新书源到 legado。支持单个或批量导入，来源可以是 JSON 字符串或远程 URL。",
  "parameters": {
    "type": "object",
    "properties": {
      "sourceJson": {
        "type": "string",
        "description": "书源 JSON 字符串（单个对象或对象数组）。与 sourceUrl 二选一。"
      },
      "sourceUrl": {
        "type": "string",
        "description": "书源远程 URL，工具自动拉取后导入。与 sourceJson 二选一。"
      },
      "enableAfterImport": {
        "type": "boolean",
        "description": "导入后是否自动启用，默认 true",
        "default": true
      }
    },
    "required": []
  }
}
```

> **注意**：`sourceJson` 和 `sourceUrl` 至少提供一个，两者都提供时优先使用 `sourceJson`。

#### 返回格式

```json
{
  "success": true,
  "data": {
    "imported": 3,
    "updated": 1,
    "failed": 0,
    "sources": [
      {
        "bookSourceName": "书源A",
        "bookSourceUrl": "https://...",
        "action": "created"
      }
    ]
  }
}
```

#### 对应 API

```
POST /saveBookSource    （单个书源）
POST /saveBookSources   （批量书源，Body 为数组）
```

#### 实现要点

1. 写操作分类：**批量确认**（导入可能覆盖同名书源）
2. 若 `sourceUrl` 不为空，工具先 HTTP GET 拉取内容，再走 `sourceJson` 流程
3. 识别输入是数组还是单对象，自动路由到 `/saveBookSource` 或 `/saveBookSources`
4. 导入前解析 JSON，提取 `bookSourceName` 列表展示给用户预览
5. 返回中区分 `created`（新书源）和 `updated`（已有同 URL 书源被覆盖）
6. 大型书源包（>50个）建议分批发送，每批 20 个

---

### 工具 11：`manage_webdav`

**用途**：管理 legado 的 WebDAV 备份文件（列出/删除/恢复/重命名）。

#### 参数定义（JSON Schema）

```json
{
  "name": "manage_webdav",
  "description": "管理 legado WebDAV 备份。支持列出备份文件、删除、恢复备份、重命名。",
  "parameters": {
    "type": "object",
    "properties": {
      "action": {
        "type": "string",
        "description": "操作类型：list=列出备份, restore=恢复, delete=删除, rename=重命名",
        "enum": ["list", "restore", "delete", "rename"]
      },
      "filename": {
        "type": "string",
        "description": "备份文件名（restore/delete/rename 时必填）"
      },
      "newFilename": {
        "type": "string",
        "description": "新文件名（rename 时必填）"
      },
      "webdavConfig": {
        "type": "object",
        "description": "WebDAV 配置（若已在 legado 内配置则可省略）",
        "properties": {
          "url": { "type": "string", "description": "WebDAV 服务器 URL" },
          "username": { "type": "string" },
          "password": { "type": "string" },
          "backupPath": { "type": "string", "description": "备份目录路径，默认 /legado/" }
        }
      }
    },
    "required": ["action"]
  }
}
```

#### 返回格式（list）

```json
{
  "success": true,
  "data": {
    "action": "list",
    "total": 5,
    "backups": [
      {
        "filename": "legado_backup_20250115_1030.zip",
        "size": "2.3MB",
        "lastModified": "2025-01-15T10:30:00",
        "type": "full"
      }
    ]
  }
}
```

#### 返回格式（restore/delete/rename）

```json
{
  "success": true,
  "data": {
    "action": "restore",
    "filename": "legado_backup_20250115_1030.zip",
    "message": "恢复成功，legado 将重载配置"
  }
}
```

#### 对应实现方式

legado 增强版的 WebDAV 功能通过 legado 内置 WebDAV 客户端实现。

相关 API 端点（根据增强版 legado 实际接口）：
- `GET /webdav/list` — 列出备份文件
- `POST /webdav/restore` — 恢复备份
- `POST /webdav/delete` — 删除备份
- `POST /webdav/rename` — 重命名

> **实现者注意**：如 legado 无上述 API，需先通过直接 WebDAV 协议（PROPFIND/DELETE/MOVE 方法）与 WebDAV 服务器交互，WebDAV 配置从 legado 的设置接口读取。

#### 实现要点

1. 写操作分类：`delete` 为**批量确认**（不可撤销，执行前列出文件名统一确认），`restore` 为**批量确认**（恢复会覆盖当前配置），`rename` 为**静默写入**
2. `restore` 操作危险性最高，确认弹窗应说明：`「恢复备份将覆盖当前所有设置，确认继续？」`
3. WebDAV 配置优先从 legado 已保存配置读取，`webdavConfig` 参数仅作临时覆盖
4. 文件大小做人类可读格式化（`2.3MB` 而非 `2411520`）

---

## 第三批：知识闭环（优先级 P2）

---

### 工具 12：`get_thoughts`

**用途**：获取读书想法和划线记录，支持按书筛选。

#### 参数定义（JSON Schema）

```json
{
  "name": "get_thoughts",
  "description": "获取读书想法（划线+评注）列表，支持按书名筛选和分页。",
  "parameters": {
    "type": "object",
    "properties": {
      "bookName": {
        "type": "string",
        "description": "按书名筛选（模糊匹配）。不填则返回所有书的想法。"
      },
      "offset": {
        "type": "integer",
        "description": "分页偏移量，默认 0",
        "default": 0
      },
      "limit": {
        "type": "integer",
        "description": "每页数量，默认 20，最大 100",
        "default": 20
      },
      "orderBy": {
        "type": "string",
        "description": "排序字段：createTime=按创建时间（默认）, bookName=按书名",
        "enum": ["createTime", "bookName"],
        "default": "createTime"
      },
      "order": {
        "type": "string",
        "description": "排序方向：desc=降序（默认）, asc=升序",
        "enum": ["desc", "asc"],
        "default": "desc"
      }
    },
    "required": []
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "total": 87,
    "offset": 0,
    "limit": 20,
    "thoughts": [
      {
        "id": "thought_001",
        "bookName": "斗破苍穹",
        "chapterName": "第100章 修炼",
        "selectedText": "天地元气，聚而为灵",
        "thought": "这段描写很有意境，联想到道德经...",
        "createTime": "2025-01-10T20:30:00"
      }
    ]
  }
}
```

#### 对应数据实体

```
BookThought {
  id: String          // 主键
  bookName: String    // 书名
  bookUrl: String     // 书籍 URL（精简时可省略）
  chapterName: String // 章节名
  chapterIndex: Int   // 章节索引（精简时可省略）
  selectedText: String // 划线原文
  thought: String     // 用户想法/评注
  createTime: Long    // 创建时间戳
}
```

#### 实现要点

1. 纯只读，直接查询数据库或调对应 API
2. `bookUrl` 从返回中剔除（精简 token），仅在需要进一步操作时才用 `bookName` 重新查
3. `selectedText` 截断到前 200 字
4. `createTime` 格式化为 ISO 8601 字符串
5. 若 legado 有 `GET /getBookThoughts` API，直接调用；否则通过增强版数据库接口查询

---

### 工具 13：`export_to_obsidian`

**用途**：将指定书籍的读书想法一键导出到 Obsidian，生成结构化笔记。

#### 参数定义（JSON Schema）

```json
{
  "name": "export_to_obsidian",
  "description": "将书籍的读书想法、笔记、评分等导出到 Obsidian vault，生成 Markdown 笔记文件。",
  "parameters": {
    "type": "object",
    "properties": {
      "bookName": {
        "type": "string",
        "description": "要导出的书名"
      },
      "bookUrl": {
        "type": "string",
        "description": "书籍 URL（与 bookName 二选一，优先 bookUrl）"
      },
      "exportPath": {
        "type": "string",
        "description": "Obsidian vault 内的目标路径（如 Reading/斗破苍穹.md）。不填则自动生成。"
      },
      "template": {
        "type": "string",
        "description": "导出模板：default=默认模板, minimal=仅划线和想法, full=全部信息",
        "enum": ["default", "minimal", "full"],
        "default": "default"
      },
      "obsidianConfig": {
        "type": "object",
        "description": "Obsidian Local REST API 配置（如已在系统配置中保存则可省略）",
        "properties": {
          "url": {
            "type": "string",
            "description": "Obsidian Local REST API 地址，如 http://localhost:27123"
          },
          "apiKey": {
            "type": "string",
            "description": "API Key"
          }
        }
      }
    },
    "required": ["bookName"]
  }
}
```

#### 返回格式

```json
{
  "success": true,
  "data": {
    "bookName": "斗破苍穹",
    "exportedPath": "Reading/斗破苍穹.md",
    "thoughtsCount": 23,
    "noteLength": 4521,
    "action": "created",
    "obsidianUrl": "obsidian://open?vault=MyVault&file=Reading/斗破苍穹"
  }
}
```

#### 对应 API

```
Obsidian Local REST API:
  PUT /vault/{filename}        — 创建或覆盖文件
  GET /vault/{filename}        — 检查文件是否已存在
  Content-Type: text/markdown
  Authorization: Bearer {apiKey}
```

#### 生成 Markdown 模板（default）

```markdown
---
title: {bookName}
author: {author}
rating: {bookRating}/5
status: {readIterationLabel}
tags: [读书笔记]
created: {exportTime}
---

## 📖 基本信息
- **作者**：{author}
- **评分**：{rating}/5 ⭐
- **状态**：{statusLabel}
- **字数**：{wordCount}

## 📝 阅读前记录
{preReadNote}

## 💭 完读感想
{postReadNote}

## 🖊️ 划线与想法（共 {count} 条）

### {chapterName}
> {selectedText}

{thought}

---
```

#### 实现要点

1. 操作流程：
   - 步骤 1：通过 `get_thoughts` + 书架信息聚合该书全部数据
   - 步骤 2：按模板渲染 Markdown
   - 步骤 3：先 GET 检查文件是否存在（`action: updated` vs `created`）
   - 步骤 4：PUT 写入 Obsidian
2. 写操作分类：**批量确认**（若文件已存在则询问是否覆盖）
3. Obsidian 配置优先从系统全局配置读取，`obsidianConfig` 参数用于临时覆盖
4. `exportPath` 自动生成规则：`{obsidianBasePath}/{bookName}.md`，其中 `obsidianBasePath` 从配置读取
5. 文件名特殊字符转义（`/` `\` `:` `*` `?` `"` `<` `>` `|` 替换为 `_`）
6. `minimal` 模板仅包含划线+想法块，不含书籍基本信息
7. `full` 模板额外包含章节目录进度等

---

### 工具 14：`get_detailed_reading_record`

**用途**：获取详细阅读记录，支持按天、按书、按时间段查询，用于统计和回顾。

#### 参数定义（JSON Schema）

```json
{
  "name": "get_detailed_reading_record",
  "description": "获取详细阅读记录（时长、进度、日期等），支持多维度查询。",
  "parameters": {
    "type": "object",
    "properties": {
      "queryType": {
        "type": "string",
        "description": "查询类型：by_day=按天汇总, by_book=按书汇总, by_range=时间段明细",
        "enum": ["by_day", "by_book", "by_range"],
        "default": "by_day"
      },
      "bookName": {
        "type": "string",
        "description": "按书名筛选（模糊匹配，不填=全部书）"
      },
      "startDate": {
        "type": "string",
        "description": "开始日期，格式 YYYY-MM-DD，默认 7 天前"
      },
      "endDate": {
        "type": "string",
        "description": "结束日期，格式 YYYY-MM-DD，默认今天"
      },
      "offset": {
        "type": "integer",
        "description": "分页偏移量，默认 0",
        "default": 0
      },
      "limit": {
        "type": "integer",
        "description": "每页数量，默认 20，最大 100",
        "default": 20
      }
    },
    "required": []
  }
}
```

#### 返回格式（by_day）

```json
{
  "success": true,
  "data": {
    "queryType": "by_day",
    "startDate": "2025-01-09",
    "endDate": "2025-01-15",
    "totalDays": 7,
    "totalMinutes": 420,
    "records": [
      {
        "date": "2025-01-15",
        "readMinutes": 65,
        "chaptersRead": 8,
        "booksRead": ["斗破苍穹", "三体"]
      }
    ]
  }
}
```

#### 返回格式（by_book）

```json
{
  "success": true,
  "data": {
    "queryType": "by_book",
    "startDate": "2025-01-09",
    "endDate": "2025-01-15",
    "records": [
      {
        "bookName": "斗破苍穹",
        "totalMinutes": 280,
        "chaptersRead": 45,
        "lastReadDate": "2025-01-15",
        "readDays": 5
      }
    ]
  }
}
```

#### 返回格式（by_range 明细）

```json
{
  "success": true,
  "data": {
    "queryType": "by_range",
    "total": 156,
    "offset": 0,
    "limit": 20,
    "records": [
      {
        "bookName": "斗破苍穹",
        "chapterIndex": 100,
        "chapterTitle": "第100章",
        "startTime": "2025-01-15T20:00:00",
        "endTime": "2025-01-15T20:35:00",
        "durationMinutes": 35,
        "readChars": 12800
      }
    ]
  }
}
```

#### 对应数据实体

```
DetailedReadRecord {
  bookName: String        // 书名
  bookUrl: String         // 书籍 URL（精简时省略）
  chapterIndex: Int       // 章节索引
  chapterTitle: String    // 章节标题
  startTime: Long         // 开始阅读时间戳
  endTime: Long           // 结束阅读时间戳
  readChars: Int          // 本次阅读字符数
}
```

#### 实现要点

1. 纯只读，对应 legado 增强版的 `DetailedReadRecord` 实体
2. 若有 `GET /getDetailedReadRecords` API，直接调用并在工具侧聚合；否则查询底层数据库
3. `by_day` 模式：按 `date(startTime)` 分组，`SUM(endTime - startTime)` 计算时长
4. `by_book` 模式：按 `bookName` 分组，统计阅读天数、总时长、章节数
5. `by_range` 模式：按 `startTime DESC` 返回明细，支持分页
6. 时长精确到**分钟**（对 token 友好），不需要秒级
7. `startDate`/`endDate` 默认值：`endDate=今天`, `startDate=今天-7天`
8. 超长时间记录（单次 > 4 小时）可能是数据异常，实现时可标记 `isAbnormal: true`

---

## 附录：实现优先级总览

| 优先级 | 工具 | 类型 | 风险等级 | 依赖 |
|--------|------|------|----------|------|
| P0-1 | `get_book_content` | 只读 | 低 | 无 |
| P0-2 | `search_online_book` | 只读（WS） | 低 | WebSocket 适配 |
| P0-3 | `save_book_progress` | 写（静默） | 低 | 无 |
| P0-4 | `rate_book` | 写（静默） | 低 | `saveBook` API |
| P0-5 | `mark_book_status` | 写（批量确认） | 中 | `saveBook` API |
| P0-6 | `set_book_note` | 写（静默） | 低 | `saveBook` API |
| P1-1 | `get_replace_rules` | 只读 | 低 | 无 |
| P1-2 | `save_replace_rule` | 写（批量确认） | 中 | 无 |
| P1-3 | `delete_replace_rule` | 写（批量确认） | 高 | 无 |
| P1-4 | `save_book_source` | 写（批量确认） | 中 | 无 |
| P1-5 | `manage_webdav` | 混合 | 高（restore） | WebDAV 配置 |
| P2-1 | `get_thoughts` | 只读 | 低 | BookThought 实体 |
| P2-2 | `export_to_obsidian` | 写（批量确认） | 低 | Obsidian API + get_thoughts |
| P2-3 | `get_detailed_reading_record` | 只读 | 低 | DetailedReadRecord 实体 |

---

## 附录：通用实现检查清单

实现每个工具时，请逐项确认：

- [ ] 参数做类型校验和边界检查
- [ ] 写操作按对应确认类型实现弹窗
- [ ] 返回字段精简（剔除 base64、大 JSON 字符串、冗余时间戳）
- [ ] 列表类返回包含 `total` 字段
- [ ] 超 100 条查询支持 `offset` + `limit` 分页
- [ ] 错误返回包含 `error` 描述（用中文，方便 AI 向用户解释）
- [ ] HTTP 超时设置合理（只读 10s，写操作 15s，WebSocket 根据 `timeout` 参数）
- [ ] `bookUrl` 作为参数时做 URL encode

---

*文档版本：v1.0 | 生成时间：2025-01 | 适用项目：Jingshiro/legado（增强版）*
