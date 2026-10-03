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

    /** 페블 패널은 1456px 이지만 맨 아래 8px 쯤은 테두리에 가려 보이지 않는다(같은 6인치 패널의 보통 높이는 1448px). */
    private const val HIDDEN_BOTTOM_PX = 8

    /** 화면 맨 아래에 붙는 [view] 의 아래를 가려지는 줄만큼 비워, 하단 줄이 보이는 영역의 가운데에 오게 한다. */
    fun reserveHiddenBottom(view: android.view.View) {
        if (isCrema) view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom + HIDDEN_BOTTOM_PX)
    }

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
