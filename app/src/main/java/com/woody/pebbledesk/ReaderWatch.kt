package com.woody.pebbledesk

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import android.view.Display
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.graphics.Rect
import android.graphics.Bitmap
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.readers.AppWindows
import com.woody.pebbledesk.readers.BookFiles
import com.woody.pebbledesk.readers.Cell
import com.woody.pebbledesk.readers.FileBook
import com.woody.pebbledesk.readers.ReaderApps
import com.woody.pebbledesk.readers.ReaderSpec
import com.woody.pebbledesk.readers.ViewerInfo
import com.woody.pebbledesk.readers.byId
import com.woody.pebbledesk.readers.usableThumb
import com.woody.pebbledesk.readers.withoutBookFile
import java.time.LocalDate

/**
 * 읽고 있는 책 자동 추가(접근성 서비스). 이북 앱([ReaderApps], 앱마다 화면 이름·요소 이름은 `readers/` 의 앱 파일)의 **서재 화면**에 보이는 제목·저자·진행률·반납일과
 * 사용자가 누른 책 칸만 읽는다. 다른 앱은 시스템이 아예 보내지 않고(`res/xml/reader_watch.xml` 의 packageNames),
 * 읽는 화면은 본문(웹 화면)을 훑지 않고 진행률 줄·메뉴 제목만 이름으로 읽는다. 읽은 내용은 책 목록에만 적고 밖으로 보내지 않는다.
 */
