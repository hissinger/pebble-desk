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
| 리디 `com.initialcoms.ridi`(원스토어판 `com.ridi.books.onestore`) | O | O (리디 책 번호) | O `ridi://Library` | O | 앱 전용 저장소 |
| 교보eBook `com.kyobo.ebook.eink` | O | 기기 안 번호라 사실상 X | O `kyoboebookeink://mylibrary` | O | `Android/data` |
| 교보eBook 휴대폰 판 `com.kyobo.ebook.common.b2c` | O(크레마 판과 같은 화면 이름) | ? | ? | ? | ? |
| 밀리의서재 휴대폰 판 `kr.co.millie.millieshelf` | O | ? | ? | ? | 앱 전용 저장소 (미확인) |
| YES24 eBook `com.yes24.ebook.fourth`(휴대폰·태블릿용) | O | ? | ? | ? | 앱 전용 저장소 (미확인) |
| 문리더 `com.flyersoft.moonreader`(유료판 `com.flyersoft.moonreaderp`. 내 파일, DRM 없음) | `ActivityTxt` | O (파일 위치를 알면 `ACTION_VIEW`) | O | X | 사용자가 둔 곳(공유 저장소) |
| 킨들 `com.amazon.kindle`(휴대폰 판 하나, 한국어 없음) | O | ? | ? | ? | 앱 전용 저장소 (미확인) |
| Libby `com.overdrive.mobile.android.libby`(OverDrive, 휴대폰 판 하나) | X (액티비티 하나. 읽는 화면은 요소 `bifocalHolder`) | `https://libbyapp.com/…` 링크는 받는다 (책은 미확인) | ? | ? | 앱 전용 저장소 (미확인) |
| 네오리더 `com.onyx.kreader`(BOOX 기본 리더, 시스템 앱. 내 파일) | `ReaderTab1~4Activity`(책 하나에 탭 하나) | 파일 위치를 알면 `ACTION_VIEW` (미확인) | X (서재는 BOOX 런처 `com.onyx`) | X | 사용자가 둔 곳(공유 저장소) |
| YES24 `com.yes24.ebook.einkstore`(my YES, 시스템 앱. USB 디버깅 중에는 쓸 수 없음) | O | X (스토어 상세만) | O | X | `Android/data` |

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
- **읽는 화면 메뉴의 제목**(2026-10-16 페블에서 확인): 가운데를 누르면 위 막대 `epub_navibar`(뒤로 `epub_back_button`·책갈피 `epub_bookmark_button`) 가운데에 제목(`팩트풀니스`)이 **이름 없는** 글자로 뜬다(몇 초 뒤 닫힘). 이름으로 찾을 수 없어 위 막대 안의 글자 하나를 제목으로 읽는다(`Millie.readViewer`). 그래서 서재를 거치지 않고 다른 책으로 가도 메뉴를 한 번 띄우면 그 책으로 바뀐다. 화면 캡처는 막혀 있다(빈 그림).

## 그 밖의 앱 (사용자는 안 씀)

- **알라딘**
  - `aladinreader://?view=viewer&itemid=<알라딘 eBook ItemId>&isShortcut=true` 로 특정 책을 연다(없으면 내려받은 뒤 연다). 마지막과 같은 주소는 무시하므로 `&ts=…` 같은 값을 붙인다. 그 밖에 `view=purchase`(구매 목록), `setting` 등.
  - 읽는 화면 `epubreader.readonbook.bookrender.ReadONBookRenderActivity`(EPUB), `cpviewer.ViewerActivity`(PDF·만화 CPUB. 메뉴 `cpviewer.dialog.MenuDialogFragment` 의 제목 `viewer_header_title`, 쪽 `viewermenu_text_page_info`).
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
  - 크레마 기기면(`Build.MODEL` 에 crema) 책을 열 때마다 그 책의 서점 표지(원본, 페블에서 260KB PNG)를 `Pictures/.bookcover/bookcover.png` 에 복사한다(`ViewerRunner.setCoverImage`). 대기 화면 설정과 상관없다(페블 `enable_standby_book=2` 에서도 씀, 2026-10-10). 직전과 같은 책이면 다시 쓰지 않고, 표지 주소가 없으면 지운다. '책 표지' 모드(`enable_standby_book=1`)면 그 파일로 대기 화면을 바꾸라고 크레마 설정에 보낸다(`com.haoqing.action.SET_WALLPAPER`). → 크레마 슬립 화면 표지는 원래 YES24 서점 앱이 채우는 것이다.
  - 방송 `com.keph.crema.shine.book_cover_added`/`_removed` 는 이노웨이브 화면보호기(`com.inno.screensaver`)에만 지정해 보낸다 → 다른 앱은 받을 수 없다.
  - 앱 화면 전체가 화면 캡처를 막는다: 모든 화면의 바탕 클래스(`com.keph.crema.module.ui.view.activity.b`)가 `onCreate` 에서 늘 `FLAG_SECURE`(`setFlags(8192, 8192)`), 푸는 코드 없음. 공개된 데이터 제공자·수신기·서비스도 없다.
  - → Pebble Desk 가 MY YES 표지를 얻는 길은 위 표지 파일뿐이다. 숨김 폴더의 다른 앱 파일이라 targetSdk 30+ 앱은 '모든 파일 접근'(`MANAGE_EXTERNAL_STORAGE`) 이 있어야 읽을 수 있다(Cover Pebble 은 targetSdk 29 예전 저장소 방식). 권한이 넓어 보류(2026-10-10), 표지는 '표지 다시 찾기'.

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
- 밀리 읽는 화면은 책을 불러오는 처음 2초쯤 아래 오른쪽(`tv_viewer_add_on_right`)에 `0%`, 왼쪽(장 제목)은 비워 두고 보이다가 진짜 값으로 바꾼다. 불러오기 문구(`epub_loading_message` `도서를 불러오고 있습니다.`)는 그 뒤로도 20초쯤 남는다 → 문구가 보이는 동안의 `0%` 는 버린다(2026-10-06).
- 밀리 서재 제목은 부제가 빠진다(`달러구트 꿈 백화점` ↔ 등록된 `달러구트 꿈 백화점 : 잠들어야만 입장 가능합니다`). 제목 맞추기는 부제(`:` 뒤)·공백·기호를 무시하고 전부 같으면 같은 책으로 본다(1.5.3 까지는 앞부분만 같아도 같은 책).
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
- 받지 않은 책 칸(전체도서 탭 등)은 누르면 받기만 하고 읽는 화면은 열리지 않는다(2026-10-17 사용자 제보: 받기만 했는데 자동 추가됨) → 누른 칸은 읽는 화면이 열릴 때 편 것으로 본다(`openOnViewer`).
- 검색 탭은 같은 `BookshelfActivity` 안의 **웹 화면**. 책 상세에 제목·저자(`… 지음 / … 옮김`)·표지 그림·`내서재에 담기`·`바로 읽기` 가 글자로 보인다(요소 이름 없음). `바로 읽기` 를 누르면 글자 없는 `Button` 클릭이 오고 곧바로 `EPubViewActivity`. → 단추 줄 왼쪽에 맞춘 위의 글자가 제목·저자, 그 왼쪽 그림이 표지.
- 앱을 다시 열 때 책장 화면이 잠깐 **모든 칸을 같은 책·0%** 로 그렸다가 고친다(다운로드 탭에서 32% 책이 0% 로 보인 적이 있음). → 화면이 멈춘 뒤에 읽는다.

