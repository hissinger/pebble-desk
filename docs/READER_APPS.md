# 이북 앱 분석 노트 (크레마 페블)

Pebble Desk 가 이북 앱에서 무엇을 알아내고 무엇을 할 수 있는지 조사한 기록이다.
방법: 기기에 깔린 앱의 매니페스트·디컴파일 코드(jadx), 기기의 사용 기록(`dumpsys usagestats`)·알림·저장소 목록, 커널·이벤트 로그.
2026-10 기준 버전. 실제로 실행해 보지 않은 것은 **(미확인)** 으로 표시했다. 사용자는 주로 **교보도서관·밀리의서재** 를 쓴다.

## 한눈에

| 앱 | 읽는 화면(사용 기록으로 구분) | 특정 책 바로 열기 | 서재 열기 | 핀 바로가기 | 받은 책 위치 |
|---|---|---|---|---|---|
| 교보도서관 `kr.co.kyobobook.KEL` | O | X | 그냥 열면 서재 | X | `Android/data` (런처 접근 불가) |
| 밀리의서재 `kr.co.millie.eink` | O | ? (코드 암호화) | `BookshelfActivity` 직접 실행 (미확인) | X | 앱 전용 저장소 |
| 알라딘 `kr.co.aladin.ebook` | O | O (알라딘 상품번호) | O | O | `Android/data` |
| 리디 `com.initialcoms.ridi` | O | O (리디 책 번호) | O `ridi://Library` | O | 앱 전용 저장소 |
| 교보eBook `com.kyobo.ebook.eink` | O | 기기 안 번호라 사실상 X | O `kyoboebookeink://mylibrary` | O | `Android/data` |
| YES24 `com.yes24.ebook.einkstore` | O | X (스토어 상세만) | O | X | `Android/data` |

- 사용 기록에는 **앱·화면 이름과 시각만** 남는다. 어떤 책인지는 알 수 없다.
- 받은 책 파일은 안드로이드 11 이후 다른 앱이 `Android/data` 를 열 수 없고, 내용도 DRM 으로 암호화돼 있다(알라딘 책 폴더에 `encryption.xml`·`rights.xml`).

## 교보도서관 `kr.co.kyobobook.KEL`

- **읽는 화면**: EPUB `com.kyobo.ebook.kel.viewer.epub.B2BViewerEpubMainActivity`, PDF `...kel.viewer.pdf.B2BViewerPdfMainActivity`. 공통 모듈의 `ViewerEpubMainActivity`·`ViewerPdfMainActivity`·`ViewerComicMainActivity` 도 들어 있다.
- **오디오북**: `...kel.viewer.audio.B2BAudioPlayActivity`, `AudioPlaySubListActivity`(공통 `AudioPlayActivity`·`AudioListActivity`). → 듣는 시간을 읽은 시간과 따로 셀 수 있다(지금은 세지 않음).
- **다 읽음**: `ReadCompleteActivity` 가 들어 있지만 교보도서관에서는 아무도 띄우지 않는다(죽은 코드). 실제 완독 안내는 뷰어 안의 대화상자(`ReadCompletePopupManager`)라 사용 기록에 남지 않는다. → **사용 기록으로 완독은 알 수 없다.**
- 그 밖의 화면: 목차 `ContentsListActivity`, 댓글 `CommentsListActivity`, 동기화 로그 `ViewerSyncLogActivity`.
- 사용 기록에서 실제로 본 것: `ui.intro.IntroActivity` → `ui.main.MainActivity`(서재) → `B2BViewerEpubMainActivity`.
- **딥링크**
  - `kyobolibrarykel://` → `ApplinkActivity` → 다운로드만 처리한다.
  - `kyoboebook://` 도 등록돼 있어 교보eBook 과 겹친다. 교보 링크는 패키지를 꼭 지정한다.
  - 공통 코드의 `openBook`·`shortcut` 처리는 교보도서관에서는 동작하지 않는다(받아 쓰는 곳이 없음).
  - 그냥 실행하면 받은 책이 있을 때 서재가 먼저 나온다.
