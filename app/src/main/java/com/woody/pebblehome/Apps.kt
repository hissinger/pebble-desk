package com.woody.pebblehome

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import java.text.Collator
import java.util.Locale

/** 런처에 보이는 앱 하나(실행 화면 단위). [key] 는 ComponentName 을 펼친 문자열이다. */
data class AppEntry(val component: ComponentName, val label: String) {
    val key: String get() = component.flattenToShortString()
    val pkg: String get() = component.packageName
}

object AppStore {
    /** 이름 순서: 영문 → 한글 (ICU 기본 순서는 라틴 문자가 한글보다 앞) */
    val collator: Collator = Collator.getInstance(Locale.ROOT)

    /** 이북 앱(기본 앱 고르기에서 먼저 보여 줄 앱) */
    private val EBOOK_PACKAGES = setOf(
        "com.initialcoms.ridi", "kr.co.millie.eink", "kr.co.aladin.ebook", "com.yes24.ebook.einkstore",
        "com.kyobo.ebook.eink", "com.bookers.ebook", "com.yes24.library.eink", "kr.co.kyobobook.KEL",
    )
    private val EBOOK_WORDS = listOf("ebook", "e-book", "eink", "e ink", "도서", "서재", "리디", "book")

    /** 설치된 앱 전체(이 런처 자신 제외)를 이름 순으로 */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadLabel(pm).toString().trim(),
                )
            }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** 앱 목록을 읽고, 지워진 앱을 설정에서 정리한다. */
    fun loadAndPrune(context: Context, prefs: HomePrefs): List<AppEntry> {
        val apps = load(context)
        prefs.prune(apps) { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
        return apps
    }

    fun isEbook(app: AppEntry): Boolean {
        if (app.pkg in EBOOK_PACKAGES) return true
        val l = app.label.lowercase(Locale.ROOT)
        return EBOOK_WORDS.any { it in l }
    }

    /** 앱을 열고 연 횟수를 센다. */
    fun launch(context: Context, app: AppEntry, prefs: HomePrefs) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            // 전자잉크라 화면 전환 애니메이션을 쓰지 않는다.
            context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
            prefs.countOpen(app.key)
        } catch (_: Exception) {
            // 지워졌거나 열 수 없는 앱. 다음에 홈으로 돌아올 때 목록에서 빠진다.
        }
    }

    fun openAppInfo(context: Context, app: AppEntry) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 첫 글자 색인: 영문은 대문자, 한글은 초성(된소리는 예사소리로), 그 밖은 # */
    fun initial(label: String): String {
        val c = label.firstOrNull() ?: return "#"
        if (c in '가'..'힣') {
            val cho = (c - '가') / 588
            return "ㄱㄱㄴㄷㄷㄹㅁㅂㅂㅅㅅㅇㅈㅈㅊㅋㅌㅍㅎ"[cho].toString()
        }
        if (c.isLetter() && c.code < 128) return c.uppercaseChar().toString()
        return "#"
    }
}

/** 흑백으로 바꾼 앱 아이콘 캐시 */
object AppIcons {
    private val cache = LruCache<String, Bitmap>(200)
    private val grayPaint = Ui.grayPaint()
    private var packageSequence = 0

    fun gray(context: Context, app: AppEntry, px: Int): Bitmap {
        val key = "${app.key}@$px"
        cache.get(key)?.let { return it }
        val drawable = runCatching { context.packageManager.getActivityIcon(app.component) }
            .getOrElse { context.packageManager.defaultActivityIcon }
        val color = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(color).let { drawable.setBounds(0, 0, px, px); drawable.draw(it) }
        val gray = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(gray).drawBitmap(color, 0f, 0f, grayPaint)
        color.recycle()
        cache.put(key, gray)
        return gray
    }

    /** 앱이 설치·업데이트·삭제됐을 때만 캐시를 비운다(아이콘이 바뀌었을 수 있으므로). */
    fun clearIfPackagesChanged(context: Context) {
        val changed = context.packageManager.getChangedPackages(packageSequence) ?: return
        packageSequence = changed.sequenceNumber
        cache.evictAll()
    }
}
