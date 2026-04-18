package io.legado.app.ui.book.read.ai

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.AiConfig
import io.legado.app.help.config.MemoryItem
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.legado.app.data.appDb
import io.legado.app.help.book.BookHelp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean

class AiChatViewModel(application: Application) : BaseViewModel(application) {

    val messagesLiveData = MutableLiveData<List<ChatMessage>>()
    val wordCountLiveData = MutableLiveData<Int>()
    /** true = 正在等待 AI 回复；false = 空闲 */
    val isGeneratingLiveData = MutableLiveData<Boolean>(false)
    /** 触发「本次交流已保存」Toast，值为保存时间戳（用于去重） */
    val memorySavedEvent = MutableLiveData<Long>()

    // 私有可变列表，外部只能读取快照
    private val _messages = mutableListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages.toList()

    // 使用 AtomicBoolean 保证并发安全
    private val isGenerating = AtomicBoolean(false)

    // 防止 calculateWordCount 高频触发：用最新的请求结果覆盖旧结果
    private val wordCountJobVersion = java.util.concurrent.atomic.AtomicLong(0L)

    fun calculateWordCount(bookUrl: String, start: Int, end: Int) {
        val chapterSize = ReadBook.chapterSize
        val clampedStart = start.coerceIn(1, chapterSize.coerceAtLeast(1))
        val clampedEnd = end.coerceIn(1, chapterSize.coerceAtLeast(1))
        val st = minOf(clampedStart, clampedEnd)
        val ed = maxOf(clampedStart, clampedEnd)

        // 防抖：递增版本号，协程内部检查版本是否仍是最新，否则放弃
        val myVersion = wordCountJobVersion.incrementAndGet()
        execute {
            var totalCount = 0
            val book = ReadBook.book ?: return@execute
            val chapterList = appDb.bookChapterDao.getChapterList(bookUrl, st - 1, ed - 1)
            for (chapter in chapterList) {
                if (wordCountJobVersion.get() != myVersion) return@execute // 被更新的请求抢占，退出
                val content = BookHelp.getContent(book, chapter)
                if (content != null) {
                    totalCount += content.length
                }
            }
            if (wordCountJobVersion.get() == myVersion) {
                wordCountLiveData.postValue(totalCount)
            }
        }
    }

    fun initMessages(start: Int, end: Int) {
        val currentBookUrl = ReadBook.book?.bookUrl ?: ""
        val cached = AiChatCache.state
        if (cached.bookUrl == currentBookUrl &&
            cached.chapterIndex == start &&
            cached.messages.isNotEmpty()
        ) {
            synchronized(_messages) {
                _messages.clear()
                _messages.addAll(cached.messages)
            }
            messagesLiveData.postValue(_messages.toList())
            return
        }

        // 不提前 clear，避免短暂空列表被观察者渲染
        execute {
            val systemPrompt = buildSystemPrompt(start, end)
            synchronized(_messages) {
                _messages.clear()
                _messages.add(ChatMessage("system", systemPrompt))
            }
            AiChatCache.state = AiChatCache.State(
                bookUrl = currentBookUrl,
                chapterIndex = start,
                messages = _messages.toList()
            )
            messagesLiveData.postValue(_messages.toList())
        }
    }