- **받은 책**: `/sdcard/Android/data/kr.co.kyobobook.KEL/files/KyoboEBookStore/contents/<번호>/<바코드>/<판>/` 에 책마다 폴더(조사 때 폴더 6개 중 1권만 내용이 있음, 나머지는 반납·미다운로드로 보임).
  - **암호화되지 않은 EPUB 이 풀려 있다.** `OEBPS/package.opf` 에 제목(`dc:title`)·저자(`dc:creator`)·표지 위치(`meta name="cover"` → `images/image-1.jpg`, 원본 JPEG 340KB).
  - `options.flk`(뷰어 글꼴·여백 설정, JSON)는 그 책을 열 때 고쳐진다 → 수정 시각 = **그 책을 마지막으로 연 시각**. 쪽 위치·진행률은 이 파일들에 없다.
  - **런처가 읽으려면**: 안드로이드 11 은 `Android/data` 를 일반 권한(‘모든 파일 접근’ 포함)으로 열어 주지 않는다. 시스템 파일 선택기(`ACTION_OPEN_DOCUMENT_TREE`, 처음 위치를 교보도서관 폴더로)로 사용자가 한 번 허락하면 될 가능성이 있다(안드로이드 13부터 막힘, 페블은 11·보안 패치 2021-06). **(미확인)**
- **알림**
  - 채널 `KEL_HIGH`·`KEL_LOW`(서버 푸시, 제목·내용은 서버가 정함 — 반납 안내를 보내는지는 도서관 서버에 달림), `TTS`(소리로 읽기), "오디오북 재생".
  - 다운로드 알림: 받는 동안 제목이 **책 제목**, 끝나면 마지막 책 제목과 `(전체 n / 성공 n / 실패 n)`. → 알림 접근 권한이 있으면 "새로 빌린 책"을 알 수 있다 **(미확인)**.
  - 반납 알림을 앱이 스스로 예약하는 코드는 없다. "반납 N일 남음"은 내 서재 화면에만 나온다.
- **오디오북**: 미디어 세션에 제목·저자·표지(`TITLE`·`AUTHOR`·`ALBUM_ART`)를 싣는다. 알림 접근 권한으로 재생 중일 때만 읽을 수 있다 **(미확인)**.
- **공개 진입점**: 파일 제공자·뷰어는 모두 비공개(`exported=false`), 위젯·정적 바로가기·책 관련 방송 없음. 핀 바로가기 코드는 공통 모듈에만 있고 교보도서관에서는 쓰이지 않는다.
- **화면 글자(접근성 서비스가 있다면)**: 내 서재 목록 항목에 `tvTitle`(제목)·`tvAuthor`·`tvRemainDate`("반납 N일 남음"/"만료")·`tvReadPercentageTxt`(읽은 %) 가 있다. 반납일·진행률을 얻는 유일한 길이지만 다른 앱 화면을 모두 읽는 권한이라 쓰지 않기로 했다.

## 밀리의서재 `kr.co.millie.eink`

- **코드는 분석 불가**: NHN AppGuard 로 암호화돼 있다(실제 코드는 `assets/classes*.jet`). 매니페스트와 리소스만 읽을 수 있다.
- **읽는 화면**: `epub.EPubViewActivity`, `pdf.PDFViewActivity`, `epub.StoryViewActivity`.
- 그 밖의 화면: 각주 `epub.FootNotePopupActivity`(뜨는 동안은 읽은 시간에서 빠진다), 글꼴 `MillieViewerFontSelectActivity`, 서재 `bookshelf.BookshelfActivity`·`BookShelfDetailActivity`·`BookShelfListActivity`, `MyLibraryBookListActivity`, 검색 `search.SearchActivity`.
- 외부에 공개된(intent-filter 없는) 화면: `BookshelfActivity`, `MyLibraryBookListActivity`, `EPubViewActivity`, `PDFViewActivity`, `SearchActivity`. 뷰어가 받는 값은 코드가 암호화돼 알 수 없다.
- 사용 기록에서 실제로 본 것: `SplashActivity` → `bookshelf.BookshelfActivity` → `epub.EPubViewActivity`.
- **딥링크**: 없다.
- **받은 책**: 외부 저장소(`Android/data/kr.co.millie.eink/files/millie/epub/`)에는 EPUB 뷰어 엔진 스크립트 `epub.eink.min.1.5.9.js`(400KB) 하나뿐이다 → 뷰어는 웹뷰로 EPUB 을 그린다. 책·표지·목록은 모두 앱 전용 저장소에 있어 어떤 방법으로도 읽을 수 없다.
- **공유 저장소**: 최근 7일 동안 `Android/` 밖에 남긴 파일이 없다(사진·다운로드·캐시 모두 없음).
- **알림**: 사실상 없다(푸시 서비스·포그라운드 서비스·부팅 수신 없음, 등록된 채널 없음). 대여 만료 문구(`book_expired_message`)는 앱 안에서만 보인다.
- **공개 데이터 제공자**: 라이브러리 것(Sentry, androidx startup, iconics)뿐.
- **서재 바로 열기**: `BookshelfActivity`·`MyLibraryBookListActivity` 는 외부 공개라 컴포넌트 이름으로 바로 열 수 있을 것이다 **(미확인)**. 뷰어는 필요한 값을 몰라 실패할 것.
- **화면 글자(접근성)**: 서재 칸 `tv_book_title`·`progress_book_reading`(진행률 막대), 뷰어 머리·꼬리에 제목·진행률을 띄우는 옵션이 있다. 쓰지 않기로 함.

