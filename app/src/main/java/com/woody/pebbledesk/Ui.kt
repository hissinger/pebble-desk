package com.woody.pebbledesk

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** 화면 크기·색·글꼴. 시안(폭 402) 기준 값을 페블 화면(폭 약 572dp)에 맞춰 1.42배 한 값이다. */
object Ui {
    const val BLACK = Color.BLACK
    const val GRAY = 0xFF555555.toInt()
    const val LIGHT_GRAY = 0xFF888888.toInt()
    const val DIVIDER = 0xFFCCCCCC.toInt()

    /** 좌우 여백 */
    const val MARGIN = 40

    /** 목록 한 줄 규격(홈·모든 앱·고르기·숨긴 앱 공통): 줄 높이 dp, 아이콘 dp, 글자 sp */
    const val ROW_DP = 62
    const val ROW_ICON_DP = 27
    const val ROW_SP = 25f

    /** 흑백(채도 0)으로 그리는 페인트: 앱 아이콘과 책 표지 */
    fun grayPaint() = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }

    val heavy: Typeface = Typeface.create(Typeface.DEFAULT, 800, false)
    val bold: Typeface = Typeface.create(Typeface.DEFAULT, 700, false)
    val medium: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    val regular: Typeface = Typeface.create(Typeface.DEFAULT, 400, false)
}

fun Context.dp(v: Number): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
).toInt()

fun Context.dpf(v: Number): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
)

fun Context.text(
    value: CharSequence,
    sp: Float,
    face: Typeface = Ui.regular,
    color: Int = Ui.BLACK,
    lines: Int = 1,
): TextView = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    typeface = face
    setTextColor(color)
    includeFontPadding = false
    maxLines = lines
    ellipsize = TextUtils.TruncateAt.END
}

/** 가로 선. [inset] 이 true 면 좌우 여백만큼 들여 그린다. */
fun Context.hline(heightDp: Number, color: Int = Ui.BLACK, inset: Boolean = true): View = View(this).apply {
    setBackgroundColor(color)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)).apply {
        if (inset) {
            marginStart = dp(Ui.MARGIN); marginEnd = dp(Ui.MARGIN)
        }
    }
}

/** 하위 화면 제목 줄 `‹ 제목`. 누르면 [onBack]. 제목 뒤에 덧붙일 것(개수 등)은 돌려받은 줄에 더한다. */
fun Context.titleRow(title: String, onBack: () -> Unit): LinearLayout = hbox().apply {
    layoutParams = lp(MATCH, dp(80))
    setPadding(dp(Ui.MARGIN - 8), 0, dp(Ui.MARGIN), 0)
    addView(text("‹", 34f), lp(WRAP, WRAP).apply { marginEnd = dp(10) })
    addView(text(title, 33f, Ui.heavy))
    setOnClickListener { onBack() }
}

/** 순서 바꾸기 버튼(↑/↓). 끝에 있어 못 옮기면 흐리게 두고 누르지 않게 한다. 누르는 폭은 48dp. */
fun Context.orderArrow(symbol: String, enabled: Boolean, onClick: () -> Unit): TextView =
    text(symbol, 24f, Ui.bold, if (enabled) Ui.BLACK else Ui.DIVIDER).apply {
        gravity = Gravity.CENTER
        minWidth = dp(48)
        setPadding(dp(4), dp(10), dp(4), dp(10))
        if (enabled) setOnClickListener { onClick() }
    }

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 앱 한 줄: [아이콘] 이름 ......... [오른쪽 표시]
 * 아이콘 설정이 꺼져 있으면 아이콘 없이 이름을 왼쪽 여백에 붙인다.
 */
fun Context.appRow(
    app: AppEntry?,
    label: CharSequence,
    iconDp: Int,
    textSp: Float,
    face: Typeface = Ui.regular,
    color: Int = Ui.BLACK,
    showIcon: Boolean,
    right: View? = null,
): LinearLayout = hbox().apply {
    setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
    if (showIcon && app != null) {
        addView(ImageView(context).apply {
            setImageBitmap(AppIcons.gray(context, app, dp(iconDp)))
        }, lp(dp(iconDp), dp(iconDp)).apply { marginEnd = dp(16) })
    }
    addView(text(label, textSp, face, color), lp(0, WRAP, 1f))
    if (right != null) addView(right, lp(WRAP, WRAP).apply { marginStart = dp(12) })
}