### 그 밖의 앱 (2026-10-05, 와이파이 꺼진 상태)

| 앱 | 서재 화면 | 읽을 수 있는 것 | 책 칸 누름 알림 | 결과 |
|---|---|---|---|---|
| 알라딘 | `kr.co.aladin.ebook.MainActivity` | 격자 보기: 제목 `txt_title`, 표지 `img_cover`. 목록 보기: 제목 `text_title`, 저자 `text_author`(`한강 지음`), 진행률 `txt_read_percent`, 대여 `text_rent_date`. 아래 '최근 읽은 책' `reading_book_tv_book_title` | 격자 보기는 안 옴, 목록 보기는 옴(`[제목, 저자, 45%]`) | **지원**. 읽는 화면(`ReadONBookRenderActivity`, 본문 웹 화면) 메뉴의 `viewer_header_title` 로도 안다. 아래 쪽 표시 `bookrender_txt_page_onepage`(`6 / 368　 저자소개`, 처음엔 높이 0 이라 늦게 그려진다)로 진행률. 메뉴에도 `viewermenu_text_pageinfo`(`6 / 368`) |
| 북커스 `com.bookers.ebook` | `ui.purchase.PurchaseActivity`(내서재) | 제목 `tv_title`, 표지 `iv_cover`, 진행률 `tv_percent`, 대여 기한 `tv_end_date`(`14일 남음`, 다운로드 탭). 리스트 보기에 저자 `tv_author`·출판 `tv_publisher` | 옴(글자에 제목) | **지원**. 읽는 화면 `ui.viewer.epub.EpubActivity`(본문 웹 화면)는 메뉴에 제목 `tv_title`·쪽 `tv_current_page`/`tv_total_page`(1/368 을 서재는 1% 로 보여 올림). 뷰어 설정의 하단 정보를 켜면 아래 줄 `ll_page_area`(이름 없는 글자 세 조각 `8`,` / `,`371`, 메뉴가 뜨면 빈다). 읽는 화면은 화면 캡처가 막혀 있다. 홈 탭은 오프라인이라 못 봄 |
| 교보eBook | `common.b2c.ui.mainV3.activity.MainV3Activity` | 화면 요소 이름이 없다(Compose). 격자 보기는 제목도 없고, 목록 보기에서만 제목·저자 글자 | 안 옴 | **지원**: 서재는 안 읽고, 읽는 화면 메뉴의 제목·진행률로 안다 |
| 리디 (26.8.1~26.10.1) | `main.activity.MainActivity`(React Native) | 칸 글자(제목, `소장`)·설명(`제목, 제목, 소장`, 목록 보기는 `제목, 26.7MB, 소장, 48%`)·표지 그림(`ImageView`, 격자 327px·목록 86px 폭)은 보이지만 요소 이름이 없다(있는 것도 `libraryGnbMenus` 처럼 `패키지:id/` 없이). 칸을 누르면 작품 화면(같은 책 묶음)을 거쳐 연다. 작품 화면이 떠 있어도 아래 서재 화면이 트리에 남아 있고 둘 다 '보임'이라, 나중에 그려지는 형제(drawingOrder)로 가린다. 홈·검색 탭으로 가면 서재는 트리에서 빠진다 | 안 옴 | **지원**: 서재에서는 표지만 잘라 두고, 읽는 화면 `reader.epub.EPubReaderActivity`·`pagebased.comic.ComicBookReaderActivity` 메뉴의 제목(26.10.1 은 머리 아래 줄 `reader_title_bar`, 머리의 `title` 은 비어 있다. 26.9.1 까지는 `title`)·쪽 `reader_toolbar_page_text`(`14 / 626`). 뷰어 설정 '하단 좌/우 정보 표시'(챕터 제목·페이지 번호·독서 진행률·시계)를 켜면 `reader_left_info`·`reader_right_info`(`15 / 640`, `2%`, 처음엔 `독서 진행률 계산 중입니다...`, 전체 쪽은 계산이 끝날 때까지 바뀐다) |
| my YES(YES24 서점) 1.5.99 | `com.keph.crema.lunar.ui.MainActivity` | USB 디버깅이 켜져 있으면 "보안정책에 따라 USB 개발자모드 진입 시에는 어플리케이션 사용이 중지됩니다." 알림을 띄우고 닫힌다(코드에 `adb_enabled`) | 옴(구매목록 칸은 `[제목, 구매일, …, 저자, …, 열기]`, `열기` 단추는 누른 요소가 비어 오기도 함). 누른 요소의 창 번호가 뒤 창(첫 화면)의 번호로 온다 | **지원**(디버깅을 끈 채 화면 구조를 기기 안에 기록해 만듦): 첫 화면 기본책장 `textView_title`·`grid_book_cover`·`textView_author`(`<김호연> 저`), 구매목록 창 `tv_buylist_title`·`aiv_buylist_thumbnail`(96×124)·`tv_buylist_author`. 읽는 화면 `ui.viewer.epub.CremaEPUBActivity`: 아래 `bottom_page_no`(`28%`, 처음엔 `페이지 계산중 - 40%`), 메뉴에 `tv_book_title`·`tv_book_author`·`text_space_page`. **앱 화면 전체가 화면 캡처를 막아(검게 찍힘) 표지는 못 가져온다** |
| YES24 전자도서관 1.0.27 | `com.yes24.library.shelf.LibShelfActivity`(내 서재) | 격자·목록 보기 같은 이름: 제목 `tv_title`, 표지 `iv_cover`, 저자 `tv_author`(`<정지원,염선형 저>`, 목록은 `/ 미래의창` 이 붙음), 도서관 `tv_lib_name`, 반납 `tv_d_day`(`D-15`), 목록 보기에 `tv_end_date`(`(2026.10.20 까지)`), 진행 막대 `pb_read_percent`(글자 없음, 읽는 화면이 8% 인데 0 이라 안 씀). 개발자 모드에서도 쓸 수 있다 | 옴(`[고양시도서관센터, 제목, <저자>]`, 누른 요소는 없음) | **지원**. 읽는 화면 `com.yes24.ebook.fourth.ui.viewer.epub.EpubActivity`(본문 웹 화면, 화면 캡처 막힘): 아래 `tv_percent`(`8%`), 뷰어 설정의 하단 정보를 켜면 그 대신 아래 줄에 제목(이름 없음)·`ll_page_area`(`14`,`/`,`283`)·`tv_toc_title`(장 제목). 메뉴에 `tv_title`. 도서관 화면 책 상세(`lib.detail.LibDetailActivity`)의 `도서 읽기`로도 연다 |

