package io.legado.app.ui.book.read.ai.tool

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

sealed class ToolExecuteResult {
    data class Data(val json: String) : ToolExecuteResult()
    data class NeedConfirmation(
        val description: String,
        val action: suspend () -> String
    ) : ToolExecuteResult()
}

object ToolRouter {

    private const val MAX_BOOKS = 100
    private const val MAX_SOURCES = 100
    private const val MAX_CHAPTERS = 200
    private const val MAX_TOP_BOOKS = 10

    suspend fun execute(name: String, args: Map<*, *>): ToolExecuteResult {
        return withContext(Dispatchers.IO) {
            try {
                when (name) {
                    // 只读工具
                    "get_bookshelf" -> ToolExecuteResult.Data(getBookshelf(args))
                    "search_bookshelf" -> ToolExecuteResult.Data(searchBookshelf(args))
                    "get_book_sources" -> ToolExecuteResult.Data(getBookSources(args))
                    "get_rss_sources" -> ToolExecuteResult.Data(getRssSources(args))
                    "get_reading_stats" -> ToolExecuteResult.Data(getReadingStats())
                    "get_book_chapters" -> ToolExecuteResult.Data(getBookChapters(args))
                    "get_book_groups" -> ToolExecuteResult.Data(getBookGroups())
                    "get_source_groups" -> ToolExecuteResult.Data(getSourceGroups())
                    // 写操作工具
                    "update_book_group" -> confirmUpdateBookGroup(args)
                    "enable_book_source" -> confirmEnableBookSource(args)
                    "enable_rss_source" -> confirmEnableRssSource(args)
                    "delete_book_source" -> confirmDeleteBookSource(args)
                    "update_book_source_group" -> confirmUpdateBookSourceGroup(args)
                    "delete_rss_source" -> confirmDeleteRssSource(args)
                    else -> ToolExecuteResult.Data("""{"error":"未知工具: $name"}""")
                }
            } catch (e: Exception) {
                ToolExecuteResult.Data("""{"error":"${e.message?.replace("\"", "'") ?: "未知错误"}"}""")
            }
        }
    }

    // ========== 只读工具 ==========

    private fun getBookshelf(args: Map<*, *>): String {
        val group = args["group"] as? String
        val books = if (group.isNullOrBlank()) {
            appDb.bookDao.all
        } else {
            val bookGroup = appDb.bookGroupDao.getByName(group)
            if (bookGroup != null) {
                appDb.bookDao.getBooksByGroup(bookGroup.groupId)
            } else {
                return """{"error":"未找到分组: $group"}"""
            }
        }
        val result = books.take(MAX_BOOKS).map { bookToSimple(it) }
        return GSON.toJson(result)
    }

    private fun searchBookshelf(args: Map<*, *>): String {
        val keyword = args["keyword"] as? String
        if (keyword.isNullOrBlank()) {
            return """{"error":"keyword 参数不能为空"}"""
        }
        val books = appDb.bookDao.all.filter {
            it.name.contains(keyword, ignoreCase = true) ||
                    it.author.contains(keyword, ignoreCase = true)
        }
        val result = books.take(MAX_BOOKS).map { bookToSimple(it) }
        return GSON.toJson(result)
    }

    private fun getBookSources(args: Map<*, *>): String {
        val enabled = args["enabled"] as? Boolean
        val group = args["group"] as? String
        val allSources = appDb.bookSourceDao.allPart
        val sources = when (enabled) {
            true -> allSources.filter { it.enabled }
            false -> allSources.filter { !it.enabled }
            else -> allSources
        }
        val filtered = if (!group.isNullOrBlank()) {
            sources.filter { it.bookSourceGroup?.contains(group) == true }
        } else {
            sources
        }
        val result = filtered.take(MAX_SOURCES).map { source ->
            mapOf(
                "name" to source.bookSourceName,
                "url" to source.bookSourceUrl,
                "enabled" to source.enabled,
                "group" to (source.bookSourceGroup ?: ""),
                "weight" to source.weight
            )
        }
        return GSON.toJson(result)
    }

