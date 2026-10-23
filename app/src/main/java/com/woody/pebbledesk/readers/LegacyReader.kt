package com.woody.pebbledesk.readers

import com.woody.pebbledesk.Seen
import com.woody.pebbledesk.WatchLog
import com.woody.pebbledesk.hasTitle
import com.woody.pebbledesk.normTitle
import java.io.File

/**
 * 옮기는 동안만 쓰는 어댑터(docs/reader-adapter-plan.md 3단계 `Legacy`): 아직 그 앱 파일로 옮기지 않은 특수 앱의 처리를
 * 예전 서비스 코드 그대로, [ReaderApp] 의 표시(`searchesBookFile`)로 갈라 둔다. 표시가 없는 앱에는 바탕 [ScreenReader] 와 같다.
 */
open class LegacyReader(app: ReaderApp) : ScreenReader(app) {
    /** 저장소에서 책 파일을 찾아 본 책(정리한 제목, [searchFile]). 서비스가 살아 있는 동안 한 권에 한 번만 */
    private val fileSearched = mutableSetOf<String>()

    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        val recorder = ctx.recorder
        val progress = shown.progress
        logShown(ctx, shown)
        retryProgress(ctx, progress)
        shown.title?.let { title ->
            // 편 책 기록의 책이 이 제목의 읽고 있는 책이면 그 책을 읽는 중이다. 아니면 이 제목의 책을 편 것으로 다시 적는다(그래야 그 뒤 시간이 이 책에)
            val opened = recorder.recordedBook(ctx.pkg)?.hasTitle(title) != true
            if (opened) ctx.opened(Seen(title, shown.author, progress), ctx.unknownOpenAt)
            if (app.searchesBookFile) searchFile(ctx, title, shown.author)
            if (opened) return
        }
        // 진행률은 편 책 기록의 책에(서비스가 꺼진 동안 다른 책을 폈을 수 있다: '책 모름'이면 메뉴·서재에서 제목을 볼 때까지 적지 않는다).
        if (progress < 0) return
        val book = recorder.recordedBook(ctx.pkg) ?: return
        recorder.status(book.id, progress)
    }

    /**
     * 연 파일을 내놓지 않는 리더([ReaderApp.searchesBookFile], 문리더)의 책 [title]: 표지도 파일도 모르면 저장소에서 그 제목의 EPUB 을 찾아
     * 그 책의 파일로 적는다(표지는 그릴 때 꺼낸다). 저장소를 훑으므로 뒤에서, 책마다 한 번만(책 파일 읽기를 허용하기 전에는 세지 않는다).
     */
    private fun searchFile(ctx: AppContext, title: String, author: String) {
        val key = normTitle(title)
        if (key in fileSearched) return
        val lookup = ctx.recorder.lookup
        val book = lookup.byTitle(title) ?: return
        if (lookup.hasCover(book.id) || lookup.filePath(book.id) != null || !BookFiles.canRead(ctx.context)) return
        fileSearched += key
        ctx.inBackground({ BookFiles.findEpub(title, author) }) { result ->
            val path = result.getOrNull() ?: return@inBackground
            ctx.log(WatchLog.Kind.FILE, title, File(path).name)
            saveFile(ctx, title, path)
        }
    }

    /** 이북 앱이 [title] 로 연 파일을 그 책에 적어 둔다(홈에서 그 파일을 바로 열고, 표지가 없으면 그릴 때 그 파일에서 꺼낸다) */
    private fun saveFile(ctx: AppContext, title: String, path: String) {
        ctx.recorder.lookup.byTitle(title)?.let { ctx.recorder.setFile(it.id, path) }
    }

}