- 알라딘 `txt_title` 은 `분야`·`필터` 단추에도 쓰인다 → 표지 그림이 들어 있는 묶음만 책 칸으로 본다.
- 알라딘 서재 아래 '최근 읽은 책' 줄은 읽는 화면에서 돌아온 직후에는 앞서 읽은 책을 보이다가 조금 뒤 바뀐다(격자 칸 순서는 바로 바뀐다, 2026-10-10 페블) → 읽는 화면 메뉴로 이미 안 책이면 그 줄을 보지 않는다.
- 북커스 격자 보기 칸에는 저자가 없고, 누르면 `[제목, 9일 남음, 1%]` 가 온다(2026-10-10) → 두 번째 글자가 반납 문구면 저자가 아니라 반납일.
- 이벤트로 받은 요소(누른 요소)는 부모로 올라갈 수 없다 → 누른 곳의 가운데가 들어 있는 칸을 서재 화면에서 찾는다.
- 북커스 내서재는 탭마다 같은 책 칸이 화면 밖에도 있다(화면 밖 칸은 표지 크기가 0 이라 자르지 않는다).
- 리디 원스토어판 `com.ridi.books.onestore`(원스토어 표시 이름 `리디 완전판`, 2026-10 기준 버전 코드 `2026100663`(AAB 배포). 원스토어 앱 페이지의 `packageNm`): **지원(코드만, 미확인)**. Ocean 5 Pro 사용자가 원스토어판으로는 자동 추가·기록이 안 되고 Aura 스토어판(Play 와 같은 `com.initialcoms.ridi`)으로 바꿔 됐다고 했다(2026-10-14 댓글) → 패키지만 달라 받지 못한 것. 화면·요소 이름은 Play 판과 같은 코드라 보고 같은 읽기를 쓴다(`RidiSpec`). 요소 이름 앞은 `com.ridi.books.onestore:id/` 가 될 것 **(추정)**.

### 문리더(Moon+ Reader) `com.flyersoft.moonreader` (2026-10-10, 갤럭시 A23 안드로이드 14, 10.7 무료판, **지원**)

내 파일(EPUB·PDF·TXT 등)을 여는 일반 리더라 DRM 이 없다. 시험용 EPUB(제목·저자·표지 넣어 만든 것)으로 확인했다.

- **화면**: 서재 `com.flyersoft.moonreader.ActivityMain`(홈 탭·최근 목록·책장이 모두 이 화면 안), 읽는 화면 `com.flyersoft.moonreader.ActivityTxt`. 목차·설정은 대화상자(`PrefChapters` 등)라 사용 기록에 따로 남지 않는다. 무료판은 읽는 화면에서 나올 때 전면 광고 `com.google.android.gms.ads.AdActivity`(같은 패키지)가 뜬다. 읽은 시간에서는 빠진다.
  - 목차(`PrefChapters`) 등 대화상자도 이름이 `com.flyersoft.moonreader.` 로 시작해 화면 바뀜 알림이 온다. 닫힐 때는 알림이 다시 오지 않으므로 액티비티만 화면으로 본다.
- **읽는 화면**: 본문은 직접 그리는 뷰(`txtTextView01`)라 글자가 접근성에 드러나지 않는다. 아래 상태 줄에 `statusMiddle`(장 이름 `2장 시험 (5/8)`, 괄호 안은 장 안의 쪽)·`statusRight`(진행률)가 이름으로 늘 보인다.
  - `statusRight` 는 기본 `30.9%`(소수). 설정에 따라 `12/300`(전체 쪽), PDF 는 `쪽/전체`, 앞에 `…`·`◐ ` 가 붙기도 한다.
  - `30.9%` 는 30 으로 읽는다(서재 칸도 `30%`).
  - 가운데를 눌러 메뉴를 띄우면 `txtTitle` = `페블 시험 책 : 문리더 편 - 홍길동`(제목 ` - ` 저자), `txtTextViewPercent` = `0%`(정수). 메뉴 위쪽은 메뉴를 띄울 때만 트리에 생긴다.
- **서재**
  - 최근 목록·목록 보기 칸: 제목 `myBookName`, 저자 `myBookAuthor`, 진행률 `progresTv`(`30%`), 표지 `myBookImage`, 읽은 시간 `description`(`0.01시간, 500단어/분`).
  - 칸을 누르면 `TYPE_VIEW_CLICKED` 가 온다. 글자는 `[제목, 저자, 30%, 0.01시간, 500단어/분]`.
  - 홈 탭의 '최근 목록' 줄은 표지 그림(`myBookImage`)만 있다. 누르면 빈 클릭(`Text: []`)이 오므로, 이 경우는 읽는 화면 메뉴의 제목으로 안다.
- **특정 책 바로 열기**: 된다. `ACTION_VIEW` + 파일(`file://`·`content://`, EPUB 등 MIME)을 `ActivityMain` 으로 보내면 곧바로 `ActivityTxt` 로 연다. 다만 런처가 그 책의 파일 위치를 알아야 한다. 접근성으로는 제목만 보이고, 서재의 파일 위치는 앱 전용 DB 에 있다.
  - 공개 방송 `com.flyersoft.moonreader.open_broadcast`(extra `bookFile`=경로)로도 열 수 있다. 문리더가 이미 떠 있을 때만 된다.
- **저장소**: 표지 캐시가 `/sdcard/Books/.MoonReader/<파일 이름>_1.png` 에 남는다. DB·진행률은 앱 전용 저장소에 있다. `BookViewProvider` 는 공개되지 않는다.
- 책장의 묶음 칸은 진행률 줄(`progressLay`)을 감춘다 → 진행률이 없는 칸은 책으로 보지 않는다.
- **유료판** `com.flyersoft.moonreaderp`(Moon+ Reader Pro, Play ₩19,000·Play Pass, 10.7): **지원**(2026-10-16 에뮬 MeebookE6_A11 에서 확인). 같은 코드로 만든 앱이라 무료판 읽기를 그대로 쓰되(`MoonReaderSpec`), 화면 클래스는 그 패키지 아래 `com.flyersoft.moonreaderp.ActivityMain`·`com.flyersoft.moonreaderp.ActivityTxt` 다(1.5.3 은 무료판 이름으로 맞춰 봐 서재·읽는 화면을 알아보지 못해 자동 추가·읽은 시간이 동작하지 않았다). 그래서 화면 이름을 패키지 이름으로 만든다. 유료판은 광고 화면이 없다.

### 밀리의서재 휴대폰 판 `kr.co.millie.millieshelf` (2026-10-10, 갤럭시 A23 안드로이드 14, 5.47.0.0, **지원**)

크레마의 `kr.co.millie.eink` 와 다른 앱이다(코드도 화면 이름도 다르다).

