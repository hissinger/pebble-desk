package com.woody.pebbledesk

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import com.woody.pebbledesk.readers.ReaderApps
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Collator
import java.util.Locale

/** 런처에 보이는 앱 하나(실행 화면 단위). [key] 는 ComponentName 을 펼친 문자열이다. */
data class AppEntry(val component: ComponentName, val label: String) {
    val key: String get() = component.flattenToShortString()
    val pkg: String get() = component.packageName
}

/** 잠금 해제 전(부팅 직후)에도 모든 앱을 찾는다. 기본값은 잠금 전에 뜰 수 있는 앱만 돌려준다. */
private const val MATCH_ALL_BOOT_STATES = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE

object AppStore {
    /** 이름 순서: 영문 → 한글 (ICU 기본 순서는 라틴 문자가 한글보다 앞) */
    val collator: Collator by lazy { Collator.getInstance(Locale.ROOT) }

    /** 이북 앱(읽고 있는 앱 고르기에서 먼저 보여 줄 앱) */
    val EBOOK_PACKAGES = ReaderApps.packages
    private val EBOOK_WORDS = listOf("ebook", "e-book", "eink", "e ink", "도서", "서재", "리디", "book")

    /** 설치된 앱 전체(이 런처 자신 제외)를 이름 순으로 */
    private var cachedApps: List<AppEntry>? = null
    private var cachedLocale: Locale? = null
    private var packageSequence = 0

