package io.legado.app.api.controller

import io.legado.app.api.ReturnData
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ThemeConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.postEvent
import splitties.init.appCtx

object ThemeController {

    /**
     * GET /getThemeConfigs
     * 返回所有已保存的主题配色列表。
     */
    val allThemeConfigs: ReturnData
        get() {
            val returnData = ReturnData()
            returnData.setData(ThemeConfig.configList)
            return returnData
        }

    /**
     * POST /saveThemeConfig
     * 新建或覆盖主题配色。Body 为 ThemeConfig.Config JSON。
     * 同名主题会被覆盖，不同名则新增。
     *
     * 必填字段：themeName, isNightTheme, primaryColor, accentColor,
     *           backgroundColor, bottomBackground, transparentNavBar, backgroundImgBlur
     */
    fun saveThemeConfig(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("数据不能为空")
        val config = GSON.fromJsonObject<ThemeConfig.Config>(postData).getOrNull()
        if (config == null) {
            returnData.setErrorMsg("格式不对，请检查 JSON 是否包含所有必填字段")
        } else if (config.themeName.isBlank()) {
            returnData.setErrorMsg("themeName 不能为空")
        } else {
            ThemeConfig.addConfig(config)
            ThemeConfig.save()
            returnData.setData("主题「${config.themeName}」已保存")
        }
        return returnData
    }

    /**
     * POST /deleteThemeConfig
     * 删除指定名称的主题。Body: {"themeName": "主题名"}
     */
    fun deleteThemeConfig(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("数据不能为空")
        val map = GSON.fromJsonObject<Map<String, String>>(postData).getOrNull()
        val themeName = map?.get("themeName")
        if (themeName.isNullOrBlank()) {
            return returnData.setErrorMsg("themeName 不能为空")
        }
        val index = ThemeConfig.configList.indexOfFirst { it.themeName == themeName }
        if (index < 0) {
            return returnData.setErrorMsg("未找到主题「$themeName」")
        }
        ThemeConfig.delConfig(index)
        returnData.setData("主题「$themeName」已删除")
        return returnData
    }

    /**
     * POST /applyThemeConfig
     * 应用指定名称的主题，触发 App 全局 UI 重建。
     * Body: {"themeName": "主题名"}
     */
    fun applyThemeConfig(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("数据不能为空")
        val map = GSON.fromJsonObject<Map<String, String>>(postData).getOrNull()
        val themeName = map?.get("themeName")
        if (themeName.isNullOrBlank()) {
            return returnData.setErrorMsg("themeName 不能为空")
        }
        val config = ThemeConfig.configList.find { it.themeName == themeName }
            ?: return returnData.setErrorMsg("未找到主题「$themeName」")
        // 将配色写入 SharedPreferences 并重建所有 Activity
        ThemeConfig.applyConfig(appCtx, config)
        returnData.setData("主题「${config.themeName}」已应用，界面将自动刷新")
        return returnData
    }
}