- **화면**: 첫 화면 `kotlin.ui.MainTabActivity`(아래 탭: 투데이·웹툰웹소·검색·피드·내서재). 읽는 화면 `kotlin.ui.viewer.millieviewer.MillieViewerActivity`, 그 밖에 `StoryViewerActivity`·`webtoon.ui.WebtoonViewerActivity`·`pdf.ui.PdfViewerActivity`·`viewer.pdf.ui.PDFWebViewActivity`, 챗북 `legacy.ui.chatbook.ChatBookActivity`, 오디오 `StoryAudioViewerActivity`·`MillieViewerMp3Activity`. '최근 책' 위젯(`AppWidgetConfigureActivity`)이 있다.
- **내서재 탭**: 통째로 웹 화면(`mWebview`). 접근성 트리는 처음 한 번 요청받은 뒤에야 채워진다.
  - 도서 줄의 책마다 단추 둘: 표지 `팩트풀니스/한스 로슬링, 올라 로슬링, 안나 로슬링 뢴룬드 지음 / 이창신 옮김 썸네일`, 제목 `팩트풀니스 한스 로슬링, …`. 오디오북은 앞에 `오디오북 리더 오디오북 리더 완독 완독` 같은 표시가 붙는다. 진행률은 없다.
  - 책장 줄은 `도서 섬네일 빌려쓰는 책장_영성 9 권`(설명).
  - 책을 누르면 `TYPE_VIEW_CLICKED`(`Button`) 의 글자는 비어 있다. 누른 요소의 글자로 안다.
  - 표지를 누르면 (받지 않은 책도) 곧바로 읽는 화면이 열린다. 제목 단추를 누르거나 그 오른쪽 `⋮`(별도 요소가 아니라 제목 단추로 온다)를 누르면 책 메뉴(책 정보·독서 기록·글쓰기·독서 상태·`다운로드`/`다운로드 완료`·책장에 담기…)가 아래에서 뜬다. `다운로드` 는 다운로드 화면(`다운로드 시작`)으로 받기만 한다(2026-10-17, 5.47.0.1) → 표지 단추만 책 칸으로 보고, 읽는 화면이 열릴 때 편 것으로 본다.
  - 아래에 '이어 읽기' 막대(`compose_player_container`, 표지·제목)가 떠서 도서 줄 아래를 가린다.
- **읽는 화면**: 화면 캡처가 막혀 있다. 열 때 '○○일 ○○시에 읽던 페이지가 있습니다' 안내 창(`common.dialog.DialogCommon`, 제목 `tv_title`=`안내`, `btn_two_cancel`·`btn_two_confirm`)이 뜨기도 한다(다른 기기에서 읽던 자리. 취소하면 앞쪽 자리가 저장될 수 있다).
  - 가운데를 눌러 메뉴: 위 막대 `viewer_top_bar` 에 제목 `tv_title`·`tv_content`, 아래에 쪽 `tv_current_page`/`tv_total_page`(`2`/`1518`), 종이책 쪽 `tv_paper_page`, 진행률 `tv_per`(`0%`). 몇 초 뒤 저절로 닫힌다.
  - 아래 줄 `tv_viewer_add_on_left`/`tv_viewer_add_on_right_parent`(e-ink 판과 같은 이름)는 기본으로 비어 있다.
- **시험 때 주의**: 실제로 읽는 책을 휴대폰에서 열면 그 자리가 서버에 저장돼 다른 기기(페블)의 위치가 바뀔 수 있다.

### YES24 eBook `com.yes24.ebook.fourth` (2026-10-10, 갤럭시 A23 안드로이드 14, 3.4.43, **지원**)

휴대폰·태블릿용 YES24 eBook. 크레마에 깔린 my YES(`com.yes24.ebook.einkstore`)와 다른 앱이고, YES24 도서관·북커스와 같은 회사 코드다(읽는 화면 `ui.viewer.epub.EpubActivity` 는 YES24 도서관이 쓰는 그 뷰어). USB 디버깅이 켜져 있어도 쓸 수 있다.

- **화면**: 내서재 `ui.purchase.PurchaseActivity`(읽고 있는 책·책장·구매한 책 탭), 읽는 화면 `ui.viewer.epub.EpubActivity`·`ui.viewer.pdf.PDFActivity`·`ui.viewer.comic.ComicActivity`(듣기 `ui.viewer.audio.AudioActivity`). 처음 책을 열면 사용법 화면 `ui.tutorial.TutorialActivity` 이 먼저 뜬다.
- **서재 칸**: 제목 `tv_title`(띄어쓰기가 줄바꿈 없는 공백 U+00A0), 저자 `tv_author`, 표지 `iv_cover`, 진행률 `tv_read_percent`·`pb_read_percent`(받은 책). 받지 않은 책은 `btn_download`(`다운로드`, 묶음은 `총 13권`).
  - 칸을 누르면 `TYPE_VIEW_CLICKED` 글자 `[제목, 저자, 0%]`.
  - 칸의 진행률은 읽는 화면보다 늦게 바뀐다(읽는 화면 1% 인데 서재·누른 글자 모두 0%) → 쓰지 않는다.
- **읽는 화면**: 본문은 웹 화면(글자가 접근성에 드러난다 → 훑지 않는다). 화면 캡처가 막혀 있다.
  - 뷰어 화면설정 › `하단 정보 표시` 기본이 `모두 표시 안함`. 고르면 아래 줄 `bottomInfoView` 안에 이름 없는 제목, `ll_page_area`(안의 이름 없는 글자 `1%`, 페이지를 고르면 쪽), 장 제목 `tv_toc_title`. 선택지: 모두 표시 안함 / 페이지만 / 독서진행률만 / 시간만 / 책 제목·페이지·목차 / 책 제목·페이지·시간 / 책 제목·독서진행률·목차 / 책 제목·독서진행률·시간.
  - 가운데를 눌러 메뉴: 제목 `tv_title`, 쪽 `ll_menu_page_area`(`1 / 519`), 단추 `듣기`·`목차`·`독서노트`·`보기설정`·`화면설정`.

## 런처에서 쓸 수 있는 것

- **지금 권한(사용 기록 액세스)으로 되는 것**
  - 오늘 읽은 시간·연속일·앱별 시간(사용 중). 접근성 서비스가 편 책을 알면 책별 시간(사용 중)
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

### 교보eBook 휴대폰 판 `com.kyobo.ebook.common.b2c` (2026-10-11, 갤럭시 A23 안드로이드 14, 3.9.14, **지원**)

크레마의 `com.kyobo.ebook.eink` 와 다른 앱이지만 같은 공통 코드다. 화면 이름·요소 이름이 크레마 판과 같다.

- **서재**: `common.b2c.ui.mainV3.activity.MainV3Activity`(Compose, 탭 기본책장·내 책장·다운로드). 요소 이름이 없다. 격자 보기 칸은 표지·`소장` 뿐 제목이 없고, 목록 보기 칸에 제목·저자 글자(이름 없음). 묶음(시리즈) 칸은 `9권`. 서재는 읽지 않는다.
- **읽는 화면**: `com.kyobo.ebook.common.b2c.viewer.epub.ViewerEpubMainActivity`(PDF·만화도 크레마 판과 같은 이름). 화면 캡처가 막혀 있다(빈 그림).
  - 늘 보이는 `bookIndicator` = `0% (1/1755p)`(처음엔 `- % ( - /787p)`).
  - 처음 열면 사용 안내 겹 화면(`viewer_guide_layout`)이 뜬다. 누르면 닫힌다.
  - 가운데를 눌러 메뉴를 띄우면 `viewer_top_booktitle` = `1984(한글판+영문판)`, `viewer_bottom_pagecount` = `0% (1/1755p)`.
