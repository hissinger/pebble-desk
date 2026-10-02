package com.woody.pebblehome

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 상태 줄에 보여 줄 기기 상태 */
data class DeviceStatus(
    val battery: Int,
    val charging: Boolean,
    val wifi: Boolean,
    val bluetooth: Boolean,
    val light: Boolean,
    /** 잔상 제거(전체 새로고침) 주기. 0 이면 표시하지 않는다. */
    val refreshEvery: Int,
) {
    companion object {
        fun read(context: Context): DeviceStatus {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            return DeviceStatus(
                battery = if (level >= 0 && scale > 0) level * 100 / scale else -1,
                charging = plugged,
                wifi = runCatching {
                    (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
                }.getOrDefault(false),
                bluetooth = bluetoothOn(),
                light = lightOn(context),
                refreshEvery = refreshEvery(),
            )
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        private fun bluetoothOn() = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }
            .getOrDefault(false)

        /** 크레마 조명: 백색은 screen_brightness, 온색은 warm_light. 둘 중 하나라도 0보다 크면 켜짐 */
        private fun lightOn(context: Context): Boolean {
            if (!Crema.isCrema) return false
            val cr = context.contentResolver
            val white = runCatching { Settings.System.getInt(cr, "screen_brightness", 0) }.getOrDefault(0)
            val warm = runCatching { Settings.System.getInt(cr, "warm_light", 0) }.getOrDefault(0)
            return white > 0 || warm > 0
        }

        /** 크레마 상단바와 같은 값(persist.vendor.fullmode_cnt). 일반 앱도 읽을 수 있다. */
        private fun refreshEvery(): Int {
            val value = runCatching {
                Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java, String::class.java)
                    .invoke(null, "persist.vendor.fullmode_cnt", "") as String
            }.getOrDefault("")
            return value.trim().toIntOrNull()?.takeIf { it in 1..99 } ?: 0
        }
    }
}

/**
 * 홈 맨 위 두 줄.
 *   [crema Pebble 로고]                 18:24
 *              10월 3일 토요일 · 100% · 와이파이 · BT · 조명 · ⑮
 * 안드로이드 상단바 대신 쓰므로 시계·배터리·와이파이 등을 직접 그린다.
 */
class TopBarView(context: Context) : View(context) {
    private val logo: Drawable? = Crema.logo(context)
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.regular
        textSize = context.dpf(42)
        color = Ui.BLACK
        letterSpacing = -0.02f
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.regular
        textSize = context.dpf(15.5f)
        color = Ui.BLACK
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.4f)
        color = Ui.BLACK
    }
    private val ringTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.bold
        textSize = context.dpf(11f)
        color = Ui.BLACK
        textAlign = Paint.Align.CENTER
    }
    private val icons = mapOf(
        "wifi" to context.getDrawable(R.drawable.ic_wifi)!!,
        "bt" to context.getDrawable(R.drawable.ic_bluetooth)!!,
        "light" to context.getDrawable(R.drawable.ic_light)!!,
        "bolt" to context.getDrawable(R.drawable.ic_bolt)!!,
    )
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
    private val dateFormat = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA)

    private var status: DeviceStatus? = null
    private var now = Date()

    private val padTop = context.dp(20)
    private val padBottom = context.dp(14)
    private val lineGap = context.dp(16)
    private val logoHeight = context.dp(32)
    private val iconSize = context.dp(19)
    private val sepGap = context.dpf(5)
    private val clockCap = capHeight(clockPaint)
    private val statusCap = capHeight(statusPaint)

    /** 시계·상태가 실제로 바뀌었을 때만 다시 그린다(전자잉크 부분 갱신을 줄이기 위해). */
    fun update(status: DeviceStatus) {
        val now = Date()
        val changed = status != this.status || timeFormat.format(now) != timeFormat.format(this.now) ||
            dateFormat.format(now) != dateFormat.format(this.now)
        this.status = status
        this.now = now
        if (changed) invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = padTop + clockCap + lineGap + statusCap + statusPaint.fontMetricsInt.descent + padBottom
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        val left = context.dp(Ui.MARGIN).toFloat()
        val right = width - context.dp(Ui.MARGIN).toFloat()
        val clockBase = (padTop + clockCap).toFloat()

        // 로고: 아랫선을 시계 글자 아랫선에 맞춘다.
        logo?.let {
            val w = logoHeight * it.intrinsicWidth / it.intrinsicHeight.coerceAtLeast(1)
            it.setBounds(left.toInt() - context.dp(2), clockBase.toInt() - logoHeight, left.toInt() + w, clockBase.toInt())
            it.draw(canvas)
        }

        val time = timeFormat.format(now)
        canvas.drawText(time, right - clockPaint.measureText(time), clockBase, clockPaint)

        // 상태 줄: 오른쪽부터 왼쪽으로 쌓는다.
        val s = status ?: return
        val base = clockBase + lineGap + statusCap
        val mid = base - statusCap / 2f
        val items = buildList<(Float) -> Float> {
            add { x -> drawText(canvas, dateFormat.format(now), x, base) }
            if (s.battery >= 0) add { x ->
                val textLeft = drawText(canvas, "${s.battery}%", x, base)
                if (s.charging) drawIcon(canvas, "bolt", textLeft - context.dpf(1), mid, iconSize * 0.8f) else textLeft
            }
            if (s.wifi) add { x -> drawIcon(canvas, "wifi", x, mid) }
            if (s.bluetooth) add { x -> drawIcon(canvas, "bt", x, mid) }
            if (s.light) add { x -> drawIcon(canvas, "light", x, mid) }
            if (s.refreshEvery > 0) add { x -> drawRing(canvas, s.refreshEvery, x, mid) }
        }
        var x = right
        items.asReversed().forEachIndexed { i, draw ->
            if (i > 0) {
                x -= sepGap
                val r = context.dpf(1.6f)
                canvas.drawCircle(x - r, mid, r, dotPaint)
                x -= 2 * r + sepGap
            }
            x = draw(x)
        }
    }

    /** 오른쪽 끝을 [right] 에 맞춰 쓰고 왼쪽 끝을 돌려준다. */
    private fun drawText(canvas: Canvas, text: String, right: Float, base: Float): Float {
        val w = statusPaint.measureText(text)
        canvas.drawText(text, right - w, base, statusPaint)
        return right - w
    }

    private fun drawIcon(canvas: Canvas, name: String, right: Float, mid: Float, size: Float = iconSize.toFloat()): Float {
        val d = icons.getValue(name)
        val half = size / 2f
        d.setBounds((right - size).toInt(), (mid - half).toInt(), right.toInt(), (mid + half).toInt())
        d.draw(canvas)
        return right - size
    }

    /** 원문자(⑮): 크레마 상단바처럼 원 안에 숫자 */
    private fun drawRing(canvas: Canvas, n: Int, right: Float, mid: Float): Float {
        val r = context.dpf(9.5f)
        val cx = right - r - ringPaint.strokeWidth / 2
        canvas.drawCircle(cx, mid, r, ringPaint)
        val b = Rect().also { ringTextPaint.getTextBounds("0", 0, 1, it) }
        canvas.drawText(n.toString(), cx, mid + b.height() / 2f, ringTextPaint)
        return cx - r - ringPaint.strokeWidth / 2
    }

    private fun capHeight(p: Paint): Int = Rect().also { p.getTextBounds("0", 0, 1, it) }.height()
}
