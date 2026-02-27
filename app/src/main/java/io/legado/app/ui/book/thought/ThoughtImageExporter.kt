package io.legado.app.ui.book.thought

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withSave
import io.legado.app.data.entities.BookThought
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.isContentScheme
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

object ThoughtImageExporter {

    fun exportToLocal(context: Context, thought: BookThought, thoughtText: String): String {
        val bitmap = buildBitmap(thought, thoughtText)
        return saveBitmap(context, bitmap)
    }

    private fun buildBitmap(thought: BookThought, thoughtText: String): Bitmap {
        val width = 1125
        val horizontalPadding = 36
        val topBarHeight = 6
        val topPadding = 22
        val bottomPadding = 28
        val contentWidth = width - horizontalPadding * 2
        val textInset = 14
        val typeface = resolveReadTypeface()

        val accentGreen = Color.parseColor("#5A7C62")
        val surfaceColor = Color.parseColor("#F5F5F0")
        val textMain = Color.parseColor("#3D3D3D")

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surfaceColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentGreen
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val topStripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentGreen
            style = Paint.Style.FILL
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentGreen
            textSize = 12f
            this.typeface = typeface
            isSubpixelText = true
            isLinearText = true
        }
        val selectedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 18f
            this.typeface = typeface
            isSubpixelText = true
            isLinearText = true
        }
        val thoughtTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentGreen
            textSize = 16f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            isSubpixelText = true
            isLinearText = true
        }
        val thoughtPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 16f
            this.typeface = typeface
            isSubpixelText = true
            isLinearText = true
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentGreen
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val thoughtBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x145A7C62
            style = Paint.Style.FILL
        }

        val leftMeta = "${thought.bookName} ${thought.bookAuthor}".trim()
        val rightMeta = thought.chapterName
        val fittedMeta = fitMeta(leftMeta, rightMeta, metaPaint, contentWidth)
        metaPaint.textSize = fittedMeta.textSize

        val selectedLayout = createLayout(
            thought.selectedText,
            selectedPaint,
            contentWidth - textInset * 2,
            lineSpacingMultiplier = 1.8f
        )
        val thoughtTitle = "[感想]"
        val thoughtLayout = createLayout(
            thoughtText,
            thoughtPaint,
            contentWidth - textInset * 2,
            lineSpacingMultiplier = 1.6f
        )

        val metaHeight = (metaPaint.fontMetrics.bottom - metaPaint.fontMetrics.top).toInt()
        val thoughtTitleHeight = (thoughtTitlePaint.fontMetrics.bottom - thoughtTitlePaint.fontMetrics.top).toInt()
        val selectedTopGap = 16
        val selectedBottomGap = 18
        val dividerTopGap = 2
        val dividerBottomGap = 18
        val thoughtContainerPaddingTop = 12
        val thoughtContainerPaddingHorizontal = textInset
        val thoughtTitleBottomGap = 10
        val thoughtContainerPaddingBottom = 14
        val thoughtContainerHeight = thoughtContainerPaddingTop +
            thoughtTitleHeight +
            thoughtTitleBottomGap +
            thoughtLayout.height +
            thoughtContainerPaddingBottom

        val contentHeight = topBarHeight +
            topPadding +
            metaHeight +
            selectedTopGap +
            selectedLayout.height +
            selectedBottomGap +
            dividerTopGap +
            1 +
            dividerBottomGap +
            thoughtContainerHeight +
            bottomPadding

        val bitmap = Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cardRect = RectF(0f, 0f, width.toFloat(), contentHeight.toFloat())
        canvas.drawRoundRect(cardRect, 22f, 22f, backgroundPaint)
        canvas.drawRoundRect(cardRect, 22f, 22f, borderPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), topBarHeight.toFloat(), topStripPaint)

        val metaBaseline = topBarHeight + topPadding - metaPaint.fontMetrics.top
        canvas.drawText(fittedMeta.leftText, horizontalPadding.toFloat(), metaBaseline, metaPaint)
        val rightWidth = metaPaint.measureText(fittedMeta.rightText)
        canvas.drawText(
            fittedMeta.rightText,
            (width - horizontalPadding).toFloat() - rightWidth,
            metaBaseline,
            metaPaint
        )

        val selectedTop = metaBaseline + metaPaint.fontMetrics.bottom + selectedTopGap
        canvas.drawRect(
            horizontalPadding.toFloat(),
            selectedTop,
            horizontalPadding + 3f,
            selectedTop + selectedLayout.height,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x665A7C62 }
        )
        canvas.withSave {
            translate(horizontalPadding + textInset.toFloat(), selectedTop)
            selectedLayout.draw(canvas)
        }

        val dividerY = selectedTop + selectedLayout.height + selectedBottomGap + dividerTopGap
        dividerPaint.shader = LinearGradient(
            horizontalPadding.toFloat(),
            dividerY,
            (width - horizontalPadding).toFloat(),
            dividerY,
            intArrayOf(0x005A7C62, 0x805A7C62.toInt(), 0x005A7C62),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(
            horizontalPadding.toFloat(),
            dividerY,
            (width - horizontalPadding).toFloat(),
            dividerY,
            dividerPaint
        )
        dividerPaint.shader = null

        val thoughtTop = dividerY + dividerBottomGap
        val thoughtRect = RectF(
            horizontalPadding.toFloat(),
            thoughtTop,
            (width - horizontalPadding).toFloat(),
            thoughtTop + thoughtContainerHeight
        )
        canvas.drawRoundRect(thoughtRect, 8f, 8f, thoughtBgPaint)
        val titleBaseline = thoughtRect.top + thoughtContainerPaddingTop - thoughtTitlePaint.fontMetrics.top
        canvas.drawText(
            thoughtTitle,
            thoughtRect.left + thoughtContainerPaddingHorizontal,
            titleBaseline,
            thoughtTitlePaint
        )
        canvas.withSave {
            translate(
                thoughtRect.left + thoughtContainerPaddingHorizontal,
                titleBaseline + thoughtTitlePaint.fontMetrics.bottom + thoughtTitleBottomGap
            )
            thoughtLayout.draw(canvas)
        }
        return bitmap
    }

    private fun createLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        lineSpacingMultiplier: Float = 1.25f
    ): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .setIncludePad(false)
                .setLineSpacing(0f, lineSpacingMultiplier)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                width,
                alignment,
                lineSpacingMultiplier,
                0f,
                false
            )
        }
    }

    private fun fitMeta(
        leftMeta: String,
        chapterName: String,
        paint: TextPaint,
        contentWidth: Int
    ): TopMeta {
        val workPaint = TextPaint(paint)
        val gap = 24f
        var size = 12f
        while (size >= 10f) {
            workPaint.textSize = size
            if (workPaint.measureText(leftMeta) + workPaint.measureText(chapterName) + gap <= contentWidth) {
                return TopMeta(size, leftMeta, chapterName)
            }
            size -= 0.5f
        }
        workPaint.textSize = 10f
        val rightMax = contentWidth * 0.38f
        val rightText = TextUtils.ellipsize(
            chapterName,
            workPaint,
            rightMax,
            TextUtils.TruncateAt.END
        ).toString()
        val rightWidth = workPaint.measureText(rightText)
        val leftMax = (contentWidth - rightWidth - gap).coerceAtLeast(contentWidth * 0.4f)
        val leftText = TextUtils.ellipsize(
            leftMeta,
            workPaint,
            leftMax,
            TextUtils.TruncateAt.END
        ).toString()
        return TopMeta(
            textSize = 10f,
            leftText = leftText,
            rightText = rightText
        )
    }

    private data class TopMeta(
        val textSize: Float,
        val leftText: String,
        val rightText: String
    )

    private fun resolveReadTypeface(): Typeface {
        val fontPath = ReadBookConfig.textFont
        return kotlin.runCatching {
            when {
                fontPath.isContentScheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    appCtx.contentResolver
                        .openFileDescriptor(Uri.parse(fontPath), "r")
                        ?.use { Typeface.Builder(it.fileDescriptor).build() }
                }

                fontPath.isContentScheme() -> {
                    RealPathUtil.getPath(appCtx, Uri.parse(fontPath))
                        ?.let { Typeface.createFromFile(it) }
                }

                fontPath.isNotEmpty() -> Typeface.createFromFile(fontPath)
                else -> when (AppConfig.systemTypefaces) {
                    1 -> Typeface.SERIF
                    2 -> Typeface.MONOSPACE
                    else -> Typeface.SANS_SERIF
                }
            } ?: Typeface.SANS_SERIF
        }.getOrDefault(Typeface.SANS_SERIF)
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): String {
        val displayName = "thought_${System.currentTimeMillis()}.png"
        val relativeDir = "${Environment.DIRECTORY_PICTURES}/LegadoThoughts"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException("create media record failed")
                context.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                } ?: throw IllegalStateException("open output stream failed")
                "$relativeDir/$displayName"
            } else {
                saveBitmapLegacy(context, bitmap, displayName)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun saveBitmapLegacy(context: Context, bitmap: Bitmap, displayName: String): String {
        val parent = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "LegadoThoughts"
        )
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val file = File(parent, displayName)
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/png"),
            null
        )
        return file.absolutePath
    }
}