## 그 밖의 앱 (사용자는 안 씀)

- **알라딘**
  - `aladinreader://?view=viewer&itemid=<알라딘 eBook ItemId>&isShortcut=true` 로 특정 책을 연다(없으면 내려받은 뒤 연다). 마지막과 같은 주소는 무시하므로 `&ts=…` 같은 값을 붙인다. 그 밖에 `view=purchase`(구매 목록), `setting` 등.
  - 읽는 화면 `cpviewer.ViewerActivity`, `epubreader.readonbook.bookrender.ReadONBookRenderActivity`.
  - 표지를 `/sdcard/sleep/.bookcover/bookcover.png` 에 쓰고 `com.keph.crema.shine.book_cover_added` 를 방송하는 코드(`CremaUtil`)가 있다. 페블에는 그 폴더가 없다(다른 크레마 기종용으로 보임). 폴더를 만들어 두고 책을 열어 봐도 쓰지 않았다(2026-10-05).
  - 소리로 읽기(TTS) 음성 데이터, 책장 목록 `files/bookshelf.json`(런처 접근 불가).
- **리디**
  - `SplashActivity` 에 `book_id`(리디 책 번호, `ridibooks.com/books/<번호>`) 를 주면 그 책을 연다. `ridi://Library` 는 서재.
  - "최근 읽은 책" 위젯(`RecentBookWidgetProvider`)이 있어, 런처가 위젯을 품으면 마지막에 읽은 책을 알 수 있다(사용자 허락 필요).
  - 읽는 화면 `reader.epub.EPubReaderActivity`, `PDFReaderActivity`, `ComicBookReaderActivity`, `WebtoonReaderActivity`, `BomReaderActivity`.
- **교보eBook**
  - `kyoboebookeink://mylibrary`(서재).
  - 책 바로가기는 기기 안에서만 쓰는 번호(`books.oid`)라 런처가 알 수 없다.
  - 읽는 화면은 교보도서관과 같은 공통 모듈.
- **YES24(크레마 기본 서점)**
  - `com.keph.crema.lunar.ui.MainActivity` 에 `GoLibrary=GoPurchaseList`(구매 목록).
  - `ActProduct` 에 `ProductNo=<YES24 상품번호>` 로 스토어 상세 화면. Pebble Desk 책 검색과 같은 번호라 "YES24에서 보기"에 쓸 수 있다.
  - 읽는 화면 `com.keph.crema.lunar.ui.viewer.{epub.CremaEPUBActivity, pdf.CremaPDFActivity, cpub.CremaCPUBActivity, txt.CremaTXTActivity}`.
  - 책을 열 때 대기 화면이 '책 표지' 모드면 `Pictures/.bookcover/bookcover.png` 를 쓴다(`ViewerRunner`). 방송 `com.keph.crema.shine.book_cover_added`/`_removed` 를 정의한다. → 크레마 슬립 화면 표지는 원래 YES24 서점 앱이 채우는 것이다.

## 핀 바로가기("홈 화면에 추가")로 얻는 것

알라딘·리디·교보eBook 은 책 화면에서 `ShortcutManager.requestPinShortcut` 을 부른다. 런처가 `LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT` 을 받으면 다음을 얻는다.

| | 알라딘 | 리디 | 교보eBook |
|---|---|---|---|
| 앱 | O | O | O |
| 책 제목(바로가기 이름) | O | O | O |
| 표지(바로가기 아이콘) | 원본 그대로 | 아이콘 크기 정사각형, 아래 잘림 | 아이콘 크기 정사각형으로 찌그러짐 |
| 그 책 바로 열기(`LauncherApps.startShortcut`) | O | O | O |