- 책 칸을 누르면 받지 않은 책은 내려받기만 하고, 받은 책은 바로 읽는 화면을 연다.

### 킨들 `com.amazon.kindle` (2026-10-12, 에뮬레이터 `CremaPebble_A11` 안드로이드 11, 8.158.0.100, **지원**)

e-ink 판이 따로 없다(킨들 단말기는 안드로이드가 아니다). 크레마·BOOX 에서도 Play 스토어의 이 앱 하나를 쓴다(minSdk 30). 한국어가 없어 한국어 기기에서도 화면·설명이 영어다. 로그인 전에는 서재가 비어 있다(무료 고전으로 시험).

- **화면**: 첫 화면 `com.amazon.kindle.UpgradePage`(별칭) → `ThirdPartyUpgradePage` → 서재 `com.amazon.kcp.oob.MainActivity`(아래 탭 Home·Library·More, Compose). 책을 열면 `com.amazon.kindle.bookopen.content.BookOpenActivity` 를 거쳐 읽는 화면 `com.amazon.kcp.reader.StandAloneBookReaderActivity`. 그 밖의 읽는 화면 `SimplifiedStandAloneBookReaderActivity`·`StandAlonePdfReaderActivity`·`StandAloneMopReaderActivity`·`MopReaderActivity`(매니페스트).
- **서재 칸**: 설명이 `제목, 성, 이름, , 상태, 크기`. 상태는 `Book not downloaded.` 또는 `Book downloaded.Reading is 4% completed.`, 오디오북이 딸리면 앞에 `Audible companion available `.
  - 격자 보기·홈 탭 `From Your Library` 줄: 단추 `badgeable_cover`(설명) 안에 표지 `cover_image` 뿐. 표지 위에 진행률 띠 `top_right_badge`(`4%`)·받음 ✓ `bottom_right_badge`·오디오 `top_right_corner_badge` 를 그린다.
  - 목록 보기: 이름 없는 단추(설명) 안에 표지 `cover_image`, 제목 `lib_book_row_title`, 저자 `lib_book_row_author`(`BRONTË, EMILY`, 대문자), 크기 `lib_book_row_file_size`, 진행률(`4%`). 표시는 표지 밖 오른쪽.
  - 홈 탭의 추천 줄은 설명이 `제목 by 저자` 로 다르다.
  - 칸을 누르면 `TYPE_VIEW_CLICKED` 가 오는데 요소는 단추를 감싼 묶음(`FrameLayout`, 같은 자리, 글자·설명 없음)이다. 받지 않은 책은 Library 탭에서 누르면 내려받기만 하고(다시 눌러야 열린다), 홈 탭 `From Your Library` 줄에서는 받은 뒤 바로 열었다. 받은 책은 바로 연다.
  - 아래 탭 줄 왼쪽에 마지막으로 읽은 책의 '이어 읽기' 표지(이름 없는 요소 `[89,1297][179,1432]`, 탭 줄 위로 43px 솟음)가 늘 떠 있다. 누르면 그 책을 열고, 누름 알림은 오지 않는다.
- **읽는 화면**: 본문·위 제목(`WUTHERING HEIGHTS`)·아래 `Learning reading speed`·`0%` 가 모두 직접 그리는 뷰(`KRFView`)라 드러나지 않는다. 가운데를 눌러 메뉴를 띄우면 제목 `command_bar_title_bottom`(`Wuthering Heights`)·진행률 `nln_text_secondary`(`Location 1 of 4559  •  0%`, 쪽이 있는 책은 `Page 15 of 325  •  4%`)·진행 막대 `krx_location_control`. 화면 캡처는 막혀 있지 않다.
- **진행률**: 서재 칸 설명의 `Reading is 4% completed.` 는 읽는 화면을 닫고 돌아오면 바로 바뀐다.
- **특정 책 바로 열기**: 매니페스트에 `kindle://` 링크가 있다 **(미확인)**. 내 파일(PDF·EPUB)도 `ACTION_VIEW` 로 받는다.

### Libby `com.overdrive.mobile.android.libby` (2026-10-16, 갤럭시 A23 안드로이드 14 · WebView 154, 에뮬레이터 `CremaPebble_A11_Play`(안드로이드 11) · WebView 155, 옛 `CremaPebble_A11` · WebView 91, 9.5.0, **지원**)

OverDrive 의 도서관 앱. 도서관 카드로 로그인해 빌린다. 카드 없이도 도서관을 골라 검색하고 `샘플 읽기` 는 된다. 한국에서 만들 수 있는 카드: 한국문학번역원 한국문학 디지털도서관(Libby 에서 `Literature Translation Institute of Korea (LTI Korea)`, 무료, 영어 등 번역서), Queens Public Library(연 $50, 해외 거주자도 온라인 신청). 시험 계정은 LTI(사용자 가입).

- **화면**: 매니페스트에 액티비티가 `com.overdrive.mobile.android.nautilus.ui.Activity_Main` **하나뿐**이다. 모든 화면이 웹 화면이고, 서재·검색은 `clientHolder` › `fragment_webview` › `webViewHolder` 안, 책을 열면(샘플·빌린 책 모두) 그 자리에 읽는 화면 `bifocalHolder` › `fragment_webview_bifocal` 이 뜬다(닫으면 다시 `clientHolder`). 그래서 **사용 기록으로는 서재와 읽는 화면을 가를 수 없다**.
- 링크: `https://libbyapp.com/library/<도서관>`, `https://libbyapp.com/search/<도서관>/search/query-<검색어>/page-1` 를 `Activity_Main` 이 받는다(NYPL `nypl`, LTI `lti`). 그 밖에 `libby:`, `dewey-oauth:`.
- 웹 화면 요소: HTML id 가 요소 이름으로 나온다(앞에 패키지 없음. `footer-nav-shelf`, `aria-3a9f6c60` 처럼 바뀌는 것). 화면마다 숨은 글자 `현재 위치: 서가.`(검색 결과·도서관 등)가 있다. 아래 탭: 검색·도서관·메뉴·서가·태그.
- **서가(서재)**: `대출 1`·`예약 0`·`타임라인`·`공지` 줄 아래 '최근 대출 자료', `대출` 을 누르면 대출 목록. 칸은 한 목록 아래 형제로
  - 이름표 `책: Peace Under Heaven, Man-Sik Chae Kyung-Ja Chun별`(안에 제목 글자 `Peace Under Heaven`)
  - 상태 단추 `도서 상태: 대출됨, 100% 다운로드 완료`
  - 할 일 묶음: `책 열기`, `대출 관리`, `만료일: 14일`, `0%를 읽음`.
  - 표지는 요소가 없다(배경 그림). 할 일 묶음 왼쪽에 이름표 왼쪽 끝부터 할 일 묶음 왼쪽까지·할 일 묶음 높이로 그려지고, 오른쪽·아래에 회색 그림자(폭의 3% 남짓)가 붙는다.
  - 검색 결과 칸도 이름표·상태 단추(`도서 상태: 대기 목록, ~8주`)·할 일(`대출`·`예약`·`샘플 읽기`·`저장`) 모양이 같고 진행률이 없다.
  - 아래 탭 위에 '지금 읽고 있는 자료' 띠(`지금 읽고 있는 자료: 제목.`·`대출 관리`·`지금 읽고 있는 자료 삭제` 단추, 탭 줄 바로 위에서 끝난다)가 떠 있고 누르면 그 책을 다시 연다. 목록은 이 띠와 탭 줄 밑으로도 이어져, 칸의 아래가 가려진다.
  - 옛 WebView(91, 에뮬레이터)는 Libby 를 오래 쓰다 보면 웹 화면 안쪽을 통째로 내놓지 않는다(uiautomator 덤프도 `Null child`, Libby 를 다시 시작해도 그대로). 이때는 서가를 읽지 못한다.
