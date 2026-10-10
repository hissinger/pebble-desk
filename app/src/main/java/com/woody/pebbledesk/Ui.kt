package com.woody.pebbledesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import kotlin.math.roundToInt

/** 화면 크기·색·글꼴. 시안(폭 402) 기준 값을 페블 화면(폭 572dp)에 맞춰 1.42배 한 값이다. 다른 기기도 이 폭이 되도록 밀도를 맞춘다([designDensityDpi]). */
object Ui {
    /** 모든 dp 값의 기준 화면 폭(크레마 페블 1072px / 1.875). 세로 모양 화면의 짧은 변이 이 폭이 되게 밀도를 정한다. */
    const val DESIGN_WIDTH_DP = 572

    /** 화면 짧은 변이 [DESIGN_WIDTH_DP] 가 되는 밀도(dpi). 페블 300, 메이북 E6·M6C(1072px) 300, 1404px 7.8인치면 393. */
    fun designDensityDpi(context: Context): Int {
        val dm = context.resources.displayMetrics
        densityBasis = "${dm.widthPixels}×${dm.heightPixels}"
        val shortPx = minOf(dm.widthPixels, dm.heightPixels)
        return (shortPx * 160f / DESIGN_WIDTH_DP).roundToInt()
    }

    /** 마지막으로 [designDensityDpi] 가 본 화면 크기(px). 기기 정보에서 밀도가 왜 그렇게 정해졌는지 본다(Moaan MIX7S 는 1264px 인데 300dpi 로 잡혔다). */
    @Volatile var densityBasis: String? = null
        private set

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

/** 앱 버전 이름(설정 바닥글·기기 정보). 못 읽으면 빈 글자. */
fun Context.appVersion(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

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

/**
 * 오른쪽에 완독 도장(`drawable-nodpi/stamp_completed.png`, 회색 [Ui.LIGHT_GRAY] 로 칠한다)을 찍은 가로 줄. 도장은 글자보다
 * 먼저(아래에) 그리고 줄 밖으로 나간 부분은 잘린다(종이 끝에 걸쳐 찍은 것처럼. 부모가 clipChildren=false 면 넘쳐 그린다). 지름 [sizeDp], 가운데는 오른쪽 끝에서 [endDp] 안쪽,
 * 위에서 [topDp] 아래(null 이면 줄 가운데). 글자가 도장에 덮이지 않게 부르는 쪽이 글자 칸 오른쪽을 비운다.
 */
class StampedRow(context: Context, private val sizeDp: Int, private val endDp: Int, private val topDp: Int? = null) : LinearLayout(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Ui.LIGHT_GRAY, PorterDuff.Mode.SRC_IN)
    }
    private val dst = RectF()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    override fun dispatchDraw(canvas: Canvas) {
        val r = context.dp(sizeDp) / 2f
        val cx = width - context.dp(endDp).toFloat()
        val cy = topDp?.let { context.dp(it).toFloat() } ?: (height / 2f)
        dst.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawBitmap(stamp(context), null, dst, paint)
        super.dispatchDraw(canvas)
    }

    companion object {
        @Volatile private var bitmap: Bitmap? = null

        /** 도장 그림(420px, 한 번 읽어 둔다) */
        private fun stamp(context: Context): Bitmap = bitmap ?: BitmapFactory.decodeResource(
            context.resources, R.drawable.stamp_completed, BitmapFactory.Options().apply { inScaled = false },
        ).also { bitmap = it }
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
