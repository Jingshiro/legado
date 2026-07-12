package io.legado.app.ui.about

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityReadRecordWebBinding
import io.legado.app.help.readrecord.DetailedReadRecordHelper
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
                DetailedReadRecordHelper.buildExportJson(appDb.detailedReadRecordDao.all())
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
     * 该函数由 LegadoRecord HTML 原版提供，接受 JSON 字符串或对象。
     */
    private fun injectData(json: String) {
        pendingJsonData = null
        binding.webView.evaluateJavascript(
            "if(typeof setLegadoRecord === 'function'){ setLegadoRecord($json); }",
            null
        )
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            destroy()
        }
        super.onDestroy()
    }
}
