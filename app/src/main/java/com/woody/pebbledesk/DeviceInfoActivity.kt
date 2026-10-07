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

        // 화면: 기기가 선언한 밀도·dp(앱 밀도를 바꾸지 않은 applicationContext)와 앱이 그리는 밀도·dp(EinkActivity 가 맞춘 것)
        val real = DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(real)
        val sys = applicationContext.resources.displayMetrics
        val dm = resources.displayMetrics
        fun dpSize(density: Float) = "${(real.widthPixels / density).roundToInt()}×${(real.heightPixels / density).roundToInt()}dp"
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
            getString(R.string.device_screen) to "${real.widthPixels}×${real.heightPixels} · ${sys.densityDpi}dpi · ${dpSize(sys.density)}" +
                (if (dm.densityDpi != sys.densityDpi) "\n${getString(R.string.device_screen_app)} ${dm.densityDpi}dpi · ${dpSize(dm.density)}" else ""),
            getString(R.string.device_kind) to "$kind · Pebble Desk ${appVersion()}",
        )))

        // 기기마다 다른 동작을 가르는 시스템 속성·설정 값: 이미 아는 키(크레마·메이북)와 이름으로 찾은 기기 고유 값 중 있는 것만.
        section(list, R.string.device_section_values)
        listOf(vendorProps(), vendorSettings()).forEach {
            list.addView(text(it.ifEmpty { "-" }, 14f, lines = 3).apply { setLineSpacing(0f, 1.15f) })
        }

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

    /** 시스템 속성 중 아는 키와 이름이 [VENDOR_KEY] 에 맞는 것(`getprop` 전체 목록에서). 읽지 못하면 아는 키만. */
    private fun vendorProps(): String {
        val all = runCatching {
            ProcessBuilder("getprop").redirectErrorStream(true).start().inputStream.bufferedReader().useLines { lines ->
                lines.mapNotNull { PROP_LINE.matchEntire(it)?.destructured?.let { (k, v) -> k to v } }.toMap()
            }
        }.getOrDefault(emptyMap())
        val keys = PROPS + all.keys.filter { vendorKey(it) }.sorted()
        return keys.distinct().mapNotNull { key -> (all[key] ?: Device.prop(key)).ifEmpty { null }?.let { "$key=${short(it)}" } }
            .joinToString(" · ")
    }

    /** `Settings.System` 중 아는 키와 이름이 [VENDOR_KEY]·[SETTING_KEY] 에 맞는 것(전체 목록에서). 목록을 못 읽으면 아는 키만. */
    private fun vendorSettings(): String {
        val all = runCatching {
            contentResolver.query(Settings.System.CONTENT_URI, arrayOf("name", "value"), null, null, null)?.use { c ->
                buildMap { while (c.moveToNext()) c.getString(0)?.let { put(it, c.getString(1).orEmpty()) } }
            }
        }.getOrNull().orEmpty()
        val keys = SETTINGS + all.keys.filter { (vendorKey(it) || SETTING_KEY.containsMatchIn(it)) && !AOSP_SETTING.matches(it) }.sorted()
        return keys.distinct().mapNotNull { key ->
            (all[key] ?: runCatching { Settings.System.getString(contentResolver, key) }.getOrNull())?.let { "$key=${short(it)}" }
        }.joinToString(" · ")
    }

    /** 기기 고유 값으로 보이는 이름: 전자잉크·조명 낱말이나 제조사·브랜드 이름이 들어 있다. */
    private fun vendorKey(key: String): Boolean {
        val k = key.lowercase()
        return VENDOR_KEY.containsMatchIn(k) || makerWords.any { it in k }
    }

    private val makerWords by lazy {
        listOf(Build.MANUFACTURER, Build.BRAND).flatMap { it.lowercase().split(' ', '-', '_') }.filter { it.length >= 4 }.distinct()
    }

    private fun short(value: String) = if (value.length > VALUE_MAX) value.take(VALUE_MAX) + "…" else value

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

        /** 이미 아는 기기의 키(크레마·메이북). 없는 기기에서는 보이지 않는다. */
        private val PROPS = listOf("ro.haoqing.brand", "persist.haoqing.updatemodel", "persist.haoqing.pagekeys", "persist.vendor.fullmode_cnt")
        private val SETTINGS = listOf("screen_brightness", "warm_light", "isLightOn", "light_mode", "haoqing_warm_light", "hq_contrast")

        /** 처음 보는 기기의 고유 값을 이름으로 찾는다: 전자잉크·화면 갱신·조명·버튼 낱말, 알려진 펌웨어 회사 이름. */
        private val VENDOR_KEY = Regex("eink|epd|ebc|fullmode|pagekey|backlight|frontlight|warm|contrast|haoqing|wisky|viwoods|crema|wetao|onyx|boox|bigme")

        /** `Settings.System` 에서 더 보는 낱말(조명·버튼·제스처·화면 갱신). 시스템 속성에는 이름이 비슷한 안드로이드 기본 값이 많아 쓰지 않는다. */
        private val SETTING_KEY = Regex("light|bright|key|gesture|refresh|ghost", RegexOption.IGNORE_CASE)

        /** 이름은 맞지만 안드로이드 기본에 있는 설정 */
        private val AOSP_SETTING = Regex("screen_brightness_(mode|float|for_vr.*)|notification_light_pulse|show_key_presses|.*vibration.*|.*haptic.*")

        private val PROP_LINE = Regex("""\[(.+?)]: \[(.*)]""")
        private const val VALUE_MAX = 32

        /** 빠른 설정 여는 방법들([Device]). 어느 것을 눌렀을 때 실제로 열리는지 사용자가 보고 알려 준다. */
        private val QUICK_SETTINGS: List<Pair<Int, (Context) -> Boolean>> = listOf(
            R.string.device_qs_crema to { c -> runCatching { Device.sendCremaQuickSettings(c) }.isSuccess },
            R.string.device_qs_meebook to { c -> runCatching { Device.sendMeebookQuickSettings(c) }.isSuccess },
            R.string.device_qs_android to Device::expandQuickSettings,
        )

        fun intent(context: Context) = Intent(context, DeviceInfoActivity::class.java)
    }
}