이북 앱은 기본 런처가 핀 바로가기를 받을 수 있을 때만 버튼을 보여 준다. 크레마에서 실제로 버튼이 나오는지는 **(미확인)**.

## 크레마 시스템

- **슬립 화면 표지**: 크레마 런처(`com.wetao.cremalauncher`)가 `/sdcard/Pictures/.bookcover/` 를 감시하다가, 대기 화면이 '책 표지' 모드(`Settings.System enable_standby_book=1`)면 `bookcover.png` 를 대기 화면으로 쓴다. Cover Pebble 이 이 경로를 쓴다.
- **앱별 전자잉크 설정**
  - 시스템 UI 의 `content://com.android.systemui.eink/einksettingsupdate` 가 앱마다 DPI·명암·표백(bleach) 값을 준다.
  - 앱이 바뀔 때 `com.rockchip.eink.appcustom` 방송을 보내는데, `control_type=dpi` 면 액티비티를 다시 만들고 `bleach` 면 모든 `ImageView` 와 배경 있는 뷰 묶음을 다시 그린다(크레마가 고친 `Activity`·`View`). 표백 값은 `Settings.System app_bleach_filter`.
- **화면 갱신**
  - 커널 로그 `ebc-dev: frame start, mode = …` 가 실제 화면 갱신 한 번이다(깜빡임 세는 기준).
  - 15번마다 전체 갱신(`full_mode_num = 15`, 상단의 ⑮ = `persist.vendor.fullmode_cnt`).
- **부팅**
  - 일반 런처면 잠금이 풀릴 때까지 시스템 임시 홈(`FallbackHome`)이 먼저 뜬다.
  - 크레마 업데이트 앱(`com.reink.cremaupdate`)이 부팅 때 한 번 죽는다.
  - SD 카드가 꽂혀 있으면 몇 초 뒤 알림 때문에 화면이 한 번 더 갱신된다.
- **공유 저장소**: `/sdcard/.keph/` 에 크레마 샘플 책 3권뿐.

## 접근성 서비스 시험 결과 (2026-10-05, 실제 기기)

(처음 시험) 교보도서관·밀리의서재만 받도록 제한한(`packageNames`) 시험용 접근성 서비스를 켜고, 서재 → 책 열기 → 뒤로를 해 봤다. 시험 앱은 지웠다.

| | 밀리의서재 | 교보도서관 |
|---|---|---|
| 보안 솔루션 반응 | 막지 않음(AppGuard 경고·종료 없음) | – |
| 서재 화면 | `tv_book_title`·`bookshelf_cell_reading`: `달러구트 꿈 백화점 32%`, `팩트풀니스 54%` | `tvTitle`·`tvAuthor`·`tvRemainDate`·`tvReadPercentageTxt`: `렛뎀 이론 / 멜 로빈스 / 반납 4일 남음 / 47%` |
| 책 칸 누름(`TYPE_VIEW_CLICKED`) | 이벤트 글자 `[팩트풀니스, 54%]` | 이벤트 글자 `[렛뎀 이론, 멜 로빈스, 반납 4일 남음, 47%]` |
| 뷰어 화면 | 제목 `팩트풀니스` + 진행률 `tv_viewer_add_on_right=54%` (서재를 거치지 않아도 알 수 있음) | 제목 없음, `bookIndicator=- %`. 웹뷰에 **본문 글자**가 그대로 보인다 |

- 서재 화면은 뒤로 돌아올 때 `TYPE_WINDOW_STATE_CHANGED` 로 다시 읽힌다(갱신된 % 를 얻는 시점).
- 교보도서관 뷰어는 쪽을 넘길 때마다 `TYPE_WINDOW_CONTENT_CHANGED` 가 많이 온다. 본문을 읽지 않도록 뷰어 화면에서는 트리를 읽지 않는다.
- 밀리 서재 제목은 부제가 빠진다(`달러구트 꿈 백화점` ↔ 등록된 `달러구트 꿈 백화점 : 잠들어야만 입장 가능합니다`). 제목 맞추기는 부제(`:` 앞)·공백을 무시하고 앞부분이 같으면 같은 책으로 본다.
- YES24 표지 검색: `렛뎀 이론`(+저자)·`팩트풀니스` 는 첫 결과가 맞지만 `달러구트 꿈 백화점` 은 첫 결과가 다른 권(`… 0`). → 자동 추가 때는 검색하지 않기로 했다(표지는 사용자가 '표지 다시 찾기'로 고른다).

