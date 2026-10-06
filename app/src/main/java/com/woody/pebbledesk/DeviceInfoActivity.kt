package com.woody.pebbledesk

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 기기 정보(설정 › 기기 정보). 다른 기기를 쓰는 사람이 이 화면을 사진으로 찍어 보내면 기기마다 다른 동작을 맞출 수 있게
 * 한 화면에 모은다: 기기·시스템 속성·설정 값·권한, 누른 버튼의 키 번호, 최근에 뜬 다른 화면, 빠른 설정 여는 방법 시험. 기기별 조사는 docs/DEVICES.md.
 */
class DeviceInfoActivity : EinkActivity() {
    private lateinit var keyValue: TextView
    private lateinit var recentValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.device_info)) { finish() })
        root.addView(hline(2))
        val list = vbox().apply { setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0) }
        root.addView(list, lp(MATCH, 0, 1f))

        list.addView(text(getString(R.string.device_photo_hint), 15f, color = Ui.GRAY, lines = 3),
            lp(MATCH, WRAP).apply { topMargin = dp(8) })

        val real = DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(real)
        val dm = resources.displayMetrics
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        val kind = getString(when (Device.kind) {
            Device.Kind.CREMA -> R.string.device_kind_crema
            Device.Kind.MEEBOOK -> R.string.device_kind_meebook
            Device.Kind.OTHER -> R.string.device_kind_other
        })
        section(list, R.string.device_section_device)
        list.addView(table(listOf(
            getString(R.string.device_model) to "${Build.MODEL} (${Build.DEVICE})",
            getString(R.string.device_maker) to "${Build.MANUFACTURER} · ${Build.BRAND}",
            getString(R.string.device_android) to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            getString(R.string.device_build) to Build.DISPLAY,
            getString(R.string.device_screen) to "${real.widthPixels}×${real.heightPixels} · ${dm.densityDpi}dpi · " +
                "${(real.widthPixels / dm.density).roundToInt()}×${(real.heightPixels / dm.density).roundToInt()}dp",
            getString(R.string.device_kind) to "$kind · Pebble Desk $version",
        )))

        // 기기마다 다른 동작을 가르는 시스템 속성·설정 값. 없으면 `-`.
        section(list, R.string.device_section_values)
        val props = PROPS.joinToString(" · ") { "$it=${Device.prop(it).ifEmpty { "-" }}" }
        val settings = SETTINGS.joinToString(" · ") { key ->
            "$key=${runCatching { Settings.System.getString(contentResolver, key) }.getOrNull() ?: "-"}"
        }
        list.addView(text("$props\n$settings", 14f, lines = 6).apply { setLineSpacing(0f, 1.15f) })

        section(list, R.string.device_section_perms)
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName == packageName
        list.addView(text(listOf(
            getString(R.string.auto_books_short) to ReaderWatchService.isEnabled(this),
            getString(R.string.reading_time) to ReadingLog.hasAccess(this),
            getString(R.string.device_perm_home) to home,
        ).joinToString("  ·  ") { (label, on) -> "$label ${onOff(on)}" }, 16f, lines = 2))

        // 버튼 시험: 페이지 버튼·화면 아래 버튼이 무슨 키를 보내는지. 뒤로 키는 화면을 닫으므로 다시 열면 보인다.
        section(list, R.string.device_section_keys)
        keyValue = text(lastKey ?: getString(R.string.device_key_hint), 17f, Ui.bold, lines = 2)
        list.addView(keyValue)

        // 최근에 앞에 뜬 다른 앱 화면(사용 기록). 기본 런처·상단 바에서 빠른 설정을 연 뒤 돌아오면, 빠른 설정이
        // 크레마처럼 따로 된 화면이면 여기 남는다. 시스템 알림창은 화면(Activity)이 아니라 남지 않는다.
        section(list, R.string.device_section_recent)
        recentValue = text("", 14f, lines = 3).apply { setLineSpacing(0f, 1.15f) }
        list.addView(recentValue)

        section(list, R.string.device_section_qs)
        list.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            QUICK_SETTINGS.forEach { (label, open) ->
                val result = text("", 13f, color = Ui.GRAY)
                addView(vbox().apply {
                    addView(text(getString(label) + " ›", 18f, Ui.bold))
                    addView(result)
                    setPadding(0, dp(6), 0, dp(6))
                    setOnClickListener {
                        result.text = getString(if (open(this@DeviceInfoActivity)) R.string.device_qs_sent else R.string.device_qs_failed)
                    }
                }, lp(0, WRAP, 1f))
            }
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        recentValue.text = recentScreens()
    }

    /** 최근 [RECENT_MS] 안에 앞에 뜬 다른 앱 화면 [RECENT_COUNT] 개(시각 · 패키지 / 화면 클래스 이름). 한 줄에 들게 클래스는 마지막 이름만. 사용 기록 권한이 없으면 안내. */
    private fun recentScreens(): String {
        if (!ReadingLog.hasAccess(this)) return getString(R.string.device_recent_no_access)
        val now = System.currentTimeMillis()
        val events = getSystemService(UsageStatsManager::class.java)?.queryEvents(now - RECENT_MS, now) ?: return "-"
        val e = UsageEvents.Event()
        val time = SimpleDateFormat("HH:mm:ss", Locale.US)
        val recent = ArrayDeque<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND || e.packageName == packageName) continue
            recent.addLast("${time.format(e.timeStamp)}  ${e.packageName} / ${e.className.orEmpty().substringAfterLast('.')}")
            if (recent.size > RECENT_COUNT) recent.removeFirst()
        }
        return recent.reversed().joinToString("\n").ifEmpty { "-" }
    }

    /** 버튼을 누르면 키 번호·이름·스캔 코드를 보여 준다(쪽 넘기기는 하지 않는다). 뒤로 키는 기록만 하고 닫는다. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lastKey = "$keyCode  ${KeyEvent.keyCodeToString(keyCode)}  · scan ${event.scanCode}"
        keyValue.text = lastKey
        return if (keyCode == KeyEvent.KEYCODE_BACK) super.onKeyDown(keyCode, event) else true
    }

    private fun onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)

    private fun section(list: LinearLayout, title: Int) {
        list.addView(text(getString(title), 15f, color = Ui.LIGHT_GRAY), lp(MATCH, WRAP).apply { topMargin = dp(9); bottomMargin = dp(2) })
    }

    /** 이름·값 표. 이름 칸 너비를 가장 긴 이름에 맞춰 값의 왼쪽이 줄을 맞춘다. */
    private fun table(fields: List<Pair<String, String>>) = TableLayout(this).apply {
        setColumnShrinkable(1, true)
        fields.forEach { (label, value) ->
            addView(TableRow(context).apply {
                setPadding(0, dp(1), 0, dp(1))
                addView(text(label, 16f, color = Ui.GRAY).apply { setPadding(0, 0, dp(14), 0) })
                addView(text(value, 16f, lines = 2))
            })
        }
    }

    companion object {
        /** 이 프로세스에서 마지막으로 누른 키(뒤로 키로 닫혀도 다시 열면 보이게) */
        private var lastKey: String? = null

        private const val RECENT_MS = 10 * 60 * 1000L
        private const val RECENT_COUNT = 3

        private val PROPS = listOf("ro.haoqing.brand", "persist.haoqing.updatemodel", "persist.haoqing.pagekeys", "ro.sf.lcd_density")
        private val SETTINGS = listOf("isLightOn", "light_mode", "screen_brightness", "warm_light", "haoqing_warm_light", "hq_contrast")

        /** 빠른 설정 여는 방법들([Device]). 어느 것을 눌렀을 때 실제로 열리는지 사용자가 보고 알려 준다. */
        private val QUICK_SETTINGS: List<Pair<Int, (Context) -> Boolean>> = listOf(
            R.string.device_qs_crema to { c -> runCatching { Device.sendCremaQuickSettings(c) }.isSuccess },
            R.string.device_qs_meebook to { c -> runCatching { Device.sendMeebookQuickSettings(c) }.isSuccess },
            R.string.device_qs_android to Device::expandQuickSettings,
        )

        fun intent(context: Context) = Intent(context, DeviceInfoActivity::class.java)
    }
}
