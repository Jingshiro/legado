package io.legado.app.help.book

import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import splitties.init.appCtx
import java.io.File

/**
 * 字体管理器
 * 扫描用户配置的字体目录，提供按字体名加载 Typeface 的能力
 */
object FontManager {

    // 字体名（不含扩展名）-> 文件路径
    private var fontMap: Map<String, String>? = null

    // 字体缓存: 字体名 -> Typeface
    private val typefaceCache = mutableMapOf<String, Typeface?>()

    private val fontRegex = Regex("(?i).*\\.[ot]tf")

    /**
     * 根据字体名获取 Typeface
     * @param fontFamily 字体名称（不含扩展名），如 "楷体"
     * @return Typeface 对象，找不到时返回 null
     */
    fun getTypeface(fontFamily: String): Typeface? {
        if (fontFamily.isEmpty()) return null

        typefaceCache[fontFamily]?.let { return it }

        val fontPath = getFontMap()[fontFamily] ?: return null

        val typeface = loadTypeface(fontPath)
        typefaceCache[fontFamily] = typeface
        return typeface
    }

    /**
     * 获取所有可用字体名列表
     */
    fun getAvailableFontNames(): List<String> {
        return getFontMap().keys.sorted()
    }

    fun getFontMap(): Map<String, String> {
        fontMap?.let { return it }
        val map = scanFonts()
        fontMap = map
        return map
    }

    fun refresh() {
        fontMap = null
        typefaceCache.clear()
    }

    private fun scanFonts(): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // 1. 扫描本地字体目录（始终可访问）
        scanLocalFonts(result)

        // 2. 扫描用户配置的字体目录
        val fontFolder = appCtx.getPrefString(PreferKey.fontFolder)
        if (!fontFolder.isNullOrEmpty()) {
            scanUserFontFolder(fontFolder, result)
        }

        return result
    }

    /**
     * 扫描 app 本地字体目录 externalFiles/font/
     */
    private fun scanLocalFonts(result: MutableMap<String, String>) {
        try {
            val fontDir = File(appCtx.externalFiles, "font")
            if (fontDir.exists() && fontDir.isDirectory) {
                fontDir.listFiles()?.forEach { file ->
                    if (file.isFile && fontRegex.matches(file.name)) {
                        val fontName = getFontName(file.name)
                        if (fontName.isNotEmpty()) {
                            result[fontName] = file.absolutePath
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 扫描用户通过设置选择的字体目录
     */
    private fun scanUserFontFolder(fontFolder: String, result: MutableMap<String, String>) {
        try {
            if (fontFolder.isContentScheme()) {
                val uri = Uri.parse(fontFolder)
                // 尝试用 DocumentFile（需要 URI 权限）
                val docFile = DocumentFile.fromTreeUri(appCtx, uri)
                if (docFile != null && docFile.canRead()) {
                    scanDocumentFile(docFile, result)
                }
            } else {
                val dir = File(fontFolder)
                if (dir.exists() && dir.isDirectory) {
                    scanFileDirectory(dir, result)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun scanDocumentFile(dir: DocumentFile, result: MutableMap<String, String>) {
        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDocumentFile(file, result)
            } else if (file.isFile && fontRegex.matches(file.name ?: "")) {
                val fontName = getFontName(file.name ?: "")
                if (fontName.isNotEmpty()) {
                    result[fontName] = file.uri.toString()
                }
            }
        }
    }

    private fun scanFileDirectory(dir: File, result: MutableMap<String, String>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanFileDirectory(file, result)
            } else if (file.isFile && fontRegex.matches(file.name)) {
                val fontName = getFontName(file.name)
                if (fontName.isNotEmpty()) {
                    result[fontName] = file.absolutePath
                }
            }
        }
    }

    private fun getFontName(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(0, lastDot) else fileName
    }

    private fun loadTypeface(fontPath: String): Typeface? {
        return try {
            when {
                fontPath.isContentScheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    appCtx.contentResolver
                        .openFileDescriptor(Uri.parse(fontPath), "r")!!
                        .use {
                            Typeface.Builder(it.fileDescriptor).build()
                        }
                }
                fontPath.isContentScheme() -> {
                    Typeface.createFromFile(
                        io.legado.app.utils.RealPathUtil.getPath(appCtx, Uri.parse(fontPath))
                    )
                }
                fontPath.isNotEmpty() -> Typeface.createFromFile(fontPath)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
