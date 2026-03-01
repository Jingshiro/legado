package io.legado.app.ui.book.thought

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookThought
import io.legado.app.databinding.DialogShareThoughtBinding
import io.legado.app.databinding.ItemThoughtShareStyleBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import androidx.core.content.ContextCompat
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import android.util.Base64

class ShareThoughtDialog : BaseDialogFragment(R.layout.dialog_share_thought) {

    private lateinit var binding: DialogShareThoughtBinding
    private var bookThought: BookThought? = null
    private var thoughtText: String = ""
    private var currentStyleIndex: Int = AppConfig.thoughtShareStyle
    private val adapter by lazy { StyleAdapter(requireContext()) }

    // 五种视觉风格
    private val styles = listOf(
        StyleItem("默认", R.color.md_grey_300),
        StyleItem("黏土", R.color.md_amber_100),
        StyleItem("水墨", R.color.md_grey_100),
        StyleItem("复古", R.color.md_brown_100),
        StyleItem("黑金", R.color.md_grey_900)
    )

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding = DialogShareThoughtBinding.bind(view)

        arguments?.let {
            bookThought = it.getParcelable("bookThought")
            thoughtText = it.getString("thoughtText") ?: ""
        }

        if (bookThought == null) {
            dismiss()
            return
        }

        initView()
        initWebView()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun initView() {
        // 标题已在布局 XML 中静态设置，无需动态赋值

        binding.recyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerView.adapter = adapter

        if (currentStyleIndex !in styles.indices) {
            currentStyleIndex = 0
            AppConfig.thoughtShareStyle = 0
        }

        adapter.setItems(styles)

        binding.btnSave.setOnClickListener { saveImage() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        // 不使用宽视口，让卡片按 100vw 渲染，避免宽度被强制拉伸
        webSettings.useWideViewPort = false
        webSettings.loadWithOverviewMode = false

        binding.webView.addJavascriptInterface(WebAppInterface(requireContext()), "Android")

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.isVisible = false
                // 先注入字体，再填充内容，避免内容渲染时字体尚未就绪
                injectFont()
                injectDataToWebView()
            }
        }

