package io.legado.app.ui.book.thought

import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogThoughtUnderlineStyleBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ThoughtUnderlineStyleDialog(
    private val currentStyle: TextLine.ThoughtUnderlineStyle = TextLine.ThoughtUnderlineStyle(),
    private val onConfirm: (TextLine.ThoughtUnderlineStyle) -> Unit
) : BaseDialogFragment(R.layout.dialog_thought_underline_style) {

    private val binding by viewBinding(DialogThoughtUnderlineStyleBinding::bind)
    private var selectedStyle = currentStyle.style
    private var selectedWeight = currentStyle.weight
    private var selectedColor = currentStyle.color
    private var selectedColorInt: Int = Color.TRANSPARENT

    companion object {
        const val COLOR_DIALOG_ID = 1001

        /** 静态持有者：让 Activity 的 onColorSelected 能直接调到当前 dialog */
        @Volatile
        var pendingDialog: ThoughtUnderlineStyleDialog? = null

        /** 从 AppConfig 读取上次使用的样式 */
        fun lastUsedStyle(): TextLine.ThoughtUnderlineStyle {
            return TextLine.ThoughtUnderlineStyle(
                style = AppConfig.thoughtUnderlineStyle,
                weight = AppConfig.thoughtUnderlineWeight,
                color = AppConfig.thoughtUnderlineColor
            )
        }

        /** 保存样式到 AppConfig */
        fun saveLastUsedStyle(style: TextLine.ThoughtUnderlineStyle) {
            AppConfig.thoughtUnderlineStyle = style.style
            AppConfig.thoughtUnderlineWeight = style.weight
            AppConfig.thoughtUnderlineColor = style.color
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onResume() {
        super.onResume()
        pendingDialog = this
    }

    override fun onPause() {
        super.onPause()
        pendingDialog = null
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? ReadBookActivity)?.bottomDialog++
        binding.root.applyNavigationBarPadding()
        val bg = requireContext().bottomBackground
        (binding.root as? com.google.android.material.card.MaterialCardView)
            ?.setCardBackgroundColor(bg)
        binding.root.background = GradientDrawable().apply {
            cornerRadius = 16f.dpToPx()
            setColor(bg)
        }
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = requireContext().getPrimaryTextColor(isLight)
        binding.run {
            tvTitle.setTextColor(textColor)
            tvWeightValue.setTextColor(textColor)
            tvCancel.setTextColor(textColor)
        }
        selectedColorInt = if (selectedColor.isNotEmpty()) {
            try { Color.parseColor(selectedColor) } catch (_: Exception) {
                requireContext().getColor(R.color.accent)
            }
        } else {
            requireContext().getColor(R.color.accent)
        }
        updateColorDisplay()
        initStyleChips()
        initWeightSeekBar()
        initColorButton()
        initPreview()
        initButtons()
    }

    /** 供 ReadBookActivity 转发颜色回调 */
    fun onColorSelected(dialogId: Int, color: Int) {
        if (dialogId == COLOR_DIALOG_ID) {
            selectedColorInt = color
            selectedColor = String.format("#%06X", 0xFFFFFF and color)
            updateColorDisplay()
            updatePreview()
        }
    }

    private fun initStyleChips() = binding.run {
        chipGroupStyle.check(when (selectedStyle) {
            1 -> R.id.chip_solid
            2 -> R.id.chip_dashed
            4 -> R.id.chip_dotted
            else -> R.id.chip_default
        })
        chipGroupStyle.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedStyle = when (checkedIds.firstOrNull()) {
                R.id.chip_solid -> 1
                R.id.chip_dashed -> 2
                R.id.chip_dotted -> 4
                else -> 0
            }
            updatePreview()
        }
    }

    private fun initWeightSeekBar() = binding.run {
        seekWeight.progress = ((selectedWeight - 0.5f) * 20).toInt()
        tvWeightValue.text = String.format("%.1fdp", selectedWeight)
        seekWeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                selectedWeight = 0.5f + progress / 20f
                tvWeightValue.text = String.format("%.1fdp", selectedWeight)
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun initColorButton() = binding.run {
        tvColorValue.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(COLOR_DIALOG_ID)
                .setColor(selectedColorInt)
                .show(requireActivity())
        }
    }

    private fun updateColorDisplay() = binding.run {
        if (selectedColor.isEmpty()) {
            tvColorValue.text = getString(R.string.underline_select_color)
            tvColorValue.setTextColor(requireContext().getColor(R.color.accent))
        } else {
            tvColorValue.text = selectedColor
            tvColorValue.setTextColor(selectedColorInt)
        }
    }

    private fun getColor(): Int {
        return if (selectedColor.isNotEmpty()) {
            try { Color.parseColor(selectedColor) } catch (_: Exception) {
                requireContext().getColor(R.color.accent)
            }
        } else {
            requireContext().getColor(R.color.accent)
        }
    }

    private fun initPreview() {
        updatePreview()
    }

    private fun updatePreview() = binding.run {
        val previewView = vwPreview
        previewView.post {
            val w = previewView.width.toFloat()
            val h = previewView.height.toFloat()
            if (w <= 0 || h <= 0) return@post
            val bitmap = android.graphics.Bitmap.createBitmap(
                w.toInt(), h.toInt(), android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            val lineY = h * 0.7f
            val color = getColor()
            val strokeWidthPx = selectedWeight.dpToPx()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = strokeWidthPx
                style = Paint.Style.STROKE
            }
            when (selectedStyle) {
                1 -> paint.pathEffect = null
                2 -> paint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                4 -> paint.pathEffect = DashPathEffect(floatArrayOf(2f, 8f), 0f)
                else -> paint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            canvas.drawLine(0f, lineY, w, lineY, paint)
            previewView.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
        }
    }

    private fun initButtons() = binding.run {
        tvCancel.setOnClickListener { dismiss() }
        tvOk.setOnClickListener {
            val result = TextLine.ThoughtUnderlineStyle(selectedStyle, selectedWeight, selectedColor)
            saveLastUsedStyle(result)
            onConfirm(result)
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? ReadBookActivity)?.bottomDialog--
    }
}