    private suspend fun buildSystemPrompt(start: Int, end: Int): String {
        return withContext(Dispatchers.IO) {
            val chapterSize = ReadBook.chapterSize
            val clampedStart = start.coerceIn(1, chapterSize.coerceAtLeast(1))
            val clampedEnd = end.coerceIn(1, chapterSize.coerceAtLeast(1))
            val st = minOf(clampedStart, clampedEnd)
            val ed = maxOf(clampedStart, clampedEnd)
            buildString {
                append("【人设与要求】\n")
                append(AiConfig.persona)
                val memoryCtx = AiConfig.buildMemoryContext()
                if (memoryCtx.isNotBlank()) {
                    append("\n\n【之前的对话记忆】\n")
                    append(memoryCtx)
                }
                append("\n\n【参考章节内容】\n")
                val book = ReadBook.book
                if (book != null) {
                    val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl, st - 1, ed - 1)
                    for (chapter in chapterList) {
                        val content = BookHelp.getContent(book, chapter) ?: continue
                        append("=== ${chapter.title} ===\n")
                        append(content)
                        append("\n\n")
                    }
                }
            }
        }
    }

    fun sendMessage(userText: String, start: Int, end: Int) {
        if (!isGenerating.compareAndSet(false, true)) return
        isGeneratingLiveData.postValue(true)
        if (userText.isBlank()) {
            isGenerating.set(false)
            isGeneratingLiveData.postValue(false)
            return
        }

        synchronized(_messages) {
            _messages.add(ChatMessage("user", userText))
        }
        syncCache()
        messagesLiveData.postValue(_messages.toList())

        execute {
            try {
                val newSystemPrompt = buildSystemPrompt(start, end)
                synchronized(_messages) {
                    if (_messages.isNotEmpty() && _messages.first().role == "system") {
                        _messages[0] = ChatMessage("system", newSystemPrompt)
                    } else {
                        _messages.add(0, ChatMessage("system", newSystemPrompt))
                    }
                }

                val responseText = requestOpenAi(_messages.toList())
                synchronized(_messages) {
                    _messages.add(ChatMessage("assistant", responseText))
                }
            } catch (e: Exception) {
                synchronized(_messages) {
                    _messages.add(ChatMessage("assistant", "请求失败: ${e.message}"))
                }
            } finally {
                isGenerating.set(false)
                isGeneratingLiveData.postValue(false)
                syncCache()
                messagesLiveData.postValue(_messages.toList())
            }
        }
    }

    fun summarizeAndMemory(chapterStart: Int, chapterEnd: Int) {
        if (!isGenerating.compareAndSet(false, true)) return
        isGeneratingLiveData.postValue(true)
        if (_messages.isEmpty()) {
            isGenerating.set(false)
            isGeneratingLiveData.postValue(false)
            return
        }

        execute {
            try {
                val tempMessages = _messages.toMutableList()
                tempMessages.add(
                    ChatMessage(
                        "user",
                        "请简要总结以上我们探讨的核心内容，提取关键点。这段总结将被作为记忆保留，用于未来的对话上下文。"
                    )
                )

                val responseText = requestOpenAi(tempMessages)
                val now = System.currentTimeMillis()
                AiConfig.addMemory(
                    MemoryItem(
                        key = now,
                        content = responseText,
                        start = chapterStart,
                        end = chapterEnd
                    )
                )
                memorySavedEvent.postValue(now)
                _messages.add(ChatMessage("assistant", "【系统提示】记忆已保存。\n\n本次记忆内容：\n$responseText"))
            } catch (e: Exception) {
                _messages.add(ChatMessage("assistant", "记忆提取失败: ${e.message}"))
            } finally {
                isGenerating.set(false)
                isGeneratingLiveData.postValue(false)
                syncCache()
                messagesLiveData.postValue(_messages.toList())
            }
        }
    }

    /** 将当前消息列表原子性地同步到缓存 */
    private fun syncCache() {
        val current = AiChatCache.state
        AiChatCache.state = current.copy(messages = _messages.toList())
    }

    private suspend fun requestOpenAi(chatMessages: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val url = AiConfig.apiUrl
            val apiKey = AiConfig.apiKey
            val model = AiConfig.model

            val messagesJsonList = chatMessages.map {
                mapOf("role" to it.role, "content" to it.content)
            }

            val requestBodyMap = mapOf(
                "model" to model,
                "messages" to messagesJsonList
            )

            val jsonBody = GSON.toJson(requestBodyMap)
            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val responseString = okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body.string()
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: $bodyStr")
                }
                bodyStr.ifBlank { throw Exception("Empty response body") }
            }

            val jsonObject = GSON.fromJsonObject<Map<String, Any>>(responseString).getOrThrow()
            val choices = jsonObject["choices"] as? List<*>
            val firstChoice = choices?.firstOrNull() as? Map<*, *>
            val messageMap = firstChoice?.get("message") as? Map<*, *>
            val content = messageMap?.get("content") as? String

            return@withContext content ?: throw Exception("解析响应失败")
        }
}

data class ChatMessage(val role: String, val content: String)

/** 应用级内存缓存，持久化跨 Activity 周期的聊天记录 */
object AiChatCache {
    data class State(
        val bookUrl: String = "",
        val chapterIndex: Int = -1,
        val messages: List<ChatMessage> = emptyList()
    )

    // @Volatile 保证 state 引用的可见性，data class 整体替换保证原子性
    @Volatile
    var state: State = State()
}
