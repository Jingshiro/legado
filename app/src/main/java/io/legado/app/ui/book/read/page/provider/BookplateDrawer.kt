package io.legado.app.ui.book.read.page.provider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.PaintPool
import io.legado.app.help.book.update
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.dpToPx
import io.legado.app.utils.longToastOnUi
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BookplateDrawer {

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    private var preNoteRect = RectF()
    private var postNoteRect = RectF()
    private var ratingRect = RectF()

    fun draw(canvas: Canvas, textPage: TextPage, book: Book) {
        val width = ChapterProvider.visibleWidth.toFloat()
        val height = ChapterProvider.visibleHeight.toFloat()
        
        val isStart = textPage.isBookplateStart
        
        val bpWidth = width * 0.8f
        val bpHeight = height * 0.75f
        val left = (width - bpWidth) / 2f + ChapterProvider.paddingLeft
        val top = (height - bpHeight) / 2f + ChapterProvider.paddingTop
        val right = left + bpWidth
        val bottom = top + bpHeight

        val paint = PaintPool.obtain()
        paint.isAntiAlias = true
        
        // Draw background
        paint.color = Color.parseColor("#FFFDF6E3") // A slight paper color
        paint.style = Paint.Style.FILL
        
        // Create jagged edge path
        val path = Path()
        val toothSize = 8.dpToPx().toFloat()
        path.moveTo(left, top)
        var currentX = left
        while (currentX < right) {
            currentX += toothSize
            if (currentX > right) currentX = right
            path.lineTo(currentX - toothSize / 2, top + toothSize / 2)
            path.lineTo(currentX, top)
        }
        path.lineTo(right, bottom)
        var currentXBottom = right
        while (currentXBottom > left) {
            currentXBottom -= toothSize
            if (currentXBottom < left) currentXBottom = left
            path.lineTo(currentXBottom + toothSize / 2, bottom - toothSize / 2)
            path.lineTo(currentXBottom, bottom)
        }
        path.lineTo(left, top)
        canvas.drawPath(path, paint)
        
        // Draw border
        paint.color = Color.parseColor("#88000000")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.dpToPx().toFloat()
        canvas.drawPath(path, paint)

        // Draw content
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.typeface = Typeface.MONOSPACE
        
        var currentY = top + 40.dpToPx()
        
        // Title
        paint.textSize = 24.dpToPx().toFloat()
        val titleText = if (isStart) "- 卷 首 票 -" else "- 卷 尾 票 -"
        val titleWidth = paint.measureText(titleText)
        canvas.drawText(titleText, left + (bpWidth - titleWidth) / 2f, currentY, paint)
        
        currentY += 40.dpToPx()
        paint.textSize = 14.dpToPx().toFloat()
        
        // Add Time
        val addTimeStr = "入库: " + (if (book.addTime > 0) dateFormat.format(Date(book.addTime)) else "未知")
        canvas.drawText(addTimeStr, left + 20.dpToPx(), currentY, paint)
        
        currentY += 30.dpToPx()
        val preNoteTitle = "读前记录:"
        canvas.drawText(preNoteTitle, left + 20.dpToPx(), currentY, paint)
        currentY += 20.dpToPx()
        
        val preNote = if (book.preReadNote.isNullOrEmpty()) "点击记录你的期待..." else book.preReadNote!!
        paint.color = if (book.preReadNote.isNullOrEmpty()) Color.GRAY else Color.BLACK
        canvas.drawText(preNote, left + 20.dpToPx(), currentY, paint)
        
        preNoteRect.set(left + 20.dpToPx(), currentY - 30.dpToPx(), right - 20.dpToPx(), currentY + 10.dpToPx())
        
        currentY += 40.dpToPx()
        
        // Divider
        paint.color = Color.parseColor("#44000000")
        paint.style = Paint.Style.STROKE
        paint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        canvas.drawLine(left + 20.dpToPx(), currentY, right - 20.dpToPx(), currentY, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        
        currentY += 40.dpToPx()
        paint.color = Color.BLACK
        
        // Finish Time
        val finishTimeStr = "完读: " + (if (book.finishTime > 0) dateFormat.format(Date(book.finishTime)) else "未完读")
        canvas.drawText(finishTimeStr, left + 20.dpToPx(), currentY, paint)
        
        currentY += 30.dpToPx()
        
        // Note count
        val noteCount = appDb.bookmarkDao.getByBook(book.name, book.author).size
        val thoughtCount = appDb.bookThoughtDao.getByBook(book.name, book.author).size
        val noteStr = "笔记: ${noteCount + thoughtCount} 条"
        canvas.drawText(noteStr, left + 20.dpToPx(), currentY, paint)
        
        currentY += 30.dpToPx()
        val postNoteTitle = "读后感想:"
        canvas.drawText(postNoteTitle, left + 20.dpToPx(), currentY, paint)
        currentY += 20.dpToPx()
        
        val postNote = if (book.postReadNote.isNullOrEmpty()) "点击记录你的感想..." else book.postReadNote!!
        paint.color = if (book.postReadNote.isNullOrEmpty()) Color.GRAY else Color.BLACK
        canvas.drawText(postNote, left + 20.dpToPx(), currentY, paint)
        
        postNoteRect.set(left + 20.dpToPx(), currentY - 30.dpToPx(), right - 20.dpToPx(), currentY + 10.dpToPx())
        
        currentY += 50.dpToPx()
        paint.color = Color.BLACK
        
        // Rating
        val ratingTitle = "评分:"
        canvas.drawText(ratingTitle, left + 20.dpToPx(), currentY, paint)
        val startX = left + 20.dpToPx() + paint.measureText(ratingTitle) + 10.dpToPx()
        
        ratingRect.set(startX, currentY - 20.dpToPx(), startX + 5 * 30.dpToPx(), currentY + 10.dpToPx())
        
        for (i in 0 until 5) {
            val starColor = if (book.bookRating >= i + 1) Color.parseColor("#FFD700") else Color.LTGRAY
            paint.color = starColor
            canvas.drawText("★", startX + i * 30.dpToPx(), currentY, paint)
        }

        currentY += 40.dpToPx()
        
        // Footer Divider
        paint.color = Color.parseColor("#44000000")
        paint.style = Paint.Style.STROKE
        paint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        canvas.drawLine(left + 20.dpToPx(), currentY, right - 20.dpToPx(), currentY, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        
        currentY += 30.dpToPx()
        paint.color = Color.parseColor("#444444")
        paint.textSize = 10.dpToPx().toFloat()
        
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
        
        if (preNoteRect.contains(x, realY)) {
            appCtx.longToastOnUi("点击了读前记录")
            return true
        } else if (postNoteRect.contains(x, realY)) {
            if (textPage.isBookplateStart && book.finishTime <= 0) {
                appCtx.longToastOnUi("请完读后再记录")
            } else {
                appCtx.longToastOnUi("点击了读后感想")
            }
            return true
        } else if (ratingRect.contains(x, realY)) {
            if (textPage.isBookplateStart && book.finishTime <= 0) {
                appCtx.longToastOnUi("请完读后再记录")
            } else {
                // Calculate star index
                val starWidth = 30.dpToPx()
                val offset = x - ratingRect.left
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