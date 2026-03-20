package io.legado.app.ui.book.read.ai

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.AiConfig
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean

class AiChatViewModel(application: Application) : BaseViewModel(application) {

    val messagesLiveData = MutableLiveData<List<ChatMessage>>()

    // 私有可变列表，外部只能读取快照
    private val _messages = mutableListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages.toList()

    // 使用 AtomicBoolean 保证并发安全
    private val isGenerating = AtomicBoolean(false)

    fun initMessages(chapterContent: String?) {
        val currentBookUrl = ReadBook.book?.bookUrl ?: ""
        val currentChapterIndex = ReadBook.curTextChapter?.position ?: -1

        val cached = AiChatCache.state
        if (cached.bookUrl == currentBookUrl &&
            cached.chapterIndex == currentChapterIndex &&
            cached.messages.isNotEmpty()
        ) {
            _messages.clear()
            _messages.addAll(cached.messages)
            messagesLiveData.postValue(_messages.toList())
            return
        }

        _messages.clear()
        if (chapterContent != null) {
            val systemPrompt = buildSystemPrompt(chapterContent)
            _messages.add(ChatMessage("system", systemPrompt))
        }

        AiChatCache.state = AiChatCache.State(
            bookUrl = currentBookUrl,
            chapterIndex = currentChapterIndex,
            messages = _messages.toList()
        )

        messagesLiveData.postValue(_messages.toList())
    }

    private fun buildSystemPrompt(chapterContent: String): String {
        return buildString {
            append("【人设与要求】\n")
            append(AiConfig.persona)
            if (AiConfig.memory.isNotBlank()) {
                append("\n\n【之前的对话记忆】\n")
                append(AiConfig.memory)
            }
            append("\n\n【当前正在阅读的章节内容】\n")
            append(chapterContent)
        }
    }

    fun sendMessage(userText: String) {
        if (!isGenerating.compareAndSet(false, true)) return
        if (userText.isBlank()) {
            isGenerating.set(false)
            return
        }

        _messages.add(ChatMessage("user", userText))
        syncCache()
        messagesLiveData.postValue(_messages.toList())

        execute {
            try {
                val responseText = requestOpenAi(_messages.toList())
                _messages.add(ChatMessage("assistant", responseText))
            } catch (e: Exception) {
                _messages.add(ChatMessage("assistant", "请求失败: ${e.message}"))
            } finally {
                isGenerating.set(false)
                syncCache()
                messagesLiveData.postValue(_messages.toList())
            }
        }
    }

    fun summarizeAndMemory() {
        if (!isGenerating.compareAndSet(false, true)) return
        if (_messages.isEmpty()) {
            isGenerating.set(false)
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
                AiConfig.memory = responseText
                _messages.add(ChatMessage("assistant", "【系统提示】记忆已更新。\n\n新记忆内容：\n$responseText"))
            } catch (e: Exception) {
                _messages.add(ChatMessage("assistant", "记忆提取失败: ${e.message}"))
            } finally {
                isGenerating.set(false)
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