- **읽는 화면**: 열면 처음에 메뉴가 떠 있다. 늘 있는 숨은 안내 글자 `페이지 모드를 사용 중입니다. Pride and Prejudice, Jane Austen .`(메뉴가 떠 있으면 `탐색 모드를…`).
  - 메뉴: 위 `책 닫기`(`뒤로`)·`모양`·`책에서 검색`·`책갈피`, 아래 `책 정보`(둥근 표지), `페이지 6`(단추)·`총 50 페이지`(같은 줄), 장 이름 `Chapter 1 . 목차`, 장 앞뒤(`챕터 시작 . 2 페이지로 돌아가기`. 새 WebView 에서는 설명, 옛 WebView 에서는 글자).
  - 책을 막 열면 잠깐 `1%`·`페이지 계산 중...` 이 보인다.
  - **본문**: 웹 화면이 책 본문 문단을 접근성 요소로 내놓는다(쪽들을 옆으로 이어 붙인 띠, 가로 -5364~32175. 지금 쪽만 화면 안). 그래서 읽는 화면 밖으로 나가는 묶음은 훑지 않는다.
  - 옛 WebView(91)의 웹 화면 요소에는 문서 제목(= 책 제목, 서재는 `Libby - 검색 결과`)이 글자로 있다. 새 WebView(154)는 비어 있다. 옛 WebView 는 메뉴를 몇 번 열고 닫으면 안쪽 요소를 내놓지 않는다(자식이 null, `refresh()` 도 실패).
- 시험(`CremaPebble_A11_Play`, WebView 155): 서가 › 대출 칸에서 표지(그림자 없이)·반납일(14일)을 읽고, `책 열기` → 안내 글자로 제목·저자가 새 책으로 들어가고 메뉴의 5/292쪽이 1%, 리더를 연 동안만 '오늘 1분'. 리더를 연 채 Pebble Desk 를 덮어 깔면 '닫힘'을 적었다가 Libby 의 다음 알림에서 '열림'으로 다시 적는다.
- 표지와 '지금 읽고 있는 자료' 띠: 서가 첫 화면에서 칸 아래가 띠 밑으로 들어가 있으면 칸(진행률·반납일)은 읽되 표지는 자르지 않았고, 칸을 띠 위로 올린 뒤 다시 읽으면 깨끗한 표지를 잘라 왔다. 다만 웹 화면을 밀어 올릴 때는 내용 바뀜 알림이 오지 않아(밀기 알림만 오는 듯, 받지 않는다) 그 자리에서 바로 다시 읽지는 않고, 다른 화면에 갔다 오거나 내용이 바뀔 때 읽는다.
- **영어**(Libby 메뉴 › 언어. 읽는 화면은 Libby 설정이 아니라 시스템 언어를 따른다): 서가 이름표 `Book: Peace Under Heaven, by Man-Sik Chae`, 할 일 `Open Book`·`Manage Loan`·`Due In 14 Days`·`You Have Read 2 Percent`(`%` 가 아니라 낱말), 띠 `Reading now: 제목.`. 읽는 화면 안내 글자 `You are in page mode. 제목, 저자 .`(메뉴 `You are in navigating mode. …`), 메뉴 쪽 `Page 5` · `292 pages in total`. 모두 확인했다(서가 진행률 2%·반납일 14일 뒤·저자에서 `by` 뺌, 읽는 화면 제목·1%).
- 읽는 화면의 `모양`(Aa) 설정은 글자 크기·밝기(밝게·세피아·어둡게)·책 디자인(출판사 기본·가독성 높음·학술적·페이퍼백·OpenDyslexic·맞춤: 글꼴·정렬·행간·굵기)뿐이다. 메뉴 없이 쪽·% 를 늘 보이게 하는 옵션은 없다.
- 반납일 문구(에뮬레이터 날짜를 옮겨 확인, 반납 10/30 03:1x): 10/29 23시 `만료일: 1일`/`Due In 1 Day`, 10/30 01시 `만료일: 2시간`/`Due In 2 Hours`, 03:10 `만료일: 3분`/`Due In 3 Minutes`, 04시 `지금 만료`/`Due Now`. 반납이 가까우면 칸 위 저자 자리에 `곧 만료 예정. 갱신하시겠습니까?`(`Expiring soon. Renew?`)가 뜬다. Pebble Desk 는 앞의 셋을 10/30, `지금 만료` 를 지난 것(10/29)으로 적었다.
- 진행률: 서가 칸의 `0%를 읽음`, 읽는 화면 메뉴의 쪽(전체 쪽 수는 화면 크기·글자 크기에 따라 다르다. 오만과 편견 샘플이 휴대폰 59쪽, 페블 50쪽).

### 네오리더(NeoReader) `com.onyx.kreader` (2026-10-16, BOOX Palma2 펌웨어 4.2(2026-08)의 38994, 에뮬레이터 `Poke3Dpi350_A10`(안드로이드 10), BOOX 실기기(사용자, 2026-10-17), **지원**)

BOOX 기기에 들어 있는 리더(시스템 앱, 펌웨어의 `system/app/kreader2-release`. 받기·풀기는 docs/DEVICES.md › Onyx BOOX 펌웨어). 내 파일(EPUB·PDF·DJVU·CBZ·MOBI·TXT·DOCX 등)과 BOOX 서점 책을 연다.

