package io.legado.app.ui.book.read.page.provider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.PaintPool
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.dpToPx
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 阅读小票绘制器
 * 设计模板：阅读小票.html
 */
object BookplateDrawer {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private var ratingRect = RectF()
    private var reviewRect = RectF()

    // ── 颜色常量 ──
    private const val PAPER_BG = "#fafaf5"
    private const val TEXT_PRIMARY = "#1f1f1f"
    private const val TEXT_SECONDARY = "#4a4a4a"
    private const val TEXT_MUTED = "#8a8a8a"
    private const val DIVIDER_COLOR = "#3a3a3a"
    private const val STAR_EMPTY = "#c5c0b8"
    private const val STAR_FILLED = "#1f1f1f"
    private const val DOT_COLOR = "#b5b0a8"
    private const val SCISSOR_COLOR = "#8a857e"

    fun draw(canvas: Canvas, textPage: TextPage, book: Book) {
        val screenWidth = ChapterProvider.visibleWidth.toFloat()
        val screenHeight = ChapterProvider.visibleHeight.toFloat()

        // ── 卡片尺寸 ──
        val cardWidth = screenWidth * 0.85f
        val sidePad = 28.dpToPx()
        val contentWidth = cardWidth - sidePad * 2

        // ── 衬线字体 ──
        val serif = Typeface.create("serif", Typeface.NORMAL)
        val serifBold = Typeface.create("serif", Typeface.BOLD)

        // ── 测量评价文字行数 ──
        val tmpPaint = PaintPool.obtain()
        tmpPaint.typeface = serif
        tmpPaint.textSize = 12.5f.dpToPx()
        val reviewLineH = 21.dpToPx()
        val reviewContent = book.postReadNote
        val maxReviewLines = 6
        var reviewLines: List<String> = emptyList()
        var reviewSectionH = 0
        if (!reviewContent.isNullOrBlank()) {
            reviewLines = wrapText(tmpPaint, reviewContent, contentWidth, maxReviewLines)
            reviewSectionH = 30.dpToPx() + reviewLines.size * reviewLineH
        } else {
            reviewSectionH = 30.dpToPx() + reviewLineH
        }
        PaintPool.recycle(tmpPaint)

        // ── 卡片高度计算 ──
        val baseH = 300.dpToPx()
        val cardHeight = (baseH + reviewSectionH).toFloat()
        val maxH = screenHeight * 0.75f
        val effectiveH = minOf(cardHeight, maxH)

        val cardLeft = (screenWidth - cardWidth) / 2f + ChapterProvider.paddingLeft
        val cardTop = (screenHeight - effectiveH) / 2f + ChapterProvider.paddingTop
        val cardRight = cardLeft + cardWidth
        val cardBottom = cardTop + effectiveH

        val paint = PaintPool.obtain()
        paint.isAntiAlias = true

        // ════════════════════════════════════════
        //  卡片背景
        // ════════════════════════════════════════
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(PAPER_BG)
        canvas.drawRect(cardLeft, cardTop, cardRight, cardBottom, paint)

        // 阴影
        paint.color = Color.parseColor("#10000000")
        canvas.drawRect(cardLeft + 2, cardTop + 2, cardRight + 2, cardBottom + 2, paint)
        paint.color = Color.parseColor(PAPER_BG)
        canvas.drawRect(cardLeft, cardTop, cardRight, cardBottom, paint)

        // ════════════════════════════════════════
        //  顶部撕痕虚线 • • • • •
        // ════════════════════════════════════════
        var currentY = cardTop + 16.dpToPx()
        paint.textSize = 6.dpToPx().toFloat()
        paint.color = Color.parseColor(DOT_COLOR)
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.CENTER
        val dotPattern = "• • • • • • • • • • • • • • •"
        canvas.drawText(dotPattern, cardLeft + cardWidth / 2, currentY.toFloat(), paint)

        currentY += 20.dpToPx()

        // ════════════════════════════════════════
        //  头部：书本图标 + 阅读收据
        // ════════════════════════════════════════
        paint.typeface = serifBold
        paint.textSize = 16.dpToPx().toFloat()
        paint.color = Color.parseColor(TEXT_PRIMARY)
        paint.textAlign = Paint.Align.CENTER

        val headerText = appCtx.getString(R.string.bookplate_receipt)
        val headerW = paint.measureText(headerText)
        val iconSize = 16.dpToPx()
        val iconGap = 5.dpToPx()
        val totalHeaderW = iconSize + iconGap + headerW
        val headerStartX = cardLeft + (cardWidth - totalHeaderW) / 2f

        // 书本图标 (VectorDrawable)
        val bookDrawable = AppCompatResources.getDrawable(appCtx, R.drawable.ic_bookmarks)
        if (bookDrawable != null) {
            val wrapped = DrawableCompat.wrap(bookDrawable).mutate()
            val iconTop = currentY - 12.dpToPx()
            wrapped.setBounds(
                headerStartX.toInt(), iconTop.toInt(),
                (headerStartX + iconSize).toInt(), (iconTop + iconSize).toInt()
            )
            DrawableCompat.setTint(wrapped, Color.parseColor(TEXT_PRIMARY))
            wrapped.draw(canvas)
        }

        // "阅读收据"
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(headerText, headerStartX + iconSize + iconGap, currentY.toFloat(), paint)

        currentY += 18.dpToPx()

        // 副标题
        paint.typeface = serif
        paint.textSize = 10.dpToPx().toFloat()
        paint.color = Color.parseColor(TEXT_MUTED)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(appCtx.getString(R.string.bookplate_receipt_en), cardLeft + cardWidth / 2, currentY.toFloat(), paint)

        currentY += 16.dpToPx()

        // 虚线分隔
        drawDashedDivider(canvas, paint, cardLeft + sidePad, cardRight - sidePad, currentY.toFloat(), DIVIDER_COLOR)

        currentY += 20.dpToPx()

        // ════════════════════════════════════════
        //  信息行
        // ════════════════════════════════════════
        val labelX = cardLeft + sidePad
        val valueRightX = cardRight - sidePad

        // 书名
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor(TEXT_SECONDARY)
        paint.typeface = serif
        paint.textSize = 12.5f.dpToPx()
        canvas.drawText("书名", labelX, currentY.toFloat(), paint)

        paint.color = Color.parseColor(TEXT_PRIMARY)
        paint.typeface = serifBold
        paint.textSize = 14.dpToPx().toFloat()
        paint.textAlign = Paint.Align.RIGHT
        var displayName = "《${book.name}》"
        val maxNameW = valueRightX - labelX - 60.dpToPx()
        if (paint.measureText(displayName) > maxNameW) {
            while (displayName.isNotEmpty() && paint.measureText(displayName) + paint.measureText("…") > maxNameW) {
                displayName = displayName.substring(0, displayName.length - 1)
            }
            displayName += "…"
        }
        canvas.drawText(displayName, valueRightX, currentY.toFloat(), paint)

        currentY += 22.dpToPx()

        // 笔记条数
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor(TEXT_SECONDARY)
        paint.typeface = serif
        paint.textSize = 12.5f.dpToPx()
        canvas.drawText("笔记条数", labelX, currentY.toFloat(), paint)

        val noteCount = appDb.bookmarkDao.getByBook(book.name, book.author).size
        val thoughtCount = appDb.bookThoughtDao.getByBook(book.name, book.author).size
        val totalNotes = noteCount + thoughtCount
        paint.color = Color.parseColor(TEXT_PRIMARY)
        paint.typeface = serifBold
        paint.textSize = 13.5f.dpToPx()
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${totalNotes} 条", valueRightX, currentY.toFloat(), paint)

        currentY += 22.dpToPx()

        // 阅读时间
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor(TEXT_SECONDARY)
        paint.typeface = serif
        paint.textSize = 12.5f.dpToPx()
        canvas.drawText("阅读时间", labelX, currentY.toFloat(), paint)

        val earliestStartTime = appDb.detailedReadRecordDao.getEarliestStartTime(book.name) ?: book.addTime
        val trueStartTime = if (earliestStartTime > 0) earliestStartTime else book.addTime
        val timeStr = if (trueStartTime > 0) dateFormat.format(Date(trueStartTime)) else "____/__/__"
        paint.color = Color.parseColor(TEXT_PRIMARY)
        paint.typeface = serifBold
        paint.textSize = 13.5f.dpToPx()
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeStr, valueRightX, currentY.toFloat(), paint)

