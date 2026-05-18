package io.legado.app.ui.book.read.ai.tool

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.http.okHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.SourceCallBack
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class ToolExecuteResult {
    data class Data(val json: String) : ToolExecuteResult()

    /**
     * 可批量确认的操作，同一轮所有此类操作合并为一次弹窗确认
     */
    data class BatchConfirmation(
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
                    // ===== 只读工具 =====
                    "get_bookshelf" -> ToolExecuteResult.Data(getBookshelf(args))
                    "search_bookshelf" -> ToolExecuteResult.Data(searchBookshelf(args))
                    "get_book_sources" -> ToolExecuteResult.Data(getBookSources(args))
                    "get_rss_sources" -> ToolExecuteResult.Data(getRssSources(args))
                    "get_reading_stats" -> ToolExecuteResult.Data(getReadingStats())
                    "get_book_chapters" -> ToolExecuteResult.Data(getBookChapters(args))
                    "get_book_groups" -> ToolExecuteResult.Data(getBookGroups())
                    "get_source_groups" -> ToolExecuteResult.Data(getSourceGroups())
                    "get_book_content" -> ToolExecuteResult.Data(getBookContent(args))
                    "search_online_book" -> ToolExecuteResult.Data(searchOnlineBook(args))
                    "get_replace_rules" -> ToolExecuteResult.Data(getReplaceRules(args))
                    "get_thoughts" -> ToolExecuteResult.Data(getThoughts(args))
                    "get_detailed_reading_record" -> ToolExecuteResult.Data(getDetailedReadingRecord(args))
                    // ===== 静默写操作 =====
                    "save_book_progress" -> ToolExecuteResult.Data(saveBookProgress(args))
                    "rate_book" -> ToolExecuteResult.Data(rateBook(args))
                    "set_book_note" -> ToolExecuteResult.Data(setBookNote(args))
                    // ===== 批量确认写操作 =====
                    "update_book_group" -> batchUpdateBookGroup(args)
                    "enable_book_source" -> batchEnableBookSource(args)
                    "enable_rss_source" -> batchEnableRssSource(args)
                    "update_book_source_group" -> batchUpdateBookSourceGroup(args)
                    "create_book_group" -> batchCreateBookGroup(args)
                    "mark_book_status" -> batchMarkBookStatus(args)
                    "save_replace_rule" -> batchSaveReplaceRule(args)
                    "delete_replace_rule" -> batchDeleteReplaceRule(args)
                    "save_book_source" -> batchSaveBookSource(args)
                    "manage_webdav" -> handleManageWebdav(args)
                    "export_to_obsidian" -> batchExportToObsidian(args)
                    "delete_book_source" -> batchDeleteBookSource(args)
                    "delete_rss_source" -> batchDeleteRssSource(args)
                    "delete_book" -> batchDeleteBook(args)
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
        if (keyword.isNullOrBlank()) return """{"error":"keyword 参数不能为空"}"""
        val books = appDb.bookDao.all.filter {
            it.name.contains(keyword, ignoreCase = true) || it.author.contains(keyword, ignoreCase = true)
        }
        return GSON.toJson(books.take(MAX_BOOKS).map { bookToSimple(it) })
    }

    private fun getBookSources(args: Map<*, *>): String {
        val enabled = args["enabled"] as? Boolean
        val group = args["group"] as? String
        val offset = (args["offset"] as? Number)?.toInt() ?: 0
        val allSources = appDb.bookSourceDao.allPart
        val sources = when (enabled) {
            true -> allSources.filter { it.enabled }
            false -> allSources.filter { !it.enabled }
            else -> allSources
        }
        val filtered = if (!group.isNullOrBlank()) sources.filter { it.bookSourceGroup?.contains(group) == true } else sources
        val page = filtered.drop(offset).take(MAX_SOURCES)
        return GSON.toJson(mapOf("total" to filtered.size, "offset" to offset, "count" to page.size,
            "hasMore" to (offset + page.size < filtered.size),
            "sources" to page.map { mapOf("name" to it.bookSourceName, "url" to it.bookSourceUrl, "enabled" to it.enabled, "group" to (it.bookSourceGroup ?: ""), "weight" to it.weight) }))
    }

    private fun getRssSources(args: Map<*, *>): String {
        val enabled = args["enabled"] as? Boolean
        val group = args["group"] as? String
        val offset = (args["offset"] as? Number)?.toInt() ?: 0
        val sources = when {
            enabled == true -> appDb.rssSourceDao.all.filter { it.enabled }
            enabled == false -> appDb.rssSourceDao.all.filter { !it.enabled }
            else -> appDb.rssSourceDao.all
        }
        val filtered = if (!group.isNullOrBlank()) sources.filter { it.sourceGroup?.contains(group) == true } else sources
        val page = filtered.drop(offset).take(MAX_SOURCES)
        return GSON.toJson(mapOf("total" to filtered.size, "offset" to offset, "count" to page.size,
            "hasMore" to (offset + page.size < filtered.size),
            "sources" to page.map { s: RssSource -> mapOf("name" to s.sourceName, "url" to s.sourceUrl, "enabled" to s.enabled, "group" to (s.sourceGroup ?: "")) }))
    }

    private fun getReadingStats(): String {
        val totalBooks = appDb.bookDao.allBookCount
        val totalReadTime = appDb.readRecordDao.allTime
        val allRecords = appDb.readRecordDao.allShow
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayReadTime = allRecords.filter { it.lastRead >= todayStart }.sumOf { it.readTime }
        val readDays = allRecords.map { Calendar.getInstance().apply { timeInMillis = it.lastRead }.get(Calendar.DAY_OF_YEAR) }.distinct().size
        val topBooks = allRecords.sortedByDescending { it.readTime }.take(MAX_TOP_BOOKS).map { mapOf("name" to it.bookName, "readTime" to it.readTime) }
        return GSON.toJson(mapOf("totalBooks" to totalBooks, "totalReadTimeMinutes" to (totalReadTime / 60000),
            "todayReadTimeMinutes" to (todayReadTime / 60000), "readDays" to readDays, "topBooks" to topBooks))
    }

    private fun getBookChapters(args: Map<*, *>): String {
        val bookName = args["book_name"] as? String
        if (bookName.isNullOrBlank()) return """{"error":"book_name 参数不能为空"}"""
        val book = appDb.bookDao.all.find { it.name == bookName } ?: return """{"error":"未找到书籍: $bookName"}"""
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        // index 使用 0-based（与 get_book_content 的 chapterIndex 参数一致）
        return GSON.toJson(chapters.take(MAX_CHAPTERS).map { ch -> mapOf("index" to ch.index, "title" to ch.title) })
    }

    private fun getBookGroups(): String {
        return GSON.toJson(appDb.bookGroupDao.all.map { g -> mapOf("id" to g.groupId, "name" to g.groupName) })
    }

    private fun getSourceGroups(): String {
        return GSON.toJson(appDb.bookSourceDao.allGroups())
    }

    // ========== 新增只读工具 ==========

    private fun getBookContent(args: Map<*, *>): String {
        val bookUrl = args["bookUrl"] as? String ?: return """{"error":"bookUrl 参数不能为空"}"""
        val chapterIndex = (args["chapterIndex"] as? Number)?.toInt() ?: return """{"error":"chapterIndex 参数不能为空"}"""
        val maxChars = ((args["maxChars"] as? Number)?.toInt() ?: 2000).coerceIn(1, 8000)
        val book = appDb.bookDao.getBook(bookUrl) ?: return """{"error":"书架中未找到该书籍"}"""
        val chapter = appDb.bookChapterDao.getChapter(bookUrl, chapterIndex) ?: return """{"error":"未找到该章节，请先确认章节索引正确"}"""
        val rawContent = BookHelp.getContent(book, chapter) ?: return """{"error":"章节内容未缓存，请在阅读界面打开该章节后再试"}"""
        val truncated = rawContent.length > maxChars
        val content = if (truncated) rawContent.take(maxChars) else rawContent
        return GSON.toJson(mapOf("success" to true, "data" to mapOf(
            "bookName" to book.name, "chapterTitle" to chapter.title,
            "chapterIndex" to chapterIndex, "contentLength" to rawContent.length,
            "content" to content, "truncated" to truncated)))
    }

    private fun searchOnlineBook(args: Map<*, *>): String {
        val keyword = args["keyword"] as? String ?: return """{"error":"keyword 参数不能为空"}"""
        val limit = ((args["limit"] as? Number)?.toInt() ?: 10).coerceIn(1, 30)
        // WebSocket 搜索不适合在工具层同步阻塞实现，返回提示引导用户通过 UI 操作
        return GSON.toJson(mapOf("success" to false,
            "error" to "在线搜索需要通过 legado 阅读界面操作，AI 工具层暂不支持 WebSocket 流式搜索。建议：告知用户在 legado 搜索「$keyword」并将结果书名告知助手后再加入书架。",
            "keyword" to keyword, "limit" to limit))
    }

    private fun getReplaceRules(args: Map<*, *>): String {
        val offset = (args["offset"] as? Number)?.toInt() ?: 0
        val limit = ((args["limit"] as? Number)?.toInt() ?: 50).coerceIn(1, 100)
        val enabledOnly = args["enabledOnly"] as? Boolean ?: false
        val allRules = appDb.replaceRuleDao.all
        val filtered = if (enabledOnly) allRules.filter { it.isEnabled } else allRules
        val page = filtered.drop(offset).take(limit)
        return GSON.toJson(mapOf("success" to true, "data" to mapOf(
            "total" to filtered.size, "offset" to offset, "limit" to limit,
            "rules" to page.map { r -> mapOf("id" to r.id.toString(), "name" to r.name,
                "pattern" to r.pattern, "replacement" to r.replacement,
                "isRegex" to r.isRegex, "scope" to (r.scope ?: ""), "isEnabled" to r.isEnabled, "order" to r.order) })))
    }

    private fun getThoughts(args: Map<*, *>): String {
        val bookName = args["bookName"] as? String
        val offset = (args["offset"] as? Number)?.toInt() ?: 0
        val limit = ((args["limit"] as? Number)?.toInt() ?: 20).coerceIn(1, 100)
        val orderBy = args["orderBy"] as? String ?: "createTime"
        val order = args["order"] as? String ?: "desc"
        val allThoughts = if (bookName.isNullOrBlank()) {
            appDb.bookThoughtDao.all
        } else {
            appDb.bookDao.all.find { it.name == bookName }?.let { book ->
                appDb.bookThoughtDao.getByBook(book.name, book.author)
            } ?: emptyList()
        }
        val sorted = when (orderBy) {
            "chapterIndex" -> if (order == "asc") allThoughts.sortedBy { it.chapterIndex } else allThoughts.sortedByDescending { it.chapterIndex }
            else -> if (order == "asc") allThoughts.sortedBy { it.createTime } else allThoughts.sortedByDescending { it.createTime }
        }
        val page = sorted.drop(offset).take(limit)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return GSON.toJson(mapOf("success" to true, "data" to mapOf(
            "total" to sorted.size, "offset" to offset, "limit" to limit,
            "thoughts" to page.map { t -> mapOf("id" to t.id, "bookName" to t.bookName,
                "chapterName" to t.chapterName, "selectedText" to t.selectedText.take(200),
                "thought" to t.thought, "createTime" to sdf.format(Date(t.createTime))) })))
    }

    private fun getDetailedReadingRecord(args: Map<*, *>): String {
        val queryType = args["queryType"] as? String ?: "by_day"
        val bookName = args["bookName"] as? String
        val startDateStr = args["startDate"] as? String
        val endDateStr = args["endDate"] as? String
        val offset = (args["offset"] as? Number)?.toInt() ?: 0
        val limit = ((args["limit"] as? Number)?.toInt() ?: 20).coerceIn(1, 100)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayMs = 86400_000L
        val endTs = if (endDateStr != null) (sdf.parse(endDateStr)?.time ?: System.currentTimeMillis()) + dayMs else System.currentTimeMillis()
        val startTs = if (startDateStr != null) sdf.parse(startDateStr)?.time ?: (endTs - 7 * dayMs) else endTs - 7 * dayMs
        val allRecords = appDb.detailedReadRecordDao.all()
        val filtered = allRecords.filter { r ->
            r.startTime in startTs until endTs && (bookName.isNullOrBlank() || r.bookName == bookName)
        }
        val daySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return when (queryType) {
            "by_book" -> {
                val byBook = filtered.groupBy { it.bookName }
                val records = byBook.map { (name, recs) ->
                    val totalMin = recs.sumOf { (it.endTime - it.startTime) } / 60000
                    val days = recs.map { daySdf.format(Date(it.startTime)) }.distinct().size
                    mapOf("bookName" to name, "totalMinutes" to totalMin, "readDays" to days, "sessions" to recs.size)
                }.drop(offset).take(limit)
                GSON.toJson(mapOf("success" to true, "data" to mapOf("queryType" to "by_book", "total" to byBook.size, "offset" to offset, "limit" to limit, "records" to records)))
            }
            "by_range" -> {
                val page = filtered.sortedByDescending { it.startTime }.drop(offset).take(limit)
                val timeSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val records = page.map { r -> mapOf("bookName" to r.bookName, "startTime" to timeSdf.format(Date(r.startTime)),
                    "endTime" to timeSdf.format(Date(r.endTime)), "durationMinutes" to ((r.endTime - r.startTime) / 60000),
                    "isAbnormal" to ((r.endTime - r.startTime) > 4 * 3600_000L)) }
                GSON.toJson(mapOf("success" to true, "data" to mapOf("queryType" to "by_range", "total" to filtered.size, "offset" to offset, "limit" to limit, "records" to records)))
            }
            else -> { // by_day
                val byDay = filtered.groupBy { daySdf.format(Date(it.startTime)) }
                val records = byDay.entries.sortedByDescending { it.key }.drop(offset).take(limit).map { (date, recs) ->
                    mapOf("date" to date, "readMinutes" to recs.sumOf { (it.endTime - it.startTime) } / 60000,
                        "sessions" to recs.size, "booksRead" to recs.map { it.bookName }.distinct())
                }
                val totalMin = filtered.sumOf { (it.endTime - it.startTime) } / 60000
                GSON.toJson(mapOf("success" to true, "data" to mapOf("queryType" to "by_day", "total" to byDay.size,
                    "totalMinutes" to totalMin, "offset" to offset, "limit" to limit, "records" to records)))
            }
        }
    }
    // ========== 静默写操作 ==========

    private fun saveBookProgress(args: Map<*, *>): String {
        val bookUrl = args["bookUrl"] as? String ?: return """{"error":"bookUrl 参数不能为空"}"""
        val durChapterIndex = (args["durChapterIndex"] as? Number)?.toInt() ?: return """{"error":"durChapterIndex 参数不能为空"}"""
        val durChapterPos = (args["durChapterPos"] as? Number)?.toInt() ?: 0
        val durChapterTitle = args["durChapterTitle"] as? String
        val book = appDb.bookDao.getBook(bookUrl) ?: return """{"error":"书架中未找到该书籍"}"""
        book.durChapterIndex = durChapterIndex
        book.durChapterPos = durChapterPos
        if (durChapterTitle != null) book.durChapterTitle = durChapterTitle
        book.durChapterTime = System.currentTimeMillis()
        appDb.bookDao.update(book)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return GSON.toJson(mapOf("success" to true, "data" to mapOf(
            "bookName" to book.name, "bookUrl" to bookUrl,
            "durChapterIndex" to durChapterIndex, "durChapterPos" to durChapterPos,
            "savedAt" to sdf.format(Date()))))
    }

    private fun rateBook(args: Map<*, *>): String {
        val bookUrl = args["bookUrl"] as? String ?: return """{"error":"bookUrl 参数不能为空"}"""
        val rating = (args["rating"] as? Number)?.toFloat() ?: return """{"error":"rating 参数不能为空"}"""
        if (rating < 0f || rating > 5f) return """{"error":"rating 必须在 0.0 到 5.0 之间"}"""
        val book = appDb.bookDao.getBook(bookUrl) ?: return """{"error":"书架中未找到该书籍"}"""
        val previousRating = book.bookRating
        book.bookRating = rating
        appDb.bookDao.update(book)
        return GSON.toJson(mapOf("success" to true, "data" to mapOf(
            "bookName" to book.name, "bookUrl" to bookUrl,
            "previousRating" to previousRating, "newRating" to rating)))
    }

    private fun setBookNote(args: Map<*, *>): String {
        val bookUrl = args["bookUrl"] as? String ?: return """{"error":"bookUrl 参数不能为空"}"""
        @Suppress("UNCHECKED_CAST")
        val notesRaw = args["notes"] as? List<*> ?: return """{"error":"notes 参数不能为空且必须是数组"}"""
        if (notesRaw.isEmpty()) return """{"error":"notes 数组不能为空"}"""
        val book = appDb.bookDao.getBook(bookUrl) ?: return """{"error":"书架中未找到该书籍"}"""
        val AI_TAG = "\n——由AI助手生成"
        var written = 0; var failed = 0
        val resultItems = mutableListOf<Map<String, Any?>>()
        notesRaw.forEach { item ->
            val m = item as? Map<*, *> ?: run { failed++; return@forEach }
            val chapterIndex = (m["chapterIndex"] as? Number)?.toInt() ?: run { failed++; return@forEach }
            val thoughtText = (m["thought"] as? String)?.takeIf { it.isNotBlank() } ?: run { failed++; return@forEach }
            val chapter = appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
            val chapterName = chapter?.title ?: "第${chapterIndex + 1}章"
            // 确定 selectedText：优先用参数传入，其次取章节缓存内容，最后用章节标题
            val providedText = (m["selectedText"] as? String)?.takeIf { it.isNotBlank() }
            val selectedText = when {
                providedText != null -> providedText
                chapter != null -> {
                    val cached = BookHelp.getContent(book, chapter)
                    if (!cached.isNullOrBlank()) cached else chapterName
                }
                else -> chapterName
            }
            val finalThought = thoughtText.trimEnd() + AI_TAG
            try {
                val thought = io.legado.app.data.entities.BookThought(
                    bookName = book.name,
                    bookAuthor = book.author,
                    chapterIndex = chapterIndex,
                    chapterPos = 0,
                    chapterName = chapterName,
                    selectedText = selectedText,
                    textHash = selectedText.hashCode().toString(),
                    thought = finalThought
                )
                val ids = appDb.bookThoughtDao.insert(thought)
                val thoughtId = ids.firstOrNull() ?: 0L
                written++
                resultItems.add(mapOf("chapterIndex" to chapterIndex, "chapterName" to chapterName,
                    "thoughtId" to thoughtId, "selectedText" to selectedText,
                    "thought" to finalThought))
            } catch (e: Exception) {
                failed++
                resultItems.add(mapOf("chapterIndex" to chapterIndex, "error" to e.message))
            }
        }
        return GSON.toJson(mapOf("success" to true, "data" to mapOf(
            "bookName" to book.name, "total" to notesRaw.size,
            "written" to written, "failed" to failed, "thoughts" to resultItems)))
    }


    // ========== 批量确认写操作（已有） ==========

    private suspend fun batchUpdateBookGroup(args: Map<*, *>): ToolExecuteResult {
        val bookName = args["book_name"] as? String
        val groupName = args["group_name"] as? String
        if (bookName.isNullOrBlank() || groupName.isNullOrBlank()) return ToolExecuteResult.Data("""{"error":"book_name 和 group_name 参数不能为空"}""")
        val book = appDb.bookDao.all.find { it.name == bookName } ?: return ToolExecuteResult.Data("""{"error":"未找到书籍: $bookName"}""")
        val group = appDb.bookGroupDao.getByName(groupName) ?: return ToolExecuteResult.Data("""{"error":"未找到分组: $groupName"}""")
        return ToolExecuteResult.BatchConfirmation(description = "将《${book.name}》移入分组「${group.groupName}」") {
            withContext(Dispatchers.IO) { book.group = group.groupId; book.save() }
            """{"success":true,"message":"已将《${book.name}》移入分组「${group.groupName}」"}"""
        }
    }

    private suspend fun batchEnableBookSource(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String
        val enabled = args["enabled"] as? Boolean
        if (sourceName.isNullOrBlank() || enabled == null) return ToolExecuteResult.Data("""{"error":"source_name 和 enabled 参数不能为空"}""")
        val source = appDb.bookSourceDao.allPart.find { it.bookSourceName == sourceName } ?: return ToolExecuteResult.Data("""{"error":"未找到书源: $sourceName"}""")
        val action = if (enabled) "启用" else "禁用"
        return ToolExecuteResult.BatchConfirmation(description = "${action}书源「${source.bookSourceName}」") {
            withContext(Dispatchers.IO) { appDb.bookSourceDao.enable(source.bookSourceUrl, enabled) }
            """{"success":true,"message":"已${action}书源「${source.bookSourceName}」"}"""
        }
    }

    private suspend fun batchEnableRssSource(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String
        val enabled = args["enabled"] as? Boolean
        if (sourceName.isNullOrBlank() || enabled == null) return ToolExecuteResult.Data("""{"error":"source_name 和 enabled 参数不能为空"}""")
        val source = appDb.rssSourceDao.all.find { it.sourceName == sourceName } ?: return ToolExecuteResult.Data("""{"error":"未找到订阅源: $sourceName"}""")
        val action = if (enabled) "启用" else "禁用"
        return ToolExecuteResult.BatchConfirmation(description = "${action}订阅源「${source.sourceName}」") {
            withContext(Dispatchers.IO) { source.enabled = enabled; appDb.rssSourceDao.update(source) }
            """{"success":true,"message":"已${action}订阅源「${source.sourceName}」"}"""
        }
    }

    private suspend fun batchUpdateBookSourceGroup(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String
        val groupName = args["group_name"] as? String
        if (sourceName.isNullOrBlank() || groupName.isNullOrBlank()) return ToolExecuteResult.Data("""{"error":"source_name 和 group_name 参数不能为空"}""")
        val source = appDb.bookSourceDao.allPart.find { it.bookSourceName == sourceName } ?: return ToolExecuteResult.Data("""{"error":"未找到书源: $sourceName"}""")
        val fullSource = appDb.bookSourceDao.getBookSource(source.bookSourceUrl) ?: return ToolExecuteResult.Data("""{"error":"获取书源详情失败"}""")
        val oldGroup = fullSource.bookSourceGroup ?: ""
        return ToolExecuteResult.BatchConfirmation(description = "将书源「${source.bookSourceName}」从分组「$oldGroup」移入「$groupName」") {
            withContext(Dispatchers.IO) { fullSource.bookSourceGroup = groupName; appDb.bookSourceDao.update(fullSource) }
            """{"success":true,"message":"已将书源「${source.bookSourceName}」移入分组「$groupName」"}"""
        }
    }

    private suspend fun batchCreateBookGroup(args: Map<*, *>): ToolExecuteResult {
        val groupName = args["group_name"] as? String
        if (groupName.isNullOrBlank()) return ToolExecuteResult.Data("""{"error":"group_name 参数不能为空"}""")
        if (!appDb.bookGroupDao.canAddGroup) return ToolExecuteResult.Data("""{"error":"分组数量已达上限（最多64个）"}""")
        if (appDb.bookGroupDao.getByName(groupName) != null) return ToolExecuteResult.Data("""{"error":"分组「$groupName」已存在"}""")
        val maxOrder = appDb.bookGroupDao.maxOrder
        return ToolExecuteResult.BatchConfirmation(description = "创建书籍分组「$groupName」") {
            withContext(Dispatchers.IO) {
                val newId = appDb.bookGroupDao.getUnusedId()
                appDb.bookGroupDao.insert(BookGroup(groupId = newId, groupName = groupName, order = maxOrder + 1, show = true))
            }
            """{"success":true,"message":"已创建分组「$groupName」"}"""
        }
    }

    // ========== 新增批量确认写操作（P0/P1） ==========

    private suspend fun batchMarkBookStatus(args: Map<*, *>): ToolExecuteResult {
        val bookUrl = args["bookUrl"] as? String ?: return ToolExecuteResult.Data("""{"error":"bookUrl 参数不能为空"}""")
        val status = (args["status"] as? Number)?.toInt() ?: return ToolExecuteResult.Data("""{"error":"status 参数不能为空"}""")
        if (status < 0 || status > 10) return ToolExecuteResult.Data("""{"error":"status 必须在 0 到 10 之间"}""")
        val book = appDb.bookDao.getBook(bookUrl) ?: return ToolExecuteResult.Data("""{"error":"书架中未找到该书籍"}""")
        val statusLabel = readIterationLabel(status)
        val prevStatus = book.readIteration  // 在 action 执行前保存旧值
        val prevLabel = readIterationLabel(prevStatus)
        return ToolExecuteResult.BatchConfirmation(description = "将《${book.name}》阅读状态从「$prevLabel」标记为「$statusLabel」") {
            withContext(Dispatchers.IO) { book.readIteration = status; appDb.bookDao.update(book) }
            GSON.toJson(mapOf("success" to true, "data" to mapOf("bookName" to book.name,
                "previousStatus" to prevStatus, "newStatus" to status, "statusLabel" to statusLabel)))
        }
    }

    private suspend fun batchSaveReplaceRule(args: Map<*, *>): ToolExecuteResult {
        @Suppress("UNCHECKED_CAST")
        val rulesRaw = args["rules"] as? List<*> ?: return ToolExecuteResult.Data("""{"error":"rules 参数不能为空且必须是数组"}""")
        if (rulesRaw.isEmpty()) return ToolExecuteResult.Data("""{"error":"rules 数组不能为空"}""")
        val descriptions = rulesRaw.mapIndexedNotNull { i, item ->
            val m = item as? Map<*, *> ?: return@mapIndexedNotNull null
            val name = m["name"] as? String ?: "规则${i+1}"
            val hasId = (m["id"] as? String)?.isNotBlank() == true
            if (hasId) "修改规则「$name」" else "新建规则「$name」"
        }
        if (descriptions.isEmpty()) return ToolExecuteResult.Data("""{"error":"rules 格式不正确"}""")
        return ToolExecuteResult.BatchConfirmation(description = descriptions.joinToString("、")) {
            withContext(Dispatchers.IO) {
                var created = 0; var updated = 0; var failed = 0
                val resultItems = rulesRaw.map { item ->
                    val m = item as? Map<*, *>
                    if (m == null) { failed++; return@map mapOf("error" to "格式错误") }
                    try {
                        val idStr = (m["id"] as? String)?.takeIf { it.isNotBlank() }
                        val isNew = idStr == null
                        val id = idStr?.toLongOrNull() ?: System.currentTimeMillis()
                        val rule = ReplaceRule(
                            id = id, name = m["name"] as? String ?: "",
                            pattern = m["pattern"] as? String ?: "",
                            replacement = m["replacement"] as? String ?: "",
                            isRegex = m["isRegex"] as? Boolean ?: false,
                            scope = m["scope"] as? String ?: "",
                            isEnabled = m["isEnabled"] as? Boolean ?: true,
                            order = (m["order"] as? Number)?.toInt() ?: if (isNew) appDb.replaceRuleDao.maxOrder + 1 else 0
                        )
                        appDb.replaceRuleDao.insert(rule)
                        if (isNew) { created++; mapOf("id" to id, "name" to rule.name, "action" to "created") }
                        else { updated++; mapOf("id" to id, "name" to rule.name, "action" to "updated") }
                    } catch (e: Exception) { failed++; mapOf("error" to e.message) }
                }
                GSON.toJson(mapOf("success" to true, "data" to mapOf("created" to created, "updated" to updated, "failed" to failed, "rules" to resultItems)))
            }
        }
    }

    private suspend fun batchDeleteReplaceRule(args: Map<*, *>): ToolExecuteResult {
        @Suppress("UNCHECKED_CAST")
        val ids = args["ids"] as? List<*> ?: return ToolExecuteResult.Data("""{"error":"ids 参数不能为空且必须是数组"}""")
        if (ids.isEmpty()) return ToolExecuteResult.Data("""{"error":"ids 数组不能为空"}""")
        val allRules = appDb.replaceRuleDao.all
        val toDelete = ids.mapNotNull { id -> allRules.find { it.id == (id as? String)?.toLongOrNull() } }
        if (toDelete.isEmpty()) return ToolExecuteResult.Data("""{"error":"未找到任何指定的规则"}""")
        val nameList = toDelete.joinToString("、") { "「${it.name}」" }
        return ToolExecuteResult.BatchConfirmation(description = "删除以下替换规则（不可撤销）：$nameList") {
            withContext(Dispatchers.IO) {
                val deleted = mutableListOf<String>(); val failed = mutableListOf<String>()
                toDelete.forEach { rule ->
                    try { appDb.replaceRuleDao.delete(rule); deleted.add(rule.id.toString()) }
                    catch (e: Exception) { failed.add(rule.id.toString()) }
                }
                GSON.toJson(mapOf("success" to true, "data" to mapOf("deleted" to deleted, "failed" to failed, "totalDeleted" to deleted.size)))
            }
        }
    }

    private suspend fun batchSaveBookSource(args: Map<*, *>): ToolExecuteResult {
        val sourceJson = args["sourceJson"] as? String
        val sourceUrl = args["sourceUrl"] as? String
        val enableAfterImport = args["enableAfterImport"] as? Boolean ?: true
        if (sourceJson.isNullOrBlank() && sourceUrl.isNullOrBlank()) return ToolExecuteResult.Data("""{"error":"sourceJson 和 sourceUrl 至少提供一个"}""")
        val jsonStr = if (!sourceJson.isNullOrBlank()) sourceJson else {
            try { okHttpClient.newCall(okhttp3.Request.Builder().url(sourceUrl!!).build()).execute().use { it.body.string() } }
            catch (e: Exception) { return ToolExecuteResult.Data("""{"error":"拉取书源URL失败: ${e.message}"}""") }
        }
        val sourceList = try {
            GSON.fromJsonArray<io.legado.app.data.entities.BookSource>(jsonStr).getOrNull()
                ?: listOf(GSON.fromJsonObject<io.legado.app.data.entities.BookSource>(jsonStr).getOrNull()).filterNotNull()
        } catch (e: Exception) { return ToolExecuteResult.Data("""{"error":"书源JSON解析失败: ${e.message}"}""") }
        if (sourceList.isEmpty()) return ToolExecuteResult.Data("""{"error":"未解析到有效书源"}""")
        val nameList = sourceList.take(10).joinToString("、") { "「${it.bookSourceName}」" }
        val more = if (sourceList.size > 10) "等共${sourceList.size}个" else ""
        return ToolExecuteResult.BatchConfirmation(description = "导入书源：$nameList$more") {
            withContext(Dispatchers.IO) {
                var imported = 0; var updated = 0; var failed = 0
                val resultItems = mutableListOf<Map<String, Any>>()
                sourceList.forEach { src ->
                    try {
                        if (enableAfterImport) src.enabled = true
                        val exists = appDb.bookSourceDao.getBookSource(src.bookSourceUrl) != null
                        appDb.bookSourceDao.insert(src)
                        if (exists) updated++ else imported++
                        resultItems.add(mapOf("bookSourceName" to src.bookSourceName, "action" to if (exists) "updated" else "created"))
                    } catch (e: Exception) { failed++; resultItems.add(mapOf("bookSourceName" to src.bookSourceName, "error" to (e.message ?: "未知错误"))) }
                }
                GSON.toJson(mapOf("success" to true, "data" to mapOf("imported" to imported, "updated" to updated, "failed" to failed, "sources" to resultItems)))
            }
        }
    }

    private suspend fun handleManageWebdav(args: Map<*, *>): ToolExecuteResult {
        val action = args["action"] as? String ?: return ToolExecuteResult.Data("""{"error":"action 参数不能为空"}""")
        val filename = args["filename"] as? String
        return when (action) {
            "list" -> {
                val files = try { AppWebDav.getBackupFileList() } catch (e: Exception) { return ToolExecuteResult.Data("""{"error":"获取备份列表失败: ${e.message}"}""") }
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                ToolExecuteResult.Data(GSON.toJson(mapOf("success" to true, "data" to mapOf("action" to "list", "total" to files.size,
                    "backups" to files.map { f -> mapOf("filename" to f.displayName, "lastModified" to sdf.format(Date(f.lastModify))) }))))
            }
            "restore" -> {
                if (filename.isNullOrBlank()) return ToolExecuteResult.Data("""{"error":"restore 操作需要提供 filename"}""")
                ToolExecuteResult.BatchConfirmation(description = "恢复备份「$filename」（将覆盖当前所有设置，不可撤销）") {
                    try { AppWebDav.restoreWebDav(filename); """{"success":true,"message":"备份「$filename」恢复成功，legado 将重载配置"}""" }
                    catch (e: Exception) { """{"success":false,"error":"恢复失败: ${e.message}"}""" }
                }
            }
            "delete" -> {
                if (filename.isNullOrBlank()) return ToolExecuteResult.Data("""{"error":"delete 操作需要提供 filename"}""")
                ToolExecuteResult.BatchConfirmation(description = "删除备份「$filename」（不可撤销）") {
                    try { AppWebDav.deleteBackup(filename); """{"success":true,"message":"已删除备份「$filename」"}""" }
                    catch (e: Exception) { """{"success":false,"error":"删除失败: ${e.message}"}""" }
                }
            }
            else -> ToolExecuteResult.Data("""{"error":"未知 action: $action，支持 list/restore/delete"}""")
        }
    }

    private suspend fun batchExportToObsidian(args: Map<*, *>): ToolExecuteResult {
        val bookName = args["bookName"] as? String ?: return ToolExecuteResult.Data("""{"error":"bookName 参数不能为空"}""")
        val template = args["template"] as? String ?: "default"
        val obsidianUrl = args["obsidianUrl"] as? String ?: return ToolExecuteResult.Data("""{"error":"obsidianUrl 参数不能为空，请提供 Obsidian Local REST API 地址"}""")
        val obsidianApiKey = args["obsidianApiKey"] as? String ?: return ToolExecuteResult.Data("""{"error":"obsidianApiKey 参数不能为空"}""")
        val exportPath = args["exportPath"] as? String
        val book = appDb.bookDao.all.find { it.name == bookName } ?: return ToolExecuteResult.Data("""{"error":"书架中未找到书籍: $bookName"}""")
        val thoughts = appDb.bookThoughtDao.getByBook(book.name, book.author)
        val safeBookName = bookName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val targetPath = exportPath ?: "Reading/$safeBookName.md"
        return ToolExecuteResult.BatchConfirmation(description = "将《$bookName》的阅读笔记（${thoughts.size}条想法）导出到 Obsidian：$targetPath") {
            withContext(Dispatchers.IO) {
                val md = buildObsidianMarkdown(book, thoughts, template)
                try {
                    val baseUrl = obsidianUrl.trimEnd('/')
                    val checkReq = okhttp3.Request.Builder().url("$baseUrl/vault/${java.net.URLEncoder.encode(targetPath, "UTF-8")}").get().addHeader("Authorization", "Bearer $obsidianApiKey").build()
                    val exists = try { okHttpClient.newCall(checkReq).execute().use { it.isSuccessful } } catch (_: Exception) { false }
                    val putReq = okhttp3.Request.Builder().url("$baseUrl/vault/${java.net.URLEncoder.encode(targetPath, "UTF-8")}").put(md.toByteArray(Charsets.UTF_8).let { it.toRequestBody("text/markdown; charset=utf-8".toMediaType()) }).addHeader("Authorization", "Bearer $obsidianApiKey").build()
                    okHttpClient.newCall(putReq).execute().use { resp ->
                        if (resp.isSuccessful) GSON.toJson(mapOf("success" to true, "data" to mapOf("bookName" to bookName, "exportedPath" to targetPath, "thoughtsCount" to thoughts.size, "noteLength" to md.length, "action" to if (exists) "updated" else "created")))
                        else """{"success":false,"error":"Obsidian API 返回 ${resp.code}: ${resp.body?.string()}"}"""
                    }
                } catch (e: Exception) { """{"success":false,"error":"导出失败: ${e.message}"}""" }
            }
        }
    }

    // ========== 批量确认删除操作 ==========

    private suspend fun batchDeleteBook(args: Map<*, *>): ToolExecuteResult {
        val bookName = args["book_name"] as? String ?: return ToolExecuteResult.Data("""{"error":"book_name 参数不能为空"}""")
        val book = appDb.bookDao.all.find { it.name == bookName } ?: return ToolExecuteResult.Data("""{"error":"未找到书籍: $bookName"}""")
        return ToolExecuteResult.BatchConfirmation(description = "从书架移除《${book.name}》（不会删除本地文件，此操作不可撤销）") {
            withContext(Dispatchers.IO) {
                appDb.bookDao.delete(book)
                if (book.isLocal) { LocalBook.deleteBook(book, false) }
                else { val source = appDb.bookSourceDao.getBookSource(book.origin); SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, source, book) }
            }
            """{"success":true,"message":"已从书架移除《${book.name}》"}"""
        }
    }

    private suspend fun batchDeleteBookSource(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String ?: return ToolExecuteResult.Data("""{"error":"source_name 参数不能为空"}""")
        val source = appDb.bookSourceDao.allPart.find { it.bookSourceName == sourceName } ?: return ToolExecuteResult.Data("""{"error":"未找到书源: $sourceName"}""")
        return ToolExecuteResult.BatchConfirmation(description = "删除书源「${source.bookSourceName}」（此操作不可撤销）") {
            withContext(Dispatchers.IO) { SourceHelp.deleteBookSource(source.bookSourceUrl) }
            """{"success":true,"message":"已删除书源「${source.bookSourceName}」"}"""
        }
    }

    private suspend fun batchDeleteRssSource(args: Map<*, *>): ToolExecuteResult {
        val sourceName = args["source_name"] as? String ?: return ToolExecuteResult.Data("""{"error":"source_name 参数不能为空"}""")
        val source = appDb.rssSourceDao.all.find { it.sourceName == sourceName } ?: return ToolExecuteResult.Data("""{"error":"未找到订阅源: $sourceName"}""")
        return ToolExecuteResult.BatchConfirmation(description = "删除订阅源「${source.sourceName}」（此操作不可撤销）") {
            withContext(Dispatchers.IO) { SourceHelp.deleteRssSource(source.sourceUrl) }
            """{"success":true,"message":"已删除订阅源「${source.sourceName}」"}"""
        }
    }

    // ========== 工具方法 ==========

    private fun bookToSimple(book: Book): Map<String, Any?> {
        val groupNames = try { appDb.bookGroupDao.getGroupNames(book.group) } catch (_: Exception) { emptyList() }
        return mapOf("name" to book.name, "author" to book.author, "bookUrl" to book.bookUrl,
            "group" to groupNames, "durChapter" to (book.durChapterTitle ?: ""),
            "latestChapter" to (book.latestChapterTitle ?: ""),
            "rating" to book.bookRating, "readIteration" to book.readIteration,
            "readIterationLabel" to readIterationLabel(book.readIteration),
            "canUpdate" to book.canUpdate, "lastReadTime" to book.durChapterTime)
    }

    private fun readIterationLabel(iteration: Int): String = when {
        iteration == 0 -> "未读完"
        iteration == 1 -> "首次读完"
        iteration % 2 == 0 -> "${iteration / 2 + 1}刷完"
        else -> "${(iteration + 1) / 2}刷中"
    }

    private fun buildObsidianMarkdown(book: Book, thoughts: List<io.legado.app.data.entities.BookThought>, template: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val ratingStars = "⭐".repeat(book.bookRating.toInt())
        return buildString {
            appendLine("---")
            appendLine("title: ${book.name}")
            appendLine("author: ${book.author}")
            appendLine("rating: ${book.bookRating}/5")
            appendLine("status: ${readIterationLabel(book.readIteration)}")
            appendLine("tags: [读书笔记]")
            appendLine("created: ${sdf.format(Date())}")
            appendLine("---")
            appendLine()
            if (template != "minimal") {
                appendLine("## 📖 基本信息")
                appendLine("- **作者**：${book.author}")
                appendLine("- **评分**：${book.bookRating}/5 $ratingStars")
                appendLine("- **状态**：${readIterationLabel(book.readIteration)}")
                appendLine()
                if (!book.preReadNote.isNullOrBlank()) { appendLine("## 📝 阅读前记录"); appendLine(book.preReadNote!!); appendLine() }
                if (!book.postReadNote.isNullOrBlank()) { appendLine("## 💭 完读感想"); appendLine(book.postReadNote!!); appendLine() }
            }
            if (thoughts.isNotEmpty()) {
                appendLine("## 🖊️ 划线与想法（共 ${thoughts.size} 条）")
                appendLine()
                thoughts.groupBy { it.chapterName }.forEach { (chapterName, items) ->
                    appendLine("### $chapterName")
                    items.forEach { t ->
                        if (t.selectedText.isNotBlank()) appendLine("> ${t.selectedText}")
                        if (t.thought.isNotBlank()) { appendLine(); appendLine(t.thought) }
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                }
            }
        }
    }
}
