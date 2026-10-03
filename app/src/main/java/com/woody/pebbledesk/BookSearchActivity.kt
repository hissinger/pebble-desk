package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 지금 읽는 책 정하기: 제목으로 YES24 표지를 찾아 고르면 표지를 저장하고, 이어서 읽고 있는 앱을 고른다.
 * 결과는 표지 4열 그리드(한 페이지 두 줄)로 보여 주고 페이지로 넘긴다.
 */
class BookSearchActivity : EinkActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var rows: PagedRows
    private lateinit var pageText: TextView
    private var cellW = 0
    private var coverH = 0
    /** 늦게 끝난 옛 검색이 새 결과를 덮지 않도록 검색마다 번호를 붙인다. */
    private var searchId = 0
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screenW = resources.displayMetrics.widthPixels
        cellW = (screenW - 2 * dp(Ui.MARGIN) - (COLUMNS - 1) * dp(GAP_DP)) / COLUMNS
        coverH = cellW * 3 / 2

        val root = vbox()
        root.addView(titleRow(getString(R.string.book_search_title)) { finish() })
        root.addView(hline(2))

        // 제목 입력 줄: 박스 없이 아래 굵은 선만
        input = EditText(this).apply {
            hint = getString(R.string.book_query_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Ui.BLACK)
            setHintTextColor(Ui.LIGHT_GRAY)
            background = null
            setPadding(0, dp(8), 0, dp(8))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    search(); true
                } else false
            }
        }
        root.addView(hbox().apply {
            setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), 0)
            addView(input, lp(0, WRAP, 1f))
            addView(text(getString(R.string.search), 22f, Ui.bold).apply {
                setPadding(dp(20), dp(12), 0, dp(12))
                setOnClickListener { search() }
            })
        })
        root.addView(hline(2))
        status = text(getString(R.string.book_search_idle), 17f, color = Ui.GRAY, lines = 2).apply {
            setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), dp(8))
        }
        root.addView(status)

        rows = PagedRows(this, coverH + dp(56))
        root.addView(rows, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            setOnClickListener { rows.next() }
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(72)))
        rows.onPageChanged = {
            pageText.text = if (rows.pageCount <= 1) "" else "${rows.page + 1} / ${rows.pageCount}   ›"
        }
        setContentView(root)

        input.requestFocus()
        input.post { getSystemService(InputMethodManager::class.java).showSoftInput(input, 0) }
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    private fun search() {
        val query = input.text.toString().trim()
        if (query.isEmpty() || busy) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        val id = ++searchId
        status.setText(R.string.searching)
        Bg.run({ BookSearch.search(query) }) { result ->
            if (id != searchId || isDestroyed) return@run
            result.onSuccess { books ->
                status.text = if (books.isEmpty()) getString(R.string.book_none) else getString(R.string.book_results, books.size)
                rows.setRows(books.chunked(COLUMNS).map { line -> { gridRow(line) } })
                rows.showPage(0)
            }.onFailure { status.text = errorText(R.string.book_error, it) }
        }
    }

    /** 표지 한 줄. 표지 아래에 제목과 저자를 한 줄씩. */
    private fun gridRow(books: List<BookResult>): View = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), 0)
        books.forEachIndexed { i, book ->
            addView(vbox().apply {
                val cover = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(0xFFEEEEEE.toInt())
                }
                addView(cover, lp(cellW, coverH))
                loadThumb(cover, book.thumbUrl)
                addView(text(book.title, 14f, Ui.bold), lp(MATCH, WRAP).apply { topMargin = dp(6) })
                if (book.author.isNotBlank()) addView(text(book.author, 12f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(2) })
                setOnClickListener { pick(book) }
            }, lp(cellW, MATCH).apply { if (i > 0) marginStart = dp(GAP_DP) })
        }
    }

    private fun loadThumb(view: ImageView, url: String) {
        view.tag = url
        thumbs.get(url)?.let { view.setImageBitmap(it); view.background = null; return }
        val width = cellW
        Bg.run({ BookSearch.grayCover(BookSearch.download(url), width) }) { result ->
            val bmp = result.getOrNull() ?: return@run
            thumbs.put(url, bmp)
            if (view.tag == url) {
                view.setImageBitmap(bmp)
                view.background = null
            }
        }
    }

    /** 표지를 받아 저장한 뒤 읽는 앱을 고르러 간다. */
    private fun pick(book: BookResult) {
        if (busy) return
        busy = true
        status.setText(R.string.cover_loading)
        Bg.run({
            val bytes = runCatching { BookSearch.download(book.coverUrl) }.getOrElse { BookSearch.download(book.thumbUrl) }
            NowReadingStore.save(this, book, bytes)
        }) { result ->
            busy = false
            if (isDestroyed) return@run
            result.onSuccess {
                startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER))
                finish()
            }.onFailure { status.text = errorText(R.string.cover_error, it) }
        }
    }

    /** 연결 문제면 와이파이를 확인하라고, 그 밖에는 [res] 에 오류 내용을 넣어 보여 준다. */
    private fun errorText(res: Int, e: Throwable): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException -> getString(R.string.no_network)
        else -> getString(res, e.message ?: e.javaClass.simpleName)
    }

    companion object {
        private const val COLUMNS = 4
        private const val GAP_DP = 14
        /** 검색 결과 표지(흑백으로 줄인 것) */
        private val thumbs = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        fun intent(context: Context) = Intent(context, BookSearchActivity::class.java)
    }
}