        currentY += 18.dpToPx()

        // 虚线分隔
        drawDashedDivider(canvas, paint, cardLeft + sidePad, cardRight - sidePad, currentY.toFloat(), DIVIDER_COLOR)

        currentY += 16.dpToPx()

        // ════════════════════════════════════════
        //  阅读打分
        // ════════════════════════════════════════
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor(TEXT_SECONDARY)
        paint.typeface = serif
        paint.textSize = 12.5f.dpToPx()
        canvas.drawText(appCtx.getString(R.string.bookplate_rating), labelX, currentY.toFloat(), paint)

        currentY += 24.dpToPx()

        // 星星
        val starSize = 20.dpToPx().toFloat()
        val starGap = 3.dpToPx().toFloat()
        for (i in 0 until 5) {
            val starX = labelX + i * (starSize + starGap)
            if (book.bookRating >= i + 1) {
                paint.color = Color.parseColor(STAR_FILLED)
                canvas.drawText("★", starX, currentY.toFloat(), paint)
            } else {
                paint.color = Color.parseColor(STAR_EMPTY)
                canvas.drawText("☆", starX, currentY.toFloat(), paint)
            }
        }

        // 评分文字 (x / 5)
        if (book.bookRating > 0) {
            paint.color = Color.parseColor(TEXT_MUTED)
            paint.typeface = serif
            paint.textSize = 11.dpToPx().toFloat()
            val scoreText = "（${book.bookRating.toInt()} / 5）"
            val scoreX = labelX + 5 * (starSize + starGap) + 4.dpToPx()
            canvas.drawText(scoreText, scoreX, currentY.toFloat(), paint)
        }

