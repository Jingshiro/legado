package io.legado.app.ui.book.read.page.provider

import io.legado.app.R
import io.legado.app.model.ReadBook
import io.legado.app.help.book.update
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.api.PageFactory
import io.legado.app.ui.book.read.page.entities.TextPage
import splitties.init.appCtx

class TextPageFactory(dataSource: DataSource) : PageFactory<TextPage>(dataSource) {

    private val keepSwipeTip = appCtx.getString(R.string.keep_swipe_tip)

    override fun hasPrev(): Boolean = with(dataSource) {
        if (ReadBook.showBookplate == -1) return false
        return hasPrevChapter() || pageIndex > 0 || (currentChapter?.chapter?.index == 0 && pageIndex == 0)
    }

    override fun hasNext(): Boolean = with(dataSource) {
        if (ReadBook.showBookplate == 1) return false
        val isLastChapter = currentChapter?.chapter?.index == (currentChapter?.chaptersSize?.minus(1) ?: 0)
        return hasNextChapter() || (currentChapter != null && currentChapter?.isLastIndex(pageIndex) != true) || isLastChapter
    }

    override fun hasNextPlus(): Boolean = with(dataSource) {
        if (ReadBook.showBookplate == 1) return false
        return hasNextChapter() || pageIndex < (currentChapter?.pageSize ?: 1) - 2
    }

    override fun moveToFirst() {
        ReadBook.showBookplate = 0
        ReadBook.setPageIndex(0)
    }

    override fun moveToLast() = with(dataSource) {
        ReadBook.showBookplate = 0
        currentChapter?.let {
            if (it.pageSize == 0) {
                ReadBook.setPageIndex(0)
            } else {
                ReadBook.setPageIndex(it.pageSize.minus(1))
            }
        } ?: ReadBook.setPageIndex(0)
    }

    override fun moveToNext(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasNext()) {
            if (ReadBook.showBookplate == -1) {
                ReadBook.showBookplate = 0
                if (upContent) upContent(resetPageOffset = false)
                return@with true
            }
            val pageIndex = pageIndex
            if (currentChapter == null || currentChapter?.isLastIndex(pageIndex) == true) {
                val isLastChapter = currentChapter?.chapter?.index == (currentChapter?.chaptersSize?.minus(1) ?: 0)
                if (isLastChapter && ReadBook.showBookplate == 0) {
                    val book = ReadBook.book
                    if (book != null && book.readIteration % 2 == 0 && ReadBook.inBookshelf) {
                        ReadBook.callBack?.onBookEnd()
                        return@with false
                    }
                    ReadBook.showBookplate = 1
                    book?.let {
                        if (it.finishTime <= 0) {
                            it.finishTime = System.currentTimeMillis()
                            io.legado.app.data.appDb.bookDao.update(it)
                        }
                    }
                    if (upContent) upContent(resetPageOffset = false)
                    return@with true
                }
                if ((currentChapter == null || isScroll) && nextChapter == null) {
                    return@with false
                }
                ReadBook.moveToNextChapter(upContent, false)
            } else {
                if (pageIndex < 0 || currentChapter?.isLastIndexCurrent(pageIndex) == true) {
                    val isLastChapter = currentChapter?.chapter?.index == (currentChapter?.chaptersSize?.minus(1) ?: 0)
                    if (isLastChapter && ReadBook.showBookplate == 0) {
                        val book = ReadBook.book
                        if (book != null && book.readIteration % 2 == 0 && ReadBook.inBookshelf) {
                            ReadBook.callBack?.onBookEnd()
                            return@with false
                        }
                        ReadBook.showBookplate = 1
                        book?.let {
                            if (it.finishTime <= 0) {
                                it.finishTime = System.currentTimeMillis()
                                io.legado.app.data.appDb.bookDao.update(it)
                            }
                        }
                        if (upContent) upContent(resetPageOffset = false)
                        return@with true
                    }
                    return@with false
                }
                ReadBook.setPageIndex(pageIndex.plus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else {
            ReadBook.callBack?.onBookEnd()
            false
        }
    }

    override fun moveToPrev(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasPrev()) {
            if (ReadBook.showBookplate == 1) {
                ReadBook.showBookplate = 0
                if (upContent) upContent(resetPageOffset = false)
                return@with true
            }
            if (pageIndex <= 0) {
                if (currentChapter?.chapter?.index == 0 && pageIndex == 0 && ReadBook.showBookplate == 0) {
                    ReadBook.showBookplate = -1
                    if (upContent) upContent(resetPageOffset = false)
                    return@with true
                }
                if (currentChapter == null && prevChapter == null) {
                    return@with false
                }
                if (prevChapter != null && prevChapter?.isCompleted == false) {
                    return@with false
                }
                ReadBook.moveToPrevChapter(upContent, upContentInPlace = false)
            } else {
                if (currentChapter == null) {
                    return@with false
                }
                ReadBook.setPageIndex(pageIndex.minus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else
            false
    }

    override val curPage: TextPage
        get() = with(dataSource) {
            if (ReadBook.showBookplate == -1) {
                return@with TextPage().apply { isBookplateStart = true }
            } else if (ReadBook.showBookplate == 1) {
                return@with TextPage().apply { isBookplateEnd = true }
            }
            ReadBook.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                return@with it.getPage(pageIndex)
                    ?: TextPage(title = it.title).apply { textChapter = it }.format()
            }
            return TextPage().format()
        }

    override val nextPage: TextPage
        get() = with(dataSource) {
            if (ReadBook.showBookplate == -1) {
                currentChapter?.let {
                    return@with it.getPage(0) ?: TextPage(title = it.title).apply { textChapter = it }.format()
                }
                return@with TextPage().format()
            }
            ReadBook.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex < it.pageSize - 1) {
                    return@with it.getPage(pageIndex + 1)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
                if (it.chapter.index == it.chaptersSize - 1) {
                    return@with TextPage().apply { isBookplateEnd = true }
                }
            }
            nextChapter?.let {
                return@with it.getPage(0)?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val prevPage: TextPage
        get() = with(dataSource) {
            if (ReadBook.showBookplate == 1) {
                currentChapter?.let {
                    return@with it.getPage(it.pageSize - 1) ?: TextPage(title = it.title).apply { textChapter = it }.format()
                }
                return@with TextPage().format()
            }
            ReadBook.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex > 0) {
                    return@with it.getPage(pageIndex - 1)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
                if (it.chapter.index == 0) {
                    return@with TextPage().apply { isBookplateStart = true }
                }
            }
            prevChapter?.let {
                return@with it.lastPage?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val nextPlusPage: TextPage
        get() = with(dataSource) {
            if (ReadBook.showBookplate == -1) {
                currentChapter?.let {
                    return@with it.getPage(1) ?: TextPage(title = it.title).apply { textChapter = it }.format()
                }
                return@with TextPage().format()
            }
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex < it.pageSize - 2) {
                    return@with it.getPage(pageIndex + 2)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
                if (it.chapter.index == it.chaptersSize - 1) {
                    if (pageIndex == it.pageSize - 2) {
                        return@with TextPage().apply { isBookplateEnd = true }
                    }
                    return@with TextPage().format()
                }
                nextChapter?.let { nc ->
                    if (pageIndex < it.pageSize - 1) {
                        return@with nc.getPage(0)?.removePageAloudSpan()
                            ?: TextPage(title = nc.title).format()
                    }
                    return@with nc.getPage(1)?.removePageAloudSpan()
                        ?: TextPage(text = keepSwipeTip).format()
                }
            }
            return TextPage().format()
        }
}
