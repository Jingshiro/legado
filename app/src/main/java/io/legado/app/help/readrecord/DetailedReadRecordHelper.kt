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
