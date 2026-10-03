package com.woody.pebbledesk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
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