    private fun getRssSources(args: Map<*, *>): String {
        val enabled = args["enabled"] as? Boolean
        val group = args["group"] as? String
        val sources = when {
            enabled == true -> appDb.rssSourceDao.all.filter { it.enabled }
            enabled == false -> appDb.rssSourceDao.all.filter { !it.enabled }
            else -> appDb.rssSourceDao.all
        }
        val filtered = if (!group.isNullOrBlank()) {
            sources.filter { it.sourceGroup?.contains(group) == true }
        } else {
            sources
        }
        val result = filtered.take(MAX_SOURCES).map { source: RssSource ->
            mapOf(
                "name" to source.sourceName,
                "url" to source.sourceUrl,
                "enabled" to source.enabled,
                "group" to (source.sourceGroup ?: "")
            )
        }
        return GSON.toJson(result)
    }

    private fun getReadingStats(): String {
        val totalBooks = appDb.bookDao.allBookCount
        val totalReadTime = appDb.readRecordDao.allTime
        val allRecords = appDb.readRecordDao.allShow
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayRecords = allRecords.filter { it.lastRead >= todayStart }
        val todayReadTime = todayRecords.sumOf { it.readTime }
        val readDays = allRecords.map {
            Calendar.getInstance().apply { timeInMillis = it.lastRead }.get(Calendar.DAY_OF_YEAR)
        }.distinct().size
        val topBooks = allRecords.sortedByDescending { it.readTime }
            .take(MAX_TOP_BOOKS).map { mapOf("name" to it.bookName, "readTime" to it.readTime) }
        return GSON.toJson(mapOf(
            "totalBooks" to totalBooks,
            "totalReadTimeMinutes" to (totalReadTime / 60000),
            "todayReadTimeMinutes" to (todayReadTime / 60000),
            "readDays" to readDays,
            "topBooks" to topBooks
        ))
    }

    private fun getBookChapters(args: Map<*, *>): String {
        val bookName = args["book_name"] as? String
        if (bookName.isNullOrBlank()) return """{"error":"book_name 参数不能为空"}"""
        val book = appDb.bookDao.all.find { it.name == bookName }
            ?: return """{"error":"未找到书籍: $bookName"}"""
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        return GSON.toJson(chapters.take(MAX_CHAPTERS).map { ch ->
            mapOf("index" to (ch.index + 1), "title" to ch.title)
        })
    }

    private fun getBookGroups(): String {
        return GSON.toJson(appDb.bookGroupDao.all.map { g ->
            mapOf("id" to g.groupId, "name" to g.groupName)
        })
    }

    private fun getSourceGroups(): String {
        return GSON.toJson(appDb.bookSourceDao.allGroups())
    }

    // ========== 写操作工具（返回 NeedConfirmation） ==========

    private suspend fun confirmUpdateBookGroup(args: Map<*, *>): ToolExecuteResult {
        val bookName = args["book_name"] as? String
        val groupName = args["group_name"] as? String
        if (bookName.isNullOrBlank() || groupName.isNullOrBlank()) {
            return ToolExecuteResult.Data("""{"error":"book_name 和 group_name 参数不能为空"}""")
        }
        val book = appDb.bookDao.all.find { it.name == bookName }
            ?: return ToolExecuteResult.Data("""{"error":"未找到书籍: $bookName"}""")
        val group = appDb.bookGroupDao.getByName(groupName)
            ?: return ToolExecuteResult.Data("""{"error":"未找到分组: $groupName"}""")
        return ToolExecuteResult.NeedConfirmation(
            description = "将《${book.name}》移入分组「${group.groupName}」"
        ) {
            book.group = group.groupId
            book.save()
            """{"success":true,"message":"已将《${book.name}》移入分组「${group.groupName}」"}"""
        }
    }

    private suspend fun confirmEnableBookSource(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String
        val enabled = args["enabled"] as? Boolean
        if (sourceName.isNullOrBlank() || enabled == null) {
            return ToolExecuteResult.Data("""{"error":"source_name 和 enabled 参数不能为空"}""")
        }
        val source = appDb.bookSourceDao.allPart.find { it.bookSourceName == sourceName }
            ?: return ToolExecuteResult.Data("""{"error":"未找到书源: $sourceName"}""")
        val action = if (enabled) "启用" else "禁用"
        return ToolExecuteResult.NeedConfirmation(
            description = "${action}书源「${source.bookSourceName}」"
        ) {
            appDb.bookSourceDao.enable(source.bookSourceUrl, enabled)
            """{"success":true,"message":"已${action}书源「${source.bookSourceName}」"}"""
        }
    }

