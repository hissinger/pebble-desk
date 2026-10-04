package com.woody.pebbledesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

    /** 화면 맨 아래 줄(모든 앱 · 설정 · 페이지 점 등) 높이. 누르는 자리라 48dp 아래로 줄이지 않는다. */
    const val FOOTER_DP = 56

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

/**
 * 그림 한 장(비트맵 또는 드로어블)을 패딩 안쪽에 그리는 뷰. [crop] 이면 칸을 채워 가운데를 자르고, 아니면 비율을 지켜 가운데에 맞춘다.
 * ImageView 를 쓰지 않는 이유: 크레마는 앱이 바뀔 때마다 화면의 모든 ImageView 에 명암 필터를 다시 걸고 다시 그려
 * 전자잉크가 한 번 더 깜빡인다(홈으로 돌아올 때마다). 그냥 View 는 건드리지 않는다.
 */
class PictureView(context: Context, private val crop: Boolean = false) : View(context) {
    var bitmap: Bitmap? = null
        set(v) { field = v; invalidate() }
    var drawable: Drawable? = null
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return
        bitmap?.let { b ->
            if (crop) {
                // 칸 비율에 맞춰 원본 가운데를 잘라 칸을 꽉 채운다.
                val scale = maxOf(w.toFloat() / b.width, h.toFloat() / b.height)
                val sw = (w / scale).toInt()
                val sh = (h / scale).toInt()
                src.set((b.width - sw) / 2, (b.height - sh) / 2, (b.width + sw) / 2, (b.height + sh) / 2)
                dst.set(paddingLeft.toFloat(), paddingTop.toFloat(), (paddingLeft + w).toFloat(), (paddingTop + h).toFloat())
            } else {
                val scale = minOf(w.toFloat() / b.width, h.toFloat() / b.height)
                src.set(0, 0, b.width, b.height)
                fitCenter(b.width * scale, b.height * scale, w, h)
            }
            canvas.drawBitmap(b, src, dst, paint)
        }
        drawable?.let { d ->
            val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
            val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
            val scale = minOf(w.toFloat() / iw, h.toFloat() / ih)
            fitCenter(iw * scale, ih * scale, w, h)
            d.setBounds(dst.left.toInt(), dst.top.toInt(), dst.right.toInt(), dst.bottom.toInt())
            d.draw(canvas)
        }
    }

    private fun fitCenter(fw: Float, fh: Float, w: Int, h: Int) {
        val l = paddingLeft + (w - fw) / 2
        val t = paddingTop + (h - fh) / 2
        dst.set(l, t, l + fw, t + fh)
    }
}

/** 그림 한 장(앱 아이콘 등) */
fun Context.picture(bitmap: Bitmap?): PictureView = PictureView(this).apply { this.bitmap = bitmap }

/** 책 표지: 가운데를 채워 자르고 1dp 회색 테두리(흰 표지 윗변이 구분선처럼 보이지 않게) */
fun Context.coverImage(bitmap: Bitmap): PictureView = PictureView(this, crop = true).apply {
    this.bitmap = bitmap
    setBackgroundColor(Ui.LIGHT_GRAY)
    setPadding(dp(1), dp(1), dp(1), dp(1))
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
        addView(picture(AppIcons.gray(context, app, dp(iconDp))), lp(dp(iconDp), dp(iconDp)).apply { marginEnd = dp(16) })
    }
    addView(text(label, textSp, face, color), lp(0, WRAP, 1f))
    if (right != null) addView(right, lp(WRAP, WRAP).apply { marginStart = dp(12) })
}
