package com.woody.pebbledesk.readers

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import kotlin.math.abs

/**
 * 이지뷰어·채리뷰어(Cherie Soft. 내 파일(EPUB·TXT·PDF·만화 압축 파일 등)을 여는 리더, 채리뷰어는 유료판): 서재는 파일 목록 `actMain`
 * (폴더·최근문서·즐겨찾기 탭이 모두 이 화면 안). 칸에 파일 이름 `txtFileName`·그림 `imgFileIcon`(목록 보기는 아래에 파일 종류 띠 `txtFileExt` 가 겹친다), 목록 보기에는 EPUB 책 정보 `txtFileMemo`
 * (`제목\n저자: 홍길동`)·읽은 비율 `txtFilePer`(`23.5%`, 연 적 있는 파일만)도 있다. 격자 보기는 파일 이름만 있어 제목은 파일 이름(확장자 뗌)이다.
 * 폴더·그림 같은 칸도 같은 이름이라 책 파일 확장자인 칸만 책으로 보고, 눌러도 읽는 화면이 열려야 편 것으로 본다.
 * 읽는 화면은 글(`actViewText`: EPUB·TXT)과 그림(`actViewImage`: PDF·만화)이고, 본문은 직접 그려 글자가 드러나지 않는다. 위 상태 줄
 * `txtPageInfo_TitleBar` 에 EPUB 은 `장 안 쪽/장 쪽 수[장/전체 장]`(`2/5[3/12]`), 그 밖은 `쪽/전체 쪽`(`6/101`). 제목 자리(`txtFileName_TitleBar`,
 * 메뉴의 `txtFileName`)는 늘 파일 이름(`이름.epub#3 3장`, 이지뷰어 `TEzViewBase.W`)이라 제목으로 쓰지 않고, 그 파일로 넣은 책을 찾는다([ViewerInfo.file]).
 * 책은 서재에서 칸을 눌러 열 때와 ▶(마지막 파일)·다음 파일의 열기 확인 창([openingFile])으로 위치를 알 때 넣는다(다른 앱으로 연 파일은 넣지 않는다).
 * 그림 파일은 그 폴더가 한 권이다(제목은 폴더 이름, 표지는 첫 그림. 읽는 화면 위 줄에는 지금 보는 그림 이름만 보인다).
 * 칸의 위치는 최근문서 탭은 칸의 `txtFilePath`(`#휴대폰/Books`, 다른 앱이 연 파일은 `/sdcard/Download`), 폴더 탭은 위 경로 줄 `txtPath`
 * (`#휴대폰` · `Books`, 실제 경로로 들어간 폴더는 `sdcard` · `Download`). `#휴대폰` 은 내부 저장소, `#USB메모리`·`#USB_1234-5678` 은 SD 카드·USB 맨 위
 * (이지뷰어 문자열 `storage_internal`·`storage_usb`·`device_usb`, 기기 언어를 따른다)라 실제 경로로 바꿔 책의 파일로 적는다([bookFile]): EPUB 표지(책 파일 읽기)와
 * 홈에서 그 파일 바로 열기(`actMain` 이 `file://` 보기를 받는다). 사용자가 더한 서버(`+`: FTP·SMB·WebDAV·클라우드)의 파일은 이지뷰어가 받아 둔 파일에서
 * 찾는다([downloaded], 다운로드 경로를 휴대폰·SD카드로 바꾼 경우만). 못 찾으면 그 앱에서 마지막으로 편 파일(위치 + 이름)만 기억한다.
 * 패키지마다 같은 코드로 만든 앱이라(Play·원스토어 같은 패키지) 요소 이름은 같다. 이지뷰어(26.10.20)를 디컴파일해 보면 화면·앱 클래스가
 * 패키지와 상관없이 `com.ezne.easyview.` 아래에 있고(`MyApp`·`actMain`), 유료 기능은 실행 중에 패키지 이름이 `.pro` 로 끝나는지(와 리소스
 * `APP_PERM_PRO`)로 켠다. 채리뷰어는 받아 보지 못해 그 패키지 아래 이름(`com.ezne.easyview.pro.actMain`)도 같이 받는다
 * (문리더 유료판은 화면 클래스가 패키지마다 달라 1.5.3 에서 알아보지 못했다).
 */
