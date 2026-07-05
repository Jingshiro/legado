package io.legado.app.help.book

import android.os.Build
import android.util.LruCache
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.spaceRegex
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.CssStyleParser
import io.legado.app.utils.escapeRegex
import io.legado.app.utils.replace
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import splitties.init.appCtx
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

class ContentProcessor private constructor(
    private val bookName: String,
    private val bookOrigin: String
) {

    companion object {
        private val processors = hashMapOf<String, WeakReference<ContentProcessor>>()
        private val isAndroid8 = Build.VERSION.SDK_INT in 26..27

        /**
         * 高亮规则处理结果的 LRU 缓存。
         * Key: "内容长度|内容哈希|规则id|pattern|replacement"
         * Value: applyHighlightRule 的处理结果
         * 容量控制：上限 400 万字符（约 8MB 内存），根据内容长度计算 sizeOf。
         * 规则更新时全量清除。
         */
        private val highlightCache = object : LruCache<String, String>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: String): Int {
                return value.length
            }
        }

        /**
         * 跨行 HTML 标签拆分处理的 LRU 缓存。
         * Key: "内容长度|内容哈希"
         * 容量控制：上限 100 万字符（约 2MB 内存）。
         */
        private val splitHtmlCache = object : LruCache<String, String>(1024 * 1024) {
            override fun sizeOf(key: String, value: String): Int {
                return value.length
            }
        }

        fun get(book: Book) = get(book.name, book.origin)

        fun get(bookName: String, bookOrigin: String): ContentProcessor {
            val processorWr = processors[bookName + bookOrigin]
            var processor: ContentProcessor? = processorWr?.get()
            if (processor == null) {
                processor = ContentProcessor(bookName, bookOrigin)
                processors[bookName + bookOrigin] = WeakReference(processor)
            }
            return processor
        }

        fun upReplaceRules() {
            // 规则变更，所有缓存失效
            highlightCache.evictAll()
            splitHtmlCache.evictAll()
            processors.forEach {
                it.value.get()?.upReplaceRules()
            }
        }

    }

    private val titleReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    private val contentReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    val removeSameTitleCache = hashSetOf<String>()

    init {
        upReplaceRules()
        upRemoveSameTitle()
    }

    fun upReplaceRules() {
        val modeStr = when {
            AppConfig.isEInkMode -> "EINK"
            AppConfig.isNightTheme -> "NIGHT"
            else -> "DAY"
        }
        val targetBind = "${modeStr}_${ReadBookConfig.durConfig.name}"

        titleReplaceRules.run {
            clear()
            val rules = appDb.replaceRuleDao.findEnabledByTitleScope(bookName, bookOrigin)
            addAll(rules.filter { it.bindToThemes.isNullOrBlank() || it.bindToThemes!!.split(",").contains(targetBind) })
        }
        contentReplaceRules.run {
            clear()
            val rules = appDb.replaceRuleDao.findEnabledByContentScope(bookName, bookOrigin)
            addAll(rules.filter { it.bindToThemes.isNullOrBlank() || it.bindToThemes!!.split(",").contains(targetBind) })
        }
    }

    private fun upRemoveSameTitle() {
        val book = appDb.bookDao.getBookByOrigin(bookName, bookOrigin) ?: return
        removeSameTitleCache.clear()
        val files = BookHelp.getChapterFiles(book).filter {
            it.endsWith("nr")
        }
        removeSameTitleCache.addAll(files)
    }

    fun getTitleReplaceRules(): List<ReplaceRule> {
        return titleReplaceRules
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getContentReplaceRules(): List<ReplaceRule> {
        return contentReplaceRules
    }

    fun getContent(
        book: Book,
        chapter: BookChapter,
        content: String,
        includeTitle: Boolean = true,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        reSegment: Boolean = true
    ): BookContent {
        var mContent = content
        var sameTitleRemoved = false
        var effectiveReplaceRules: ArrayList<ReplaceRule>? = null
        val replaceBook by lazy { book.toReplaceBook() }
        if (content != "null") {
            //去除重复标题
            val fileName = chapter.getFileName("nr")
            if (!removeSameTitleCache.contains(fileName)) try {
                val name = Pattern.quote(book.name)
                var title = chapter.title.escapeRegex().replace(spaceRegex, "\\\\s*")
                var matcher = Pattern.compile("^(\\s|\\p{P}|${name})*${title}(\\s)*")
                    .matcher(mContent)
                if (matcher.find()) {
                    mContent = mContent.substring(matcher.end())
                    sameTitleRemoved = true
                } else if (useReplace && book.getUseReplaceRule()) {
                    title = Pattern.quote(
                        chapter.getDisplayTitle(
                            titleReplaceRules,
                            chineseConvert = false,
                            replaceBook = replaceBook
                        )
                    )
                    matcher = Pattern.compile("^(\\s|\\p{P}|${name})*${title}(\\s)*")
                        .matcher(mContent)
                    if (matcher.find()) {
                        mContent = mContent.substring(matcher.end())
                        sameTitleRemoved = true
                    }
                }
            } catch (e: Exception) {
                AppLog.put("去除重复标题出错\n${e.localizedMessage}", e)
            }
            if (reSegment && book.getReSegment()) {
                //重新分段
                mContent = ContentHelp.reSegment(mContent, chapter.title)
            }
            if (chineseConvert) {
                //简繁转换
                try {
                    when (AppConfig.chineseConverterType) {
                        1 -> mContent = ChineseUtils.t2s(mContent)
                        2 -> mContent = ChineseUtils.s2t(mContent)
                    }
                } catch (_: Exception) {
                    appCtx.toastOnUi("简繁转换出错")
                }
            }
            val useHtmlMap = mutableMapOf<String, String>()
            if (AppConfig.adaptSpecialStyle) { //html处理
                mContent = AppPattern.useHtmlRegex.replace(mContent) { matchResult ->
                    val placeholder = "特殊格式的占位不应该被看见${useHtmlMap.size}。"
                    useHtmlMap[placeholder] = "\n${matchResult.value.replace("\n","")}\n"
                    placeholder
                }
            }
            if (useReplace && book.getUseReplaceRule()) {
                //替换
                effectiveReplaceRules = arrayListOf()
                mContent = mContent.lines().joinToString("\n") { it.trim() }
                getContentReplaceRules().forEach { item ->
                    if (item.pattern.isEmpty()) {
                        return@forEach
                    }
                    try {
                        val tmp = if (item.isHighlight) {
                            // 高亮模式：按捕获组应用样式
                            applyHighlightRule(mContent, item)
                        } else if (item.isRegex) {
                            mContent.replace(
                                item.name,
                                item.regex,
                                item.replacement,
                                item.getValidTimeoutMillisecond(),
                                chapter,
                                replaceBook
                            )
                        } else {
                            mContent.replace(item.pattern, item.replacement)
                        }
                        if (mContent != tmp) {
                            effectiveReplaceRules.add(item)
                            mContent = tmp
                        }
                    } catch (e: RegexTimeoutException) {
                        item.isEnabled = false
                        appDb.replaceRuleDao.update(item)
                        mContent = item.name + e.stackTraceStr
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        AppLog.put("替换净化: 规则 ${item.name}替换出错.\n${mContent}", e)
                        appCtx.toastOnUi("替换净化: 规则 ${item.name}替换出错")
                    }
                }
                // 高亮规则产生 HTML 标签，需要标记为 usehtml 以走 HTML 渲染路径
                if (effectiveReplaceRules?.any { it.isHighlight } == true) {
                    val hasHtmlTag = mContent.contains("<b>") || mContent.contains("<i>")
                        || mContent.contains("<u>") || mContent.contains("<font ")
                        || mContent.contains("<strong>") || mContent.contains("<span ")
                    if (hasHtmlTag) {
                        mContent = splitMultiLineHtmlTags(mContent)
                        mContent = mContent.lines().joinToString("\n") { line ->
                            if (line.isBlank()) line else "<usehtml>$line<endhtml>"
                        }
                    }
                }
            }
            useHtmlMap.forEach { (placeholder, originalContent) ->
                mContent = mContent.replace(placeholder, originalContent)
            }
        }
        if (includeTitle) {
            //重新添加标题
            mContent = chapter.getDisplayTitle(
                getTitleReplaceRules(),
                useReplace = useReplace && book.getUseReplaceRule(),
                replaceBook = replaceBook
            ) + "\n" + mContent
        }
        if (isAndroid8) {
            mContent = mContent.replace('\u00A0', ' ')
        }
        val contents = arrayListOf<String>()
        mContent.split("\n").forEach { str ->
            val paragraph = str.trim {
                it.code <= 0x20 || it == '　'
            }
            if (paragraph.isNotEmpty()) {
                if (contents.isEmpty() && includeTitle) {
                    contents.add(paragraph)
                } else {
                    contents.add("${ReadBookConfig.paragraphIndent}$paragraph")
                }
            }
        }
        return BookContent(sameTitleRemoved, contents, effectiveReplaceRules)
    }

    /**
     * 应用高亮规则
     * 解析替换模板中的 HTML/CSS 样式，按捕获组生成带样式的 HTML
     *
     * @param content 原始文本内容
     * @param rule 高亮替换规则
     * @return 应用高亮样式后的 HTML 内容
     */
    private fun applyHighlightRule(content: String, rule: ReplaceRule): String {
        // LRU 缓存命中检查：key = 内容长度|内容哈希|规则id|pattern|replacement
        // 用 length 辅助 hashCode 以极大降低碰撞概率（length 相同且 hashCode 相同才可能误命中）
        val cacheKey = "${content.length}|${content.hashCode()}|${rule.id}|${rule.pattern}|${rule.replacement}"
        highlightCache.get(cacheKey)?.let { return it }

        val regex = if (rule.isRegex) {
            val options = mutableSetOf<RegexOption>()
            if (rule.isDotAll) options.add(RegexOption.DOT_MATCHES_ALL)
            rule.pattern.toRegex(options)
        } else {
            Regex.escape(rule.pattern).toRegex()
        }

        // 从替换模板中提取各捕获组的样式
        val groupStyles = CssStyleParser.extractGroupStyles(rule.replacement)

        // 构建带样式的替换结果
        val sb = StringBuilder()
        var lastEnd = 0
        regex.findAll(content).forEach { matchResult ->
            // 添加匹配前的原文
            sb.append(content, lastEnd, matchResult.range.first)

            // 解析替换模板，将 $N 替换为带样式的匹配内容
            var styledReplacement = rule.replacement
            for ((groupIndex, style) in groupStyles.entries.sortedByDescending { it.key }) {
                val groupValue = matchResult.groupValues.getOrNull(groupIndex) ?: ""
                if (groupValue.isEmpty()) continue

                // 生成带样式的 HTML
                val styledText = if (style.hasStyle()) {
                    style.wrapWithHtmlTags(groupValue)
                } else {
                    groupValue
                }

                // 替换模板中的 $N（带标签或不带标签）
                // 先替换带标签的: <tag>$N</tag>（支持嵌套标签如 <b><font color="red">$N</font></b>）
                val taggedPattern = Regex(
                    """((?:<[^/][^>]*>)+)\$$groupIndex((?:</[^>]*>)+)""",
                    RegexOption.IGNORE_CASE
                )
                styledReplacement = taggedPattern.replace(styledReplacement, styledText)
                // 再替换裸的 $N
                styledReplacement = styledReplacement.replace("\$$groupIndex", styledText)
            }
            sb.append(styledReplacement)
            lastEnd = matchResult.range.last + 1
        }
        // 添加最后一个匹配后的内容
        if (lastEnd < content.length) {
            sb.append(content, lastEnd, content.length)
        }

        val result = sb.toString()
        highlightCache.put(cacheKey, result)
        return result
    }

    /**
     * 将跨行的 HTML 标签拆分为每行都有完整标签的形式
     * 例如: <font color="red">第一行\n第二行</font>
     * 变为: <font color="red">第一行</font>\n<font color="red">第二行</font>
     */
    private fun splitMultiLineHtmlTags(content: String): String {
        val cacheKey = "${content.length}|${content.hashCode()}"
        splitHtmlCache.get(cacheKey)?.let { return it }

        val result = StringBuilder()
        val tagStack = mutableListOf<String>() // 存储未闭合的开标签原文
        val tagPattern = Regex("""<(/?)(\w+)(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
        var i = 0

        while (i < content.length) {
            if (content[i] == '\n') {
                // 换行：闭合所有未闭合标签，换行，重新打开
                for (j in tagStack.indices.reversed()) {
                    val name = tagPattern.find(tagStack[j])?.groupValues?.get(2) ?: continue
                    result.append("</$name>")
                }
                result.append('\n')
                for (tag in tagStack) {
                    result.append(tag)
                }
                i++
            } else if (content[i] == '<') {
                val match = tagPattern.find(content, i)
                if (match != null && match.range.first == i) {
                    val isClosing = match.groupValues[1] == "/"
                    val tagName = match.groupValues[2].lowercase()
                    if (isClosing) {
                        // 闭合标签：弹栈
                        if (tagStack.isNotEmpty()) {
                            val last = tagStack.last()
                            if (tagPattern.find(last)?.groupValues?.get(2)?.lowercase() == tagName) {
                                tagStack.removeAt(tagStack.lastIndex)
                            }
                        }
                    } else if (!match.value.endsWith("/>")) {
                        // 开标签（非自闭合）：压栈
                        tagStack.add(match.value)
                    }
                    result.append(match.value)
                    i = match.range.last + 1
                } else {
                    result.append(content[i])
                    i++
                }
            } else {
                result.append(content[i])
                i++
            }
        }
        val finalResult = result.toString()
        splitHtmlCache.put(cacheKey, finalResult)
        return finalResult
    }

}
