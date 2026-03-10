# 自动上传阅读记录功能：网页地址修改指南

此文档说明了当预设的接收网址（目前为 `myst423.shop/recorder/`）发生变化时，由于安全考虑限制了该功能的作用域，你需要在阅读（Legado）安卓端源码中修改哪些文件。

## 涉及修改的文件

一共需要修改两个文件，并且只需要在每个文件内**修改一行代码**。

### 1. RssJsExtensions.kt

**文件路径**：`app/src/main/java/io/legado/app/ui/rss/read/RssJsExtensions.kt`

**目的**：这个文件里的修改用来保证只有包含你的特定网址的网页，才能够通过 JavaScript 获取到你的本地阅读记录详情数据。

**修改位置**：找到并搜索 `getReadRecordDetail` 这一函数。大约位于第 70 行左右：

```kotlin
    @JavascriptInterface
    fun getReadRecordDetail(): String? {
        val url = activityRef.get()?.findViewById<android.webkit.WebView>(io.legado.app.R.id.web_view)?.url ?: ""
        // 👇👇👇 这里修改为你新的网址的关键字（用来进行安全匹配） 👇👇👇
        if (url.contains("myst423.shop/recorder/")) {
            return DetailedReadRecordHelper.buildExportJson(appDb.detailedReadRecordDao.all())
        }
        return null
    }
```

将 `url.contains(...)` 里面的字符串替换成你**新的域名或者路径**。

### 2. ReadRssActivity.kt

**文件路径**：`app/src/main/java/io/legado/app/ui/rss/read/ReadRssActivity.kt`

**目的**：这个文件里的修改用来保证当应用内置的浏览器打开了指定网址后，能自动向网页中执行预设的推送逻辑。

**修改位置**：找到并搜索 `onPageFinished` 这一函数里的 `myst423.shop/recorder/`。大约位于文件接近末尾处，`CustomWebViewClient` 内部：

```kotlin
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            // ... 省略部分代码 ...

            // 👇👇👇 这里修改为你新的网址的关键字（用来判断是否当前页面是你预设的接收页面） 👇👇👇
            if (url.contains("myst423.shop/recorder/")) {
                // 如果你的网页接收函数变了名字，也可以修改下面这一行：把 setLegadoRecord 改成新的。
                val json = io.legado.app.help.readrecord.DetailedReadRecordHelper.buildExportJson(appDb.detailedReadRecordDao.all())
                val js = "if(typeof setLegadoRecord === 'function'){ setLegadoRecord($json); }"
                view.evaluateJavascript(js, null)
            }
        }
```

将 `url.contains(...)` 里面的字符串同样替换成你**新的域名或者路径**，确保与上面 RssJsExtensions 中的内容一致。

### 重新编译和构建

在以上两处正确修改新地址并且保存后，你可以按照以前的构建方式：
使用 Android Studio 或者通过控制台命令（如 `./gradlew assembleRelease`）重新编译打包你的专属 Legado 版本。

### 网页端前置需求提示

记得在需要接收数据的新网页上，事先挂载好对应名字（默认为 `setLegadoRecord`）的接收函数：

```javascript
<script>
    // 定义一个全局函数来承接传入的记录数据
    function setLegadoRecord(jsonDataStr) {
        if (!jsonDataStr) return;

        // 这一步最好使用 try-catch 解析 JSON 数据
        try {
            const data = JSON.parse(jsonDataStr);
            console.log("成功接收阅读记录，总数：" + data.length);
            // 接下来实现你想要的服务端上传/存储逻辑
        } catch (e) {
            console.error("解析阅读记录失败", e);
        }
    }
</script>
```
