package com.woody.pebblehome

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * 스크롤 없이 페이지 단위로 넘기는 목록. 주어진 높이에 들어가는 만큼만 줄을 그리고, 줄 사이에 가는 회색 선을 둔다.
 * 전자잉크라 넘길 때 애니메이션을 쓰지 않는다.
 */
class PagedRows(context: Context, rowHeightPx: Int) : LinearLayout(context) {
    private var rows: List<() -> View> = emptyList()
    private val dividerPx = context.dp(1)

    /** 줄 높이. 글자 크기 설정이 바뀌면 다시 정한다. */
    var rowHeightPx: Int = rowHeightPx
        set(v) {
            if (v == field) return
            field = v
            // 들어갈 줄 수가 그대로여도 이미 그린 줄은 옛 높이이므로 다시 그린다.
            if (!fitRows(height)) render()
        }

    var perPage = 0
        private set
    var page = 0
        private set
    val pageCount: Int get() = if (perPage <= 0) 1 else maxOf(1, (rows.size + perPage - 1) / perPage)

    /** 목록 위에 함께 붙는 머리(제목·선). 들어갈 줄 수를 셀 때 그 높이를 뺀다. */
    var header: List<View> = emptyList()

    /** 페이지나 페이지 수가 바뀌면 불린다(페이지 점·쪽 번호 갱신용). */
    var onPageChanged: (() -> Unit)? = null

    init {
        orientation = VERTICAL
    }

    fun setRows(rows: List<() -> View>) {
        this.rows = rows
        render()
    }

    fun showPage(p: Int) {
        val target = p.coerceIn(0, pageCount - 1)
        if (target == page) return
        page = target
        render()
    }

    fun next() = showPage(if (page + 1 >= pageCount) 0 else page + 1)
    fun prev() = showPage(if (page == 0) pageCount - 1 else page - 1)

    /** [index] 번째 줄이 있는 페이지 */
    fun pageOf(index: Int) = if (perPage <= 0) 0 else index / perPage

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitRows(h)
    }

    /**
     * 높이 [h] 에 들어가는 줄 수를 다시 정한다. 레이아웃 중에 자식을 바꾸지 않도록 다음 차례에 그린다.
     * 줄 수가 바뀌어 다시 그리기로 했으면 true.
     */
    private fun fitRows(h: Int): Boolean {
        if (h <= 0) return false
        val headerPx = header.sumOf { v ->
            v.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val m = v.layoutParams as? MarginLayoutParams
            v.measuredHeight + (m?.topMargin ?: 0) + (m?.bottomMargin ?: 0)
        }
        val fit = maxOf(1, (h - headerPx + dividerPx) / (rowHeightPx + dividerPx))
        if (fit == perPage) return false
        perPage = fit
        post { render() }
        return true
    }

    private fun render() {
        removeAllViews()
        if (perPage <= 0) return
        header.forEach { addView(it) }
        page = page.coerceIn(0, pageCount - 1)
        val from = page * perPage
        val to = minOf(rows.size, from + perPage)
        for (i in from until to) {
            if (i > from) addView(context.hline(1, Ui.DIVIDER))
            addView(rows[i](), lp(MATCH, rowHeightPx))
        }
        onPageChanged?.invoke()
    }
}
