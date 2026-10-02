package com.woody.pebblehome

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build

/** 크레마 기기에서만 쓰는 기능 */
object Crema {
    private const val LAUNCHER_PKG = "com.wetao.cremalauncher"

    val isCrema: Boolean get() = Build.DEVICE.startsWith("CREMA", ignoreCase = true)

    /**
     * 크레마 런처의 `crema Pebble` 글자 로고. 상표 이미지를 이 앱에 넣지 않으려고 기기에 설치된 크레마 런처에서 불러온다.
     * 없으면 null (로고 자리를 비운다).
     */
    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables")
    fun logo(context: Context): Drawable? = runCatching {
        val res = context.packageManager.getResourcesForApplication(LAUNCHER_PKG)
        val id = res.getIdentifier("ic_icon_crema", "drawable", LAUNCHER_PKG)
        if (id == 0) null else res.getDrawable(id, null)
    }.getOrNull()

    /** 크레마 상단바를 눌렀을 때와 같은 빠른 설정 창(와이파이·BT·조명·음량)을 연다. */
    fun openQuickSettings(context: Context) {
        context.sendBroadcast(Intent("com.epd.drop_down"))
    }
}

/** Cover Pebble 이 슬립화면으로 등록한 책 = 지금 읽는 책 */
data class NowReading(val title: String, val author: String, val updatedAt: Long, val cover: Bitmap)

object NowReadingClient {
    private const val AUTHORITY = "com.woody.cremacover.nowreading"
    private val BOOK = Uri.parse("content://$AUTHORITY/book")
    private val COVER = Uri.parse("content://$AUTHORITY/cover")

    private var cached: NowReading? = null

    /** 없거나 Cover Pebble 이 설치되지 않았으면 null. 바뀌지 않았으면 표지를 다시 읽지 않는다. */
    fun read(context: Context, coverWidthPx: Int): NowReading? {
        val cr = context.contentResolver
        val row = runCatching {
            cr.query(BOOK, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) null
                else Triple(
                    c.getString(c.getColumnIndexOrThrow("title")).orEmpty(),
                    c.getString(c.getColumnIndexOrThrow("author")).orEmpty(),
                    c.getLong(c.getColumnIndexOrThrow("updated_at")),
                )
            }
        }.getOrNull() ?: return null.also { cached = null }

        cached?.let { if (it.updatedAt == row.third && it.cover.width == coverWidthPx) return it }
        val bitmap = runCatching { loadCover(context, coverWidthPx) }.getOrNull() ?: return null
        return NowReading(row.first, row.second, row.third, bitmap).also { cached = it }
    }

    private fun loadCover(context: Context, widthPx: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(COVER)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        val src = cr.openInputStream(COVER)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // 표지 비율 그대로 폭만 맞추고 흑백으로 바꾼다.
        val h = (src.height.toLong() * widthPx / src.width).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, null, Rect(0, 0, widthPx, h), Ui.grayPaint())
        src.recycle()
        return out
    }
}