        ratingRect.set(
            labelX, (currentY - starSize).toFloat(),
            labelX + 5 * (starSize + starGap), (currentY + 8.dpToPx()).toFloat()
        )

        currentY += 20.dpToPx()

        // ════════════════════════════════════════
        //  阅读评价
        // ════════════════════════════════════════
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor(TEXT_SECONDARY)
        paint.typeface = serif
        paint.textSize = 12.5f.dpToPx()
        canvas.drawText(appCtx.getString(R.string.bookplate_review), labelX, currentY.toFloat(), paint)

        currentY += 10.dpToPx()

        val reviewBoxLeft = labelX
        val reviewBoxRight = cardRight - sidePad
        val reviewBoxTop = currentY

        if (reviewContent.isNullOrBlank()) {
            // 占位符：虚线边框 + 提示文字
            val boxBottom = reviewBoxTop + 40.dpToPx()
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#d5d0c8")
            paint.strokeWidth = 1.dpToPx().toFloat()
            paint.pathEffect = DashPathEffect(floatArrayOf(6f.dpToPx(), 4f.dpToPx()), 0f)
            canvas.drawRect(reviewBoxLeft, reviewBoxTop.toFloat(), reviewBoxRight, boxBottom, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL

            paint.color = Color.parseColor("#bfbab2")
            paint.typeface = serif
            paint.textSize = 12.dpToPx().toFloat()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                appCtx.getString(R.string.bookplate_review_hint),
                (reviewBoxLeft + reviewBoxRight) / 2,
                (reviewBoxTop + boxBottom) / 2 + 4.dpToPx(),
                paint
            )
            paint.textAlign = Paint.Align.LEFT

            reviewRect.set(reviewBoxLeft, reviewBoxTop.toFloat(), reviewBoxRight, boxBottom)
            currentY = boxBottom + 16.dpToPx()
        } else {
            // 评价文字（带虚线边框）
            val topPad = 14.dpToPx()
            val bottomPad = 12.dpToPx()
            val textBottom = currentY + topPad + reviewLines.size * reviewLineH + bottomPad

            // 虚线边框
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#d5d0c8")
            paint.strokeWidth = 1.dpToPx().toFloat()
            paint.pathEffect = DashPathEffect(floatArrayOf(6f.dpToPx(), 4f.dpToPx()), 0f)
            canvas.drawRect(reviewBoxLeft, reviewBoxTop.toFloat(), reviewBoxRight, textBottom.toFloat(), paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL

            // 文字
            paint.color = Color.parseColor(TEXT_PRIMARY)
            paint.typeface = serif
            paint.textSize = 12.5f.dpToPx()
            for ((idx, line) in reviewLines.withIndex()) {
                canvas.drawText(line, labelX + 10.dpToPx(), (currentY + topPad + idx * reviewLineH).toFloat(), paint)
            }

            reviewRect.set(reviewBoxLeft, reviewBoxTop.toFloat(), reviewBoxRight, textBottom.toFloat())
            currentY = textBottom + 16.dpToPx()
        }

        // ════════════════════════════════════════
        //  底部分隔
        // ════════════════════════════════════════
        drawDashedDivider(canvas, paint, cardLeft + sidePad, cardRight - sidePad, currentY.toFloat(), DIVIDER_COLOR)
        currentY += 14.dpToPx()

        // ════════════════════════════════════════
        //  底部标语
        // ════════════════════════════════════════
        paint.textAlign = Paint.Align.CENTER
        val centerX = cardLeft + cardWidth / 2

        paint.color = Color.parseColor(TEXT_SECONDARY)
        paint.typeface = serif
        paint.textSize = 9.5f.dpToPx()
        canvas.drawText("BAD READS, NO RECEIPTS.  GOOD READS, ON REPEAT.", centerX, currentY.toFloat(), paint)
        currentY += 14.dpToPx()

        paint.textSize = 10.5f.dpToPx()
        canvas.drawText("烂书不退款，好书请多读", centerX, currentY.toFloat(), paint)
        currentY += 10.dpToPx()

        // 剪刀图标 (VectorDrawable)
        currentY += 6.dpToPx()
        val scissorsDrawable = AppCompatResources.getDrawable(appCtx, R.drawable.ic_scissors)
        if (scissorsDrawable != null) {
            val wrapped = DrawableCompat.wrap(scissorsDrawable).mutate()
            val scSize = 14.dpToPx()
            val scCenterX = (centerX - scSize / 2).toInt()
            val scTop = currentY.toInt()
            wrapped.setBounds(scCenterX, scTop, scCenterX + scSize, scTop + scSize)
            wrapped.draw(canvas)
        }

        currentY += 16.dpToPx()

        // 虚线
        paint.style = Paint.Style.FILL
        paint.textSize = 6.dpToPx().toFloat()
        paint.color = Color.parseColor(DOT_COLOR)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(dotPattern, centerX, currentY.toFloat(), paint)

        PaintPool.recycle(paint)
    }