### 교보도서관 보기 방식 (2026-10-05)

- `정렬` 메뉴의 보기방식: 목록 보기(기본) / 표지 보기. 둘 다 `MainActivity`, 칸 요소 이름은 같다(`ivThumbnail`, `tvRemainDate`, `tvReadPercentageTxt`).
- **표지 보기 칸에는 제목(`tvTitle`)·저자가 없다.** 반납은 `4일` 처럼 짧게, 표지 그림에 설명 글(contentDescription)도 없다. 칸을 누르면 `[4일, 47%]` 클릭이 온다.
- → 표지 보기 서재에서는 어떤 책인지 알 수 없다. (표지 그림 특징값으로 목록의 책과 맞춰 보는 것은 해 봤고 되지만, 추측이라 넣지 않기로 했다.) 대신 읽는 화면의 메뉴 제목으로 안다(아래 교보eBook).

### 교보 읽는 화면 (교보도서관·교보eBook 공통, 2026-10-05)

- 본문은 웹 화면(`WebView`)이고, 위아래 막대는 이름 있는 일반 요소다. 웹 화면 안을 훑지 않고 이름으로만 찾을 수 있다.
- 늘 보이는 것: `bookIndicator` = `2% (4/226p)`(처음엔 `- % ( - /0p)`), `bookMarkDefault`.
- 가운데를 눌러 메뉴를 띄우면: `viewer_top_booktitle` = 책 제목(`어린 왕자(한글판 영문판)`), `viewer_bottom_pagecount` = `2% (4/226p)`, 목차·메모·설정 단추들.
- 창 바뀜 알림의 글자는 앱 이름(`교보eBook EInk`)뿐, 웹 화면 자체에도 제목이 없다. → 제목은 메뉴를 띄웠을 때만 알 수 있다.
- 교보eBook 서재 정렬 기본값은 `최근활동순`(필터). `내 책장` 탭은 사용자가 만든 책장 목록, 책장 안은 같은 격자·목록.
- 화면 이름: 교보eBook `com.kyobo.ebook.common.b2c.viewer.{epub.ViewerEpubMainActivity, pdf.ViewerPdfMainActivity, comic.ViewerComicMainActivity}`, 교보도서관 `com.kyobo.ebook.kel.viewer.{epub.B2BViewerEpubMainActivity, pdf.B2BViewerPdfMainActivity}`.

### 밀리 탭별 (2026-10-05)

- 다운로드·전체도서 탭은 `bookshelf.BookshelfActivity`, 책장 > 읽고있는 책 등은 `bookshelf.BookShelfDetailActivity`. 칸의 요소 이름은 모두 같다(`tv_book_title`, `iv_thumbnail`, `bookshelf_cell_reading`). 보기 방식(목록/격자) 설정은 없다.
- 세 곳 모두 책 칸을 누르면 `TYPE_VIEW_CLICKED` 가 온다. 탭(`다운로드`·`책장`…)이나 책장 이름 줄을 눌러도 오므로, 책 칸이 아닌 곳은 넣으면 안 된다.
- 검색 탭은 같은 `BookshelfActivity` 안의 **웹 화면**. 책 상세에 제목·저자(`… 지음 / … 옮김`)·표지 그림·`내서재에 담기`·`바로 읽기` 가 글자로 보인다(요소 이름 없음). `바로 읽기` 를 누르면 글자 없는 `Button` 클릭이 오고 곧바로 `EPubViewActivity`. → 단추 줄 왼쪽에 맞춘 위의 글자가 제목·저자, 그 왼쪽 그림이 표지.
- 앱을 다시 열 때 책장 화면이 잠깐 **모든 칸을 같은 책·0%** 로 그렸다가 고친다(다운로드 탭에서 32% 책이 0% 로 보인 적이 있음). → 화면이 멈춘 뒤에 읽는다.

### 그 밖의 앱 (2026-10-05, 와이파이 꺼진 상태)