    private suspend fun confirmEnableRssSource(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String
        val enabled = args["enabled"] as? Boolean
        if (sourceName.isNullOrBlank() || enabled == null) {
            return ToolExecuteResult.Data("""{"error":"source_name 和 enabled 参数不能为空"}""")
        }
        val source = appDb.rssSourceDao.all.find { it.sourceName == sourceName }
            ?: return ToolExecuteResult.Data("""{"error":"未找到订阅源: $sourceName"}""")
        val action = if (enabled) "启用" else "禁用"
        return ToolExecuteResult.NeedConfirmation(
            description = "${action}订阅源「${source.sourceName}」"
        ) {
            source.enabled = enabled
            appDb.rssSourceDao.update(source)
            """{"success":true,"message":"已${action}订阅源「${source.sourceName}」"}"""
        }
    }

    private suspend fun confirmDeleteBookSource(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String
        if (sourceName.isNullOrBlank()) {
            return ToolExecuteResult.Data("""{"error":"source_name 参数不能为空"}""")
        }
        val source = appDb.bookSourceDao.allPart.find { it.bookSourceName == sourceName }
            ?: return ToolExecuteResult.Data("""{"error":"未找到书源: $sourceName"}""")
        return ToolExecuteResult.NeedConfirmation(
            description = "删除书源「${source.bookSourceName}」（此操作不可撤销）"
        ) {
            SourceHelp.deleteBookSource(source.bookSourceUrl)
            """{"success":true,"message":"已删除书源「${source.bookSourceName}」"}"""
        }
    }

    private suspend fun confirmUpdateBookSourceGroup(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String
        val groupName = args["group_name"] as? String
        if (sourceName.isNullOrBlank() || groupName.isNullOrBlank()) {
            return ToolExecuteResult.Data("""{"error":"source_name 和 group_name 参数不能为空"}""")
        }
        val source = appDb.bookSourceDao.allPart.find { it.bookSourceName == sourceName }
            ?: return ToolExecuteResult.Data("""{"error":"未找到书源: $sourceName"}""")
        val fullSource = appDb.bookSourceDao.getBookSource(source.bookSourceUrl)
            ?: return ToolExecuteResult.Data("""{"error":"获取书源详情失败"}""")
        return ToolExecuteResult.NeedConfirmation(
            description = "将书源「${source.bookSourceName}」移入分组「${groupName}」"
        ) {
            fullSource.addGroup(groupName)
            appDb.bookSourceDao.update(fullSource)
            """{"success":true,"message":"已将书源「${source.bookSourceName}」移入分组「${groupName}」"}"""
        }
    }

    private suspend fun confirmDeleteRssSource(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String
        if (sourceName.isNullOrBlank()) {
            return ToolExecuteResult.Data("""{"error":"source_name 参数不能为空"}""")
        }
        val source = appDb.rssSourceDao.all.find { it.sourceName == sourceName }
            ?: return ToolExecuteResult.Data("""{"error":"未找到订阅源: $sourceName"}""")
        return ToolExecuteResult.NeedConfirmation(
            description = "删除订阅源「${source.sourceName}」（此操作不可撤销）"
        ) {
            SourceHelp.deleteRssSource(source.sourceUrl)
            """{"success":true,"message":"已删除订阅源「${source.sourceName}」"}"""
        }
    }

    // ========== 工具方法 ==========

    private fun bookToSimple(book: Book): Map<String, Any?> {
        val groupNames = try {
            appDb.bookGroupDao.getGroupNames(book.group)
        } catch (_: Exception) {
            emptyList()
        }
        return mapOf(
            "name" to book.name,
            "author" to book.author,
            "group" to groupNames,
            "durChapter" to (book.durChapterTitle ?: ""),
            "latestChapter" to (book.latestChapterTitle ?: ""),
            "canUpdate" to book.canUpdate,
            "lastReadTime" to book.durChapterTime
        )
    }
}
