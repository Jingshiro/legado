package io.legado.app.help.config

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.model.BookCover
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readBytes
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

@Keep
object ThemeExportHelper {

    const val FULL_THEME_JSON = "fullTheme.json"

    @Keep
    data class FullTheme(
        val themeConfig: ThemeConfig.Config,
        val readConfig: ReadBookConfig.Config,
        val coverRule: BookCover.CoverRule?,
        val coverShowName: Boolean,
        val coverShowAuthor: Boolean,
        val coverShowNameN: Boolean,
        val coverShowAuthorN: Boolean,
        val readIterationTagColor: Int,
        val isNightTheme: Boolean
    )

    suspend fun exportFullTheme(context: Context, uri: Uri, themeConfig: ThemeConfig.Config) {
        withContext(Dispatchers.IO) {
            try {
                // Prepare export directory
                val exportDir = File(context.externalCache, "themeExport")
                FileUtils.delete(exportDir)
                exportDir.mkdirs()

                // Gather data — make a mutable copy so we can patch image paths
                val exportThemeConfig = themeConfig.copy()
                val readConfig = ReadBookConfig.durConfig.copy()
                val coverRule = BookCover.getConfig()
                val isNightTheme = AppConfig.isNightTheme
                val coverShowName = context.getPrefBoolean(PreferKey.coverShowName, true)
                val coverShowAuthor = context.getPrefBoolean(PreferKey.coverShowAuthor, true)
                val coverShowNameN = context.getPrefBoolean(PreferKey.coverShowNameN, true)
                val coverShowAuthorN = context.getPrefBoolean(PreferKey.coverShowAuthorN, true)
                val readIterationTagColor =
                    context.getPrefInt(PreferKey.readIterationTagColor, 0xCCB5451B.toInt())

                // Copy theme background image if it's local
                exportThemeConfig.backgroundImgPath?.let { path ->
                    if (!path.startsWith("http") && FileUtils.exist(path)) {
                        val file = File(path)
                        val targetFile = File(exportDir, "theme_bg_${file.name}")
                        file.copyTo(targetFile, overwrite = true)
                        // Store only the filename in the exported config
                        exportThemeConfig.backgroundImgPath = targetFile.name
                    }
                }

                // Copy read background image if it's local
                val copyReadBg: (Int, String) -> String = { bgType, bgStr ->
                    var resultStr = bgStr
                    if (bgType == 2 && bgStr.isNotEmpty() && !bgStr.startsWith("http")) {
                        val path = if (bgStr.contains(File.separator)) bgStr
                        else FileUtils.getPath(context.externalFiles, "bg", bgStr)
                        if (FileUtils.exist(path)) {
                            val file = File(path)
                            val targetFile = File(exportDir, "read_bg_${file.name}")
                            file.copyTo(targetFile, overwrite = true)
                            resultStr = targetFile.name
                        }
                    }
                    resultStr
                }
                readConfig.bgStr = copyReadBg(readConfig.bgType, readConfig.bgStr)
                readConfig.bgStrNight = copyReadBg(readConfig.bgTypeNight, readConfig.bgStrNight)
                readConfig.bgStrEInk = copyReadBg(readConfig.bgTypeEInk, readConfig.bgStrEInk)

                // Copy cover images if local
                val defaultCover = context.getPrefString(PreferKey.defaultCover)
                if (!defaultCover.isNullOrEmpty() && FileUtils.exist(defaultCover)) {
                    val file = File(defaultCover)
                    val targetFile = File(exportDir, "cover_${file.name}")
                    file.copyTo(targetFile, overwrite = true)
                }

                val defaultCoverDark = context.getPrefString(PreferKey.defaultCoverDark)
                if (!defaultCoverDark.isNullOrEmpty() && FileUtils.exist(defaultCoverDark)) {
                    val file = File(defaultCoverDark)
                    val targetFile = File(exportDir, "cover_dark_${file.name}")
                    file.copyTo(targetFile, overwrite = true)
                }

                // Build full theme object and write JSON
                val fullTheme = FullTheme(
                    themeConfig = exportThemeConfig,
                    readConfig = readConfig,
                    coverRule = coverRule,
                    coverShowName = coverShowName,
                    coverShowAuthor = coverShowAuthor,
                    coverShowNameN = coverShowNameN,
                    coverShowAuthorN = coverShowAuthorN,
                    readIterationTagColor = readIterationTagColor,
                    isNightTheme = isNightTheme
                )
                val jsonFile = File(exportDir, FULL_THEME_JSON)
                jsonFile.writeText(GSON.toJson(fullTheme))

                // Zip files and write to the chosen Uri
                val zipFile = File(context.externalCache, "themeExport.zip")
                FileUtils.delete(zipFile)
                val filesToZip = exportDir.listFiles()?.toList() ?: emptyList()
                if (ZipUtils.zipFiles(filesToZip, zipFile)) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        zipFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    appCtx.toastOnUi("主题导出成功")
                } else {
                    appCtx.toastOnUi("主题打包失败")
                }
            } catch (e: Exception) {
                AppLog.put("全量导出主题失败", e)
                appCtx.toastOnUi("导出失败: ${e.message}")
            }
        }
    }

    suspend fun importFullTheme(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // Prepare import directory
                val importDir = File(context.externalCache, "themeImport")
                FileUtils.delete(importDir)
                importDir.mkdirs()

                // Read zip from Uri using the correct extension Uri.readBytes(context)
                val zipFile = File(context.externalCache, "themeImport.zip")
                FileUtils.delete(zipFile)
                try {
                    zipFile.writeBytes(uri.readBytes(context))
                } catch (e: Exception) {
                    appCtx.toastOnUi("无法读取文件")
                    return@withContext
                }

                ZipUtils.unZipToPath(zipFile, importDir)

                val jsonFile = File(importDir, FULL_THEME_JSON)
                if (!jsonFile.exists()) {
                    appCtx.toastOnUi("不是有效的全量主题包")
                    return@withContext
                }

                val json = jsonFile.readText()
                val fullTheme = GSON.fromJsonObject<FullTheme>(json).getOrNull()
                if (fullTheme == null) {
                    appCtx.toastOnUi("解析主题数据失败")
                    return@withContext
                }

                // Restore theme background image
                val importThemeConfig = fullTheme.themeConfig.copy()
                importThemeConfig.backgroundImgPath?.let { bgPath ->
                    if (!bgPath.startsWith("http")) {
                        val importedFile = File(importDir, bgPath)
                        if (importedFile.exists()) {
                            val preferenceKey =
                                if (fullTheme.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
                            val targetDir = File(context.externalFiles, preferenceKey)
                            targetDir.mkdirs()
                            val name = bgPath.removePrefix("theme_bg_")
                            val targetFile = File(targetDir, name)
                            importedFile.copyTo(targetFile, overwrite = true)
                            importThemeConfig.backgroundImgPath = targetFile.absolutePath
                        }
                    }
                }

                // Restore read background images (ReadBookConfig stores just the filename)
                val importReadConfig = fullTheme.readConfig.copy()
                val restoreReadBg: (Int, String) -> String = { bgType, bgStr ->
                    var resultStr = bgStr
                    if (bgType == 2 && bgStr.isNotEmpty() && !bgStr.startsWith("http")) {
                        val importedFile = File(importDir, bgStr)
                        if (importedFile.exists()) {
                            val targetDir = File(context.externalFiles, "bg")
                            targetDir.mkdirs()
                            val name = bgStr.removePrefix("read_bg_")
                            val targetFile = File(targetDir, name)
                            importedFile.copyTo(targetFile, overwrite = true)
                            resultStr = name
                        }
                    }
                    resultStr
                }
                importReadConfig.bgStr =
                    restoreReadBg(importReadConfig.bgType, importReadConfig.bgStr)
                importReadConfig.bgStrNight =
                    restoreReadBg(importReadConfig.bgTypeNight, importReadConfig.bgStrNight)
                importReadConfig.bgStrEInk =
                    restoreReadBg(importReadConfig.bgTypeEInk, importReadConfig.bgStrEInk)

                // Restore cover files
                importDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("cover_")) {
                        val isDark = file.name.startsWith("cover_dark_")
                        val prefKey =
                            if (isDark) PreferKey.defaultCoverDark else PreferKey.defaultCover
                        val targetDir = File(context.externalFiles, "covers")
                        targetDir.mkdirs()
                        val targetFile = File(targetDir, file.name)
                        file.copyTo(targetFile, overwrite = true)
                        context.putPrefString(prefKey, targetFile.absolutePath)
                    }
                }

                // Apply Theme Config
                ThemeConfig.addConfig(importThemeConfig)
                ThemeConfig.applyConfig(context, importThemeConfig)

                // Add or update Read Config (add as new to avoid overwriting existing styles)
                val existingIndex =
                    ReadBookConfig.configList.indexOfFirst { it.name == importReadConfig.name }
                if (existingIndex != -1) {
                    ReadBookConfig.configList[existingIndex] = importReadConfig
                    ReadBookConfig.styleSelect = existingIndex
                } else {
                    ReadBookConfig.configList.add(importReadConfig)
                    ReadBookConfig.styleSelect = ReadBookConfig.configList.size - 1
                }
                ReadBookConfig.save()

                // Apply Cover Rule
                fullTheme.coverRule?.let { BookCover.saveCoverRule(it) }

                // Apply other preference settings
                context.putPrefBoolean(PreferKey.coverShowName, fullTheme.coverShowName)
                context.putPrefBoolean(PreferKey.coverShowAuthor, fullTheme.coverShowAuthor)
                context.putPrefBoolean(PreferKey.coverShowNameN, fullTheme.coverShowNameN)
                context.putPrefBoolean(PreferKey.coverShowAuthorN, fullTheme.coverShowAuthorN)
                context.putPrefInt(PreferKey.readIterationTagColor, fullTheme.readIterationTagColor)

                BookCover.upDefaultCover()

                postEvent(EventBus.UP_BOOKSHELF, "")
                appCtx.toastOnUi("全量主题导入成功")
            } catch (e: Exception) {
                AppLog.put("导入全量主题失败", e)
                appCtx.toastOnUi("导入失败: ${e.message}")
            }
        }
    }
}