| 앱 | 서재 화면 | 읽을 수 있는 것 | 책 칸 누름 알림 | 결과 |
|---|---|---|---|---|
| 알라딘 | `kr.co.aladin.ebook.MainActivity` | 격자 보기: 제목 `txt_title`, 표지 `img_cover`. 목록 보기: 제목 `text_title`, 저자 `text_author`(`한강 지음`), 진행률 `txt_read_percent`, 대여 `text_rent_date`. 아래 '최근 읽은 책' `reading_book_tv_book_title` | 격자 보기는 안 옴, 목록 보기는 옴(`[제목, 저자, 45%]`) | **지원**. 읽는 화면(`ReadONBookRenderActivity`, 본문 웹 화면) 메뉴의 `viewer_header_title` 로도 안다. 아래 쪽 표시 `bookrender_txt_page_onepage`(`6 / 368　 저자소개`, 처음엔 높이 0 이라 늦게 그려진다)로 진행률. 메뉴에도 `viewermenu_text_pageinfo`(`6 / 368`) |
| 북커스 `com.bookers.ebook` | `ui.purchase.PurchaseActivity`(내서재) | 제목 `tv_title`, 표지 `iv_cover`, 진행률 `tv_percent`, 대여 기한 `tv_end_date`(`14일 남음`, 다운로드 탭). 리스트 보기에 저자 `tv_author`·출판 `tv_publisher` | 옴(글자에 제목) | **지원**. 읽는 화면 `ui.viewer.epub.EpubActivity`(본문 웹 화면)는 메뉴에 제목 `tv_title`·쪽 `tv_current_page`/`tv_total_page`(1/368 을 서재는 1% 로 보여 올림). 뷰어 설정의 하단 정보를 켜면 아래 줄 `ll_page_area`(이름 없는 글자 세 조각 `8`,` / `,`371`, 메뉴가 뜨면 빈다). 읽는 화면은 화면 캡처가 막혀 있다. 홈 탭은 오프라인이라 못 봄 |
| 교보eBook | `common.b2c.ui.mainV3.activity.MainV3Activity` | 화면 요소 이름이 없다(Compose). 격자 보기는 제목도 없고, 목록 보기에서만 제목·저자 글자 | 안 옴 | **지원**: 서재는 안 읽고, 읽는 화면 메뉴의 제목·진행률로 안다 |
| 리디 | `main.activity.MainActivity` | 탭·메뉴만 보이고 책 칸은 접근성에 안 나온다 | 안 옴 | 미지원 |
| YES24 서점 | — | 연결이 없으면 알림만 띄우고 닫힌다 | — | 확인 못 함 |
| YES24 도서관 | `shelf.LibShelfActivity` | 읽고 있는 책이 없어 칸을 못 봄 | — | 확인 못 함 |

- 알라딘 `txt_title` 은 `분야`·`필터` 단추에도 쓰인다 → 표지 그림이 들어 있는 묶음만 책 칸으로 본다.
- 이벤트로 받은 요소(누른 요소)는 부모로 올라갈 수 없다 → 누른 곳의 가운데가 들어 있는 칸을 서재 화면에서 찾는다.
- 북커스 내서재는 탭마다 같은 책 칸이 화면 밖에도 있다(화면 밖 칸은 표지 크기가 0 이라 자르지 않는다).

## 런처에서 쓸 수 있는 것

- **지금 권한(사용 기록 액세스)으로 되는 것**
  - 오늘 읽은 시간·연속일·앱별 시간(사용 중)
  - 오디오북 듣기 시간 따로 세기(교보 오디오 화면)
  - 앱별 저장공간 **(미확인)**
  - (교보 완독 감지는 안 된다: 완독 안내가 뷰어 안 대화상자라 기록에 남지 않음)
- **폴더 허락 한 번으로 될 수 있는 것** **(미확인)**
  - 교보도서관에 받아 둔 책의 제목·저자·원본 표지, 마지막으로 연 책 → 지금 읽는 교보도서관 책 자동 등록
- **조건이 붙는 것**
  - 핀 바로가기로 책 추가·바로 열기(알라딘·리디·교보eBook)
  - 리디 최근 책 위젯
  - 알림 접근 권한으로 교보도서관 다운로드 알림(빌린 책 제목)·서버 푸시, 오디오북 재생 정보(제목·표지) 읽기 **(미확인)**
  - 밀리의서재·교보도서관 서재 화면 바로 열기 **(미확인)**
- **안 되는 것**
  - 어떤 책을 읽는지(사용 기록엔 화면 이름뿐)
  - 진행률·반납일·밑줄·서재 목록(화면 글자를 읽는 접근성 서비스 말고는 길이 없음)
  - 받은 책 파일
  - 교보도서관·밀리의서재의 특정 책 바로 열기
