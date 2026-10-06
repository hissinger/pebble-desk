package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 기기 종류와 기기마다 다른 동작(빠른 설정, 조명). 크레마 전용 화면 보정은 [Crema].
 * 기기별 조사 내용은 docs/DEVICES.md.
 */
object Device {
    enum class Kind { CREMA, MEEBOOK, OTHER }

    /** 메이북(Haoqing 펌웨어)은 기종마다 모델명이 달라(M6·M6C·E6) 회사 속성 `ro.haoqing.brand` 로 가른다. */
    val kind: Kind by lazy {
        when {
            Crema.isCrema -> Kind.CREMA
            prop("ro.haoqing.brand").isNotEmpty() -> Kind.MEEBOOK
            else -> Kind.OTHER
        }
    }

    /** 조명 상태가 들어 있는 `Settings.System` 키. 바뀌면 상태 줄을 다시 그린다. */
    val lightKeys: List<String>
        get() = when (kind) {
            Kind.CREMA -> listOf("screen_brightness", "warm_light")
            Kind.MEEBOOK -> listOf("isLightOn")
            Kind.OTHER -> emptyList()
        }

    /**
     * 조명 켜짐. 크레마는 백색(screen_brightness)·온색(warm_light) 중 하나라도 0보다 크면 켜짐.
     * 메이북은 꺼도 밝기 값이 남아 SystemUI 가 쓰는 `isLightOn`("true"/"false")을 본다.
     */
    fun lightOn(context: Context): Boolean {
        val cr = context.contentResolver
        return when (kind) {
            Kind.CREMA -> lightKeys.any { key -> runCatching { Settings.System.getInt(cr, key, 0) }.getOrDefault(0) > 0 }
            Kind.MEEBOOK -> runCatching { Settings.System.getString(cr, lightKeys.single()) }.getOrNull() == "true"
            Kind.OTHER -> false
        }
    }

    /**
     * 기기의 빠른 설정 창(와이파이·BT·조명 등)을 연다. 크레마는 `com.epd.drop_down`, 메이북은 SystemUI 가 받는
     * `com.haoqing.action.QUICK_SETTINGS`. 받는 쪽이 없는 방송은 아무 일도 하지 않으므로 기기를 가리지 않고 둘 다 보낸다.
     */
    fun openQuickSettings(context: Context) {
        sendCremaQuickSettings(context)
        sendMeebookQuickSettings(context)
    }

    /** 크레마: SystemUI 가 받아 크레마 빠른 설정 앱(`com.inno.quicksetting`)을 띄운다. */
    fun sendCremaQuickSettings(context: Context) = context.sendBroadcast(Intent("com.epd.drop_down"))

    /** 메이북: Haoqing SystemUI 가 받아 알림창(빠른 설정)을 펼친다. */
    fun sendMeebookQuickSettings(context: Context) =
        context.sendBroadcast(Intent("com.haoqing.action.QUICK_SETTINGS").setPackage("com.android.systemui"))

    /**
     * 안드로이드 기본 빠른 설정 펼치기(`StatusBarManager.expandSettingsPanel`, 숨은 함수라 리플렉션, `EXPAND_STATUS_BAR` 권한).
     * 지금은 기기 정보의 시험 버튼에서만 쓴다. 부르지 못하면 false.
     */
    fun expandQuickSettings(context: Context): Boolean = runCatching {
        val bar = context.getSystemService("statusbar") ?: error("no statusbar")
        bar.javaClass.getMethod("expandSettingsPanel").invoke(bar)
    }.isSuccess

    /** SystemProperties.get 은 숨은 함수라 한 번만 찾아 둔다(배터리 신호마다 불리므로). */
    private val getProperty by lazy {
        runCatching { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java) }.getOrNull()
    }

    /** 시스템 속성. 일반 앱도 읽을 수 있다. 없으면 빈 글자. */
    fun prop(name: String): String =
        runCatching { getProperty?.invoke(null, name, "") as? String }.getOrNull().orEmpty().trim()
}
