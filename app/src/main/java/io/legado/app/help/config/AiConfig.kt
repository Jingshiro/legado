package io.legado.app.help.config

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

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

    private val gson = Gson()

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

    /**
     * 兼容旧版单字符串，现已升级为 MemoryItem JSON 列表。
     * 旧数据若非 JSON 列表格式将被清空。
     */
    var memory: String
        get() = appCtx.getPrefString(KEY_AI_MEMORY, "") ?: ""
        set(value) {
            appCtx.putPrefString(KEY_AI_MEMORY, value)
        }

    /** 读取记忆列表（若旧格式则返回空列表） */
    fun getMemoryList(): List<MemoryItem> {
        val raw = memory
        if (raw.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<MemoryItem>>() {}.type
            gson.fromJson(raw, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 将记忆列表序列化保存 */
    private fun saveMemoryList(list: List<MemoryItem>) {
        memory = gson.toJson(list)
    }

    /** 追加一条新记忆 */
    fun addMemory(item: MemoryItem) {
        val list = getMemoryList().toMutableList()
        list.add(item)
        saveMemoryList(list)
    }

    /** 根据 key（时间戳）删除单条记忆 */
    fun deleteMemory(key: Long) {
        val list = getMemoryList().filter { it.key != key }
        saveMemoryList(list)
    }

    /** 清空全部记忆 */
    fun clearAllMemory() {
        memory = ""
    }

    /** 将所有记忆合并为提示词上下文字符串 */
    fun buildMemoryContext(): String {
        val list = getMemoryList()
        if (list.isEmpty()) return ""
        return list.joinToString("\n\n") { item ->
            val chapterLabel = if (item.start == item.end) {
                "第${item.start}章"
            } else {
                "第${item.start}章 - 第${item.end}章"
            }
            "[$chapterLabel]\n${item.content}"
        }
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

/** 记忆条目数据模型 */
data class MemoryItem(
    val key: Long,       // 唯一标识（时间戳）
    val content: String, // 摘要全文
    val start: Int,      // 起始章节编号
    val end: Int         // 结束章节编号
)
