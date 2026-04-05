package io.legado.app.help.readrecord

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.DetailedReadRecord
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers.IO

private const val MIN_SESSION_DURATION = 60_000L

data class DetailedReadSession(
    val startTime: Long,
    val endTime: Long
)

data class DetailedReadRecordExport(
    val bookName: String,
    val sessions: List<DetailedReadSession>
)

object DetailedReadRecordHelper {

    fun buildExport(records: List<DetailedReadRecord>): List<DetailedReadRecordExport> {
        if (records.isEmpty()) return emptyList()
        return records.groupBy { it.bookName }.map { (bookName, sessions) ->
            DetailedReadRecordExport(
                bookName = bookName,
                sessions = sessions.sortedBy { it.startTime }.map {
                    DetailedReadSession(it.startTime, it.endTime)
                }
            )
        }.sortedBy { it.bookName }
    }

    fun buildExportJson(records: List<DetailedReadRecord>): String {
        if (records.isEmpty()) return "[]"
        val root = JsonArray()

        // 预先查询所有笔记并在内存中按书名分组，避免在循环中重复查询数据库，防止 N+1 性能问题
        val allThoughtsByBook = appDb.bookThoughtDao.all.groupBy { it.bookName }
        val allBookmarksByBook = appDb.bookmarkDao.all.groupBy { it.bookName }

        records.groupBy { it.bookName }.toSortedMap().forEach { (bookName, sessions) ->
            val obj = JsonObject()
            obj.addProperty("bookName", bookName)
            val sessionArray = JsonArray()
            sessions.sortedBy { it.startTime }.forEach { session ->
                val sessionObj = JsonObject()
                sessionObj.addProperty("startTime", session.startTime)
                sessionObj.addProperty("endTime", session.endTime)
                sessionArray.add(sessionObj)
            }
            obj.add("sessions", sessionArray)

            // 整合笔记：BookThought（想法）和 Bookmark（书签）
            val notesArray = JsonArray()

            // 从 BookThought 表中查询该书的所有想法
            val thoughts = allThoughtsByBook[bookName]?.sortedBy { it.createTime } ?: emptyList()
            thoughts.forEach { thought ->
                val noteObj = JsonObject()
                noteObj.addProperty("bookName", thought.bookName)
                noteObj.addProperty("chapterName", thought.chapterName)
                noteObj.addProperty("bookText", thought.selectedText)
                noteObj.addProperty("content", thought.thought)
                noteObj.addProperty("time", thought.createTime)
                notesArray.add(noteObj)
            }

            // 从 Bookmark 表中查询该书的所有书签（包含书签笔记内容）
            val bookmarks = allBookmarksByBook[bookName]?.sortedBy { it.time } ?: emptyList()
            bookmarks.forEach { bookmark ->
                val noteObj = JsonObject()
                noteObj.addProperty("bookName", bookmark.bookName)
                noteObj.addProperty("chapterName", bookmark.chapterName)
                noteObj.addProperty("bookText", bookmark.bookText)
                noteObj.addProperty("content", bookmark.content)
                noteObj.addProperty("time", bookmark.time)
                notesArray.add(noteObj)
            }

            obj.add("notes", notesArray)

            root.add(obj)
        }
        return GSON.toJson(root)
    }

    fun insertSession(bookName: String, startTime: Long, endTime: Long) {
        if (!AppConfig.enableReadRecord) return
        val duration = endTime - startTime
        if (duration <= MIN_SESSION_DURATION) return
        if (bookName.isBlank()) return
        Coroutine.async(context = IO) {
            appDb.detailedReadRecordDao.insert(
                DetailedReadRecord(
                    bookName = bookName,
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
    }

    fun insertFromExport(records: List<DetailedReadRecordExport>) {
        if (records.isEmpty()) return
        val insertList = records.flatMap { export ->
            export.sessions.mapNotNull { session ->
                val duration = session.endTime - session.startTime
                if (duration <= MIN_SESSION_DURATION || export.bookName.isBlank()) {
                    null
                } else {
                    DetailedReadRecord(
                        bookName = export.bookName,
                        startTime = session.startTime,
                        endTime = session.endTime
                    )
                }
            }
        }
        if (insertList.isEmpty()) return
        appDb.detailedReadRecordDao.insertAll(insertList)
    }
}

class DetailedReadRecordTracker(
    private val bookNameProvider: () -> String?
) {
    private var startTime: Long? = null
    private var bookName: String? = null

    fun start() {
        if (!AppConfig.enableReadRecord) return
        if (startTime != null) return
        val name = bookNameProvider()?.trim().orEmpty()
        if (name.isBlank()) return
        startTime = System.currentTimeMillis()
        bookName = name
    }

    fun stop() {
        val start = startTime ?: return
        val name = bookName ?: bookNameProvider()?.trim()
        startTime = null
        bookName = null
        if (name.isNullOrBlank()) return
        DetailedReadRecordHelper.insertSession(name, start, System.currentTimeMillis())
    }
}

class DetailedReadRecordLifecycleObserver(
    private val tracker: DetailedReadRecordTracker
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        tracker.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        tracker.stop()
    }
}