    /**
     * 설치된 앱 전체. 앱 조회와 이름 읽기가 비싸서(돌아올 때 20~30ms, 런처가 새로 뜰 때 300ms) 앱이
     * 설치·삭제·업데이트됐거나 언어가 바뀌었을 때만 다시 읽고, 그 밖에는 지난번 목록을 쓴다.
     * 런처가 새로 떠도 재부팅 전이면 파일에 저장해 둔 목록을 쓴다(앱 변경 순번은 부팅마다 새로 매겨진다).
     */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val locale = context.resources.configuration.locales[0]
        if (cachedApps == null) readSaved(context, locale)
        val changed = pm.getChangedPackages(packageSequence)
        cachedApps?.let { if (changed == null && locale == cachedLocale) return it }
        changed?.let { packageSequence = it.sequenceNumber }
        cachedLocale = locale
        return queryApps(context).also { cachedApps = it; save(context, it, locale) }
    }

    private fun savedFile(context: Context) = File(Storage.of(context).filesDir, "apps.json")
    private fun bootCount(context: Context) =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** 저장해 둔 목록이 이번 부팅·언어의 것이면 쓴다. 없거나 깨졌으면 그냥 새로 읽게 둔다. */
    private fun readSaved(context: Context, locale: Locale) = runCatching {
        val json = JSONObject(savedFile(context).readText())
        if (json.optInt("boot", -2) != bootCount(context) || json.optString("locale") != locale.toLanguageTag()) return@runCatching
        val arr = json.getJSONArray("apps")
        val apps = (0 until arr.length()).map { arr.getJSONArray(it) }
            .map { AppEntry(ComponentName(it.getString(0), it.getString(1)), it.getString(2)) }
        packageSequence = json.getInt("seq")
        cachedApps = apps
        cachedLocale = locale
    }

    private fun save(context: Context, apps: List<AppEntry>, locale: Locale) {
        val arr = JSONArray()
        apps.forEach { arr.put(JSONArray().put(it.component.packageName).put(it.component.className).put(it.label)) }
        val json = JSONObject().put("boot", bootCount(context)).put("seq", packageSequence)
            .put("locale", locale.toLanguageTag()).put("apps", arr)
        runCatching { savedFile(context).writeText(json.toString()) }
    }

    private fun queryApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, MATCH_ALL_BOOT_STATES)
        val frozen = if (Device.hasBooxLauncher(context)) frozenActivities(pm, intent) else emptyList()
        return (found + frozen)
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

    /**
     * BOOX 가 얼려 둔 앱의 실행 화면. BOOX 는 앱을 꺼 두는 것(DISABLED_USER)으로 얼려 보통 조회에는 빠진다.
     * 앱이 스스로 끈 실행 화면(아이콘 바꾸기 별칭 등)은 넣지 않는다. 누르면 BOOX 런처가 풀고 연다([launch]).
     */
    private fun frozenActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        pm.queryIntentActivities(intent, MATCH_ALL_BOOT_STATES or PackageManager.MATCH_DISABLED_COMPONENTS)
            // 켜진 앱의 화면은 보통 조회에 이미 있다(꺼진 화면이면 앱이 스스로 끈 것).
            .filter { !it.activityInfo.applicationInfo.enabled && Device.isFrozen(pm, it.activityInfo.packageName) }
            .filter {
                val a = it.activityInfo
                when (runCatching { pm.getComponentEnabledSetting(ComponentName(a.packageName, a.name)) }.getOrNull()) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> a.enabled
                    else -> false
                }
            }

    /** 앱 목록을 읽고, 지워진 앱을 설정과 읽고 있는 책에서 정리한다. */
    fun loadAndPrune(context: Context, prefs: HomePrefs): List<AppEntry> {
        val apps = load(context)
        // 잠금 해제 전에는 앱 정보가 덜 보일 수 있어, 지워진 앱 정리는 잠금이 풀린 뒤에만 한다.
        if (!Storage.isUnlocked(context)) return apps
        // 숨긴(hidden) 앱도 설치된 것으로 본다. 기본 조회로는 지워진 앱처럼 못 찾는다.
        val fix = keyFixer(apps) { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES) }.isSuccess
        }
        prefs.prune(fix)
        BookShelf.pruneApps(context, fix)
        return apps
    }

    /**
     * 저장해 둔 앱 키를 지금 목록에 맞춘다. 앱을 업데이트하는 동안에는 잠깐 목록에서 빠지므로,
     * 패키지가 정말 지워졌을 때만 null(빼기), 실행 화면 이름만 바뀌었으면 같은 패키지의 새 화면 키를 돌려준다.
     */
    private fun keyFixer(apps: List<AppEntry>, isInstalled: (String) -> Boolean): (String) -> String? {
        val keys = apps.map { it.key }.toSet()
        return fix@{ key ->
            if (key in keys) return@fix key
            val pkg = key.substringBefore('/')
            apps.firstOrNull { it.pkg == pkg }?.let { return@fix it.key }
            if (isInstalled(pkg)) key else null
        }
    }

    fun isEbook(app: AppEntry): Boolean {
        if (app.pkg in EBOOK_PACKAGES) return true
        val l = app.label.lowercase(Locale.ROOT)
        return EBOOK_WORDS.any { it in l }
    }

    fun launch(context: Context, app: AppEntry) {
        if (Device.hasBooxLauncher(context) && Device.isFrozen(context.packageManager, app.pkg)) return openFrozen(context, app)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            // 전자잉크라 화면 전환 애니메이션을 쓰지 않는다.
            context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
        } catch (_: Exception) {
            // 지워졌거나 열 수 없는 앱. 다음에 홈으로 돌아올 때 목록에서 빠진다.
        }
    }

    /**
     * BOOX 가 얼린 앱을 BOOX 런처에 풀고 열어 달라고 한다. 런처가 죽어 있었으면 먼저 다시 띄우는데, 막 뜬 런처는
     * 받는 곳을 등록하기 전에 온 방송을 놓칠 수 있다. 그래서 조금 뒤에도 이 화면이 그대로면 한 번 더 보내고,
     * 그래도 안 열리면 얼리기에서 빼라고 안내한다. 다시 누르면 앞의 재시도는 그만둔다.
     */
    private fun openFrozen(context: Context, app: AppEntry) {
        val activity = context as? Activity
        // 앱이 열리거나 사용자가 다른 데로 가면 이 화면이 초점을 잃는다.
        val left = { activity != null && (activity.isFinishing || !activity.hasWindowFocus()) }
        main.removeCallbacksAndMessages(FROZEN_RETRY)
        // 런처를 띄우는 동안 다시 누르면 앞의 것은 그만둔다(재시도·안내가 겹치지 않게).
        val tap = ++frozenTaps
        Device.keepBooxLauncher(context) {
            // 그 사이 다른 데로 갔으면 열지 않는다.
            if (tap != frozenTaps || left()) return@keepBooxLauncher
            Device.sendOpenFrozenApp(context, app.component)
            main.postDelayed({
                if (left()) return@postDelayed
                // BOOX 런처가 풀기는 했는데 열지 못했으면 직접 연다.
                if (!Device.isFrozen(context.packageManager, app.pkg)) return@postDelayed launch(context, app)
                Device.sendOpenFrozenApp(context, app.component)
                main.postDelayed({ if (!left() && activity != null) showFrozenHelp(activity, app) }, FROZEN_RETRY, FROZEN_WAIT_MS)
            }, FROZEN_RETRY, FROZEN_WAIT_MS)
        }
    }

    private fun showFrozenHelp(activity: Activity, app: AppEntry) {
        Sheet(activity).header(app.label, activity.getString(R.string.boox_frozen_help), app = app)
            .item(activity.getString(R.string.boox_freeze_settings), bold = true) { Device.openBooxFreezeSettings(activity) }
            .item(activity.getString(R.string.app_info)) { openAppInfo(activity, app) }
            .show()
    }

    private val main = Handler(Looper.getMainLooper())
    /** 얼린 앱 열기 재시도의 표(다시 누르면 지운다) */
    private val FROZEN_RETRY = Any()
    /** 얼린 앱을 누른 횟수. 런처를 띄운 뒤 이어 할 일이 마지막 누름의 것인지 본다. */
    private var frozenTaps = 0
    /** 얼린 앱을 열어 달라고 한 뒤 열렸는지 볼 때까지(BOOX 런처가 풀고 여는 시간을 넉넉히) */
    private const val FROZEN_WAIT_MS = 2000L

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

