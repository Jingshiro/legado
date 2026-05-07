package io.legado.app.ui.book.thought

import android.net.Uri
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookThought
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.exists
import io.legado.app.utils.writeText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ThoughtObsidianExporter {

    private val timestampFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

    suspend fun exportBook(
        bookName: String,
        bookAuthor: String,
        thoughts: List<BookThought>? = null
    ): Result<Unit> = kotlin.runCatching {
        val book = appDb.bookDao.getBook(bookName, bookAuthor)
        val intro = book?.getDisplayIntro()
        val bookThoughts = thoughts ?: appDb.bookThoughtDao.getByBook(bookName, bookAuthor)
        if (bookThoughts.isEmpty()) return Result.success(Unit)
        val markdown = ThoughtMarkdownGenerator.generate(bookName, intro, bookThoughts)
        val fileName = generateUniqueFileName(bookName)

        when (AppConfig.obsidianExportMethod) {
            0 -> exportViaApi(fileName, markdown)
            1 -> exportViaLocalFile(fileName, markdown)
            else -> throw IllegalArgumentException("Unknown export method")
        }
    }

    suspend fun exportAll(): Result<Pair<Int, Int>> = kotlin.runCatching {
        val allThoughts = appDb.bookThoughtDao.all
        val grouped = allThoughts.groupBy { it.bookName to it.bookAuthor }
        var successCount = 0
        grouped.forEach { (key, thoughts) ->
            val (name, author) = key
            exportBook(name, author, thoughts).onSuccess { successCount++ }
        }
        successCount to grouped.size
    }

    private suspend fun exportViaApi(fileName: String, content: String) {
        val apiUrl = AppConfig.obsidianApiUrl
        val apiKey = AppConfig.obsidianApiKey
        val subPath = AppConfig.obsidianVaultSubPath.trim('/')
        val filePath = if (subPath.isEmpty()) fileName else "$subPath/$fileName"
        ObsidianApi.putFile(apiUrl, apiKey, filePath, content).getOrThrow()
    }

    private fun exportViaLocalFile(fileName: String, content: String) {
        val dirUri = AppConfig.obsidianLocalDirUri
            ?: throw IllegalStateException("Obsidian local directory not configured")
        val dirDoc = io.legado.app.utils.FileDoc.fromUri(Uri.parse(dirUri), true)
        val subPath = AppConfig.obsidianVaultSubPath.trim('/')
        val subDirs = if (subPath.isEmpty()) emptyArray() else subPath.split('/').toTypedArray()
        val fileDoc = dirDoc.createFileIfNotExist(fileName, *subDirs)
        fileDoc.writeText(content)
    }

    private fun generateUniqueFileName(bookName: String): String {
        val sanitized = bookName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val timestamp = timestampFormat.format(Date())
        val baseName = "《${sanitized}》_${timestamp}"
        var fileName = "$baseName.md"

        if (AppConfig.obsidianExportMethod == 1) {
            val dirUri = AppConfig.obsidianLocalDirUri ?: return fileName
            val dirDoc = io.legado.app.utils.FileDoc.fromUri(Uri.parse(dirUri), true)
            val subPath = AppConfig.obsidianVaultSubPath.trim('/')
            val subDirs = if (subPath.isEmpty()) emptyArray() else subPath.split('/').toTypedArray()
            var suffix = 0
            while (true) {
                val checkName = if (suffix == 0) fileName else "$baseName-$suffix.md"
                val exists = dirDoc.exists(checkName, *subDirs)
                if (!exists) {
                    fileName = checkName
                    break
                }
                suffix++
            }
        }
        return fileName
    }
}