class ReaderWatchService : AccessibilityService() {
    /** 앱마다 지금 앞에 있는 화면(액티비티 이름) */
    private val screen = mutableMapOf<String, String>()
    /**
     * 서재의 '최근 읽은 책' 줄을 둔다. 예전에는 앱마다 마지막으로 편 책 제목(`opened_<앱>`)도 두어 진행률을 붙였는데, 지금은 편 책 기록(DB `book_open`,
     * [ReadingLog.openBook])이 그 일을 하고 옛 값은 그 기록이 비어 있을 때만 읽는다([ReadingLog]).
     */
    private val openedPrefs by lazy { Storage.of(this).getSharedPreferences("reader_watch", MODE_PRIVATE) }
    /** 탭마다 책이 다른 앱([ReaderSpec.viewerTabs])에서 읽는 화면 [cls](탭)로 마지막으로 편 책의 제목(서비스가 다시 시작돼도 남긴다) */
    private fun tabBook(cls: String) = openedPrefs.getString("tab_$cls", null)
    private fun setTabBook(cls: String, title: String) {
        if (tabBook(cls) != title) openedPrefs.edit().putString("tab_$cls", title).apply()
    }
    /** 읽으러 가기 전에 본 서재의 '최근 읽은 책'([ReaderSpec.recent]) */
    private fun recentBefore(pkg: String) = openedPrefs.getString("recent_$pkg", null)
    /** [title] 이 null 이면 지운다(모른다) */
    private fun setRecentBefore(pkg: String, title: String?) {
        if (recentBefore(pkg) != title) openedPrefs.edit().putString("recent_$pkg", title).apply()
    }
    /** 검색(웹 화면)에서 보고 있는 책 상세. 여기서 읽는 화면으로 넘어가면 이 책을 편 것으로 본다(`바로 읽기` 클릭에는 글자가 없다). */
    private val webBook = mutableMapOf<String, Cell>()
    /** 눌렀지만 아직 읽는 화면이 열리지 않은 책 칸과 누른 때([ReaderSpec.openOnViewer] 인 앱) */
    private val clicked = mutableMapOf<String, Pair<Cell, Long>>()
    /** 서재에서 책 칸으로 알아보지 못한 것을 누른 때(문리더 홈 탭·교보도서관 표지 보기의 표지만 있는 칸은 눌러도 제목이 오지 않는다) */
    private val unknownClick = mutableMapOf<String, Long>()
    /**
     * 그렇게 누른 뒤 읽는 화면이 열려 어떤 책인지 모르는 앱과 열린 때. 앞서 편 책과 다른 책일 수 있어 마지막으로 편 책을 지워 두고
     * (진행률을 적지 않는다. 서비스가 다시 시작돼도), 읽는 화면 메뉴의 제목으로 알면 열린 때부터 그 책으로 센다. 메뉴 없이 그 읽는 화면을 떠났다
     * 다시 들어오면 지운다(다른 책일 수 있어 그 사이 시간은 책 모름 그대로).
     */
    private val unknownOpen = mutableMapOf<String, Long>()
    /** 탭마다 책이 다른 앱([ReaderSpec.viewerTabs])에서 [unknownOpen] 을 적은 탭(같은 모르는 탭에 다시 와도 처음 열린 때를 둔다) */
    private val unknownTab = mutableMapOf<String, String>()
    private var lastWebRead = 0L
    /** 앱마다 읽는 화면이 마지막으로 앞에 온 때 */
    private val viewerAt = mutableMapOf<String, Long>()
    /** 이북 앱 데이터로 책을 아는 앱([ReaderSpec.fileBook])에 메뉴 없이 물어볼 남은 횟수(읽는 화면이 앞에 올 때마다 다시 센다) */
    private val fileTries = mutableMapOf<String, Int>()
    /** 그 앱에서 마지막으로 메뉴 제목으로 물어본 결과(못 찾았으면 null. 메뉴를 띄워 둔 동안 다시 묻지 않게, 읽는 화면이 앞에 올 때마다 비운다) */
    private val fileTitles = mutableMapOf<String, Pair<String, FileBook?>>()
    /**
     * 아직 목록에 없는 책의 잘라 둔 표지(정리한 제목 → JPEG). 검색 화면에서 `바로 읽기`를 누를 때, 서재가 멈췄을 때(모든 앱),
     * 알라딘·리디처럼 서재를 잠깐만 보는 앱은 서재가 보이는 대로도 찍어 두었다가 그 책이 들어오면 붙인다. 최근 [PENDING_MAX]권만.
     */
    private val pendingCovers = object : LinkedHashMap<String, ByteArray>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > PENDING_MAX
    }
    private var lastEagerRead = 0L
    /** 그 앱의 화면이 마지막으로 바뀐 때 */
    private var lastTransition = 0L
    /** 앱마다 마지막으로 읽은 서재 칸들(누른 순간 화면이 이미 넘어가 칸을 못 읽을 때 누른 글자와 맞춰 본다) */
    private val lastShelf = mutableMapOf<String, List<Cell>>()
    /** 이번 서재 방문에서 책(정리한 제목)마다 바로 자르기를 해 본 횟수. 그 앱 화면이 새로 열릴 때마다 비운다. */
    private val eagerTries = mutableMapOf<String, Int>()
    /** 서재에서 읽는 화면으로 갔다 온 앱(서재의 '최근 읽은 책' 줄로 어떤 책인지 안다) */
    private val readSince = mutableSetOf<String>()
    /** [readSince] 앱마다 서재를 떠나 읽는 화면이 처음 열린 때(돌아와서야 어떤 책인지 아는 앱의 읽은 시간을 그 책으로 세게) */
    private val readerAt = mutableMapOf<String, Long>()
    /** [readSince] 앱 가운데 읽는 화면 메뉴로 이미 책을 안 앱('최근 읽은 책' 줄은 늦게 바뀌어 앞서 읽은 책이 보이기도 해 다시 보지 않는다) */
    private val readKnown = mutableSetOf<String>()
    /** [readSince] 앱 가운데 서재로 돌아왔는데 '최근 읽은 책' 줄이 아직 읽으러 가기 전 그대로인 앱(바뀌기를 기다린다) */
    private val recentWait = mutableSetOf<String>()
    private var lastShot = 0L
    private val handler = Handler(Looper.getMainLooper())
    /** 화면이 멈추면 다시 읽을 서재 */
    private var settleSpec: ReaderSpec? = null
    private val settleLater = Runnable { settleSpec?.let(::settleShelf) }
    /** 읽는 화면이 잠잠해지면(쪽 넘김·메뉴 열기 뒤) 읽을 앱 */
    private var viewerSpec: ReaderSpec? = null
    /** 진행률을 아직 못 읽어 다시 읽어 볼 횟수(메뉴의 쪽 정보는 조금 늦게 채워지고, 채워질 때 알림이 없다) */
    private var viewerRetries = 0
    private val viewerLater = Runnable { viewerSpec?.let(::readViewer) }
    /** 읽는 화면이 서재 액티비티 안에 뜨는 앱([ReaderSpec.viewerHolder])을 마지막으로 본 때와, 미뤄 두고 다시 볼 앱 */
    private var lastHolderCheck = 0L
    private var holderSpec: ReaderSpec? = null
    private val holderLater = Runnable { holderSpec?.let { holderSpec = null; checkHolder(it); readScreen(it, force = false) } }


    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val spec = ReaderApps.byPackage[e.packageName?.toString()] ?: return
        WatchLog.event(this, spec.pkg)
        // 서재는 화면이 바뀌는 동안(칸을 다시 채우는 중, 표지를 불러오는 중, 누른 칸이 반전된 순간) 읽지 않고 멈출 때까지 미룬다.
        if (settleSpec?.pkg == spec.pkg) scheduleSettle(spec)
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 대화상자 같은 것은 빼고 그 앱의 화면만 기억한다(문리더 목차 `PrefChapters` 처럼 앱 안의 대화상자도 이름이 앱 것이라 액티비티인지 본다).
                val cls = e.className?.toString()
                val isScreen = cls != null && cls.startsWith(spec.classPrefix) && isActivity(spec.pkg, cls)
                val name = cls?.takeIf { isScreen }?.let { withHolder(spec, it) }
                logScreen(spec, name ?: cls.orEmpty(), isScreen)
                // 화면이 새로 열릴 때마다(홈에서 돌아온 서재 포함) 바로 자르기 횟수를 다시 센다.
                name?.let { eagerTries.clear(); enterScreen(spec, it) }
                readScreen(spec, force = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 책이 열려 있는지를 데이터로 아는 앱(KOReader)은 앞에 올 때만 맞춘다(내용이 바뀔 때마다 파일을 읽지 않는다)
                if (spec.fileState) return
                checkHolder(spec)
                readScreen(spec, force = false)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> if (screen[spec.pkg] in spec.shelves) {
                val windows = windowsOf(spec)
                if (windows.all.any(spec::editing)) return
                val cell = spec.clickedCell(e.source, e.text, windows, lastShelf[spec.pkg])?.let { c ->
                    // 방금 읽어 둔 칸으로 맞춘 것은 그 뒤 읽는 화면에서 진행률이 바뀌었을 수 있어 목록에 이미 있는 책에는 쓰지 않는다(반납일은 그대로).
                    if (c.stale && BookShelf.findByTitle(this, c.title) != null) c.copy(progress = -1) else c
                }
                WatchLog.add(this, WatchLog.Kind.CLICK, spec.pkg, cell?.title.orEmpty())
                when {
                    cell == null -> {
                        // 표지만 있는 칸일 수 있다(누르자마자 읽는 화면으로 넘어가 누른 요소도 읽을 수 없다). 메뉴에 제목이 없는 앱은 알 길이 없어 두지 않는다.
                        if (spec.tellsTitle) unknownClick[spec.pkg] = System.currentTimeMillis()
                        rememberWebBook(spec)
                    }
                    // 누르면 내려받기·기간 만료 안내가 먼저 뜨는 앱은 읽는 화면이 열릴 때 편 것으로 본다. 기간이 지난 책은 열리지 않는다.
                    spec.openOnViewer -> if (cell.due in 0 until LocalDate.now().toEpochDay()) clicked.remove(spec.pkg)
                        else clicked[spec.pkg] = cell to System.currentTimeMillis()
                    else -> bookOpened(spec, cell)
                }
            }
        }
    }

    private fun logScreen(spec: ReaderSpec, name: String, isScreen: Boolean) {
        WatchLog.add(this, if (isScreen) WatchLog.Kind.SCREEN else WatchLog.Kind.OTHER_WINDOW, spec.pkg, name.substringAfterLast('.'),
            if (!isScreen) "" else when (name) {
                in spec.shelves -> WatchLog.Role.SHELF.name
                in spec.viewers -> WatchLog.Role.VIEWER.name
                else -> WatchLog.Role.OTHER.name
            })
    }

    /**
     * 그 앱의 화면이 [name] 으로 바뀌었다(액티비티, 또는 서재 액티비티 안에 뜬 읽는 화면 `액티비티#요소`).
     * 읽는 화면이 안에 뜨는 앱([ReaderSpec.viewerHolder])은 읽는 화면이 보이고 사라진 때를 읽은 시간에 적는다.
     */
    private fun enterScreen(spec: ReaderSpec, name: String) {
        val now = System.currentTimeMillis()
        lastTransition = now
        // 같은 화면의 알림이 한 번 더 온 것(새로 들어온 것이 아니다)
        val again = screen[spec.pkg] == name
        screen[spec.pkg] = name
        if (spec.viewerHolder != null) ReadingLog.viewerShown(this, spec.pkg, name in spec.viewers, now)
        if (ReadingLog.isReaderScreen(name)) {
            viewerAt[spec.pkg] = now
            fileTries[spec.pkg] = FILE_TRIES
            fileTitles -= spec.pkg
            // 서재를 떠나 처음 연 읽는 화면의 때만 적는다(읽는 중 목차 같은 다른 화면에 갔다 와도 처음부터 그 책으로 세게).
            // 줄이 바뀌기를 기다리다 서재에서 다시 읽으러 갔으면 이번 책의 때로 다시 적는다.
            if (spec.recent != null && (readSince.add(spec.pkg) || recentWait.remove(spec.pkg))) readerAt[spec.pkg] = now
            val unknown = unknownClick.remove(spec.pkg)?.takeIf { now - it < CLICK_OPEN_MS }
            if (unknown != null) {
                unknownOpen[spec.pkg] = now
                // 제목을 못 읽은 칸을 눌러 들어왔다: 여기부터는 책 모름(앞서 편 책에 붙이지 않게. 메뉴의 제목을 읽으면 그 책으로)
                ReadingLog.bookOpened(this, spec.pkg, "", "", now)
            } else if (!again && !spec.viewerTabs) {
                // 그렇게 연 읽는 화면을 메뉴 없이 떠났다 다시 들어왔으면(다른 책일 수 있다) 그때 연 때를 쓰지 않는다(그 사이 시간은 책 모름 그대로).
                // 같은 읽는 화면의 알림이 한 번 더 온 것이면 그대로 둔다. 탭마다 책이 다른 앱은 탭마다 따로 둔다([enterTab]).
                unknownOpen -= spec.pkg
            }
            webBook.remove(spec.pkg)?.let { book -> bookOpened(spec, book) }
            clicked.remove(spec.pkg)?.takeIf { now - it.second < CLICK_OPEN_MS }?.let { bookOpened(spec, it.first) }
            if (spec.viewerTabs && name in spec.viewers) enterTab(spec, name, now)
        }
    }

    /**
     * 탭마다 책이 다른 앱([ReaderSpec.viewerTabs])에서 읽는 화면 탭 [cls] 가 앞에 왔다. 그 탭으로 마지막으로 안 책이 지금 편 책과
     * 다르면 그 책을 편 것으로 본다(탭을 오가며 읽으면 진행률·읽은 시간이 앞 탭의 책에 붙지 않게). 모르는 탭이면 앞서 편 책과 다를 수 있어
     * '책 모름'을 적고(진행률·읽은 시간을 앞 책에 붙이지 않는다), 메뉴나 이북 앱 데이터([ReaderSpec.fileBook])로 알면 탭이 열린 때부터 그 책으로 센다. 같은 탭에 다른 책을 열면 데이터로 아는 앱은
     * 방금 연 책으로 바로잡고, 아니면 메뉴를 띄울 때까지는 알 수 없다.
     */
    private fun enterTab(spec: ReaderSpec, cls: String, now: Long) {
        val title = tabBook(cls)
        if (title == null) {
            // 같은 모르는 탭에 다시 오면(네오리더 설정 화면에 갔다 옴 등) 처음 열린 때를 둔다.
            if (unknownTab[spec.pkg] != cls || spec.pkg !in unknownOpen) {
                unknownOpen[spec.pkg] = now
                unknownTab[spec.pkg] = cls
                ReadingLog.bookOpened(this, spec.pkg, "", "", now)
            }
        } else if (recordedBook(spec)?.hasTitle(title) != true) bookOpened(spec, Cell(title, "", -1, -1, null, Rect()), now)
    }

    /**
     * 서재 액티비티 [cls] 안에 읽는 화면([ReaderSpec.viewerHolder])이 떠 있으면 `cls#요소`, 아니면 [cls].
     * 맨 앞 창이 그 앱이 아니면(알림 창 등) 알 수 없어 지금 화면 그대로 둔다.
     */
    private fun withHolder(spec: ReaderSpec, cls: String): String {
        val holder = spec.viewerHolder ?: return cls
        if (cls !in spec.shelves) return cls
        val root = root(spec) ?: return screen[spec.pkg]?.takeIf { it.substringBefore('#') == cls } ?: cls
        return if (root.byId(spec.pkg, holder).isNotEmpty()) "$cls#$holder" else cls
    }

    /**
     * 읽는 화면이 서재 액티비티 안에 뜨는 앱은 화면 바뀜 알림 없이 내용만 바뀌므로, 내용이 바뀔 때 읽는 화면이 떴는지·닫혔는지 본다.
     * 웹 화면은 알림이 잦아 [HOLDER_CHECK_MS] 에 한 번만 보고, 그 사이 알림은 마지막에 한 번 더 본다.
     */
    private fun checkHolder(spec: ReaderSpec) {
        if (spec.viewerHolder == null) return
        val now = System.currentTimeMillis()
        handler.removeCallbacks(holderLater)
        if (now - lastHolderCheck < HOLDER_CHECK_MS) {
            holderSpec = spec
            handler.postDelayed(holderLater, HOLDER_CHECK_MS)
            return
        }
        lastHolderCheck = now
        // 서비스가 그 앱이 떠 있는 동안 다시 시작되면 화면 바뀜 알림을 받지 못해 화면을 모른다. 서재 액티비티가 하나뿐이면(Libby) 그것으로 본다.
        val base = screen[spec.pkg]?.substringBefore('#') ?: spec.shelves.singleOrNull()?.takeIf { root(spec) != null } ?: return
        val name = withHolder(spec, base)
        if (name == screen[spec.pkg]) return
        logScreen(spec, name, isScreen = true)
        enterScreen(spec, name)
    }

    /**
     * 읽는 화면이 서재 액티비티 안에 뜨는 앱(Libby)의 읽는 화면을 닫힌 것으로 적는다(서비스가 붙을 때·끝날 때). 서비스가 없는 동안
     * '열림'이 남으면 그 뒤 서재만 본 시간도 읽은 시간으로 센다. 읽는 화면이 떠 있으면 다음 알림에서 다시 '열림'으로 적힌다.
     * 책이 열린 동안을 데이터로 아는 앱(KOReader)은 거꾸로 '열림'으로 적는다(서비스가 없는 동안 '닫힘'이 남으면 읽은 시간을 놓친다).
     */
    private fun closeHeldViewers() {
        val now = System.currentTimeMillis()
        for (spec in ReaderApps.ALL) if (spec.viewerHolder != null) {
            screen.remove(spec.pkg)
            ReadingLog.viewerShown(this, spec.pkg, shown = false, at = now)
        } else if (spec.fileState) {
            // 책이 열린 동안을 데이터로 아는 앱(KOReader)은 서비스가 없는 동안 열림·닫힘을 모르니 앞에 있던 동안을 다 센다(다시 읽으면 고친다)
            ReadingLog.viewerShown(this, spec.pkg, shown = true, at = now)
        }
    }

    /** 서재 화면이면 멈춘 뒤에 보이는 책들을 읽고([settleShelf]), 읽는 화면이면 잠잠해진 뒤 진행률·제목을 읽는다([readViewer]). 그 밖의 화면은 읽지 않는다. */
    private fun readScreen(spec: ReaderSpec, force: Boolean) {
        when (screen[spec.pkg]) {
            in spec.shelves -> {
                // 검색 화면의 책 상세는 `바로 읽기`를 누르자마자 사라지므로 멈추기를 기다리지 않고 1초마다 기억해 둔다.
                val now = System.currentTimeMillis()
                // 아래 둘이 같이 쓴다(그 앱의 화면 트리는 필요할 때 한 번만 가져온다).
                val shelfRoot by lazy { root(spec) }
                if (force || now - lastWebRead >= THROTTLE_MS) {
                    lastWebRead = now
                    shelfRoot?.let { root ->
                        spec.webBook(root)?.let { webBook[spec.pkg] = it } ?: webBook.remove(spec.pkg)
                        // 서재를 잠깐만 보고 책으로 넘어가도 읽으러 가기 전의 '최근 읽은 책' 줄을 알도록 멈추기를 기다리지 않는다.
                        rememberRecent(spec, root)
                    }
                }
                // 서재를 잠깐만 보고 책으로 넘어가는 앱(알라딘·북커스·리디·YES24 전자도서관)은 멈추기를 기다리지 않고 표지가 보이는 대로 잘라 둔다.
                // 화면이 막 바뀐 때(읽는 화면에서 돌아오는 중이면 화면 캡처가 막힌 읽는 화면이 검게 찍힌다)와 아직 찍을 수 없을 때는 건너뛴다.
                val ready = now - lastTransition >= TRANSITION_MS && now - lastShot >= SHOT_INTERVAL_MS
                if (spec.eagerCovers && ready && (force || now - lastEagerRead >= THROTTLE_MS)) {
                    lastEagerRead = now
                    shelfRoot?.let { root ->
                        captureCovers(spec, readShelf(spec, root).distinctBy { it.title }.mapNotNull { s ->
                            val thumb = s.thumb?.takeIf(::usableThumb) ?: return@mapNotNull null
                            val book = BookShelf.findByTitle(this, s.title)
                            // 목록에 없는 책은 들어올 때 붙일 표지를 미리 찍어 둔다.
                            val need = if (book == null) wantsPendingCover(s.title) else !BookShelf.hasCover(this, book.id)
                            if (need && tryCover(s.title)) s.title to thumb else null
                        }, retry = false)
                    }
                }
                scheduleSettle(spec)
            }
            in spec.viewers -> {
                viewerSpec = spec
                viewerRetries = VIEWER_RETRIES
                handler.removeCallbacks(viewerLater)
                handler.postDelayed(viewerLater, VIEWER_SETTLE_MS)
            }
        }
    }

    /**
     * 읽는 화면: 진행률·제목은 앱이 읽는다([ReaderSpec.readViewer]). 진행률이 아직 비어 있으면 조금 뒤 다시 읽는다(메뉴의 쪽 정보는 늦게
     * 채워지고 그때 알림이 없다). 제목이 보이면 그 책을 편 것으로 보고(서재 칸으로는 알 수 없는 교보도서관 표지 보기·교보eBook·알라딘 격자 보기·
     * 리디·문리더 홈 탭, 서재 없이 바로 열린 책), 진행률을 그 책에 적는다.
     */
    private fun readViewer(spec: ReaderSpec) {
        viewerSpec = null
        if (screen[spec.pkg] !in spec.viewers) return
        // 책이 열려 있는지를 데이터로 아는 앱(KOReader)은 화면을 읽지 않고 데이터로 맞춘다(앞에 온 때 한 번)
        if (spec.fileState) return syncFiles(spec)
        val windows = windowsOf(spec).takeIf { it.all.isNotEmpty() } ?: return logNoWindow(spec)
        val shown = spec.readViewer(windows)
        val file = fileBook(spec, shown)
        val info = file?.let { ViewerInfo(knownTitle(it), it.author, shown.progress) } ?: shown
        val progress = info.progress
        if (progress >= 0 || info.title != null) WatchLog.add(this, WatchLog.Kind.PROGRESS, spec.pkg, "$progress", info.title.orEmpty())
        if (progress < 0 && viewerRetries-- > 0) {
            viewerSpec = spec
            handler.postDelayed(viewerLater, VIEWER_RETRY_MS)
        }
        info.title?.let { title ->
            // '최근 읽은 책' 줄로 아는 앱은 서재를 떠난 뒤 메뉴로 처음 안 책은 앞서 편 책과 같아도 편 것으로 본다(맨 앞으로).
            // 같은 책을 다시 읽으면 그 줄이 그대로라 서재로 돌아와서는 알 수 없다.
            val first = spec.pkg in readSince && readKnown.add(spec.pkg)
            // 편 책 기록의 책이 이 제목의 읽고 있는 책이면 그 책을 읽는 중이다(같은 제목의 책이 여럿이어도 목록 순서로 다른 책으로 바꾸지 않는다).
            // 아니면(서비스가 꺼졌다 켜져 '책 모름'·다른 책·책 id 없는 옛 줄·그 책이 목록에 없음) 이 제목의 책을 편 것으로 다시 적는다(그래야 그 뒤 시간이 이 책에)
            if (first || recordedBook(spec)?.hasTitle(title) != true) {
                // 메뉴 없이 이북 앱 데이터로 방금 연 책을 알았으면 읽는 화면이 열린 때부터 그 책으로 센다.
                val at = unknownOpen[spec.pkg] ?: viewerAt[spec.pkg]?.takeIf { file != null && shown.title == null }
                bookOpened(spec, Cell(title, info.author, progress, -1, null, Rect()), at)
                file?.let { saveFile(title, it.path) }
                return
            }
            file?.let { saveFile(title, it.path) }
        }
        if (progress < 0) return
        // 진행률은 편 책 기록의 책에(서비스가 꺼진 동안 다른 책을 폈을 수 있다: '책 모름'이면 메뉴·서재에서 제목을 볼 때까지 적지 않는다)
        val book = recordedBook(spec) ?: return
        BookShelf.updateStatus(this, book.id, progress, -1)
    }

    /** 책이 열려 있는지를 데이터로 아는 앱(KOReader)을 모두 맞춘다(홈으로 돌아올 때, [syncFileApps]). 깔려 있는 앱만 */
    private fun syncFiles() {
        for (spec in ReaderApps.ALL) if (spec.fileState && runCatching { packageManager.getApplicationInfo(spec.pkg, 0) }.isSuccess) syncFiles(spec)
    }

    /**
     * 책이 열려 있는지를 데이터로 아는 앱(KOReader, [ReaderSpec.fileState])을 그 앱의 데이터로 맞춘다. 그 앱이 앞에 올 때와 홈으로 돌아올 때만
     * 부르고(지켜보지 않는다), 지난번에 맞춘 뒤([since])의 일을 데이터에 적힌 때로 한꺼번에 적는다:
     * - 그 사이 열었다 닫은 앞 책들(앱 안에서 책을 바꿈): 오래된 것부터 연 때에 편 것으로(목록에 없으면 넣는다), 열려 있던 동안만 읽은 시간으로.
     * - 지금 책: 그 사이 열었으면(지금 닫혀 있어도) 연 때에 편 것으로. 그 전부터 열려 있던 책은 편 책 기록이 다르면(서비스가 다시 시작됨 등)
     *   다시 적되, 목록에 없는 책은 처음 보는 파일일 때만 넣는다(완독·빼기 한 책이 그 앱에 열려 있어도 되살아나지 않게). 진행률을 그 책에.
     * 처음 맞출 때는 지금 책만 본다(그 앱의 지난 기록을 모두 넣지 않게). 데이터를 읽을 수 없으면 앞에 있던 동안을 다 센다.
     */
    private fun syncFiles(spec: ReaderSpec) {
        val now = System.currentTimeMillis()
        val key = "sync_${spec.pkg}"
        // 그 앱은 때를 초로 버려 적으므로 지난번에 맞춘 그 초에 일어난 일도 다시 본다(같은 것은 다시 적히지 않는다)
        val since = openedPrefs.getLong(key, 0L).let { if (it > 0) it / 1000 * 1000 - 1000 else 0L }
        // 처음 맞출 때는 지금 책만 묻는다(지난 기록의 책들을 읽지 않게)
        val books = spec.fileBooks(this, if (since > 0) since else Long.MAX_VALUE)
            ?: return ReadingLog.viewerShown(this, spec.pkg, shown = true, at = now)
        openedPrefs.edit().putLong(key, now).apply()
        val current = books.lastOrNull() ?: return
        if (since > 0) for (b in books.dropLast(1)) {
            if (b.openedAt > since) {
                fileOpened(spec, b, b.openedAt)
                ReadingLog.viewerShown(this, spec.pkg, shown = true, at = b.openedAt, current = false)
            }
            if (b.closedAt > since) ReadingLog.viewerShown(this, spec.pkg, shown = false, at = b.closedAt, current = false)
        }
        markFileViewer(spec, current)
        val title = knownTitle(current)
        val newFile = newFile(spec, current.path)
        if (recordedBook(spec)?.hasTitle(title) != true) when {
            since > 0 && current.openedAt > since -> return fileOpened(spec, current, current.openedAt)
            current.closedAt == 0L && (newFile || BookShelf.findByTitle(this, title) != null) ->
                return fileOpened(spec, current, viewerAt[spec.pkg] ?: now)
        }
        saveFile(title, current.path)
        if (current.progress < 0) return
        val book = recordedBook(spec)?.takeIf { it.hasTitle(title) } ?: return
        BookShelf.updateStatus(this, book.id, current.progress, -1)
    }

    /** 데이터로 안 책 [b] 를 [at] 에 편 것으로 적는다(목록에 없으면 넣고 맨 앞으로, 그 파일을 적어 둔다) */
    private fun fileOpened(spec: ReaderSpec, b: FileBook, at: Long) {
        val title = knownTitle(b)
        bookOpened(spec, Cell(title, b.author, b.progress, -1, null, Rect()), at)
        saveFile(title, b.path)
    }

    /**
     * 지금 책의 열림·닫힘을 적는다([ReadingLog.viewerShown], 읽은 시간은 열려 있던 동안만): 닫혔으면 열려 있던 동안을 지난 일로 적고 지금은 닫힘,
     * 열려 있으면 연 때부터 열림, 모르면(다른 앱이 접근 시각을 바꿈) 열림(앞에 있던 동안을 다 센다). 지금 상태가 앞서 적은 것보다 앞의 때면 앞서 적은 때부터.
     */
    private fun markFileViewer(spec: ReaderSpec, file: FileBook) {
        if (file.closedAt > 0) {
            if (file.openedAt > 0) ReadingLog.viewerShown(this, spec.pkg, shown = true, at = file.openedAt, current = false)
            ReadingLog.viewerShown(this, spec.pkg, shown = false, at = file.closedAt)
        } else ReadingLog.viewerShown(this, spec.pkg, shown = true, at = file.openedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
    }

    /** 그 앱 데이터의 지금 책 파일이 지난번에 본 것과 다른가(처음 보면 참). 지금 것을 적어 둔다(서비스가 다시 시작돼도 남긴다) */
    private fun newFile(spec: ReaderSpec, path: String): Boolean {
        val key = "file_${spec.pkg}"
        if (openedPrefs.getString(key, null) == path) return false
        openedPrefs.edit().putString(key, path).apply()
        return true
    }

    /** 편 책 기록([ReadingLog.openBook])이 가리키는 읽고 있는 책(책 모름·목록에 없으면 null) */
    private fun recordedBook(spec: ReaderSpec): ShelfBook? =
        ReadingLog.openBook(this, spec.pkg)?.let { id -> BookShelf.list(this).firstOrNull { it.id == id } }

    /**
     * 이북 앱 데이터로 책을 아는 앱([ReaderSpec.fileBook], 네오리더). 쪽을 넘길 때마다 그 앱을 깨우지 않도록 메뉴 제목이 새로 보일 때(결과는 기억)와
     * 읽는 화면이 앞에 온 뒤 메뉴 없이 [FILE_TRIES]번까지만 묻는다(방금 연 책을 찾으면 그만 묻는다).
     */
    private fun fileBook(spec: ReaderSpec, shown: ViewerInfo): FileBook? {
        val title = shown.title
        if (title != null) {
            fileTitles[spec.pkg]?.let { (t, book) -> if (t == title) return book }
            return spec.fileBook(this, shown, 0).also { fileTitles[spec.pkg] = title to it }
        }
        val tries = fileTries[spec.pkg] ?: 0
        if (tries <= 0) return null
        fileTries[spec.pkg] = tries - 1
        val since = viewerAt[spec.pkg] ?: return null
        return spec.fileBook(this, shown, since)?.also { fileTries[spec.pkg] = 0 }
    }

    /**
     * 이북 앱 데이터로 찾은 책이 이미 있으면 그 책의 제목: 그 파일로 넣은 책, 없으면 파일 이름(확장자 뗌)으로 넣은 책(메뉴 제목이 파일 이름이던
     * 1.5.4 에서 넣은 책. 읽고 있는 책·지우지 않은 나간 책). 그래야 같은 책이 책 정보의 제목으로 한 번 더 들어가지 않고, 나간 책은 그 책으로 되살아난다.
     */
    private fun knownTitle(file: FileBook): String {
        BookShelf.titleByFile(this, file.path)?.let { return it }
        val fileTitle = withoutBookFile(File(file.path).name)
        return BookShelf.findByTitle(this, fileTitle)?.title ?: BookShelf.findLivePast(this, fileTitle)?.book?.title ?: file.title
    }

    /**
     * 이북 앱이 [title] 로 연 파일을 그 책에 적어 둔다(홈에서 그 파일을 바로 연다). 표지가 없으면 책 파일(EPUB)의 표지를 넣는다
     * (저장소를 읽을 수 있을 때, 파일마다 서비스가 떠 있는 동안 한 번만 해 본다).
     */
    private fun saveFile(title: String, path: String) {
        val book = BookShelf.findByTitle(this, title) ?: return
        BookShelf.setFilePath(this, book.id, path)
        if (BookShelf.hasCover(this, book.id) || !BookFiles.canRead(this) || !coverTried.add(path)) return
        val context = applicationContext
        Bg.run({ BookFiles.cover(path) }) { result ->
            val bytes = result.getOrNull() ?: return@run
            if (!BookShelf.hasCover(context, book.id)) runCatching { BookShelf.setCover(context, book.id, bytes) }
        }
    }

    /** 표지를 꺼내 본 책 파일들([saveFile]) */
    private val coverTried = mutableSetOf<String>()

    /**
     * 책 칸이 아닌 것을 눌렀다: 검색 화면의 `바로 읽기`일 수 있으니 지금 보이는 책 상세를 기억하고 표지를 찍어 둔다
     * (웹 화면은 다 그려진 뒤 알림이 없을 때가 있어, 1초마다 기억해 둔 것이 비어 있을 수 있다).
     */
    private fun rememberWebBook(spec: ReaderSpec) {
        val root = root(spec) ?: return
        val web = spec.webBook(root) ?: return
        webBook[spec.pkg] = web
        val book = BookShelf.findByTitle(this, web.title)
        if (web.thumb != null && (book == null || !BookShelf.hasCover(this, book.id))) captureCovers(spec, listOf(web.title to web.thumb))
    }

    /** 찍어도 버려지는 칸(한 가지 색 표지 등)을 계속 찍지 않도록 서재를 볼 때마다 책마다 [EAGER_TRIES]번만 자른다. 더 해 볼 수 있으면 센다. */
    private fun tryCover(title: String): Boolean {
        val key = normTitle(title)
        val tries = eagerTries.getOrDefault(key, 0)
        if (tries >= EAGER_TRIES) return false
        eagerTries[key] = tries + 1
        return true
    }

    /** 목록에 없는 [title] 의 표지를 잘라 둘까: 아직 잘라 두지 않았으면(권수 제한이 없어 펴면 늘 들어온다) */
    private fun wantsPendingCover(title: String) = pendingFor(title) == null

    /** 잘라 둔 표지 가운데 [title] 의 것(부제·공백 무시)의 열쇠 */
    private fun pendingFor(title: String): String? {
        val n = normTitle(title)
        return pendingCovers.keys.firstOrNull { sameTitle(it, n) }
    }

    /**
     * 읽으러 가기 전의 '최근 읽은 책' 줄을 적어 둔다(돌아와 아직 기다리는 동안은 고치지 않는다).
     * 줄이 안 보이면(아직 안 그려짐) 지운다: 앞서 적은 줄과 비교하면 그사이 바뀐 줄을 방금 편 책으로 잘못 본다.
     */
    private fun rememberRecent(spec: ReaderSpec, root: AccessibilityNodeInfo) {
        if (spec.recent == null || spec.pkg in readSince) return
        setRecentBefore(spec.pkg, spec.recentTitle(root))
    }

    /** 서재에 보이는 책 칸들(읽은 것은 [lastShelf] 에 남긴다). 서재 편집 중이면 읽지 않는다(고르기 표시가 표지에 찍힌다). */
    private fun readShelf(spec: ReaderSpec, root: AccessibilityNodeInfo): List<Cell> =
        if (spec.editing(root)) emptyList() else spec.shelfCells(root).also { if (it.isNotEmpty()) lastShelf[spec.pkg] = it }

    /**
     * 책을 폈다: 목록에 있으면 맨 앞으로, 없으면 새로 넣는다(전에 뺀 책이면 같은 책으로 되살린다. 권수 제한은 없다). 인터넷 검색은 하지 않고,
     * 표지는 이북 앱 서재에 보이는 그 책의 표지 칸을 화면에서 잘라 온다(안 되면 빈 표지).
     * [at] 은 그 책을 편 때(서재로 돌아와서야 알면 읽는 화면이 열린 때). 읽은 시간을 이때부터 이 책으로 센다.
     */
    private fun bookOpened(spec: ReaderSpec, seen: Cell, at: Long? = null) {
        webBook.remove(spec.pkg)
        // 앞서 눌러 내려받기만 한 칸이 그 뒤 연 다른 책 대신 들어가지 않게
        clicked -= spec.pkg
        unknownClick -= spec.pkg
        unknownOpen -= spec.pkg
        // 편 때는 한 번만 정해 회차 시작일과 편 책 기록에 같이 쓴다(자정에 걸쳐도 같은 날)
        val openedAt = at ?: System.currentTimeMillis()
        var result = WatchLog.Opened.FRONT
        val book = BookShelf.findByTitle(this, seen.title)?.also {
            BookShelf.moveToFront(this, it.id)
            BookShelf.updateStatus(this, it.id, seen.progress, seen.due, seen.author)
        } ?: run {
            // 완독한 책도 펴면 들어온다(새 회차가 그날 시작, [BookShelf.addOpened]. 1.5.3 까지는 완독한 책은 넣지 않았고, 10권이 차면 넣지 않았다).
            // 파일로만 여는 이북 앱(네오리더)은 그 항목([AppStore.fileReaders])으로
            val app = AppStore.readerKey(this, spec.pkg)
            result = WatchLog.Opened.NEW
            BookShelf.addOpened(this, seen.title, seen.author, app, seen.progress, seen.due, openedAt)
        }
        WatchLog.add(this, WatchLog.Kind.OPENED, spec.pkg, seen.title, result.name)
        // 편 책 기록에는 앱이 보여 준 제목을 적는다(제목을 고친 책이어도. 지운 뒤 그 시간을 다시 정할 때 같은 제목의 새 책을 찾게)
        ReadingLog.bookOpened(this, spec.pkg, seen.title, book.id, openedAt)
        if (spec.viewerTabs) screen[spec.pkg]?.takeIf { it in spec.viewers }?.let { setTabBook(it, seen.title) }
        // 미리 잘라 둔 표지(검색 화면의 `바로 읽기`, 서재)
        pendingFor(book.title)?.let { key ->
            val bytes = pendingCovers.remove(key) ?: return@let
            if (!BookShelf.hasCover(this, book.id)) runCatching { BookShelf.setCover(this, book.id, bytes) }
        }
        // 그 밖의 표지는 지금(누른 칸이 반전돼 있거나 화면이 넘어가는 중) 찍지 않고, 서재로 돌아와 화면이 멈췄을 때 잘라 온다.
    }

    private fun scheduleSettle(spec: ReaderSpec) {
        settleSpec = spec
        handler.removeCallbacks(settleLater)
        handler.postDelayed(settleLater, SETTLE_MS)
    }

    /**
     * 서재 화면이 [SETTLE_MS] 동안 그대로면: 보이는 책들의 진행률·반납일을 적고, 표지 없는 책은 표지 칸을 찍어 잘라 온다
     * (목록에 없는 책도 잘라 두었다가 그 책이 들어오면 붙인다).
     * (밀리는 앱을 다시 열 때 잠깐 모든 칸을 한 책·0% 로 채웠다가 고친다. 그 사이 값을 적지 않도록 멈춘 뒤에 읽는다.)
     */
    private fun settleShelf(spec: ReaderSpec) {
        settleSpec = null
        if (screen[spec.pkg] !in spec.shelves) return
        val root = root(spec) ?: return logNoWindow(spec)
        val seen = readShelf(spec, root)
        WatchLog.add(this, WatchLog.Kind.SHELF, spec.pkg, "${seen.size}")
        // 책 칸을 눌러도 알림이 오지 않는 앱(알라딘): 읽는 화면에서 돌아오면 서재 아래 '최근 읽은 책'이 방금 편 책이다.
        // 그 줄이 읽으러 가기 전 그대로(모르면 그대로로 본다)면 믿지 않고 바뀌기를 기다린다: 알라딘은 줄을 늦게 바꾸고, 내 파일(PDF 등)은 줄에 오르지 않는다.
        val recent = spec.recentTitle(root)
        if (spec.pkg in readSince) {
            if (readKnown.remove(spec.pkg)) readSince -= spec.pkg
            else if (recent != null && recentBefore(spec.pkg).let { it == null || it == recent }) {
                // 몰랐으면 처음 본 줄을 읽으러 가기 전의 줄로 적어 둔다(그래야 바뀌면 알아본다).
                setRecentBefore(spec.pkg, recent)
                if (recentWait.add(spec.pkg)) WatchLog.add(this, WatchLog.Kind.RECENT_WAIT, spec.pkg, recent)
            } else recent?.let { title ->
                readSince -= spec.pkg
                recentWait -= spec.pkg
                bookOpened(spec, seen.firstOrNull { sameTitle(it.title, title) } ?: Cell(title, "", -1, -1, null, Rect()), readerAt[spec.pkg])
            }
        }
        rememberRecent(spec, root)
        val targets = mutableListOf<Pair<String, Rect>>()
        // 검색 화면의 책 상세에 보이는 표지(목록에 있는데 표지가 없으면)
        spec.webBook(root)?.let { web ->
            webBook[spec.pkg] = web
            val book = BookShelf.findByTitle(this, web.title)
            if (book != null && web.thumb != null && !BookShelf.hasCover(this, book.id)) targets += web.title to web.thumb
        }
        val statuses = mutableListOf<BookShelf.Status>()
        for (s in seen) {
            val book = BookShelf.findByTitle(this, s.title)
            if (book == null) {
                // 목록에 없는 책도 멈춘 지금 잘라 두어, 그 책을 펴면 처음부터 표지가 붙게 한다(밀리·교보도서관 등. 1.5.7 까지는
                // 바로 자르기 앱만, 그 밖의 앱은 편 뒤 서재로 돌아와야 채웠다). 리디 서재는 다 그려진 뒤 알림이 없어
                // 바로 자르기([readScreen])는 칸을 누른 순간에야 찍고, 그때는 화면이 넘어가는 중이라 버려진다.
                val thumb = s.thumb?.takeIf(::usableThumb)
                if (thumb != null && wantsPendingCover(s.title)) targets += s.title to thumb
                continue
            }
            statuses += BookShelf.Status(book.id, s.progress, s.due, s.author)
            if (s.thumb != null && !BookShelf.hasCover(this, book.id)) targets += s.title to s.thumb
        }
        // 서재 한 화면의 진행률·반납일을 한 번에 쓴다
        BookShelf.updateStatuses(this, statuses)
        if (!spec.secure) captureCovers(spec, targets)
    }

    /**
     * 화면을 한 번 찍어 책(제목)마다 표지 칸([Rect], 화면 좌표)을 잘라 표지로 저장한다. 아직 목록에 없는 책이면
     * [pendingCovers] 에 두었다가 그 책이 들어올 때 붙인다. 안드로이드 11 부터 되고,
     * 1초에 한 번만 찍을 수 있으므로 너무 이르면 조금 뒤에 다시 한다. 한 가지 색뿐인 칸(그림을 불러오기 전)은 버린다.
     */
    private fun captureCovers(spec: ReaderSpec, targets: List<Pair<String, Rect>>, retry: Boolean = true) {
        if (targets.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = System.currentTimeMillis()
        if (now - lastShot < SHOT_INTERVAL_MS) { if (retry) scheduleSettle(spec); return }
        lastShot = now
        val context = applicationContext
        val wanted = targets.distinctBy { it.first }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val shot = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()
                shot ?: return
                // 요소 이름 없이 자리로 칸을 찾는 서재(리디·휴대폰 밀리)는 화면이 움직이는 중이었을 수 있어, 찍은 뒤에도 같은 자리에 있는 칸만 자른다.
                val still = if (!spec.cellsByPosition) wanted else {
                    val now = root(spec)?.let { readShelf(spec, it) }.orEmpty().associate { it.title to it.thumb }
                    wanted.filter { (title, rect) -> now[title] == rect }
                }
                Bg.run({
                    try {
                        still.mapNotNull { (title, rect) ->
                            val r = Rect(rect).apply { intersect(0, 0, shot.width, shot.height) }
                            if (!usableThumb(r)) return@mapNotNull null
                            val crop = trimEdges(Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height()))
                            // 화면 캡처를 막은 창(읽는 화면 등)은 검게 찍혀 여기서 버려진다.
                            if (isFlat(crop)) return@mapNotNull null
                            title to ByteArrayOutputStream().also { crop.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                        }
                    } finally {
                        shot.recycle()
                    }
                }) { result ->
                    for ((title, bytes) in result.getOrNull().orEmpty()) {
                        val book = BookShelf.findByTitle(context, title)
                        if (book == null) { pendingCovers[normTitle(title)] = bytes; continue }
                        // 한 권이 실패해도(저장 공간 등) 나머지는 넣는다. 못 넣은 책은 다음에 서재를 볼 때 다시 한다.
                        if (!BookShelf.hasCover(context, book.id)) runCatching { BookShelf.setCover(context, book.id, bytes) }
                    }
                }
            }

            override fun onFailure(errorCode: Int) {}
        })
    }


    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 지난번 서비스가 내려가 있던 동안(끊긴 때부터, 끊김 알림 없이 끝났으면 살아 있던 마지막 때부터 — 강제 종료·메모리 부족·업데이트·재부팅)
        // 편 책을 모른다. 그 사이 읽은 시간이 그 전에 편 책에 붙지 않게 '책 모름'을 그때로 적는다(어느 책인지 모르면 버린다).
        downSince(this)?.let { ReadingLog.watchStopped(this, it) }
        recordLife(this, connected = true)
        bound = true
        instance = this
        WatchLog.connected(this)
        closeHeldViewers()
        // 설정 › 업데이트로 새 판이 깔린 뒤 다시 붙은 것이면 홈을 띄운다(깔린 직후의 알림에서는 막힐 때가 있다).
        Update.reopenHomeAfterUpdate(this, fromService = true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bound = false
        instance = null
        recordLife(this, connected = false)
        // 사용자가 접근성에서 끈 것이면(설정에서도 꺼짐) 그 뒤 읽은 시간은 어느 책인지 모른다(꺼지기 전에 마지막으로 편 책에 붙이지 않게).
        // 업데이트·재부팅처럼 설정은 켜진 채 잠깐 떨어진 것이면 다시 붙을 때 끊긴 때로 적는다([downSince]).
        // 끊긴 때(방금 적은 것)와 같은 때로 적어, 셀 때·다시 붙을 때 다시 적지 않게 한다.
        if (!isEnabled(this)) ReadingLog.watchStopped(this, lifeLog(this).second)
        WatchLog.add(this, WatchLog.Kind.UNBOUND)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        bound = false
        instance = null
        handler.removeCallbacks(settleLater)
        handler.removeCallbacks(viewerLater)
        handler.removeCallbacks(holderLater)
        closeHeldViewers()
        WatchLog.add(this, WatchLog.Kind.DESTROYED)
        WatchLog.flush()
        super.onDestroy()
    }

    /** 화면에 떠 있는 그 앱의 창들(읽는 화면 + 위에 뜬 메뉴·알림). 창 목록을 못 얻으면 맨 앞 창만. */
    private fun appRoots(spec: ReaderSpec): List<AccessibilityNodeInfo> =
        runCatching { windows.mapNotNull { it.root }.filter { it.packageName == spec.pkg } }.getOrDefault(emptyList())
            .ifEmpty { listOfNotNull(root(spec)) }

    /**
     * [cls] 가 [pkg] 의 액티비티인가. 대화상자가 닫힐 때는 화면 바뀜 알림이 다시 오지 않아, 대화상자를 화면으로 기억하면
     * 그 아래 읽는 화면을 읽지 않게 된다. 액티비티 목록은 크므로(킨들·교보는 수백 개) 앱마다 담아 두고 앱이 업데이트됐을 때만 다시 읽는다.
     * 앱 정보를 못 읽으면 액티비티로 본다.
     */
    private fun isActivity(pkg: String, cls: String): Boolean {
        val updated = runCatching { packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull() ?: return true
        val known = activities[pkg]?.takeIf { it.first == updated }?.second
            ?: runCatching { packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities }.getOrNull()
                ?.mapTo(HashSet()) { it.name }?.also { activities[pkg] = updated to it }
            ?: return true
        return cls in known
    }

    /** 이북 앱마다 (업데이트한 때, 액티비티 이름들)([isActivity]) */
    private val activities = HashMap<String, Pair<Long, Set<String>>>()

    /** 지금 앞에 있는 그 앱의 화면(다른 앱·시스템 창이면 null) */
    private fun root(spec: ReaderSpec): AccessibilityNodeInfo? = rootInActiveWindow?.takeIf { it.packageName == spec.pkg }

    private fun windowsOf(spec: ReaderSpec) = AppWindows(root(spec), appRoots(spec))

    /** 그 앱의 화면을 못 얻었을 때 기록한다. 다른 앱으로 떠난 것(맨 앞 창이 다른 앱)은 빼고, 맨 앞 창을 아예 못 얻을 때만. */
    private fun logNoWindow(spec: ReaderSpec) {
        if (rootInActiveWindow == null) WatchLog.add(this, WatchLog.Kind.NO_WINDOW, spec.pkg)
    }

    companion object {
        private const val THROTTLE_MS = 1000L
        /** 읽는 화면이 이만큼 잠잠하면 진행률·제목을 읽는다 */
        private const val VIEWER_SETTLE_MS = 700L
        /** 진행률이 비어 있으면 이 간격으로 이만큼 더 읽어 본다 */
        private const val VIEWER_RETRY_MS = 1000L
        private const val VIEWER_RETRIES = 3
        /** 읽는 화면이 앞에 온 뒤 메뉴 없이 이북 앱 데이터에 물어볼 횟수(책을 여는 동안 연 때가 늦게 적힐 수 있다) */
        private const val FILE_TRIES = 3
        private const val SHOT_INTERVAL_MS = 1100L
        /** 서재 화면이 이만큼 그대로여야 표지를 찍는다 */
        private const val SETTLE_MS = 1500L
        /** 바로 자르기를 서재 방문마다 책 한 권에 해 보는 최대 횟수 */
        private const val EAGER_TRIES = 3
        /** 목록에 없는 책의 표지를 잘라 둘 최대 권수(서재가 멈추면 보이는 칸을 한꺼번에 잘라 둔다. 휴대폰 격자 보기는 9~12칸) */
        private const val PENDING_MAX = 12
        /** 칸을 누른 뒤 이 안에 읽는 화면이 열리면 그 책을 편 것으로 본다(내려받기를 기다리는 시간) */
        private const val CLICK_OPEN_MS = 120_000L
        /** 화면이 바뀐 뒤 이만큼은 바로 자르지 않는다 */
        private const val TRANSITION_MS = 1000L
        /** 읽는 화면이 서재 액티비티 안에 뜨는 앱에서 그 화면이 떴는지 보는 간격 */
        private const val HOLDER_CHECK_MS = 500L

        /** 거의 한 가지 색뿐인 그림(아직 그려지지 않은 표지 칸)인가. 16×16 점의 밝기 차로 본다. */
        private fun isFlat(b: Bitmap): Boolean {
            var min = 255
            var max = 0
            for (y in 0 until 16) for (x in 0 until 16) {
                val l = luma(b.getPixel((x * 2 + 1) * b.width / 32, (y * 2 + 1) * b.height / 32))
                if (l < min) min = l
                if (l > max) max = l
            }
            return max - min < FLAT_RANGE
        }

        private const val FLAT_RANGE = 40

        /** 색 하나의 밝기(0~255) */
        private fun luma(c: Int) = (299 * (c shr 16 and 0xFF) + 587 * (c shr 8 and 0xFF) + 114 * (c and 0xFF)) / 1000

        /**
         * 표지 칸 가장자리의 테두리 선과 여백(밀리는 칸에 1px 선을 두르고 표지를 가운데 맞춰 양옆이 빈다)을 걷어 낸다.
         * 바깥 2px 안의 한 가지 색 줄(테두리 선)과 흰 줄(여백)만 지운다. 표지 바탕이 한 가지 색(빨간 표지 등)이어도
         * 깎지 않도록 그 밖의 줄은 두고, 한쪽에 [MAX_TRIM] 까지만.
         */
        private fun trimEdges(b: Bitmap): Bitmap {
            /** [depth]: 이 줄이 가장자리에서 몇 번째인가 */
            fun trimmable(px: IntArray, depth: Int): Boolean {
                var min = 255
                var max = 0
                for (c in px) {
                    val l = luma(c)
                    if (l < min) min = l
                    if (l > max) max = l
                }
                return max - min < FLAT_RANGE && (depth < FRAME_PX || min >= WHITE)
            }
            val w = b.width
            val h = b.height
            val maxX = (w * MAX_TRIM).toInt()
            val maxY = (h * MAX_TRIM).toInt()
            var left = 0
            var right = w
            var top = 0
            var bottom = h
            // 지금 남은 범위 안에서만 본다(테두리 선을 걷어야 그 안쪽 여백 줄이 한 가지 색이 된다).
            fun row(y: Int, depth: Int) =
                trimmable(IntArray(right - left).also { b.getPixels(it, 0, right - left, left, y, right - left, 1) }, depth)
            fun col(x: Int, depth: Int) =
                trimmable(IntArray(bottom - top).also { b.getPixels(it, 0, 1, x, top, 1, bottom - top) }, depth)
            do {
                var changed = false
                if (left < maxX && col(left, left)) { left++; changed = true }
                if (w - right < maxX && col(right - 1, w - right)) { right--; changed = true }
                if (top < maxY && row(top, top)) { top++; changed = true }
                if (h - bottom < maxY && row(bottom - 1, h - bottom)) { bottom--; changed = true }
            } while (changed)
            if (left == 0 && top == 0 && right == w && bottom == h) return b
            return Bitmap.createBitmap(b, left, top, right - left, bottom - top)
        }

        private const val MAX_TRIM = 0.08f
        /** 테두리 선으로 보는 가장자리 두께 */
        private const val FRAME_PX = 2
        /** 여백으로 보는 밝기 */
        private const val WHITE = 230

        /**
         * 서비스가 연결·끊긴 때와 끊긴 횟수(기기 정보에서 본다). 켜 둔 서비스가 저절로 꺼지는 기기(Moaan MIX7S)에서
         * 언제 끊겼는지 알려고. 이 앱 프로세스가 왜 끝났는지는 기기 정보가 안드로이드에서 따로 읽는다.
         */
        private fun recordLife(context: Context, connected: Boolean) {
            val prefs = lifePrefs(context)
            val now = System.currentTimeMillis()
            prefs.edit().apply {
                if (connected) putLong(LIFE_CONNECTED, now)
                else putLong(LIFE_DISCONNECTED, now).putInt(LIFE_DISCONNECTS, prefs.getInt(LIFE_DISCONNECTS, 0) + 1)
            }.apply()
        }

        /**
         * (지금 붙어 있지 않은) 서비스가 내려가 그 뒤로 편 책을 모르게 된 때: 정상적으로 끊겼으면 끊긴 때, 끊김 알림(onUnbind) 없이 끝났으면
         * 살아 있던 것이 확실한 마지막 때(연결된 때·이북 앱 알림을 마지막으로 받은 때·편 책을 마지막으로 적은 때 중 늦은 것). 연결된 적이 없으면 null.
         * 다시 붙을 때([onServiceConnected])와 꺼져 있는 채 셀 때([stoppedAt]) '책 모름'을 이때로 적는다.
         */
        internal fun downSince(context: Context): Long? {
            val (connected, disconnected, _) = lifeLog(context)
            val at = when {
                connected == 0L && disconnected == 0L -> return null
                disconnected >= connected -> disconnected
                // 편 책 기록도 살아 있던 서비스가 적은 것(알림 수는 몇 초 늦게 저장되고, 같은 알림을 처리하며 편 책을 적는 것이 그 뒤라 그보다 앞이면
                // '책 모름'이 편 책 줄 앞에 놓여 쓰이지 않는다)
                else -> maxOf(connected, WatchLog.lastEventAt(context), ReadingLog.lastOpenAt(context))
            }
            return minOf(at, System.currentTimeMillis())
        }

        /**
         * 이 프로세스에서 서비스가 붙어 있다(홈과 같은 프로세스). 설정에서 켜져 있어도 붙어 있지 않으면(시스템이 떼어 놓음·저절로 꺼지는 기기·
         * 프로세스가 막 떠 아직 안 붙음) 편 책을 모른다([ReadingLog.days]).
         */
        @Volatile var bound = false
            private set

        /** 붙어 있는 서비스([syncFileApps]) */
        @Volatile private var instance: ReaderWatchService? = null

        /**
         * 책이 열려 있는지를 데이터로 아는 앱(KOReader)을 맞춘다: 홈으로 돌아올 때(읽은 시간·책을 그리기 전에) 부른다. 그 앱을 지켜보지 않으므로
         * 앱 안에서 책을 바꾸거나 닫은 것은 이때 데이터에 적힌 때로 적힌다. 자동 추가 서비스가 붙어 있을 때만.
         */
        fun syncFileApps() {
            instance?.syncFiles()
        }

        /** 꺼져 있는 서비스가 편 책을 모르게 된 때([downSince], 기록이 없으면 지금). 그 뒤 시간이 꺼지기 전에 편 책에 붙지 않게([ReadingLog.days]). */
        fun stoppedAt(context: Context): Long = downSince(context) ?: System.currentTimeMillis()

        /** 마지막 연결 때·마지막 끊김 때(없으면 0)·끊긴 횟수 */
        fun lifeLog(context: Context): Triple<Long, Long, Int> = lifePrefs(context).let {
            Triple(it.getLong(LIFE_CONNECTED, 0), it.getLong(LIFE_DISCONNECTED, 0), it.getInt(LIFE_DISCONNECTS, 0))
        }

        private fun lifePrefs(context: Context) = Storage.of(context).getSharedPreferences("reader_watch_life", Context.MODE_PRIVATE)
        private const val LIFE_CONNECTED = "connected"
        private const val LIFE_DISCONNECTED = "disconnected"
        private const val LIFE_DISCONNECTS = "disconnects"

        /** 시스템 접근성 설정에서 이 서비스가 켜져 있는가 */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, ReaderWatchService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * 안드로이드 13+ 의 '제한된 설정': 파일(APK)로 직접 깐 앱은 접근성에서 흐리게 막힌다. 앱 정보 › ⋮ ›
         * `제한된 설정 허용` 으로 풀어야 한다(그 창을 한 번 본 뒤에야 메뉴가 생긴다). 막혔는지는 알 수 없어 13+ 이면 안내한다.
         */
        val mayBeRestricted: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        /** 시스템 접근성 설정 */
        fun openSettings(context: Context) {
            runCatching { context.startActivity(settingsIntent()) }
        }

        fun settingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        /**
         * 이 앱의 앱 정보(제한된 설정 허용). 기기 설정 아이콘을 숨긴 기기(iReader)에서도 직접 열린다.
         * 안드로이드 13 설정 앱은 ⋮ 메뉴를 만들 때 앱 uid 를 앱 목록이 넘기는 인자나 extra `uId` 에서만 읽어,
         * 없으면 -1 로 묻다가 실패해 `제한된 설정 허용` 이 빠진다(에뮬레이터 API 33 에서 확인). 그래서 uid 를 함께 넘긴다.
         */
        fun openAppInfo(context: Context) {
            runCatching { context.startActivity(appInfoIntent(context)) }
        }

        fun appInfoIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).putExtra("uId", Process.myUid())
    }
}