        binding.progressBar.isVisible = true
        binding.webView.loadUrl("file:///android_asset/thought_template.html")
    }

    private fun injectDataToWebView() {
        val bt = bookThought ?: return
        val bookName = escapeJsString(bt.bookName)
        val author = escapeJsString(bt.bookAuthor)
        val chapter = escapeJsString(bt.chapterName)
        val selectedText = escapeJsString(bt.selectedText)
        val thought = escapeJsString(thoughtText)
        val js = "javascript:updateContent('$bookName','$author','$chapter','$selectedText','$thought',$currentStyleIndex);"
        binding.webView.evaluateJavascript(js, null)
    }

    /**
     * 读取用户在阅读界面配置的字体，注入到 WebView。
     * 自定义字体文件通过 base64 编码后以 @font-face 方式注入，
     * 系统字体则直接更新 CSS 变量。
     * 字体读取在 IO 线程完成，注入在主线程执行。
     */
    private fun injectFont() {
        lifecycleScope.launch {
            val fontPath = ReadBookConfig.textFont
            when {
                fontPath.isContentScheme() -> {
                    val base64 = withContext(Dispatchers.IO) {
                        runCatching {
                            appCtx.contentResolver
                                .openInputStream(Uri.parse(fontPath))
                                ?.use { it.readBytes() }
                                ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                        }.getOrNull()
                    }
                    if (base64 != null) {
                        val mime = guessFontMime(fontPath)
                        val js = "javascript:setFont('$base64','$mime');"
                        binding.webView.evaluateJavascript(js, null)
                    } else {
                        injectSystemFont()
                    }
                }

                fontPath.isNotEmpty() -> {
                    val base64 = withContext(Dispatchers.IO) {
                        runCatching {
                            val realPath = if (fontPath.startsWith("/")) {
                                fontPath
                            } else {
                                RealPathUtil.getPath(appCtx, Uri.parse(fontPath))
                            }
                            realPath?.let {
                                java.io.File(it).readBytes()
                                    .let { bytes -> Base64.encodeToString(bytes, Base64.NO_WRAP) }
                            }
                        }.getOrNull()
                    }
                    if (base64 != null) {
                        val mime = guessFontMime(fontPath)
                        val js = "javascript:setFont('$base64','$mime');"
                        binding.webView.evaluateJavascript(js, null)
                    } else {
                        injectSystemFont()
                    }
                }

                else -> injectSystemFont()
            }
        }
    }

    private fun injectSystemFont() {
        val family = when (AppConfig.systemTypefaces) {
            1    -> "serif"
            2    -> "monospace"
            else -> "sans-serif"
        }
        binding.webView.evaluateJavascript("javascript:setSystemFont('$family');", null)
    }

    private fun guessFontMime(path: String): String {
        return when {
            path.endsWith(".otf", ignoreCase = true) -> "font/otf"
            path.endsWith(".woff2", ignoreCase = true) -> "font/woff2"
            path.endsWith(".woff", ignoreCase = true) -> "font/woff"
            else -> "font/ttf"
        }
    }

    private fun escapeJsString(text: String?): String {
        return text
            ?.replace("\\", "\\\\")
            ?.replace("'", "\\'")
            ?.replace("\"", "\\\"")
            ?.replace("\n", "\\n")
            ?.replace("\r", "") ?: ""
    }

    private fun changeStyle(index: Int) {
        if (index in styles.indices) {
            currentStyleIndex = index
            AppConfig.thoughtShareStyle = index
            adapter.notifyDataSetChanged()
            binding.webView.evaluateJavascript("javascript:changeStyle($index);", null)
        }
    }

    private fun saveImage() {
        binding.progressBar.isVisible = true
        val ctx = requireContext()
        val bitmap = captureWebView(binding.webView)
        if (bitmap == null) {
            binding.progressBar.isVisible = false
            ctx.toastOnUi(R.string.thought_image_save_failed)
            return
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val fileName = "thought_${System.currentTimeMillis()}.png"
                    saveToGallery(ctx, bitmap, fileName)
                }
            }.onSuccess { fileUri ->
                binding.progressBar.isVisible = false
                if (fileUri != null) {
                    ctx.toastOnUi(getString(R.string.thought_image_saved, fileUri.toString()))
                } else {
                    ctx.toastOnUi(R.string.thought_image_save_failed)
                }
            }.onFailure {
                binding.progressBar.isVisible = false
                ctx.toastOnUi(R.string.thought_image_save_failed)
            }
        }
    }

    private fun saveToGallery(ctx: Context, bitmap: Bitmap, fileName: String): android.net.Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }
        val uri = ctx.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ) ?: return null
        val out: OutputStream = ctx.contentResolver.openOutputStream(uri) ?: return null
        out.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }

    /**
     * 在主线程截取 WebView 全部内容（含超出可见屏幕的部分）。
     * 应用内部已在 App 初始化阶段开启 WebView.enableSlowWholeDocumentDraw()，
     * 因此直接 draw 即可完整截取而不会被视野裁剪，也不用再去瞎切软件图层导致文字渲染丢失。
     */
    private fun captureWebView(webView: WebView): Bitmap? {
        return try {
            val width = webView.measuredWidth.takeIf { it > 0 } ?: return null
            val height = (webView.contentHeight * webView.scale).toInt().takeIf { it > 0 }
                ?: webView.measuredHeight.takeIf { it > 0 } ?: return null

            // 必须滚回顶部，否则绘制会从当前 scrollY 起始，造成顶部被切掉
            webView.scrollTo(0, 0)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            webView.draw(Canvas(bitmap))

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun updateStyleHeight(height: Int) {
            // 保留接口，后续扩展使用
        }
    }

    data class StyleItem(val name: String, val colorRes: Int)

    inner class StyleAdapter(context: Context) :
        RecyclerAdapter<StyleItem, ItemThoughtShareStyleBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemThoughtShareStyleBinding {
            return ItemThoughtShareStyleBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThoughtShareStyleBinding,
            item: StyleItem,
            payloads: MutableList<Any>
        ) {
            val position = holder.layoutPosition
            binding.tvStyleName.text = item.name
            binding.tvStyleName.setBackgroundColor(
                ContextCompat.getColor(context, item.colorRes)
            )
            if (position == currentStyleIndex) {
                binding.tvStyleName.alpha = 1.0f
                binding.tvStyleName.textSize = 14f
                binding.tvStyleName.paint.isFakeBoldText = true
            } else {
                binding.tvStyleName.alpha = 0.5f
                binding.tvStyleName.textSize = 12f
                binding.tvStyleName.paint.isFakeBoldText = false
            }
            binding.root.setOnClickListener {
                if (position != currentStyleIndex) changeStyle(position)
            }
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemThoughtShareStyleBinding
        ) {
            // 在 convert 中管理
        }
    }

    companion object {
        fun newInstance(bookThought: BookThought, thoughtText: String): ShareThoughtDialog {
            val fragment = ShareThoughtDialog()
            val args = Bundle()
            args.putParcelable("bookThought", bookThought)
            args.putString("thoughtText", thoughtText)
            fragment.arguments = args
            return fragment
        }
    }
}
