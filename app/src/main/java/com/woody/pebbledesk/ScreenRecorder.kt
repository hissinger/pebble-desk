package com.woody.pebbledesk

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * (임시) my YES(YES24 서점) 화면 구조 기록. 이 앱은 USB 디버깅이 켜져 있으면 쓸 수 없어 adb 로 화면을 볼 수 없다.
 * 디버깅을 끈 채 쓰는 동안 화면 요소의 종류·이름·위치·짧은 글자를 `Android/data/com.woody.pebbledesk/files/record/` 에 적는다.
 * 웹 화면 안과 40자가 넘는 글(본문)은 적지 않고, 읽는 화면은 찍지 않는다. 지원을 만든 뒤 지운다.
 */
object ScreenRecorder {
    val PACKAGES = setOf("com.yes24.ebook.einkstore")
    private const val MAX_TEXT = 40
    private const val MAX_DEPTH = 80
    private const val MAX_BYTES = 4_000_000L
    private const val MAX_SHOTS = 12
    private const val THROTTLE_MS = 2000L
    private const val SETTLED_MS = 1500L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    /** 적는 순서를 지키도록 한 줄로 쓴다 */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var lastDump = 0L
    private var lastHash = 0
    private var shots = 0
    private val shotClasses = mutableSetOf<String>()
    /** 마지막으로 열린 화면(찍기 전에 읽는 화면으로 넘어갔으면 찍지 않는다) */
    private var lastClass = ""
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    fun record(service: AccessibilityService, e: AccessibilityEvent) {
        val dir = File(service.getExternalFilesDir(null) ?: return, "record").apply { mkdirs() }
        val log = File(dir, "record.txt")
        if (log.length() > MAX_BYTES) return
        val pkg = e.packageName?.toString() ?: return
        val head = "${time.format(Date())} ${type(e.eventType)} ${e.className}"
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val src = e.source?.let { describe(it) }.orEmpty()
                write(log, "$head text=${e.text.map { short(it) }} desc=${short(e.contentDescription)} src=[$src]\n")
                return
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                write(log, "$head\n")
                val cls = e.className?.toString().orEmpty()
                if (cls.startsWith("com.")) lastClass = cls
                if (cls.startsWith("com.") && !ReadingLog.isReaderScreen(cls) && shotClasses.add(cls) && shots < MAX_SHOTS) shot(service, dir, pkg, cls)
                // 화면이 열리는 순간에는 창이 아직 비어 있을 수 있어 조금 뒤에도 적는다.
                handler.postDelayed({ dump(service, log, pkg, "$head (뒤)") }, SETTLED_MS)
            }
            else -> if (System.currentTimeMillis() - lastDump < THROTTLE_MS) return
        }
        dump(service, log, pkg, head)
    }

    private fun dump(service: AccessibilityService, log: File, pkg: String, head: String) {
        lastDump = System.currentTimeMillis()
        val roots = runCatching { service.windows.mapNotNull { it.root }.filter { it.packageName == pkg } }.getOrDefault(emptyList())
            .ifEmpty { listOfNotNull(service.rootInActiveWindow?.takeIf { it.packageName == pkg }) }
        val sb = StringBuilder()
        for (r in roots) walk(r, 0, sb)
        if (sb.isEmpty()) return
        val hash = sb.toString().hashCode()
        if (hash == lastHash) return
        lastHash = hash
        write(log, "---- $head\n$sb")
    }

    private fun walk(n: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        sb.append("  ".repeat(depth.coerceAtMost(40))).append(describe(n)).append('\n')
        // 웹 화면(본문일 수 있다) 안은 적지 않는다.
        if (n.className?.endsWith("WebView") == true || depth >= MAX_DEPTH) return
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1, sb) }
    }

    private fun describe(n: AccessibilityNodeInfo): String {
        val r = Rect().also { n.getBoundsInScreen(it) }
        val id = n.viewIdResourceName?.substringAfter(":id/")
        return buildString {
            append(n.className?.toString()?.substringAfterLast('.'))
            if (id != null) append(" #").append(id)
            n.text?.let { append(" t=").append(short(it)) }
            n.contentDescription?.let { append(" d=").append(short(it)) }
            append(' ').append(r.toShortString())
            if (!n.isVisibleToUser) append(" hidden")
            if (n.isClickable) append(" click")
            if (n.isSelected) append(" sel")
        }
    }

    /** 짧은 글자만 그대로, 긴 글(본문일 수 있다)은 길이만 */
    private fun short(t: CharSequence?): String {
        val s = t?.toString()?.replace('\n', ' ')?.trim() ?: return "null"
        return if (s.length <= MAX_TEXT) "\"$s\"" else "<긴 글 ${s.length}자>"
    }

    private fun type(t: Int) = when (t) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
        else -> t.toString()
    }

    private fun write(log: File, text: String) = writer.execute { runCatching { log.appendText(text) } }

    /** 서재 같은 화면(읽는 화면 말고)을 화면마다 한 장씩 찍어 둔다. 화면이 다 그려지도록 조금 뒤에. */
    private fun shot(service: AccessibilityService, dir: File, pkg: String, cls: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        shots++
        val name = "${shots}_${cls.substringAfterLast('.')}.png"
        handler.postDelayed({
            // 그 사이 다른 화면·다른 앱으로 넘어갔으면 찍지 않는다.
            if (lastClass != cls || service.rootInActiveWindow?.packageName != pkg) return@postDelayed
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val bmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    result.hardwareBuffer.close()
                    bmp ?: return
                    Bg.run({ File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle() }) {}
                }

                override fun onFailure(errorCode: Int) {}
            })
        }, 2500)
    }
}
