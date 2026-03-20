package io.legado.app.help.config

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

    var memory: String
        get() = appCtx.getPrefString(KEY_AI_MEMORY, "") ?: ""
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