- **서재가 없다.** 실행 화면(LAUNCHER)도 없어 앱 목록에 나오지 않는다. 책은 BOOX 런처(`com.onyx`, 실행 화면 `com.onyx.StartupActivity` 'ONYX Launcher')의 서재나 파일 관리자에서 열어 `ui.ReaderHomeActivity`(파일·공유를 받아 탭으로 넘기고 바로 닫힌다)로 들어온다. 링크 없이 열면 아무것도 열지 않는다.
- **읽는 화면**: `com.onyx.kreader.ui.ReaderTab1Activity`~`ReaderTab4Activity`. 책 하나에 탭 하나(여러 책을 동시에 열 수 있다, 최대 4), 탭마다 다른 프로세스·작업(singleTask). 여러 탭 끄기(BOOX 설정)면 한 탭을 다시 쓴다 **(추정)**. 본문은 `SurfaceView` 라 글자가 드러나지 않는다.
- **진행률**: 아래 상태 줄(`reader_status_bar`)의 진행 칸 `page_name`. 기본 `12/300`(쪽), 설정 `진행률 % 보기` 면 `4.0%`, 쪽 이름(PDF 쪽 라벨)이 있으면 `iv(12/300)`, 새 엔진이 쪽을 세는 동안 `.`·`..`·`...`. 상태 줄 칸(시각·배터리·남은 시간·장·진행)은 사용자가 고르고 옮길 수 있다.
- **제목**: 메뉴(가운데 누르기)의 아래 진행 칸(`progress_menu_layout`)에 제목 `tv_book_title`·장 `tv_book_chapter`·쪽 `page_num`(상태 줄과 같은 글자, 막대를 끄는 동안은 끌고 있는 쪽). 제목은 `ReaderBundleHelper.getDocTitle`: BOOX 설정 `책 제목 사용`(`OnyxSystemConfig.isLibraryUseBookTitle`)이 켜져 있으면 책 정보의 제목, 아니면 파일 이름(확장자 포함), 둘 다 없으면 경로의 파일 이름. 저자는 없다.
- 책을 둘 이상 열면 메뉴 맨 위에 탭 줄(`doc_bar`, 탭마다 같은 제목 글자. 요소 이름 없음)이 나오고 상주 설정이면 읽는 화면 위에도(`reader_doc_bar`). 지금 탭은 `activated` 로만 표시돼 접근성으로는 알 수 없다.
- 그 밖의 화면: 설정 `ui.settings.MainSettingsActivity` 등, 웹 `ui.WebViewActivity`.
- **Pebble Desk**: 읽는 화면만 본다(`readers/NeoReader.kt`). 어떤 책인지는 데이터 창구 `Metadata` 로 메뉴 없이 안다(아래 '책 정보 창구', 창구가 없으면 메뉴 제목으로). 진행률은 `page_name` → `page_num` 순. 탭마다 마지막으로 안 책을 기억해(`ReaderSpec.viewerTabs`) 탭을 오가면 그 탭의 책을 맨 앞으로 하고 진행률을 그 책에 적는다. 처음 보는 탭은 '책 모름'으로 두었다가 창구(또는 메뉴)로 알면 그 탭이 열린 때부터 그 책으로 센다. 같은 탭에 다른 책을 열어도 창구의 '연 때'로 방금 연 책을 알아 바로잡는다(창구가 없으면 메뉴를 띄울 때까지 앞 책). 표지는 없다(빈 표지). 책의 앱은 `네오리더(BOOX)`(파일을 받는 `ReaderHomeActivity`)라 홈에서 누르면 그 책의 파일을 네오리더로 연다(파일을 모르면 BOOX 런처). 모든 앱·자주 쓰는 앱·읽고 있는 앱 고르기에는 보이지 않는다(자동 추가로만 책에 붙는다).
- **에뮬레이터에서 돌리기**(2026-10-16, `Poke3Dpi350_A10` 안드로이드 10. 요소 이름 확인용, 만든 APK 는 scratchpad 밖으로 내보내지 않았다):
  1. 펌웨어의 APK 는 v2 서명이 벗겨져 그대로는 깔리지 않는다 → `META-INF` 서명을 지우고 시험 키(디버그 키)로 다시 서명.
  2. 네이티브 라이브러리는 APK 밖(`system/lib64`)이다. 코드의 `System.loadLibrary` 이름에서 ELF NEEDED 를 따라 에뮬레이터에 없는 31개(`libc++_shared`·`libmmkv`·`libneo_*`·`libonyx_*`·`libopencv_java4` 등, 129MB)를 `lib/arm64-v8a` 에 무압축으로 넣는다(`extractNativeLibs=false`).
  3. 안드로이드 10 저장소 제한: `appops set --uid com.onyx.kreader LEGACY_STORAGE allow`(패키지 단위로는 안 된다) 뒤 `Download/` 의 파일을 `ACTION_VIEW`(`file://`, `ReaderHomeActivity`)로 연다. 앱 전용 폴더(`Android/data/…/files`)는 파일 제공자 경로를 되돌리지 못해 열리지 않았다.
  4. 문서를 열기 직전 **기기 검사**(`OpenWithOptionsRequest.c()`: `Build.MANUFACTURER`·`BRAND`·지문에 `onyx` 가 없으면 `ReaderException(101)` → `Failed to open the document`, 늘 켜짐)가 있어, 시험판에서만 apktool 로 `classes12.dex` 의 이 메서드가 바로 참을 돌려주게 고쳤다.
  - 그러면 EPUB 이 열린다(엔진 `NReaderPlugin`). 처음 열면 `Spacing Adjustment Notice` 창이 한 번 뜬다.