open class EasyViewSpec(pkg: String) : ReaderApp(
    pkg = pkg, classPrefix = BASE,
    shelves = screens(pkg, "actMain"),
    viewers = screens(pkg, "actViewText") + screens(pkg, "actViewImage"),
    titles = listOf("txtFileName"), thumbs = listOf("imgFileIcon"), authors = emptyList(), progress = "txtFilePer", due = null,
    viewerProgress = listOf("txtPageInfo_TitleBar"),
    openOnViewer = true,
    opensBookFile = true,
    needsBookFiles = true,
) {
    /**
     * 파일 이름 칸 → 책 파일만, 제목·저자는 EPUB 책 정보가 있으면 그것. 표지 칸은 아래에 겹친 파일 종류 띠(`EPUB`)를 빼고,
     * 글 파일(TXT 등)은 그림이 본문 첫 쪽 미리 보기라 표지로 자르지 않는다. 탭(폴더·최근문서·즐겨찾기)은 옆으로 넘기는 쪽이라
     * 화면 밖 탭의 칸도 트리에 남는데, 그 탭은 다시 열 때까지 읽은 비율이 그대로라 보이지 않는 칸은 읽지 않는다.
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? {
        if (!cell.isVisibleToUser) return null
        val c = super.readCell(cell) ?: return null
        val image = isImageFile(c.title)
        val name = withoutBookFile(c.title).takeIf { it != c.title && it.isNotBlank() || image } ?: return null
        val parts = partsOf(cell)
        val memo = parts[MEMO]?.text?.lines()?.map { it.trim() }.orEmpty()
        val title = memo.firstOrNull()?.takeIf { it.isNotEmpty() && AUTHOR.matchEntire(it) == null } ?: name
        val author = memo.firstNotNullOfOrNull { AUTHOR.matchEntire(it)?.groupValues?.get(1)?.trim() }.orEmpty()
        val thumb = c.thumb?.takeIf { !TEXT_FILE.containsMatchIn(c.title) }?.let { t ->
            val ext = parts[EXT]?.bounds()
            if (ext != null && ext.top > t.top && ext.top < t.bottom) Rect(t.left, t.top, t.right, ext.top) else t
        }
        // 최근문서 탭은 칸에 위치가 있고, 폴더 탭은 위 경로 줄을 붙인다([shelfCells]·[clickedCell])
        val folder = parts[FOLDER]?.text?.let(::plain)?.takeIf { it.isNotEmpty() }
        // 그림 칸의 읽은 비율은 그 그림부터 열었을 때의 것이라(그림마다 따로) 폴더 한 권의 진행률로 쓰지 않는다(읽는 화면의 쪽으로)
        val read = c.copy(title = title, author = author, thumb = thumb, file = c.title, progress = if (image) -1 else c.progress)
        return folder?.let { inFolder(read, it) } ?: read
    }

    /**
     * 칸 [c] 의 파일이 [folder] 안에 있다. 그림 파일은 그 폴더가 한 권(제목은 폴더 이름. 이지뷰어는 그림을 열면 그 폴더의 그림을 차례로 보인다,
     * 표지는 그 폴더의 첫 그림 [BookFiles.cover])
     */
    private fun inFolder(c: Cell, folder: String): Cell {
        val file = "$folder/${c.file}"
        val title = folder.substringAfterLast('/').trimStart('#').takeIf { isImageFile(c.file.orEmpty()) && it.isNotEmpty() } ?: c.title
        return c.copy(title = title, file = file)
    }

    /**
     * 최근문서 탭은 같은 파일이 경로 표기(`#휴대폰/Books`·`/sdcard/Books`)마다 따로 보이고 읽은 비율도 따로라, 같은 파일은 맨 위(마지막으로 연) 칸만.
     * 폴더 탭 칸에는 지금 보이는 위 경로 줄을 붙인다
     */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        val cells = super.shelfCells(root)
        val folder by lazy { folderOf(root) }
        // 같은 파일인지는 제목과 폴더 마지막 이름·파일 이름으로 본다(다른 폴더의 같은 이름 파일은 합치지 않는다)
        return cells.groupBy { c -> c.title + "|" + c.file?.split('/')?.takeLast(2)?.joinToString("/")?.lowercase() }.values.map { it.first() }
            .map { c -> if (c.file != null && '/' !in c.file) folder?.let { inFolder(c, it) } ?: c else c }
            // 어느 폴더인지 모르는 그림은 책으로 보지 않는다(폴더가 한 권)
            .filterNot { c -> c.file?.let { '/' !in it && isImageFile(it) } == true }
    }

    /**
     * 누른 칸에 위치가 없으면(폴더 탭) 지금 보이는 위 경로 줄을 붙인다: 저장소 맨 위에서 들어가면 `#휴대폰` · `Books`,
     * 다른 앱이 연 파일의 폴더처럼 실제 경로로 들어가면 맨 앞 `/` 없이 `sdcard` · `Download`. 원격 파일은 누르자마자 받는 창이 맨 앞에 떠
     * 그 앱의 모든 창에서 찾고, 그래도 없으면 방금 읽어 둔 서재 칸([last], 위치를 붙여 둠)에서 같은 이름의 칸을 쓴다
     */
    override fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? {
        val c = super.clickedCell(source, text, windows, last) ?: return null
        val file = c.file?.takeIf { '/' !in it } ?: return c
        val folder = (listOfNotNull(windows.active) + windows.all).firstNotNullOfOrNull(::folderOf)
        if (folder != null) return inFolder(c, folder)
        return last?.singleOrNull { it.file?.let { f -> '/' in f && f.substringAfterLast('/') == file } == true }?.let { c.copy(title = it.title, file = it.file) }
            ?: c.takeIf { !isImageFile(file) }
    }

    /** 폴더 탭의 지금 보이는 위 경로 줄(`#휴대폰` · `Books` → `#휴대폰/Books`, 맨 앞 `/` 없이 `sdcard` · `Download` → `/sdcard/Download`). 없으면 null */
    private fun folderOf(root: AccessibilityNodeInfo): String? {
        val folder = root.byId(pkg, "txtPath").filter { it.isVisibleToUser }
            .mapNotNull { n -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() } }.joinToString("/").ifEmpty { return null }
        return if (folder.startsWith("#") || folder.startsWith("/")) folder else "/$folder"
    }

    /** 위 상태 줄의 쪽(EPUB 은 장까지 셈해 전체에서의 비율, 그 밖은 쪽 ÷ 전체 쪽)과 파일 이름(`이름.epub#3 3장` → `이름.epub`) */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        fun text(id: String) = windows.all.flatMap { it.byId(pkg, id) }.firstNotNullOfOrNull { n -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() } }
        val progress = viewerProgress.firstNotNullOfOrNull(::text)?.let(::pageInfoPercent) ?: -1
        return ViewerInfo(null, "", progress, text(TITLE_BAR)?.let(::viewerFileName))
    }

    /** 찾은 실제 경로(서재를 볼 때마다 칸마다 찾으므로. 폴더 목록을 읽는 [realCase] 를 되풀이하지 않게, 못 찾은 것은 받아 둘 수 있어 담지 않는다) */
    private val resolved = LruCache<String, String>(RESOLVED_MAX)

    /** `#휴대폰/Books/a.epub` → `/storage/emulated/0/Books/a.epub`, `/sdcard/Download/a.epub` → `/storage/emulated/0/Download/a.epub`. 그 밖(네트워크·클라우드 등)은 null */
    override fun bookFile(context: Context, shown: String): String? {
        // 책 파일 읽기를 켜고 끄면 실제 대소문자를 찾는지가 달라진다
        val key = "${BookFiles.canRead(context)}|$shown"
        resolved.get(key)?.let { return it }
        return resolve(context, shown)?.also { resolved.put(key, it) }
    }

    private fun resolve(context: Context, shown: String): String? {
        if (shown.startsWith("/")) return realCase(context, shown.replaceFirst(PRIMARY, BookFiles.storageRoot.path))
        // 위치가 없는 이름(어느 폴더인지 모름)은 짐작하지 않는다
        if (!shown.startsWith("#") || '/' !in shown) return null
        val label = shown.substring(1).substringBefore('/')
        val labels = labels(context)
        val usb = labels["device_usb"]?.let { "${it}_" }
        val root = when {
            label == labels["storage_internal"] || label == labels["device_phone"] -> BookFiles.storageRoot.path
            // 카드 번호가 붙은 SD 카드·USB(`#USB_1234-5678`, 이지뷰어 안에서는 `#EXT_SD_1234-5678`)
            usb != null && label.startsWith(usb) -> "/storage/" + label.removePrefix(usb)
            // SD 카드(`#USB메모리` 등): 앱 전용 폴더 목록의 둘째부터(`/storage/XXXX-XXXX/Android/data/…`)
            label == labels["storage_external"] || label == labels["device_sdcard"] || label == labels["storage_usb"] -> sdRoots(context).firstOrNull()?.path
            // 사용자가 더한 서버(FTP·클라우드 등)는 받아 둔 파일에서 찾는다
            else -> null
        } ?: return downloaded(context, shown.substringAfter('/'))
        return realCase(context, root + "/" + shown.substringAfter('/'))
    }

    /** SD 카드·USB 맨 위들(앱 전용 폴더 목록의 둘째부터 `/storage/XXXX-XXXX/Android/data/…` 의 앞) */
    private fun sdRoots(context: Context): List<File> = context.getExternalFilesDirs(null).drop(1)
        .mapNotNull { it?.path?.substringBefore("/Android/", "")?.ifEmpty { null }?.let(::File) }

    /**
     * 목록에 보이는 이름은 이지뷰어 설정(폴더/파일명 처리 › 첫글자를 대문자로: 첫 글자만 대문자, 나머지는 소문자 `DCIM` → `Dcim`)에 따라 실제 이름과
     * 대소문자가 다를 수 있어, 파일을 읽을 수 있으면 조각마다 폴더 목록에서 실제 이름을 찾는다(공유 저장소는 대소문자를 가리지 않아 보이는 대로도 열리지만,
     * 다른 앱이 적은 같은 파일의 경로와 맞게). 못 찾거나 읽을 수 없으면 그대로
     */
    private fun realCase(context: Context, path: String): String {
        if (!BookFiles.canRead(context)) return path
        var dir = File("/")
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            val names = dir.list()
            // 목록을 못 읽는 위쪽 폴더(`/storage` 등)는 그대로 내려간다
            val real = if (names == null || part in names) part else names.firstOrNull { it.equals(part, ignoreCase = true) } ?: return path
            dir = File(dir, real)
        }
        return dir.path
    }

    /**
     * 원격(서버·클라우드) 파일 [remote](서버 맨 위에서의 경로 `novels/a.epub`)를 열 때 이지뷰어가 받아 둔 파일(하나뿐일 때, 없으면 null).
     * 받는 곳은 이지뷰어 설정의 다운로드 경로(`App.Cache.Down.Path`): 시스템 기본은 이지뷰어 전용 캐시라 읽을 수 없고, 휴대폰·SD카드면 그 저장소의
     * `easyview/down/<서버 번호>/<원격 경로>`(이지뷰어 `u51.Z(…, 3)`·`xq4`). 책 파일 읽기가 켜져 있어야 찾는다. 이름이 50자가 넘으면 줄여 받으므로 찾지 못한다
     */
    private fun downloaded(context: Context, remote: String): String? {
        if (remote.isEmpty() || !BookFiles.canRead(context)) return null
        return (listOf(BookFiles.storageRoot) + sdRoots(context)).flatMap { root -> File(root, DOWNLOADS).listFiles().orEmpty().toList() }
            .filter { it.isDirectory }.map { File(it, remote) }.filter { it.isFile }
            .singleOrNull()?.let { realCase(context, it.path) }
    }

    /**
     * 이지뷰어가 그림을 아직 만들지 않은 파일(받기 전의 원격 파일 포함)에 그리는 파일 종류 아이콘(`ic_file_ebook`·`ic_file_pdf` 등. 흰 모양을 회색으로 칠해
     * 그린다)인가: 자른 그림에서 바탕과 밝기가 다른 곳의 모양을 아이콘의 불투명한 곳의 모양과 [MASK]×[MASK] 칸으로 줄여 겹치는 정도(IoU)로 본다
     * (2026-10-21 에뮬레이터 화면: 기본 아이콘 0.89, EPUB 표지·만화 첫 쪽·PDF 첫 쪽·TXT 미리 보기 0.33 아래)
     */
    override fun placeholderCover(context: Context, crop: Bitmap): Boolean {
        val shape = cropMask(crop) ?: return false
        return iconMasks(context).any { icon ->
            abs(icon.aspect - shape.aspect) <= ASPECT_SLACK * icon.aspect && iou(icon.cells, shape.cells) >= PLACEHOLDER_IOU
        }
    }

    private class Mask(val cells: BooleanArray, val aspect: Float)

    /** 이지뷰어의 파일 종류 아이콘 모양(이지뷰어가 업데이트되면 다시 읽는다) */
    @Volatile private var icons: Pair<Long, List<Mask>>? = null

    private fun iconMasks(context: Context): List<Mask> {
        val updated = runCatching { context.packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull() ?: return emptyList()
        icons?.takeIf { it.first == updated }?.let { return it.second }
        val res = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull() ?: return emptyList()
        val masks = ICONS.mapNotNull { name ->
            val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: return@mapNotNull null
            val d = runCatching { res.getDrawable(id, null) }.getOrNull() ?: return@mapNotNull null
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: ICON_PX
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: ICON_PX
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(b))
            maskOf(b) { Color.alpha(it) > OPAQUE }.also { b.recycle() }
        }
        icons = updated to masks
        return masks
    }

    /** 자른 그림의 모양: 바탕(네 귀퉁이 밝기 가운데 셋째로 밝은 것)과 밝기가 [INK] 넘게 다른 곳 */
    private fun cropMask(b: Bitmap): Mask? {
        val bg = listOf(b.getPixel(0, 0), b.getPixel(b.width - 1, 0), b.getPixel(0, b.height - 1), b.getPixel(b.width - 1, b.height - 1))
            .map(::luma).sorted()[2]
        return maskOf(b) { abs(luma(it) - bg) > INK }
    }

    /** [b] 에서 [on] 인 곳을 감싸는 상자를 [MASK]×[MASK] 칸으로 나눠 칸 가운데 점마다 본 모양과 상자의 가로 ÷ 세로. 아무 데도 없으면 null */
    private fun maskOf(b: Bitmap, on: (Int) -> Boolean): Mask? {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) if (on(px[y * w + x])) {
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        if (right < 0) return null
        val cells = BooleanArray(MASK * MASK) { k ->
            val x = left + ((right - left) * (k % MASK + 0.5f) / MASK).toInt()
            val y = top + ((bottom - top) * (k / MASK + 0.5f) / MASK).toInt()
            on(px[y * w + x])
        }
        return Mask(cells, (right - left + 1f) / (bottom - top + 1f))
    }

    private fun iou(a: BooleanArray, b: BooleanArray): Float {
        var both = 0
        var any = 0
        for (i in a.indices) { if (a[i] && b[i]) both++; if (a[i] || b[i]) any++ }
        return if (any == 0) 0f else both.toFloat() / any
    }

    private fun luma(c: Int) = (299 * Color.red(c) + 587 * Color.green(c) + 114 * Color.blue(c)) / 1000

    /** ▶(마지막 파일)·마지막 쪽의 다음 파일은 `다시 열겠습니까?`·`다음 파일을 열겠습니까?` 창에 파일 이름·위치를 보인 뒤 연다 */
    override val confirmsOpen: Boolean get() = true

    /** 열기 확인 창(`첫페이지부터 보기` 칸 `chkOpenFirstPage` 가 있는 창)의 위치 `txtFilePath` + 파일 이름 `txtFileName` */
    override fun openingFile(windows: AppWindows): String? {
        for (root in windows.all) {
            if (root.byId(pkg, "chkOpenFirstPage").isEmpty()) continue
            val name = root.byId(pkg, "txtFileName").firstNotNullOfOrNull { n -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() } } ?: continue
            val folder = root.byId(pkg, FOLDER).firstNotNullOfOrNull { n -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() } }
            return if (folder == null) name else "$folder/$name"
        }
        return null
    }

    /** 이지뷰어가 저장소 이름으로 보이는 글자(이지뷰어가 업데이트되거나 기기 언어가 바뀌면 다시 읽는다. 서재 칸마다 쓴다) */
    @Volatile private var labelCache: Pair<String, Map<String, String>>? = null

    /** 이지뷰어가 저장소 이름으로 보이는 글자(문자열 이름 → 기기 언어의 글자). 이지뷰어 리소스를 못 읽으면 빈 표 */
    private fun labels(context: Context): Map<String, String> {
        val updated = runCatching { context.packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull() ?: return emptyMap()
        val key = "$updated|${Resources.getSystem().configuration.locales.toLanguageTags()}"
        labelCache?.takeIf { it.first == key }?.let { return it.second }
        val res = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull() ?: return emptyMap()
        return LABELS.mapNotNull { name -> res.getIdentifier(name, "string", pkg).takeIf { it != 0 }?.let { name to res.getString(it) } }.toMap()
            .also { labelCache = key to it }
    }

    /** 칸 안의 EPUB 책 정보·파일 종류 띠·위치(이벤트로 받은 칸에서는 이름으로 찾기가 안 되어 직접 훑는다) */
    private fun partsOf(cell: AccessibilityNodeInfo): Map<String, AccessibilityNodeInfo> {
        val found = mutableMapOf<String, AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            n.viewIdResourceName?.substringAfter(":id/")?.takeIf { it == MEMO || it == EXT || it == FOLDER }?.let { found.putIfAbsent(it, n) }
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        return found
    }

    companion object {
        private const val BASE = "com.ezne.easyview"
        private const val MEMO = "txtFileMemo"
        private const val EXT = "txtFileExt"
        private const val FOLDER = "txtFilePath"
        private const val TITLE_BAR = "txtFileName_TitleBar"
        private const val RESOLVED_MAX = 64
        /** 원격 파일을 받아 두는 폴더(저장소 맨 위에서. 그 아래 서버마다 폴더 하나, 그 안에 원격 폴더 모양) */
        private const val DOWNLOADS = "easyview/down"
        /** 이지뷰어 저장소 이름 문자열(내부 저장소·SD 카드·USB) */
        private val LABELS = listOf("storage_internal", "device_phone", "storage_external", "device_sdcard", "storage_usb", "device_usb")

        /** 이지뷰어 파일 종류 아이콘(그림이 없는 파일에 그린다) */
        private val ICONS = listOf("ic_file_ebook", "ic_file_pdf", "ic_file_zip", "ic_file_txt", "ic_file_img", "ic_file_file", "ic_file_unknown")
        private const val ICON_PX = 144
        /** 모양을 줄여 비교하는 칸 수(가로·세로), 기본 아이콘으로 볼 겹침, 가로 ÷ 세로가 다를 수 있는 정도 */
        private const val MASK = 24
        private const val PLACEHOLDER_IOU = 0.7f
        private const val ASPECT_SLACK = 0.2f
        /** 아이콘의 불투명한 곳, 자른 그림에서 바탕과 다른 밝기 */
        private const val OPAQUE = 128
        private const val INK = 40

        /** 내부 저장소의 다른 이름(최근문서 탭은 다른 앱이 연 파일을 `/sdcard/…` 로 보인다) */
        private val PRIMARY = Regex("""^/(sdcard|storage/self/primary)(?=/|$)""")

        /** 읽는 화면 위 줄 → 파일 이름: 첫 줄에서 EPUB 의 `#장 번호 장 제목` 을 떼고, 경로가 붙었으면(설정·압축 파일 안 그림) 책 파일인 마지막 조각 */
        internal fun viewerFileName(text: String): String? {
            val line = text.lineSequence().first().replace(CHAPTER, "").trim()
            val parts = line.split('/').filter { it.isNotBlank() }
            return (parts.lastOrNull { withoutBookFile(it) != it } ?: parts.lastOrNull())?.trim()
        }

        /** EPUB 제목 뒤의 `#3 3장` */
        private val CHAPTER = Regex("""#\d+(\s.*)?$""")

        /** 화면 클래스: 이지뷰어처럼 패키지와 상관없는 이름과, 패키지 아래 이름(채리뷰어 미확인) */
        private fun screens(pkg: String, name: String) = setOf("$BASE.$name", "$pkg.$name")

        /** 그림이 본문 미리 보기인 글 파일 */
        private val TEXT_FILE = Regex("""\.(txt|html?|xhtml|md|rtf|docx?|odt)$""", RegexOption.IGNORE_CASE)

        /** `저자: 홍길동`(이지뷰어 문자열 `item_book_author`: 한국어 `저자:`, 영어 `Author:`, 중국어·일본어 `作者:`·`作者：`) */
        private val AUTHOR = Regex("""(?:저자|Author|作者)\s*[:：]\s*(.*)""", RegexOption.IGNORE_CASE)

        /** EPUB 의 `2/5[3/12]`(장 안 쪽/장 쪽 수[장/전체 장]) */
        private val CHAPTER_PAGES = Regex("""(\d+)\s*/\s*(\d+)\s*\[\s*(\d+)\s*/\s*(\d+)\s*]""")

        /** `2/5[3/12]` → (앞 장 2개 + 이 장의 2/5) ÷ 12 = 20, `6/101` → 5. 모르면 -1 */
        internal fun pageInfoPercent(text: String): Int {
            val m = CHAPTER_PAGES.find(text) ?: return viewerPercent(text, false)
            val (page, pages, chapter, chapters) = m.destructured.toList().map { it.toLong() }
            if (pages <= 0 || chapters <= 0 || chapter < 1) return -1
            return ((chapter - 1 + page.toDouble() / pages) * 100 / chapters).toInt().coerceIn(0, 100)
        }
    }
}

/** 무료판(Play 의 `이지뷰어-TTS,만화,…` 와 원스토어 이지뷰어) */
object EasyView : EasyViewSpec("com.ezne.easyview.n")

/** 무료판(Play 의 `이지뷰어-만화,소설,…`. 2026-10 에뮬레이터에서 확인한 판) */
object EasyViewFree : EasyViewSpec("com.ezne.easyview.free")

/** 유료판 채리뷰어(Play·원스토어) */
object CherieViewer : EasyViewSpec("com.ezne.easyview.pro")
