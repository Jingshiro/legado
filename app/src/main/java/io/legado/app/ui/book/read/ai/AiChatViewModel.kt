package io.legado.app.ui.book.read.ai

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.AiConfig
import io.legado.app.help.config.AiMemoryItem
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ai.tool.AiToolDef
import io.legado.app.ui.book.read.ai.tool.ToolExecuteResult
import io.legado.app.ui.book.read.ai.tool.ToolRouter
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.legado.app.data.appDb
import io.legado.app.help.book.BookHelp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean

class AiChatViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        private const val MAX_TOOL_ROUNDS = 5
    }

    val messagesLiveData = MutableLiveData<List<ChatMessage>>()
    val wordCountLiveData = MutableLiveData<Int>()
    val isGeneratingLiveData = MutableLiveData<Boolean>()
    val confirmationLiveData = MutableLiveData<ConfirmationRequest?>()

    fun confirmAction(confirmed: Boolean) {
        confirmationLiveData.value?.deferred?.complete(confirmed)
        confirmationLiveData.postValue(null)
    }

    private val _messages = mutableListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages.toList()

    private val isGenerating = AtomicBoolean(false)

    private fun setGenerating(generating: Boolean) {
        isGenerating.set(generating)
        isGeneratingLiveData.postValue(generating)
    }

    private val wordCountJobVersion = java.util.concurrent.atomic.AtomicLong(0L)

    fun calculateWordCount(bookUrl: String, start: Int, end: Int) {
        val chapterSize = ReadBook.chapterSize
        val clampedStart = start.coerceIn(1, chapterSize.coerceAtLeast(1))
        val clampedEnd = end.coerceIn(1, chapterSize.coerceAtLeast(1))
        val st = minOf(clampedStart, clampedEnd)
        val ed = maxOf(clampedStart, clampedEnd)

        val myVersion = wordCountJobVersion.incrementAndGet()
        execute {
            var totalCount = 0
            val book = ReadBook.book ?: return@execute
            val chapterList = appDb.bookChapterDao.getChapterList(bookUrl, st - 1, ed - 1)
            for (chapter in chapterList) {
                if (wordCountJobVersion.get() != myVersion) return@execute
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
                if (AiConfig.toolEnabled) {
                    append("\n\n【工具使用指南】\n")
                    append("你可以调用工具来查询和管理用户的书架、书源、订阅等数据。")
                    append("写操作（如删除、修改分组、启用/禁用）会弹出确认框，请先告知用户你将要执行的操作。")
                    append("如果用户取消了操作，请尊重用户的选择，不要重复尝试。")
                }
                if (AiConfig.memory.isNotBlank()) {
                    append("\n\n【之前的对话记忆】\n")
                    append(AiConfig.memory)
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
            setGenerating(false)
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

                val toolsEnabled = AiConfig.toolEnabled
                if (toolsEnabled) {
                    val tools = AiToolDef.allTools
                    val responseText = requestWithTools(_messages.toList(), tools)
                    synchronized(_messages) {
                        _messages.add(ChatMessage("assistant", responseText))
                    }
                } else {
                    val responseText = requestOpenAi(_messages.toList())
                    synchronized(_messages) {
                        _messages.add(ChatMessage("assistant", responseText))
                    }
                }
            } catch (e: Exception) {
                synchronized(_messages) {
                    _messages.add(ChatMessage("assistant", "请求失败: ${e.message}"))
                }
            } finally {
                setGenerating(false)
                syncCache()
                messagesLiveData.postValue(_messages.toList())
            }
        }
    }

    fun summarizeAndMemory(start: Int, end: Int) {
        if (!isGenerating.compareAndSet(false, true)) return
        isGeneratingLiveData.postValue(true)
        if (_messages.isEmpty()) {
            setGenerating(false)
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

                val rangeStr = if (start == end) "第${start}章" else "第${start}-${end}章"
                val currentList = AiConfig.memoryList.toMutableList()
                currentList.add(AiMemoryItem(chapterRange = rangeStr, content = responseText))
                AiConfig.memoryList = currentList

                _messages.add(ChatMessage("assistant", "【系统提示】记忆已更新。\n\n新记忆内容：\n$responseText"))
                getApplication<Application>().toastOnUi("本次交流已保存")
            } catch (e: Exception) {
                _messages.add(ChatMessage("assistant", "记忆提取失败: ${e.message}"))
            } finally {
                setGenerating(false)
                syncCache()
                messagesLiveData.postValue(_messages.toList())
            }
        }
    }

    private fun syncCache() {
        val current = AiChatCache.state
        AiChatCache.state = current.copy(messages = _messages.toList())
    }

    /**
     * 带工具调用的请求循环：AI 返回 tool_call 时执行工具并再次请求，直到返回普通文本
     */
    private suspend fun requestWithTools(
        chatMessages: List<ChatMessage>,
        tools: List<Map<String, Any>>
    ): String {
        val currentMessages = chatMessages.toMutableList()
        repeat(MAX_TOOL_ROUNDS) {
            val response = requestOpenAiMessage(currentMessages, tools)
            if (response.toolCalls.isNullOrEmpty()) {
                return response.content
            }
            // 追加 assistant message（含 tool_calls）
            currentMessages.add(response)
            // 执行每个 tool_call，追加 tool role messages
            for (toolCall in response.toolCalls) {
                val args = try {
                    GSON.fromJsonObject<Map<String, Any>>(toolCall.function.arguments)
                        .getOrThrow() ?: emptyMap()
                } catch (_: Exception) {
                    emptyMap<String, Any>()
                }
                val toolResult = ToolRouter.execute(toolCall.function.name, args)
                val resultJson = when (toolResult) {
                    is ToolExecuteResult.Data -> toolResult.json
                    is ToolExecuteResult.NeedConfirmation -> {
                        val confirmed = requestConfirmation(toolResult.description)
                        if (confirmed) {
                            toolResult.action()
                        } else {
                            """{"cancelled":true,"message":"用户取消了操作"}"""
                        }
                    }
                }
                currentMessages.add(
                    ChatMessage(
                        role = "tool",
                        content = resultJson,
                        toolCallId = toolCall.id
                    )
                )
            }
        }
        return "工具调用轮次已达上限，请重新提问。"
    }

    /**
     * 向 Activity 发起确认请求，挂起等待用户选择
     */
    private suspend fun requestConfirmation(description: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmationLiveData.postValue(ConfirmationRequest(description, deferred))
        return deferred.await()
    }

    /**
     * 调用 OpenAI API，返回完整的 ChatMessage（可能包含 tool_calls）
     */
    private suspend fun requestOpenAiMessage(
        chatMessages: List<ChatMessage>,
        tools: List<Map<String, Any>>? = null
    ): ChatMessage = withContext(Dispatchers.IO) {
        val url = AiConfig.apiUrl
        val apiKey = AiConfig.apiKey
        val model = AiConfig.model

        val messagesJsonList = chatMessages.map { msg ->
            val map = mutableMapOf<String, Any>("role" to msg.role)
            if (msg.role == "tool") {
                map["content"] = msg.content
                msg.toolCallId?.let { map["tool_call_id"] = it }
            } else if (!msg.toolCalls.isNullOrEmpty()) {
                map["content"] = msg.content.ifBlank { "" }
                map["tool_calls"] = msg.toolCalls.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to "function",
                        "function" to mapOf(
                            "name" to tc.function.name,
                            "arguments" to tc.function.arguments
                        )
                    )
                }
            } else {
                map["content"] = msg.content
            }
            map
        }

        val requestBodyMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messagesJsonList
        )
        if (!tools.isNullOrEmpty()) {
            requestBodyMap["tools"] = tools
        }

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
        val messageMap = firstChoice?.get("message") as? Map<*, *> ?: throw Exception("解析响应失败")

        val content = messageMap["content"] as? String ?: ""
        val toolCallsRaw = messageMap["tool_calls"] as? List<Map<*, *>>

        if (!toolCallsRaw.isNullOrEmpty()) {
            val toolCalls = toolCallsRaw.map { tc ->
                val func = tc["function"] as? Map<*, *>
                ToolCall(
                    id = tc["id"] as? String ?: "",
                    function = FunctionCall(
                        name = func?.get("name") as? String ?: "",
                        arguments = func?.get("arguments") as? String ?: "{}"
                    )
                )
            }
            return@withContext ChatMessage(
                role = "assistant",
                content = content,
                toolCalls = toolCalls
            )
        }

        return@withContext ChatMessage(role = "assistant", content = content)
    }

    /**
     * 不带工具调用的简单请求，返回纯文本（用于总结记忆等场景）
     */
    private suspend fun requestOpenAi(chatMessages: List<ChatMessage>): String {
        val response = requestOpenAiMessage(chatMessages, tools = null)
        return response.content.ifBlank { throw Exception("响应内容为空") }
    }
}

data class ChatMessage(
    val role: String,
    val content: String = "",
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null
)

data class ToolCall(
    val id: String,
    val function: FunctionCall
)

data class FunctionCall(
    val name: String,
    val arguments: String
)

data class ConfirmationRequest(
    val description: String,
    val deferred: CompletableDeferred<Boolean>
)

/** 应用级内存缓存，持久化跨 Activity 周期的聊天记录 */
object AiChatCache {
    data class State(
        val bookUrl: String = "",
        val chapterIndex: Int = -1,
        val messages: List<ChatMessage> = emptyList()
    )

    @Volatile
    var state: State = State()
}
