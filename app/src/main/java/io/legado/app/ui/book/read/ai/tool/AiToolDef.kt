package io.legado.app.ui.book.read.ai.tool

/**
 * AI 工具定义层 — 以 OpenAI tools JSON Schema 格式声明每个工具
 */
object AiToolDef {

    val allTools: List<Map<String, Any>> by lazy {
        listOf(
            // ===== 已有工具（只读）=====
            tool(
                "get_bookshelf",
                "获取用户书架上的书籍列表。返回书名、作者、分组、当前阅读进度等信息。",
                properties = mapOf(
                    "group" to prop("string", "按分组名筛选，不传则返回全部书籍")
                )
            ),
            tool(
                "search_bookshelf",
                "在书架中按关键词搜索书籍（匹配书名或作者）。",
                required = listOf("keyword"),
                properties = mapOf(
                    "keyword" to prop("string", "搜索关键词")
                )
            ),
            tool(
                "get_book_sources",
                "获取书源列表。可按启用状态和分组筛选。每次最多返回100条，通过 offset 参数翻页。返回的 hasMore 为 true 时代表还有更多数据，请继续查询。如果用户书源数量较多（如500+），请告知用户本次只处理了部分书源，并分批处理。",
                properties = mapOf(
                    "enabled" to prop("boolean", "true 只返回已启用的书源，false 只返回未启用的，不传返回全部"),
                    "group" to prop("string", "按书源分组名筛选"),
                    "offset" to prop("integer", "分页偏移量，从0开始，默认0")
                )
            ),
            tool(
                "get_rss_sources",
                "获取 RSS 订阅源列表。可按启用状态和分组筛选。每次最多返回100条，通过 offset 参数翻页。返回的 hasMore 为 true 时代表还有更多数据，请继续查询。",
                properties = mapOf(
                    "enabled" to prop("boolean", "true 只返回已启用的订阅源，false 只返回未启用的，不传返回全部"),
                    "group" to prop("string", "按订阅源分组名筛选"),
                    "offset" to prop("integer", "分页偏移量，从0开始，默认0")
                )
            ),
            tool(
                "get_reading_stats",
                "获取用户的阅读统计数据，包括书籍总数、总阅读时长、各书阅读时长排名等。",
                properties = emptyMap()
            ),
            tool(
                "get_book_chapters",
                "获取指定书籍的章节目录。需要提供书名。",
                required = listOf("book_name"),
                properties = mapOf(
                    "book_name" to prop("string", "书架上的书名")
                )
            ),
            tool(
                "get_book_groups",
                "获取书架的分组列表（如'全部'、'本地'、用户自定义分组等）。",
                properties = emptyMap()
            ),
            tool(
                "get_source_groups",
                "获取书源和订阅源的分组列表。",
                properties = emptyMap()
            ),
            // 已有写操作工具（批量确认，低风险）
            tool(
                "update_book_group",
                "将书架上的书籍移入指定分组。需要提供书名和目标分组名。多个此类操作会合并为一次确认。",
                required = listOf("book_name", "group_name"),
                properties = mapOf(
                    "book_name" to prop("string", "书架上的书名"),
                    "group_name" to prop("string", "目标分组名")
                )
            ),
            tool(
                "enable_book_source",
                "启用或禁用指定书源。多个此类操作会合并为一次确认。",
                required = listOf("source_name", "enabled"),
                properties = mapOf(
                    "source_name" to prop("string", "书源名称"),
                    "enabled" to prop("boolean", "true 启用，false 禁用")
                )
            ),
            tool(
                "enable_rss_source",
                "启用或禁用指定 RSS 订阅源。多个此类操作会合并为一次确认。",
                required = listOf("source_name", "enabled"),
                properties = mapOf(
                    "source_name" to prop("string", "订阅源名称"),
                    "enabled" to prop("boolean", "true 启用，false 禁用")
                )
            ),
            tool(
                "update_book_source_group",
                "将指定书源移入目标分组（替换原有分组，不是追加）。多个此类操作会合并为一次确认。注意：每次最多可以批量处理100个书源，如果用户书源数量超过100个，请分批处理并告知用户。",
                required = listOf("source_name", "group_name"),
                properties = mapOf(
                    "source_name" to prop("string", "书源名称"),
                    "group_name" to prop("string", "目标分组名（将完全替换原有分组）")
                )
            ),
            // 已有写操作工具（批量确认，高风险删除操作）
            tool(
                "delete_book_source",
                "删除指定书源（不可撤销）。此操作会合并为批量确认弹窗。",
                required = listOf("source_name"),
                properties = mapOf(
                    "source_name" to prop("string", "要删除的书源名称")
                )
            ),
            tool(
                "delete_rss_source",
                "删除指定 RSS 订阅源（不可撤销）。此操作会合并为批量确认弹窗。",
                required = listOf("source_name"),
                properties = mapOf(
                    "source_name" to prop("string", "要删除的订阅源名称")
                )
            ),
            // 分组管理
            tool(
                "create_book_group",
                "创建一个新的书籍分组。最多64个分组，同名分组无法重复创建。多个创建操作会合并为一次确认。",
                required = listOf("group_name"),
                properties = mapOf(
                    "group_name" to prop("string", "要创建的分组名称")
                )
            ),
            // 书籍移除（批量确认，不可撤销）
            tool(
                "delete_book",
                "从书架上移除指定书籍（不会删除本地文件，但此操作不可撤销）。此操作会合并为批量确认弹窗。",
                required = listOf("book_name"),
                properties = mapOf(
                    "book_name" to prop("string", "要移除的书名")
                )
            ),

            // ===== 新增工具（P0：核心阅读体验）=====
            tool(
                "get_book_content",
                "获取指定书籍某章节的正文内容。内容默认截断为前2000字，可调节（最大8000字）。",
                required = listOf("bookUrl", "chapterIndex"),
                properties = mapOf(
                    "bookUrl" to prop("string", "书籍的唯一标识 URL（从 get_bookshelf 获取 bookUrl 字段，如不存在请用书名从书架查找）"),
                    "chapterIndex" to prop("integer", "章节索引，从 0 开始"),
                    "maxChars" to prop("integer", "返回最大字符数，默认 2000，最大 8000")
                )
            ),
            tool(
                "search_online_book",
                "使用 legado 书源在线搜索书籍，返回匹配结果列表。搜索过程是流式的，等待 timeout 秒后返回结果。",
                required = listOf("keyword"),
                properties = mapOf(
                    "keyword" to prop("string", "搜索关键词（书名、作者均可）"),
                    "limit" to prop("integer", "返回结果数量上限，默认 10，最大 30"),
                    "timeout" to prop("integer", "等待搜索结果的超时秒数，默认 10")
                )
            ),
            tool(
                "save_book_progress",
                "保存指定书籍的阅读进度（章节索引和章节内位置）。此操作直接执行，无需确认。",
                required = listOf("bookUrl", "durChapterIndex"),
                properties = mapOf(
                    "bookUrl" to prop("string", "书籍唯一标识 URL"),
                    "durChapterIndex" to prop("integer", "当前阅读章节索引（0-based）"),
                    "durChapterPos" to prop("integer", "章节内字符位置，默认 0"),
                    "durChapterTitle" to prop("string", "章节标题（可选，辅助校验）")
                )
            ),
            tool(
                "rate_book",
                "给书架中的书籍打评分，0-5 分（支持 0.5 步进）。直接执行，无需确认。",
                required = listOf("bookUrl", "rating"),
                properties = mapOf(
                    "bookUrl" to prop("string", "书籍唯一标识 URL"),
                    "rating" to prop("number", "评分，0.0 到 5.0 之间")
                )
            ),
            tool(
                "mark_book_status",
                "标记书籍的阅读状态（首次读完、二刷中、二刷完等）。无论单本还是多本，均需批量确认弹窗；多本时合并一次弹窗。",
                required = listOf("bookUrl", "status"),
                properties = mapOf(
                    "bookUrl" to prop("string", "书籍唯一标识 URL"),
                    "status" to prop("integer", "阅读状态：0=未读完, 1=首次读完, 2=二刷中, 3=二刷完, 4=三刷中, 5=三刷完（以此类推）")
                )
            ),
            tool(
                "set_book_note",
                "为书籍指定章节写阅读感想，以 BookThought（想法）形式写入。支持一次为多个章节写感想。所有 AI 写入的感想末尾会自动追加「——由AI助手生成」标注。" +
                "【重要】若系统提示词中已包含【当前阅读书籍信息】（即用户在书籍阅读界面打开了AI助手），则 bookUrl 直接从该区块中获取，章节内容也已在【参考章节内容】中提供，无需再调用 get_bookshelf 或 get_book_content，直接根据已有内容撰写感想即可。" +
                "若无上述上下文（独立模式），则需先调用 get_bookshelf 获取 bookUrl，再用 get_book_content 获取章节内容。",
                required = listOf("bookUrl", "notes"),
                properties = mapOf(
                    "bookUrl" to prop("string", "书籍唯一标识 URL。在书籍阅读上下文中可直接从系统提示词的【当前阅读书籍信息】中读取；否则从 get_bookshelf 获取"),
                    "notes" to propArray(
                        "感想列表，每条对应一个章节。支持一次写多个章节。",
                        mapOf(
                            "chapterIndex" to prop("integer", "章节索引，从 0 开始。在书籍阅读上下文中可从【参考章节内容】区块中确认对应章节的序号"),
                            "selectedText" to prop("string", "关联的原文片段（建议取章节内关键段落）。在书籍阅读上下文中请从【参考章节内容】中摘取，留空则自动取章节完整内容"),
                            "thought" to prop("string", "AI 的阅读感想内容（系统会自动在末尾追加标注，无需手动添加）")
                        ),
                        listOf("chapterIndex", "thought")
                    )
                )
            ),


            // ===== 新增工具（P1：管理闭环）=====
            tool(
                "get_replace_rules",
                "获取 legado 中所有文本替换规则列表，支持分页和按启用状态筛选。",
                properties = mapOf(
                    "offset" to prop("integer", "分页偏移量，默认 0"),
                    "limit" to prop("integer", "每页数量，默认 50，最大 100"),
                    "enabledOnly" to prop("boolean", "true=只返回已启用的规则，默认 false")
                )
            ),
            tool(
                "save_replace_rule",
                "创建或修改文本替换规则，支持批量传入。rules 数组中每条：有 id 则更新，无 id 则新建。多条操作合并为一次批量确认。",
                required = listOf("rules"),
                properties = mapOf(
                    "rules" to propArray(
                        "规则列表，至少包含一条。单条操作时数组长度为 1。",
                        mapOf(
                            "id" to prop("string", "规则 ID（修改时必填，新建时留空）"),
                            "name" to prop("string", "规则名称"),
                            "pattern" to prop("string", "匹配模式（普通字符串或正则表达式）"),
                            "replacement" to prop("string", "替换内容，留空字符串表示删除匹配内容"),
                            "isRegex" to prop("boolean", "是否使用正则表达式，默认 false"),
                            "scope" to prop("string", "生效范围：'all' 全部书籍，或填写 bookUrl 仅对该书生效，默认 'all'"),
                            "isEnabled" to prop("boolean", "是否启用，默认 true"),
                            "order" to prop("integer", "执行顺序，数字越小越先执行，默认 0")
                        ),
                        listOf("name", "pattern", "replacement")
                    )
                )
            ),
            tool(
                "delete_replace_rule",
                "删除指定的文本替换规则（不可撤销）。执行前列出所有待删除规则名称并合并为一次批量确认。",
                required = listOf("ids"),
                properties = mapOf(
                    "ids" to propStringArray("要删除的规则 ID 列表（支持一次传多个）")
                )
            ),
            tool(
                "save_book_source",
                "导入新书源到 legado。支持单个或批量导入，来源可以是 JSON 字符串或远程 URL。导入前展示书源名称列表并批量确认。",
                properties = mapOf(
                    "sourceJson" to prop("string", "书源 JSON 字符串（单个对象或对象数组）。与 sourceUrl 二选一。"),
                    "sourceUrl" to prop("string", "书源远程 URL，工具自动拉取后导入。与 sourceJson 二选一。"),
                    "enableAfterImport" to prop("boolean", "导入后是否自动启用，默认 true")
                )
            ),
            tool(
                "manage_webdav",
                "管理 legado WebDAV 备份。支持列出备份文件(list)、恢复备份(restore)、删除备份(delete)。restore/delete 需批量确认。",
                required = listOf("action"),
                properties = mapOf(
                    "action" to prop("string", "操作类型：list=列出备份, restore=恢复, delete=删除"),
                    "filename" to prop("string", "备份文件名（restore/delete 时必填）")
                )
            ),

            // ===== 新增工具（P2：知识闭环）=====
            tool(
                "get_thoughts",
                "获取读书想法（划线+评注）列表，支持按书名筛选和分页。",
                properties = mapOf(
                    "bookName" to prop("string", "按书名筛选（精确匹配）。不填则返回所有书的想法。"),
                    "offset" to prop("integer", "分页偏移量，默认 0"),
                    "limit" to prop("integer", "每页数量，默认 20，最大 100"),
                    "orderBy" to prop("string", "排序：createTime=按创建时间（默认）, chapterIndex=按章节顺序"),
                    "order" to prop("string", "排序方向：desc=降序（默认）, asc=升序")
                )
            ),
            tool(
                "export_to_obsidian",
                "将书籍的读书想法、笔记、评分等导出到 Obsidian vault，生成 Markdown 笔记文件。若文件已存在则询问是否覆盖（批量确认）。",
                required = listOf("bookName"),
                properties = mapOf(
                    "bookName" to prop("string", "要导出的书名"),
                    "exportPath" to prop("string", "Obsidian vault 内的目标路径（如 Reading/斗破苍穹.md）。不填则自动生成。"),
                    "template" to prop("string", "导出模板：default=默认模板, minimal=仅划线和想法, full=全部信息"),
                    "obsidianUrl" to prop("string", "Obsidian Local REST API 地址，如 http://localhost:27123"),
                    "obsidianApiKey" to prop("string", "Obsidian Local REST API Key")
                )
            ),
            tool(
                "get_detailed_reading_record",
                "获取详细阅读记录（时长、进度、日期等），支持按天/按书/时间段明细查询。",
                properties = mapOf(
                    "queryType" to prop("string", "查询类型：by_day=按天汇总（默认）, by_book=按书汇总, by_range=时间段明细"),
                    "bookName" to prop("string", "按书名筛选（精确匹配，不填=全部书）"),
                    "startDate" to prop("string", "开始日期，格式 YYYY-MM-DD，默认 7 天前"),
                    "endDate" to prop("string", "结束日期，格式 YYYY-MM-DD，默认今天"),
                    "offset" to prop("integer", "分页偏移量，默认 0"),
                    "limit" to prop("integer", "每页数量，默认 20，最大 100")
                )
            )
        )
    }

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, Map<String, Any>>,
        required: List<String> = emptyList()
    ): Map<String, Any> {
        val params = mutableMapOf<String, Any>(
            "type" to "object",
            "properties" to properties
        )
        if (required.isNotEmpty()) {
            params["required"] = required
        }
        return mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to name,
                "description" to description,
                "parameters" to params
            )
        )
    }

    private fun prop(type: String, description: String): Map<String, Any> {
        return mapOf("type" to type, "description" to description)
    }

    /**
     * 生成数组类型的属性定义（用于 items 包含对象的数组参数）
     */
    private fun propArray(
        description: String,
        itemProperties: Map<String, Map<String, Any>>,
        itemRequired: List<String> = emptyList()
    ): Map<String, Any> {
        val itemSchema = mutableMapOf<String, Any>(
            "type" to "object",
            "properties" to itemProperties
        )
        if (itemRequired.isNotEmpty()) {
            itemSchema["required"] = itemRequired
        }
        return mapOf(
            "type" to "array",
            "description" to description,
            "items" to itemSchema
        )
    }

    /**
     * 生成字符串数组类型的属性定义
     */
    private fun propStringArray(description: String): Map<String, Any> {
        return mapOf(
            "type" to "array",
            "description" to description,
            "items" to mapOf("type" to "string")
        )
    }
}
