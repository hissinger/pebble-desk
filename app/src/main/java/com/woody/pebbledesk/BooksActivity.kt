package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 읽고 있는 책 목록(설정 > 읽고 있는 책). 줄마다 표지·제목·읽고 있는 앱, 오른쪽에 `↑ ↓`(순서)와 `빼기`.
 * 맨 위 책이 홈에 크게 보인다. 줄을 누르면 그 책의 앱을 바꾸고, 하단 `책 추가 ›` 로 책을 찾는다.
 */
class BooksActivity : EinkActivity() {
    private lateinit var titleCount: TextView
    private lateinit var list: LinearLayout
    private lateinit var empty: View
    private lateinit var addLink: TextView
    private lateinit var countText: TextView
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.now_reading)) { finish() }.apply {
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        root.addView(hline(2))
        hint = text(getString(R.string.books_order_hint), 15f, color = Ui.GRAY, lines = 2).apply {
            setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), dp(4))
        }
        root.addView(hint)

        empty = vbox().apply {
            setPadding(dp(Ui.MARGIN), dp(56), dp(Ui.MARGIN), 0)
            addView(text(getString(R.string.books_empty), 23f, Ui.bold))
            addView(text(getString(R.string.books_empty_desc), 17f, color = Ui.GRAY, lines = 3),
                lp(WRAP, WRAP).apply { topMargin = dp(12) })
        }
        root.addView(empty)
        list = vbox()
        root.addView(list, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        addLink = text(getString(R.string.add_book_link), 21f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { startActivity(BookSearchActivity.intent(this@BooksActivity)) }
        }
        footer.addView(addLink, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        countText = text("", 19f, color = Ui.GRAY).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
        }
        footer.addView(countText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val apps = AppStore.loadAndPrune(this, prefs)
        val books = BookShelf.list(this)
        titleCount.text = books.size.toString()
        countText.text = getString(R.string.books_count, books.size, BookShelf.MAX)
        // 다 차면 추가를 막는다(먼저 한 권을 빼게 한다).
        val full = BookShelf.isFull(this)
        addLink.isEnabled = !full
        addLink.setTextColor(if (full) Ui.LIGHT_GRAY else Ui.BLACK)
        empty.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
        hint.visibility = if (books.size > 1) View.VISIBLE else View.GONE

        list.removeAllViews()
        books.forEachIndexed { i, book ->
            if (i > 0) list.addView(hline(1, Ui.DIVIDER))
            val app = book.app?.let { key -> apps.find { it.key == key } }
            list.addView(hbox().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), dp(12))
                val w = dp(COVER_DP)
                addView(coverImage(BookShelf.cover(context, book, w)), lp(w + dp(2), w * 3 / 2 + dp(2)))
                addView(vbox().apply {
                    addView(text(book.title, 22f, Ui.bold, lines = 2))
                    addView(text(app?.label ?: getString(R.string.pick_reader_link), 16f,
                        color = if (app != null) Ui.GRAY else Ui.LIGHT_GRAY), lp(WRAP, WRAP).apply { topMargin = dp(6) })
                }, lp(0, WRAP, 1f).apply { marginStart = dp(16) })
                addView(orderArrow("↑", i > 0) { BookShelf.move(this@BooksActivity, book.id, -1); render() })
                addView(orderArrow("↓", i < books.size - 1) { BookShelf.move(this@BooksActivity, book.id, 1); render() })
                addView(text(getString(R.string.remove), 19f).apply {
                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    setPadding(dp(16), dp(14), 0, dp(14))
                    setOnClickListener { BookShelf.remove(this@BooksActivity, book.id); render() }
                })
                setOnClickListener {
                    startActivity(AppListActivity.intent(this@BooksActivity, AppListActivity.Mode.PICK_READER, book.id))
                }
            })
        }
    }


    companion object {
        private const val COVER_DP = 48

        fun intent(context: Context) = Intent(context, BooksActivity::class.java)
    }
}
