package io.legado.app.ui.book.read.page.provider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.PaintPool
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.dpToPx
import io.legado.app.utils.longToastOnUi
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BookplateDrawer {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private var ratingRect = RectF()

    fun draw(canvas: Canvas, textPage: TextPage, book: Book) {
        val width = ChapterProvider.visibleWidth.toFloat()
        val height = ChapterProvider.visibleHeight.toFloat()
        
        val bpWidth = width * 0.8f
        val bpHeight = 360.dpToPx().toFloat()
        val left = (width - bpWidth) / 2f + ChapterProvider.paddingLeft
        val top = (height - bpHeight) / 2f + ChapterProvider.paddingTop
        val right = left + bpWidth
        val bottom = top + bpHeight

        val paint = PaintPool.obtain()
        paint.isAntiAlias = true
        
        // Draw shadow
        paint.color = Color.parseColor("#22000000")
        paint.style = Paint.Style.FILL
        canvas.drawRect(left + 4.dpToPx(), top + 4.dpToPx(), right + 4.dpToPx(), bottom + 4.dpToPx(), paint)
        
        // Draw background
        paint.color = Color.parseColor("#FCFCFA")
        canvas.drawRect(left, top, right, bottom, paint)
        
        // Draw dashed top/bottom borders
        paint.color = Color.parseColor("#B0B0B0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.dpToPx().toFloat()
        paint.pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        canvas.drawLine(left, top, right, top, paint)
        canvas.drawLine(left, bottom, right, bottom, paint)
        paint.pathEffect = null

        // Draw content
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#222222")
        paint.typeface = Typeface.MONOSPACE
        
        var currentY = top + 40.dpToPx()
        
        // Title
        paint.textSize = 18.dpToPx().toFloat()
        paint.isFakeBoldText = true
        val titleText = "Reading Certificate"
        val titleWidth = paint.measureText(titleText)
        canvas.drawText(titleText, left + (bpWidth - titleWidth) / 2f, currentY, paint)
        
        currentY += 25.dpToPx()
        paint.textSize = 14.dpToPx().toFloat()
        paint.isFakeBoldText = false
        val subtitleText = "=== 阅 读 凭 证 ==="
        val subtitleWidth = paint.measureText(subtitleText)
        canvas.drawText(subtitleText, left + (bpWidth - subtitleWidth) / 2f, currentY, paint)
        
        currentY += 30.dpToPx()
        
        val drawRow = { title: String, value: String, y: Float, isBold: Boolean ->
            paint.isFakeBoldText = isBold
            canvas.drawText(title, left + 20.dpToPx(), y, paint)
            val valWidth = paint.measureText(value)
            canvas.drawText(value, right - 20.dpToPx() - valWidth, y, paint)
        }
        
        val drawDivider = { y: Float ->
            paint.color = Color.parseColor("#666666")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.dpToPx().toFloat()
            paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
            canvas.drawLine(left + 20.dpToPx(), y, right - 20.dpToPx(), y, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#222222")
        }
        
        paint.textSize = 12.dpToPx().toFloat()
        
        // Add Time
        val earliestStartTime = appDb.detailedReadRecordDao.getEarliestStartTime(book.name) ?: book.addTime
        val trueStartTime = if (earliestStartTime > 0) earliestStartTime else book.addTime
        
        val addTimeStr = if (trueStartTime > 0) dateFormat.format(Date(trueStartTime)) else "____/__/__"
        drawRow("开始时间", addTimeStr, currentY, false)
        
        currentY += 20.dpToPx()
        drawDivider(currentY)
        currentY += 30.dpToPx()
        
        // Finish Time
        val finishTimeStr = if (book.finishTime > 0) dateFormat.format(Date(book.finishTime)) else "____/__/__"
        drawRow("完读时间", finishTimeStr, currentY, false)
        
        currentY += 25.dpToPx()
        
        paint.textSize = 14.dpToPx().toFloat()
        
        // 结算清单
        paint.isFakeBoldText = true
        val listTitle = "- 结 算 清 单 -"
        val listTitleWidth = paint.measureText(listTitle)
        canvas.drawText(listTitle, left + (bpWidth - listTitleWidth) / 2f, currentY, paint)
        currentY += 25.dpToPx()
        
        paint.isFakeBoldText = false
        
        val drawListRow = { title: String, value: String, y: Float ->
            canvas.drawText(title, left + 20.dpToPx(), y, paint)
            val valWidth = paint.measureText(value)
            val valueX = right - 20.dpToPx() - valWidth
            canvas.drawText(value, valueX, y, paint)
            
            val titleWidth = paint.measureText(title)
            val dashStartX = left + 20.dpToPx() + titleWidth + 5.dpToPx()
            val dashEndX = valueX - 5.dpToPx()
            if (dashEndX > dashStartX) {
                val oldStyle = paint.style
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.dpToPx().toFloat()
                paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                val textMiddleY = y - paint.textSize / 3f
                canvas.drawLine(dashStartX, textMiddleY, dashEndX, textMiddleY, paint)
                paint.pathEffect = null
                paint.style = oldStyle
            }
        }
        
        // Book Name
        var displayBookName = book.name
        val maxNameWidth = bpWidth - 40.dpToPx() - paint.measureText("书名  ") - 20.dpToPx()
        if (paint.measureText(displayBookName) > maxNameWidth) {
            val ellipsizeWidth = paint.measureText("...")
            while (displayBookName.isNotEmpty() && paint.measureText(displayBookName) + ellipsizeWidth > maxNameWidth) {
                displayBookName = displayBookName.substring(0, displayBookName.length - 1)
            }
            displayBookName += "..."
        }
        drawListRow("书名", displayBookName, currentY)
        currentY += 20.dpToPx()
        
        // Note count
        val noteCount = appDb.bookmarkDao.getByBook(book.name, book.author).size
        val thoughtCount = appDb.bookThoughtDao.getByBook(book.name, book.author).size
        val totalNotes = noteCount + thoughtCount
        val noteStr = if (totalNotes > 0) "$totalNotes" else "?"
        drawListRow("笔记条数", noteStr, currentY)
        currentY += 20.dpToPx()
        
        // Reading time
        val readingTimeStr = if (book.finishTime > 0 && trueStartTime > 0) {
            val diff = book.finishTime - trueStartTime
            val days = kotlin.math.max(0L, diff / (1000 * 60 * 60 * 24))
            "${days} 天"
        } else {
            "? 天"
        }
        drawListRow("阅读时间", readingTimeStr, currentY)
        
        currentY += 20.dpToPx()
        drawDivider(currentY)
        currentY += 30.dpToPx()
        
        // Rating
        paint.isFakeBoldText = false
        canvas.drawText("阅读打分", left + 20.dpToPx(), currentY, paint)
        
        val starsStr = "[ ☆ ☆ ☆ ☆ ☆ ]"
        val starsWidth = paint.measureText(starsStr)
        val starsX = right - 20.dpToPx() - starsWidth
        canvas.drawText(starsStr, starsX, currentY, paint)
        
        ratingRect.set(starsX, currentY - 20.dpToPx(), starsX + starsWidth, currentY + 10.dpToPx())
        
        // Draw filled stars
        paint.color = Color.parseColor("#222222")
        val starWidth = paint.measureText("☆ ")
        val bracketWidth = paint.measureText("[ ")
        for (i in 0 until 5) {
            if (book.bookRating >= i + 1) {
                canvas.drawText("★", starsX + bracketWidth + i * starWidth, currentY, paint)
            }
        }
        paint.color = Color.parseColor("#222222")
        
        currentY += 20.dpToPx()
        drawDivider(currentY)
        currentY += 30.dpToPx()
        
        // Footer
        paint.color = Color.parseColor("#444444")
        paint.textSize = 9.dpToPx().toFloat()
        paint.isFakeBoldText = false
        
        val footer1 = "BAD READS, NO RECEIPTS; GOOD READS, ON REPEAT."
        val footer2 = "烂书不退款，好书请多读。"
        val f1Width = paint.measureText(footer1)
        val f2Width = paint.measureText(footer2)
        canvas.drawText(footer1, left + (bpWidth - f1Width) / 2f, currentY, paint)
        currentY += 16.dpToPx()
        canvas.drawText(footer2, left + (bpWidth - f2Width) / 2f, currentY, paint)

        PaintPool.recycle(paint)
    }

    fun onClick(context: Context, x: Float, y: Float, textPage: TextPage, book: Book, relativeOffset: Float): Boolean {
        if (!textPage.isBookplateStart && !textPage.isBookplateEnd) return false
        
        val realY = y - relativeOffset
        
        if (ratingRect.contains(x, realY)) {
            if (textPage.isBookplateStart && book.finishTime <= 0) {
                appCtx.longToastOnUi("请完读后再打分")
            } else {
                val paint = PaintPool.obtain()
                paint.typeface = Typeface.MONOSPACE
                paint.textSize = 14.dpToPx().toFloat()
                paint.isFakeBoldText = false
                val bracketWidth = paint.measureText("[ ")
                val starWidth = paint.measureText("☆ ")
                PaintPool.recycle(paint)
                
                val offset = x - ratingRect.left - bracketWidth
                var rating = (offset / starWidth).toInt() + 1
                if (rating > 5) rating = 5
                if (rating < 1) rating = 1
                book.bookRating = rating.toFloat()
                io.legado.app.data.appDb.bookDao.update(book)
                appCtx.longToastOnUi("已评分 $rating 星")
            }
            return true
        }
        
        return false
    }
}