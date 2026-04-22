# 修改计划 - 修正阅读记录中的订阅源记录及删除逻辑

## 目标内容
1.  **排除订阅源记录**：在“详细阅读记录”中，不再记录打开订阅（RSS）源及停留的时间。
2.  **联动删除**：当用户在“我的-阅读记录”中删除某本书的阅读记录或清空所有记录时，同步删除对应的“详细阅读记录”。

## 修改方案

### 1. 排除订阅源的详细阅读记录

#### [MODIFY] [ReadRssActivity](app/src/main/java/io/legado/app/ui/rss/read/ReadRssActivity.kt)
- 删除 `detailedReadRecordTracker` 和 `detailedReadRecordObserver` 的定义。
- 删除 `onActivityCreated` 中对 `detailedReadRecordObserver` 的添加和 `start()` 的调用。
- 删除 `onNewIntent` 中对 `detailedReadRecordTracker.start()` 的调用。

#### [MODIFY] [VideoPlayerActivity](app/src/main/java/io/legado/app/ui/video/VideoPlayerActivity.kt)
- 修改 `detailedReadRecordTracker` 的 `bookNameProvier` 逻辑，如果当前源是订阅源 (`VideoPlay.source is RssSource`)，则返回 `null`，从而跳过记录。

### 2. 联动删除详细阅读记录

#### [MODIFY] [ReadRecordActivity](app/src/main/java/io/legado/app/ui/about/ReadRecordActivity.kt)
- 在删除单个书籍记录的 `sureDelAlert` 方法中，增加调用 `appDb.detailedReadRecordDao.deleteByBookName(item.bookName)`。
- 在清空所有记录的 `initView` 点击监听器中，增加调用 `appDb.detailedReadRecordDao.clear()`。

## 待确认问题
- 除了 `ReadRssActivity` 和 `VideoPlayerActivity`（播放订阅视频时），是否还有其他地方需要排除订阅记录？初步检查 `AudioPlayActivity` 似乎只有书籍路径，但在 `VideoPlay` 中明确支持了 `SourceType.rss`。

## 验证计划
- **手动验证**：
    1.  打开订阅源进行阅读，确认数据库中没有产生 `bookName` 为订阅源名称或 `Null` 的详细阅读记录。
    2.  删除某一本书的阅读记录，确认其详细阅读记录也被一并删除。
    3.  点击清空所有阅读记录，确认详细阅读记录表也被清空。
