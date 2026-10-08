package com.woody.pebbledesk

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout

/**
 * 스크롤 없이 페이지 단위로 넘기는 목록. 주어진 높이에 들어가는 만큼만 줄을 그리고, 줄 사이에 가는 회색 선을 둔다.
 * 전자잉크라 넘길 때 애니메이션을 쓰지 않는다.
 * [fixedRows] 가 0보다 크면 높이에 맞추지 않고 늘 그 줄 수(격자면 그 높이 안에 gridRows 줄)를 쓴다. 높이는 WRAP 으로 둔다.
 * [insetDividers] 가 false 면 줄 사이 선을 좌우 여백 없이 끝까지 긋는다(이미 여백 안에 놓인 좁은 칸).
 * [dividers] 가 false 면 줄 사이 선을 긋지 않는다(표지 격자처럼 줄마다 칸이 나뉘어 보이는 목록). 높이 계산은 같다.
 */
class PagedRows(
    context: Context,
    private val listRowPx: Int,
    private val fixedRows: Int = 0,
    private val insetDividers: Boolean = true,
    private val dividers: Boolean = true,
) : LinearLayout(context) {
    private var rows: List<() -> View> = emptyList()
    private val dividerPx = context.dp(1)

    /**
     * 격자 모드: 목록일 때 들어갈 높이를 그대로 쓰고 그 안을 [gridRows] 줄로 나눈다(줄 사이 선 없음).
     * 덜 차도 그 높이를 비워 두어 머리(제목·선) 위치가 목록일 때와 같다. 0 이면 목록.
     */
    private var gridRows = 0

    /** 지금 한 줄의 높이(격자면 목록 높이를 나눈 값) */
    var rowHeightPx: Int = listRowPx
        private set

    fun setGrid(gridRows: Int) {
        if (gridRows == this.gridRows) return
        this.gridRows = gridRows
        perPage = 0
        if (!fitRows(height)) render()
    }

    var perPage = 0
        private set
    var page = 0
        private set
    val pageCount: Int get() = if (perPage <= 0) 1 else maxOf(1, (rows.size + perPage - 1) / perPage)

    /** 목록 위에 함께 붙는 머리(제목·선). 들어갈 줄 수를 셀 때 그 높이를 뺀다. */
    var header: List<View> = emptyList()
        set(v) {
            if (v == field) return
            field = v
            if (!fitRows(height)) render()
        }

    /** 페이지나 페이지 수가 바뀌면 불린다(페이지 점·쪽 번호 갱신용). */
    var onPageChanged: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        if (fixedRows > 0) fitRows(0)
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

    private var revealIndex: Int? = null

    /** [index] 번째 줄이 있는 페이지를 보여 준다. 아직 줄 수를 모르면 정해진 뒤에 보여 준다. */
    fun reveal(index: Int) {
        if (perPage > 0) showPage(pageOf(index)) else revealIndex = index
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitRows(h)
    }

    /**
     * 높이 [h] 에 들어가는 줄 수를 다시 정한다. 레이아웃 중에는 자식을 바꾸지 않고, 화면에 그리기 직전에 채운다.
     * (다음 차례로 미루면 빈 목록을 한 번 그린 뒤 다시 그리게 되어 전자잉크가 깜빡인다.) 줄 수가 바뀌었으면 true.
     */
    private fun fitRows(h: Int): Boolean {
        if (fixedRows == 0 && h <= 0) return false
        val listFit = if (fixedRows > 0) fixedRows else maxOf(1, (h - headerHeight() + dividerPx) / (listRowPx + dividerPx))
        val fit: Int
        val rowPx: Int
        if (gridRows > 0) {
            fit = minOf(gridRows, listFit)
            rowPx = (listFit * (listRowPx + dividerPx) - dividerPx) / fit
        } else {
            fit = listFit
            rowPx = listRowPx
        }
        if (fit == perPage && rowPx == rowHeightPx) return false
        perPage = fit
        rowHeightPx = rowPx
        renderBeforeDraw()
        return true
    }

    /**
     * 머리 뷰 높이 합. 실제 배치와 같은 조건(정해진 높이·좌우 여백)으로 잰다. 레이아웃 중에 다른 조건으로 재면
     * 그 값이 그대로 남아 선(높이 2dp)이 0 으로 그려진다.
     */
    private fun headerHeight(): Int = header.sumOf { v ->
        val m = v.layoutParams as? MarginLayoutParams
        val lpW = v.layoutParams?.width ?: MATCH
        val lpH = v.layoutParams?.height ?: WRAP
        val wSpec = getChildMeasureSpec(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            paddingLeft + paddingRight + (m?.leftMargin ?: 0) + (m?.rightMargin ?: 0), lpW,
        )
        val hSpec = if (lpH >= 0) MeasureSpec.makeMeasureSpec(lpH, MeasureSpec.EXACTLY)
        else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        v.measure(wSpec, hSpec)
        v.measuredHeight + (m?.topMargin ?: 0) + (m?.bottomMargin ?: 0)
    }

    private var renderPending = false

    /** 이번 그리기를 건너뛰고 줄을 채운 뒤 다시 레이아웃해서 그린다. */
    private fun renderBeforeDraw() {
        if (renderPending) return
        renderPending = true
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this)
                renderPending = false
                render()
                return false
            }
        })
    }

    private fun render() {
        removeAllViews()
        if (perPage <= 0) return
        revealIndex?.let { page = pageOf(it); revealIndex = null }
        header.forEach { addView(it) }
        page = page.coerceIn(0, pageCount - 1)
        val from = page * perPage
        val to = minOf(rows.size, from + perPage)
        for (i in from until to) {
            if (i > from && gridRows == 0) {
                // 선을 긋지 않아도 같은 높이를 비워 둔다(들어가는 줄 수 계산이 같도록).
                if (dividers) addView(context.hline(1, Ui.DIVIDER, insetDividers)) else addView(View(context), lp(MATCH, dividerPx))
            }
            addView(rows[i](), lp(MATCH, rowHeightPx))
        }
        // 덜 찬 페이지도 같은 높이를 쓴다(아래를 비워 제목·선 위치가 움직이지 않게).
        // 목록은 페이지가 여러 개일 때만, 격자는 늘(목록과 같은 높이를 유지).
        val missing = if (pageCount > 1 || gridRows > 0 || fixedRows > 0) perPage - (to - from) else 0
        val gap = if (gridRows > 0) 0 else dividerPx
        if (missing > 0) addView(View(context), lp(MATCH, missing * (rowHeightPx + gap)))
        onPageChanged?.invoke()
    }
}
