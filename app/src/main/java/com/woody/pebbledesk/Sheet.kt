package com.woody.pebbledesk

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.view.ViewGroup

/**
 * 아래에서 올라오는 메뉴. 위쪽 굵은 선, 머리(아이콘·이름·설명), 항목들, 맨 아래 오른쪽 `닫기`.
 * 박스 없이 선으로만 나눈다.
 */
class Sheet(private val context: Context) {
    private class Item(val label: String, val bold: Boolean, val action: () -> Unit)

    private var app: AppEntry? = null
    /** 앱 아이콘 대신 보여 줄 그림(책 표지). 세로로 긴 비율로 그린다. */
    private var image: Bitmap? = null
    private var title: String = ""
    private var subtitle: String? = null
    private val items = mutableListOf<Item>()

    fun header(title: String, subtitle: String? = null, app: AppEntry? = null, image: Bitmap? = null) = apply {
        this.title = title; this.subtitle = subtitle; this.app = app; this.image = image
    }

    fun item(label: String, bold: Boolean = false, action: () -> Unit) = apply { items += Item(label, bold, action) }

    fun show() {
        val dialog = Dialog(context, R.style.Sheet)
        val c = context
        val root = c.vbox()
        Crema.reserveHiddenBottom(root)
        root.addView(c.hline(2, inset = false))

        val head = c.hbox().apply { setPadding(c.dp(Ui.MARGIN), c.dp(22), c.dp(Ui.MARGIN), c.dp(20)) }
        app?.let {
            head.addView(c.picture(AppIcons.gray(c, it, c.dp(45))),
                lp(c.dp(45), c.dp(45)).apply { marginEnd = c.dp(16) })
        }
        image?.let {
            head.addView(c.coverImage(it), lp(c.dp(38), c.dp(56)).apply { marginEnd = c.dp(16) })
        }
        val names = c.vbox()
        names.addView(c.text(title, 29f, Ui.heavy))
        subtitle?.let { names.addView(c.text(it, 18f, color = Ui.GRAY), lp(WRAP, WRAP).apply { topMargin = c.dp(7) }) }
        head.addView(names, lp(0, WRAP, 1f))
        root.addView(head)

        items.forEach { item ->
            root.addView(c.hline(1, Ui.DIVIDER))
            root.addView(c.hbox().apply {
                setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
                addView(c.text(item.label, 24f, if (item.bold) Ui.bold else Ui.regular), lp(MATCH, WRAP))
                setOnClickListener { dialog.dismiss(); item.action() }
            }, lp(MATCH, c.dp(Ui.ROW_DP)))
        }
        root.addView(c.hline(1, Ui.DIVIDER))
        root.addView(c.text(c.getString(R.string.close), 21f, Ui.bold).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
            setOnClickListener { dialog.dismiss() }
        }, lp(MATCH, c.dp(62)))

        dialog.setContentView(root)
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.setPadding(0, 0, 0, 0)
        }
        dialog.show()
    }
}

/** 고르기 메뉴: 지금 값은 굵게 + ✓ */
fun Context.choose(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val sheet = Sheet(this).header(title)
    options.forEachIndexed { i, label ->
        sheet.item(if (i == selected) "$label  ✓" else label, bold = i == selected) { onPick(i) }
    }
    sheet.show()
}
