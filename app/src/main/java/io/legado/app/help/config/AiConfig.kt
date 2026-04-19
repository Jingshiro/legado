package io.legado.app.help.config

import androidx.annotation.Keep
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

@Keep
data class AiMemoryItem(
    val id: Long = System.currentTimeMillis(),
    val chapterRange: String,
    val content: String
) {
    val preview: String get() = content.take(15) + if (content.length > 15) "..." else ""
}

/**
 * AI 阅读伴侣相关配置
 */
object AiConfig {
    private const val KEY_AI_API_URL = "ai_api_url"
    private const val KEY_AI_API_KEY = "ai_api_key"
    private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_AI_PERSONA = "ai_persona"
    private const val KEY_AI_MEMORY = "ai_memory"
    private const val KEY_AI_AVATAR = "ai_avatar"
    private const val KEY_USER_AVATAR = "user_avatar"

    var apiUrl: String
        get() = appCtx.getPrefString(KEY_AI_API_URL, "https://api.openai.com/v1/chat/completions") ?: ""
        set(value) {
            appCtx.putPrefString(KEY_AI_API_URL, value)
        }

    var apiKey: String
        get() = appCtx.getPrefString(KEY_AI_API_KEY, "") ?: ""
        set(value) {
            appCtx.putPrefString(KEY_AI_API_KEY, value)
        }

    var model: String
        get() = appCtx.getPrefString(KEY_AI_MODEL, "gpt-3.5-turbo") ?: ""
        set(value) {
            appCtx.putPrefString(KEY_AI_MODEL, value)
        }

    var persona: String
        get() = appCtx.getPrefString(KEY_AI_PERSONA, "你是一个擅长分析文学作品的阅读伴侣，请结合用户发送的当下正在阅读的章节内容，回答用户的问题。如果用户想探讨剧情人物，请积极互动。") ?: ""
        set(value) {
            appCtx.putPrefString(KEY_AI_PERSONA, value)
        }

    var memoryList: List<AiMemoryItem>
        get() {
            val raw = appCtx.getPrefString(KEY_AI_MEMORY, "") ?: ""
            if (raw.isBlank()) return emptyList()
            return try {
                if (raw.trimStart().startsWith("[")) {
                    GSON.fromJsonArray<AiMemoryItem>(raw).getOrNull() ?: emptyList()
                } else {
                    listOf(AiMemoryItem(id = 0L, chapterRange = "未知章节", content = raw))
                }
            } catch (e: Exception) {
                listOf(AiMemoryItem(id = 0L, chapterRange = "未知章节", content = raw))
            }
        }
        set(value) {
            appCtx.putPrefString(KEY_AI_MEMORY, GSON.toJson(value))
        }

    var memory: String
        get() {
            val list = memoryList
            if (list.isEmpty()) return ""
            return list.joinToString("\n\n") { "【${it.chapterRange}】\n${it.content}" }
        }
        set(value) {
            appCtx.putPrefString(KEY_AI_MEMORY, value)
        }

    var aiAvatar: String
        get() = appCtx.getPrefString(KEY_AI_AVATAR, "") ?: ""
        set(value) {
            appCtx.putPrefString(KEY_AI_AVATAR, value)
        }

    var userAvatar: String
        get() = appCtx.getPrefString(KEY_USER_AVATAR, "") ?: ""
        set(value) {
            appCtx.putPrefString(KEY_USER_AVATAR, value)
        }
}