/** 자주 쓰는 앱을 직접 정한 순서대로(숨긴 앱·지워진 앱 제외). */
fun favoriteApps(apps: List<AppEntry>, prefs: HomePrefs): List<AppEntry> {
    val hidden = prefs.hidden
    val byKey = apps.associateBy { it.key }
    return prefs.favoriteList.mapNotNull { byKey[it] }.filter { it.key !in hidden }
}

/** 흑백으로 바꾼 앱 아이콘 캐시 */
object AppIcons {
    private val cache = LruCache<String, Bitmap>(200)
    private val grayPaint = Ui.grayPaint()
    private var packageSequence = 0

    fun gray(context: Context, app: AppEntry, px: Int): Bitmap {
        val key = "${app.key}@$px"
        cache.get(key)?.let { return it }
        val pm = context.packageManager
        // 못 찾으면 기본 아이콘을 쓰되 기억하지 않는다(다음에 다시 찾는다).
        val found = runCatching {
            pm.getActivityInfo(app.component, MATCH_ALL_BOOT_STATES or PackageManager.MATCH_DISABLED_COMPONENTS).loadIcon(pm)
        }.getOrNull()
        val drawable = found ?: pm.defaultActivityIcon
        val color = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(color).let { drawable.setBounds(0, 0, px, px); drawable.draw(it) }
        val gray = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(gray).drawBitmap(color, 0f, 0f, grayPaint)
        color.recycle()
        if (found != null) cache.put(key, gray)
        return gray
    }

    /** 앱이 설치·업데이트·삭제됐을 때만 캐시를 비운다(아이콘이 바뀌었을 수 있으므로). 비웠으면 true. */
    fun clearIfPackagesChanged(context: Context): Boolean {
        val changed = context.packageManager.getChangedPackages(packageSequence) ?: return false
        packageSequence = changed.sequenceNumber
        cache.evictAll()
        return true
    }
}