    /**
     * 绘制虚线分隔
     */
    private fun drawDashedDivider(canvas: Canvas, paint: Paint, left: Float, right: Float, y: Float, color: String) {
        val oldStyle = paint.style
        val oldColor = paint.color
        val oldWidth = paint.strokeWidth
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor(color)
        paint.strokeWidth = 0.8f.dpToPx()
        paint.pathEffect = DashPathEffect(floatArrayOf(6f.dpToPx(), 3f.dpToPx()), 0f)
        canvas.drawLine(left, y, right, y, paint)
        paint.pathEffect = null
        paint.style = oldStyle
        paint.color = oldColor
        paint.strokeWidth = oldWidth
    }

    /**
     * 文字自动换行
     */
    private fun wrapText(paint: Paint, text: String, maxWidth: Float, maxLines: Int): List<String> {
        val lines = mutableListOf<String>()
        val chars = text.toCharArray()
        var start = 0
        var i = 0
        while (i < chars.size && lines.size < maxLines) {
            if (chars[i] == '\n') {
                lines.add(String(chars, start, i - start))
                start = i + 1
                i = start
                continue
            }
            val w = paint.measureText(chars, start, i - start + 1)
            if (w > maxWidth) {
                if (lines.size == maxLines - 1) {
                    var end = i
                    while (end > start && paint.measureText(chars, start, end - start) + paint.measureText("…") > maxWidth) {
                        end--
                    }
                    lines.add(String(chars, start, end - start) + "…")
                    return lines
                }
                lines.add(String(chars, start, i - start))
                start = i
            } else {
                i++
            }
        }
        if (start < chars.size && lines.size < maxLines) {
            lines.add(String(chars, start, chars.size - start))
        }
        return lines
    }

    fun onClick(
        context: Context, x: Float, y: Float,
        textPage: TextPage, book: Book, relativeOffset: Float
    ): Boolean {
        if (!textPage.isBookplateStart && !textPage.isBookplateEnd) return false
        val realY = y - relativeOffset

        // ── 评分点击 ──
        if (ratingRect.contains(x, realY)) {
            if (textPage.isBookplateStart && book.finishTime <= 0) {
                appCtx.longToastOnUi("请完读后再打分")
            } else {
                val starSize = 20.dpToPx().toFloat()
                val starGap = 3.dpToPx().toFloat()
                val offset = x - ratingRect.left
                var rating = (offset / (starSize + starGap)).toInt() + 1
                rating = rating.coerceIn(1, 5)
                book.bookRating = rating.toFloat()
                appDb.bookDao.update(book)
                appCtx.toastOnUi("已评分 $rating 星")
            }
            return true
        }

        // ── 评价点击 ──
        if (reviewRect.contains(x, realY)) {
            if (textPage.isBookplateStart && book.finishTime <= 0) {
                appCtx.longToastOnUi("请完读后再评价")
                return true
            }
            showReviewDialog(context, book)
            return true
        }

        return false
    }

    private fun showReviewDialog(context: Context, book: Book) {
        context.alert(appCtx.getString(R.string.bookplate_review)) {
            val editText = EditText(context).apply {
                hint = appCtx.getString(R.string.bookplate_review_hint)
                setText(book.postReadNote ?: "")
                setHintTextColor(Color.parseColor("#bfbab2"))
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.create("serif", Typeface.NORMAL)
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
                minLines = 3
                maxLines = 8
                val h = (16 * context.resources.displayMetrics.density).toInt()
                val v = (12 * context.resources.displayMetrics.density).toInt()
                setPadding(h, v, h, v)
                isSingleLine = false
                background = null
            }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val m = (20 * context.resources.displayMetrics.density).toInt()
                setPadding(m, 0, m, 0)
                addView(editText, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            setCustomView(container)
            okButton {
                val text = editText.text?.toString()?.trim() ?: ""
                book.postReadNote = text.ifEmpty { null }
                appDb.bookDao.update(book)
                appCtx.toastOnUi("评价已保存")
                io.legado.app.model.ReadBook.callBack?.upContent()
            }
            cancelButton()
        }
    }
}
