package io.legado.app.ui.book.read.ai.tool

/**
 * AI 工具定义层 — 以 OpenAI tools JSON Schema 格式声明每个工具
 */
object AiToolDef {

    val allTools: List<Map<String, Any>> by lazy {
        listOf(
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
                "获取书源列表。可按启用状态和分组筛选。",
                properties = mapOf(
                    "enabled" to prop("boolean", "true 只返回已启用的书源，false 只返回未启用的，不传返回全部"),
                    "group" to prop("string", "按书源分组名筛选")
                )
            ),
            tool(
                "get_rss_sources",
                "获取 RSS 订阅源列表。可按启用状态和分组筛选。",
                properties = mapOf(
                    "enabled" to prop("boolean", "true 只返回已启用的订阅源，false 只返回未启用的，不传返回全部"),
                    "group" to prop("string", "按订阅源分组名筛选")
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
            // 写操作工具（需要用户确认）
            tool(
                "update_book_group",
                "将书架上的书籍移入指定分组。需要提供书名和目标分组名。此操作会弹出确认框。",
                required = listOf("book_name", "group_name"),
                properties = mapOf(
                    "book_name" to prop("string", "书架上的书名"),
                    "group_name" to prop("string", "目标分组名")
                )
            ),
            tool(
                "enable_book_source",
                "启用或禁用指定书源。此操作会弹出确认框。",
                required = listOf("source_name", "enabled"),
                properties = mapOf(
                    "source_name" to prop("string", "书源名称"),
                    "enabled" to prop("boolean", "true 启用，false 禁用")
                )
            ),
            tool(
                "enable_rss_source",
                "启用或禁用指定 RSS 订阅源。此操作会弹出确认框。",
                required = listOf("source_name", "enabled"),
                properties = mapOf(
                    "source_name" to prop("string", "订阅源名称"),
                    "enabled" to prop("boolean", "true 启用，false 禁用")
                )
            ),
            tool(
                "delete_book_source",
                "删除指定书源（不可撤销）。此操作会弹出确认框。",
                required = listOf("source_name"),
                properties = mapOf(
                    "source_name" to prop("string", "要删除的书源名称")
                )
            ),
            tool(
                "update_book_source_group",
                "将指定书源移入目标分组。此操作会弹出确认框。",
                required = listOf("source_name", "group_name"),
                properties = mapOf(
                    "source_name" to prop("string", "书源名称"),
                    "group_name" to prop("string", "目标分组名")
                )
            ),
            tool(
                "delete_rss_source",
                "删除指定 RSS 订阅源（不可撤销）。此操作会弹出确认框。",
                required = listOf("source_name"),
                properties = mapOf(
                    "source_name" to prop("string", "要删除的订阅源名称")
                )
            )
        )
    }

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, Map<String, String>>,
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

    private fun prop(type: String, description: String): Map<String, String> {
        return mapOf("type" to type, "description" to description)
    }
}
