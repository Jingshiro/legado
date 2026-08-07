package io.legado.app.ui.about

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityReadRecordWebBinding
import io.legado.app.help.readrecord.DetailedReadRecordHelper
import io.legado.app.utils.GSON
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadRecordWebActivity : BaseActivity<ActivityReadRecordWebBinding>() {

    override val binding by viewBinding(ActivityReadRecordWebBinding::inflate)

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    // 等待页面加载完成后注入的 JSON 数据
    private var pendingJsonData: String? = null
    // 页面是否已完成加载（JS 环境就绪）
    private var pageReady = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = getString(R.string.read_record)
        setupWebView()
        loadDataFromDb()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true        // localStorage 存储主题偏好
                allowFileAccess = false         // 用 AssetLoader 替代 file:// 直接访问
                allowContentAccess = false
                setSupportZoom(false)
                displayZoomControls = false
                builtInZoomControls = false
            }

            // JS bridge：让页面按需从数据库查询笔记，避免把全量笔记/书签文本一次性注入导致 OOM
            addJavascriptInterface(ReadRecordJsBridge(), "LegadoBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ) = assetLoader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    pageReady = true
                    // 如果数据已经查好了，直接注入
                    pendingJsonData?.let { injectData(it) }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                    if (newProgress >= 100) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        AppLog.put("ReadRecordWeb JS: ${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            // 通过 AssetLoader 加载本地 assets 中的 HTML
            // URL 格式：https://appassets.androidplatform.net/assets/<path>
            loadUrl("https://appassets.androidplatform.net/assets/read_record/index.html")
        }

        // 处理返回键：优先让 WebView 回退历史
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * 从数据库异步读取详细阅读记录，构建 JSON 字符串
     */
    private fun loadDataFromDb() {
        lifecycleScope.launch {
            val jsonData = withContext(IO) {
                // 只注入阅读会话，笔记通过 JS bridge 按需查询，防止全量文本撑爆内存 (OOM)
                DetailedReadRecordHelper.buildExportJson(
                    appDb.detailedReadRecordDao.all(),
                    includeNotes = false
                )
            }
            // 回到主线程
            if (pageReady) {
                injectData(jsonData)
            } else {
                // 页面还没准备好，先存起来，等 onPageFinished 触发注入
                pendingJsonData = jsonData
            }
        }
    }

    /**
     * 调用页面内置的 window.setLegadoRecord() 函数注入数据。
     * 第二个参数 useBridgeNotes=true 让页面通过 JS bridge 按需加载笔记，而不是从 JSON 读取。
     * 该函数由 LegadoRecord HTML 原版提供，接受 JSON 字符串或对象。
     */
    private fun injectData(json: String) {
        pendingJsonData = null
        binding.webView.evaluateJavascript(
            "if(typeof setLegadoRecord === 'function'){ setLegadoRecord($json, true); }",
            null
        )
    }

    /**
     * JS bridge，供页面按需查询当前时间范围内的笔记（书签 + 想法）。
     * 返回结构：{ total, perBook: {书名: 数量}, notes: [{bookName, chapterName, bookText, content, time}, ...] }
     * notes 只返回最新的 limit 条（默认 500），避免一次性加载全量笔记文本导致 OOM。
     */
    @Suppress("unused")
    private class ReadRecordJsBridge {
        @JavascriptInterface
        fun getNotes(startTs: Long, endTs: Long, maxCount: Int): String {
            val limit = maxCount.coerceIn(10, 1000)
            val bookmarks = appDb.bookmarkDao.getByTimeRange(startTs, endTs, limit)
            val thoughts = appDb.bookThoughtDao.getByTimeRange(startTs, endTs, limit)

            val perBook = linkedMapOf<String, Int>()
            appDb.bookmarkDao.countByTimeRange(startTs, endTs).forEach {
                perBook[it.bookName] = perBook.getOrDefault(it.bookName, 0) + it.cnt.toInt()
            }
            appDb.bookThoughtDao.countByTimeRange(startTs, endTs).forEach {
                perBook[it.bookName] = perBook.getOrDefault(it.bookName, 0) + it.cnt.toInt()
            }

            val notes = mutableListOf<Map<String, Any?>>()
            bookmarks.forEach {
                notes += mapOf(
                    "bookName" to it.bookName,
                    "chapterName" to it.chapterName,
                    "bookText" to it.bookText,
                    "content" to it.content,
                    "time" to it.time
                )
            }
            thoughts.forEach {
                notes += mapOf(
                    "bookName" to it.bookName,
                    "chapterName" to it.chapterName,
                    "bookText" to it.selectedText,
                    "content" to it.thought,
                    "time" to it.createTime
                )
            }
            val capped = notes.sortedByDescending { it["time"] as Long }.take(limit)

            return GSON.toJson(
                mapOf(
                    "total" to perBook.values.sum(),
                    "perBook" to perBook,
                    "notes" to capped
                )
            )
        }
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            destroy()
        }
        super.onDestroy()
    }
}