- **확인한 요소**(uiautomator): 읽는 화면 `ReaderTab1Activity` 아래 상태 줄 `tv_time`(`03:26 PM`)·`tv_battery`(`100%`)·`tvChapter`(`1장 (1/35)`, 장 안의 쪽이라 쓰지 않는다)·`page_name`(`1/70`), 본문은 `surfaceview_content`(글자 없음). 가운데를 누르면 메뉴의 진행 칸이 기본으로 떠 `tv_book_title`(`neo-test.epub`, 에뮬레이터는 `책 제목 사용` 이 꺼진 상태라 파일 이름)·`tv_book_chapter`(`1장`)·`page_num`(`1/70`). 메뉴 위 줄 단추(`Doc`·`Sync`·`Share`·`Search`·`More`)와 아래 탭(`Contents`·`Progress`·`Style`·`Refresh`·`Display`)은 이름 없는 글자.
- **자동 추가 시험**(진짜 네오리더): 탭을 처음 열면(메뉴 전) 책을 넣지 않음 → 메뉴를 띄우면 `neo-test`(확장자 뗌) 1% 로 새 책 → 메뉴 없이 쪽 6번 넘김 → 상태 줄 `7/70` 으로 10%. `reader_watch` 에 `tab_…ReaderTab1Activity = neo-test`.
- 시험(에뮬레이터 `Poke3Dpi350_A10`, 흐름만): 같은 패키지·탭 이름·요소 이름의 가짜 앱(탭 2개, 메뉴를 켜고 끔)과 가짜 `com.onyx` 런처로: 모르는 탭은 책을 넣지 않음, 메뉴 → `가짜 첫 책.epub` 이 `가짜 첫 책` 4%(12/300), 탭 2 → 둘째 책 25%(`iv(50/200)`), 탭을 오가면 그 책이 맨 앞·진행률 10%·50%, `8.0%` → 8%, 같은 탭에 다른 책(`.pdf`)을 메뉴로 → 새 책·앱 `com.onyx/.StartupActivity`.
- **책 정보 창구**(2026-10-17 확인): 네오리더가 권한 없이 공개한 데이터 창구 `content://com.onyx.content.database.ContentProvider`(`ContentDatabaseContentProvider`, exported, 권한·호출자 확인 없음)의 `Metadata` 표에 연 책마다 한 줄이 있다. 에뮬레이터에서 책을 열고 메뉴 없이 쪽만 넘긴 뒤 `content query` 로 `title=네오 시험 책`(EPUB 안의 제목. 메뉴 제목은 파일 이름 `neo-test.epub` 이었다), `authors=홍길동`, `name=neo-test.epub`, `nativeAbsolutePath=/storage/emulated/0/Download/neo-test.epub`, `lastAccess`(연 때, ms), `type=epub`, `readingStatus=1`, `coverUrl=NULL` 을 읽었다. `progress=346/10000` 은 화면의 `6/70`(8.6%)과 맞지 않아 진행률로 쓰지 않는다. 같은 창구에 `Thumbnail`(비어 있었다)·`Library`·`Annotation`·`Bookmark` 등도 있다. → 메뉴 없이 방금 연 책(가장 늦은 `lastAccess`)의 제목·저자·파일 경로를 알 수 있고, 경로로 그 책을 바로 열 수도 있다. `lastAccess` 는 책을 여는 동안 적힌다(읽는 화면이 뜬 지 약 1초 뒤). Pebble Desk 에 넣고 에뮬레이터에서 확인(2026-10-17): 메뉴 없이 연 EPUB 이 `네오 시험 책 · 홍길동` 으로 들어가고 경로가 저장됐다. 네오리더를 닫고 홈에서 누르니 `ReaderHomeActivity` 가 `openFile: /storage/emulated/0/Download/neo-test.epub` 로 그 책을 열었다. (그 뒤 고르기에서는 빼기로 해서, 깔려 있어도 고르기에 보이지 않는 것을 확인했다.) BOOX 런처는 이 창구를 쓰지 않는다(런처의 `com.onyx.system.database.ContentProvider` 는 설정 값만).
- **실기기 확인**(2026-10-17, 1.5.4, 사용자 Onyx Page 또는 Go6, 기록 사진): 파일 관리자에서 PDF 를 네오리더로 열고 메뉴를 띄웠다 닫으니 `화면 ReaderTab1Activity · 읽는 화면` → `편 책 20261013_english_phrases_go6_bw (2) · 새로 넣음`(메뉴 제목 = 파일 이름, `.pdf` 를 뗐다) → 메뉴 없이 `진행률 20%`(상태 줄). 책의 앱은 `ONYX 런처`(BOOX 런처 한국어 이름)로 들어갔고, 사용자는 홈에서 눌렀을 때 파일 관리자가 열리도록 `책 정보 고치기` 에서 앱을 BOOX `파일` 로 바꿔 썼다(실행 화면이 없는 네오리더는 고르기에 없다).
- 사용자 보고(2026-10-17, 1.5.4, Onyx Page·Go6, 위 확인 전): 파일 관리자 › 연결 프로그램 › 네오리더로 EPUB 을 읽었는데 자동 추가가 안 됐다. 메뉴를 띄웠는지는 모른다(지금은 메뉴의 제목으로만 넣는다). 직접 넣은 책의 `읽고 있는 앱 고르기` 에 네오리더가 없다(실행 화면이 없어서). 카페에 올라온 `OnyxLauncher_56372_fix.apk` 를 깔아도 같았다고 했는데, 이것은 네오리더가 아니라 **BOOX 런처 수정판**이다(패키지 `com.onyx`, 56372판, 실행 화면 `com.onyx.reader.main.ui.MainActivity`·HOME, 다른 키 `CN=qwertycvb` 로 다시 서명, 네오리더 코드 없음). 그래서 네오리더(`com.onyx.kreader`)는 그대로이고 이 APK 와 자동 추가는 상관없다. 원본 런처(펌웨어 57287)와 비교해 찾은 고친 곳: 런처는 시작할 때 네오리더의 책 정보 창구(`com.onyx.content.database.ContentProvider` 의 `Metadata`)에 바뀜 감시를 걸어 '최근 읽은 책'을 갱신하는데(`FilesChangedReceiverAction`), 원본은 창구가 없으면 여기서 죽는다. 카페판은 `resolveContentProvider` 로 먼저 보고 없으면(또는 `SecurityException`) 죽지 않고 `NeoReader를 설치한 후 이용해 주세요.` 를 띄운다(한국어 문구가 박힌 곳은 여기뿐, 런처에는 `onyx` 기기 검사가 없다). 즉 네오리더가 없는(꺼진) 기기에서 런처를 쓰려는 수정이고, 네오리더를 앱 목록에 보이게 하는 수정은 아니다. BOOX 런처는 따로 Onyx 앱 목록을 두어(57287 태블릿판 `LoadAppManageDataListAction` 의 `com.onyx.note`·`com.onyx.kreader`·`com.onyx.kime`) 실행 화면이 없는 네오리더도 보일 수 있다 **(추정)**. → 이 창구가 BOOX 의 '최근 읽은 책' 원천이라는 뜻이기도 하다. Pebble Desk 가 읽을 때도 창구가 없으면 조용히 넘어가야 한다.
- **파일 없이 띄우기**(2026-10-17, 에뮬레이터 `Poke3Dpi350_A10`, 네오리더 38994): 책을 연 뒤 홈으로 나와 `ReaderHomeActivity` 를 파일 없이 띄워도 마지막 책으로 돌아가지 않았다. 네오리더가 살아 있을 때·프로세스만 죽였을 때(`am kill`)·완전히 껐을 때(`force-stop`) 모두 같다. 액션 없이(`am start -n …`) 띄우면 `OpenFileProcessor.safelyHandleIntent` 가 `Handle intent failed` 로 바로 끝나고(코드도 액션이 없으면 그냥 돌아간다), `MAIN`·`VIEW` 액션을 붙이면 파일이 없다고 보고 안내(Toast)를 띄우려다 이 에뮬(안드로이드 10)에 없는 `Toast.Callback` 으로 죽었다(실기기 안드로이드 11+ 에서는 안내만 뜰 것 **(추정)**). 2022년 MobileRead 글(Lumi 2, https://www.mobileread.com/forums/showthread.php?p=4235854)에서는 같은 `am start -n` 으로 읽던 PDF 로 돌아갔다고 해, 그 뒤 판에서 바뀐 것으로 본다. → 네오리더를 '앱'으로 띄워 이어 읽게 할 수는 없다(하려면 Pebble Desk 가 데이터 창구의 마지막 책 파일을 열어야 한다).
- BOOX 런처 서재의 책 칸(표지·진행률)을 읽는 것은 하지 않았다(런처 전체 화면을 받게 되고 실기기로 확인할 수 없음).
