package io.legado.app.utils

import android.graphics.Color
import android.util.LruCache
import androidx.annotation.ColorInt

/**
 * CSS 样式解析器
 * 用于解析高亮规则中的 CSS 样式和 HTML 标签
 */
object CssStyleParser {

    // 匹配 style 属性中的各CSS属性
    private val fontWeightRegex = Regex("""font-weight\s*:\s*(bold|\d+)""")
    private val fontStyleRegex = Regex("""font-style\s*:\s*italic""")
    private val textDecorationRegex = Regex("""text-decoration\s*:\s*underline""")
    private val colorRegex = Regex("""color\s*:\s*([^;]+)""")
    private val fontSizeRegex = Regex("""font-size\s*:\s*([\d.]+)\s*px""")
    private val fontFamilyRegex = Regex("""font-family\s*:\s*([^;]+)""")

    // 匹配 HTML 标签
    private val boldTagRegex = Regex("""</?(?:b|strong)>""", RegexOption.IGNORE_CASE)
    private val italicTagRegex = Regex("""</?(?:i|em)>""", RegexOption.IGNORE_CASE)
    private val underlineTagRegex = Regex("""</?(?:u)>""", RegexOption.IGNORE_CASE)
    private val fontSizeSpanRegex = Regex(
        """<span\s+[^>]*style\s*=\s*["'][^"']*font-size\s*:\s*([\d.]+)\s*px[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val fontColorTagRegex = Regex(
        """<font\s+[^>]*color\s*=\s*["']([^"']+)["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val fontFaceTagRegex = Regex(
        """<font\s+[^>]*face\s*=\s*["']([^"']+)["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val spanTagRegex = Regex(
        """<span\s+[^>]*style\s*=\s*["']([^"']+)["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )

    // CSS 颜色名映射
    private val colorNameMap = mapOf(
        "red" to Color.RED,
        "blue" to Color.BLUE,
        "green" to Color.GREEN,
        "yellow" to Color.YELLOW,
        "white" to Color.WHITE,
        "black" to Color.BLACK,
        "gray" to Color.GRAY,
        "grey" to Color.GRAY,
        "cyan" to Color.CYAN,
        "magenta" to Color.MAGENTA,
        "orange" to 0xFFFFA500.toInt(),
        "purple" to 0xFF800080.toInt(),
        "pink" to 0xFFFFC0CB.toInt(),
        "brown" to 0xFFA52A2A.toInt(),
        "gold" to 0xFFFFD700.toInt(),
        "silver" to 0xFFC0C0C0.toInt(),
        "navy" to 0xFF000080.toInt(),
        "teal" to 0xFF008080.toInt(),
        "maroon" to 0xFF800000.toInt()
    )

    /**
     * 解析 CSS style 属性字符串
     * @param style CSS 样式字符串，如 "font-weight:bold; color:red; font-size:16px"
     * @return HighlightStyle 对象
     */
    fun parseStyle(style: String): HighlightStyle {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var color: Int? = null
        var fontSizePx: Float? = null
        var fontFamily: String? = null

        fontWeightRegex.find(style)?.let { match ->
            val value = match.groupValues[1]
            isBold = value == "bold" || (value.toIntOrNull()?.let { it >= 700 } == true)
        }
        fontStyleRegex.find(style)?.let {
            isItalic = true
        }
        textDecorationRegex.find(style)?.let {
            isUnderline = true
        }
        colorRegex.find(style)?.let { match ->
            color = parseColor(match.groupValues[1].trim())
        }
        fontSizeRegex.find(style)?.let { match ->
            fontSizePx = match.groupValues[1].toFloatOrNull()
        }
        fontFamilyRegex.find(style)?.let { match ->
            fontFamily = match.groupValues[1].trim()
        }

        return HighlightStyle(isBold, isItalic, isUnderline, color, fontSizePx, fontFamily)
    }

    /**
     * 从 HTML 标签和 style 属性中提取样式信息
     * @param html 包含 HTML 标签的字符串片段
     * @return HighlightStyle 对象
     */
    fun parseHtmlStyle(html: String): HighlightStyle {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var color: Int? = null
        var fontSizePx: Float? = null
        var fontFamily: String? = null

        // 检查 HTML 标签
        if (boldTagRegex.containsMatchIn(html)) isBold = true
        if (italicTagRegex.containsMatchIn(html)) isItalic = true
        if (underlineTagRegex.containsMatchIn(html)) isUnderline = true

        // 检查 font 标签的 color 属性
        fontColorTagRegex.find(html)?.let { match ->
            color = parseColor(match.groupValues[1].trim())
        }

        // 检查 font 标签的 face 属性
        fontFaceTagRegex.find(html)?.let { match ->
            fontFamily = match.groupValues[1].trim()
        }

        // 检查 span 标签的 style 属性
        spanTagRegex.find(html)?.let { match ->
            val styleStr = match.groupValues[1]
            val parsed = parseStyle(styleStr)
            if (parsed.isBold) isBold = true
            if (parsed.isItalic) isItalic = true
            if (parsed.isUnderline) isUnderline = true
            if (parsed.color != null) color = parsed.color
            if (parsed.fontSizePx != null) fontSizePx = parsed.fontSizePx
            if (parsed.fontFamily != null) fontFamily = parsed.fontFamily
        }

        // 检查 <span style="font-size:Npx"> 标签
        fontSizeSpanRegex.find(html)?.let { match ->
            fontSizePx = match.groupValues[1].toFloatOrNull()
        }

        return HighlightStyle(isBold, isItalic, isUnderline, color, fontSizePx, fontFamily)
    }

    // 样式标签名（不包含 div/p 等结构标签，避免丢失外层包裹）
    private val styleTagNames = "b|i|u|font|span|strong|em|big|small"
    private val groupStylesCache = LruCache<String, Map<Int, HighlightStyle>>(100)

    /**
     * 从替换模板中提取各捕获组的样式
     * 替换模板示例: <b><font color="red">$1</font></b><i>$2</i>
     * @param replacement 替换模板字符串
     * @return Map<Int, HighlightStyle> 捕获组索引 -> 样式
     */
    fun extractGroupStyles(replacement: String): Map<Int, HighlightStyle> {
        groupStylesCache.get(replacement)?.let { return it }

        val result = mutableMapOf<Int, HighlightStyle>()

        // 匹配 $N 周围的样式标签（不包含 div/p 等结构标签）
        val pattern = Regex(
            """((?:<(?!\/)(?:$styleTagNames)\b[^>]*>)+)\$(\d+)((?:<\/(?:$styleTagNames)>)+)""",
            RegexOption.IGNORE_CASE
        )
        pattern.findAll(replacement).forEach { match ->
            val groupIndex = match.groupValues[2].toIntOrNull() ?: return@forEach
            val openTags = match.groupValues[1].lowercase()
            val style = parseHtmlStyle(openTags)
            result[groupIndex] = style
        }

        // 纯 $N（无样式标签），使用默认样式
        val pattern2 = Regex("""\$(\d+)""")
        pattern2.findAll(replacement).forEach { match ->
            val groupIndex = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (groupIndex !in result) {
                result[groupIndex] = HighlightStyle()
            }
        }

        groupStylesCache.put(replacement, result)
        return result
    }

    /**
     * 解析颜色字符串
     * 支持: #RGB, #RRGGBB, #AARRGGBB, 颜色名
     */
    @ColorInt
    fun parseColor(colorStr: String): Int? {
        val str = colorStr.trim().lowercase()
        return try {
            when {
                str.startsWith("#") -> Color.parseColor(str)
                str in colorNameMap -> colorNameMap[str]
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 高亮样式数据类
     */
    data class HighlightStyle(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        @param:ColorInt val color: Int? = null,
        val fontSizePx: Float? = null,
        val fontFamily: String? = null
    ) {
        fun hasStyle(): Boolean {
            return isBold || isItalic || isUnderline || color != null || fontSizePx != null || fontFamily != null
        }

        /**
         * 用 HTML 标签包裹文本（兼容 Android Html.fromHtml）
         * 使用 <b>, <i>, <u>, <font color> 等原生支持的标签
         */
        fun wrapWithHtmlTags(text: String): String {
            var result = text
            if (isBold) result = "<b>$result</b>"
            if (isItalic) result = "<i>$result</i>"
            if (isUnderline) result = "<u>$result</u>"
            fontFamily?.let {
                result = "<font face=\"$it\">$result</font>"
            }
            color?.let {
                val colorStr = ColorUtils.intToString(it)
                result = "<font color=\"$colorStr\">$result</font>"
            }
            fontSizePx?.let { px ->
                result = "<span style=\"font-size:${px.toInt()}px\">$result</span>"
            }
            return result
        }
    }
}
