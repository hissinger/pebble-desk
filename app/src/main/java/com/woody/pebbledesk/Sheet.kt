package com.woody.pebbledesk

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText

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
    /** 글자 입력 줄(지금 값, 다 쓰면 부를 일). 있으면 머리 아래에 두고 `저장`을 맨 위 항목으로 둔다. */
    private var input: Pair<String, (String) -> Unit>? = null
    private var numeric = false
    /** 아래 `닫기` 줄을 두는가. 확인 창은 `취소`를 항목으로 두고 닫기를 뺀다. */
    private var closeRow = true

    fun noClose() = apply { closeRow = false }

    /** 머리 오른쪽 위에 둘 뱃지(올해 읽은 책의 `✓ 다 읽음`) */
    private var badge: View? = null

    fun badge(view: View) = apply { badge = view }

    fun header(title: String, subtitle: String? = null, app: AppEntry? = null, image: Bitmap? = null) = apply {
        this.title = title; this.subtitle = subtitle; this.app = app; this.image = image
    }

    fun item(label: String, bold: Boolean = false, action: () -> Unit) = apply { items += Item(label, bold, action) }

    /** 한 줄 입력(박스 없이 아래 굵은 선). 빈 글자는 저장하지 않는다. [numeric] 이면 숫자 키보드. */
    fun input(initial: String, numeric: Boolean = false, onDone: (String) -> Unit) = apply {
        input = initial to onDone; this.numeric = numeric
    }

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
        subtitle?.let { names.addView(c.text(it, 18f, color = Ui.GRAY, lines = 6), lp(WRAP, WRAP).apply { topMargin = c.dp(7) }) }
        head.addView(names, lp(0, WRAP, 1f))
        badge?.let { head.addView(it, lp(WRAP, WRAP).apply { gravity = Gravity.TOP; marginStart = c.dp(12) }) }
        root.addView(head)

        input?.let { (initial, onDone) ->
            val field = EditText(c).apply {
                setText(initial)
                setSelection(text.length)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(Ui.BLACK)
                background = null
                setPadding(0, c.dp(8), 0, c.dp(8))
                isSingleLine = true
                inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_DONE
            }
            val save = {
                val v = field.text.toString().trim()
                if (v.isNotEmpty()) { dialog.dismiss(); onDone(v) }
            }
            field.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { save(); true } else false }
            root.addView(field, lp(MATCH, WRAP).apply { marginStart = c.dp(Ui.MARGIN); marginEnd = c.dp(Ui.MARGIN) })
            root.addView(c.hline(2))
            root.addView(c.hbox().apply {
                setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
                addView(c.text(c.getString(R.string.save), 24f, Ui.bold), lp(MATCH, WRAP))
                setOnClickListener { save() }
            }, lp(MATCH, c.dp(Ui.ROW_DP)))
            field.requestFocus()
        }

        items.forEach { item ->
            root.addView(c.hline(1, Ui.DIVIDER))
            root.addView(c.hbox().apply {
                setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
                addView(c.text(item.label, 24f, if (item.bold) Ui.bold else Ui.regular), lp(MATCH, WRAP))
                setOnClickListener { dialog.dismiss(); item.action() }
            }, lp(MATCH, c.dp(Ui.ROW_DP)))
        }
        root.addView(c.hline(1, Ui.DIVIDER))
        if (closeRow) root.addView(c.text(c.getString(R.string.close), 21f, Ui.bold).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
            setOnClickListener { dialog.dismiss() }
        }, lp(MATCH, c.dp(62)))

        dialog.setContentView(root)
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.setPadding(0, 0, 0, 0)
            // 입력 줄이 있으면 키보드를 띄우고, 메뉴가 키보드 위로 올라오게 한다.
            if (input != null) setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
    }
}

/**
 * 되돌리기 어려운 일 확인: 머리 [title]·[subtitle], 아래에 두 줄 `[action]`(굵게, 누르면 [onConfirm]) / `취소`.
 * (`닫기`보다 할 일과 그만두기가 맞서 보여 고르기 쉽다)
 */
fun Context.confirm(title: String, subtitle: String?, action: String, onConfirm: () -> Unit) {
    Sheet(this).header(title, subtitle).item(action, bold = true) { onConfirm() }.item(getString(R.string.cancel)) {}.noClose().show()
}

/** 고르기 메뉴: 지금 값은 굵게 + ✓ */
fun Context.choose(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val sheet = Sheet(this).header(title)
    options.forEachIndexed { i, label ->
        sheet.item(if (i == selected) "$label  ✓" else label, bold = i == selected) { onPick(i) }
    }
    sheet.show()
}
