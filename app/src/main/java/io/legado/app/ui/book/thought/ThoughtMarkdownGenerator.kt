package io.legado.app.ui.book.thought

import io.legado.app.data.entities.BookThought
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ThoughtMarkdownGenerator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun generate(bookName: String, bookIntro: String?, thoughts: List<BookThought>): String {
        val sb = StringBuilder()

        if (!bookIntro.isNullOrBlank()) {
            sb.appendLine("### 书籍简介")
            sb.appendLine()
            sb.appendLine(bookIntro.trim())
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }

        val grouped = thoughts.groupBy { it.chapterIndex to it.chapterName }
            .toSortedMap(compareBy({ it.first }, { it.second }))

        grouped.forEach { (chapterInfo, chapterThoughts) ->
            val (_, chapterName) = chapterInfo
            sb.appendLine("### $chapterName")
            sb.appendLine()

            chapterThoughts.forEachIndexed { index, thought ->
                if (thought.selectedText.isNotBlank()) {
                    sb.appendLine(thought.selectedText.trim())
                    sb.appendLine()
                }

                if (thought.thought.isNotBlank()) {
                    sb.appendLine("> ${thought.thought.trim()}")
                    sb.appendLine()
                }

                val timeStr = dateFormat.format(Date(thought.createTime))
                sb.appendLine("<font>$timeStr</font>")
                sb.appendLine()

                if (index < chapterThoughts.size - 1) {
                    sb.appendLine("---")
                    sb.appendLine()
                }
            }

            sb.appendLine("---")
            sb.appendLine()
        }

        return sb.toString().trimEnd() + "\n"
    }
}
