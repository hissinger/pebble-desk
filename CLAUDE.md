# Pebble Desk — 작업 노트

크레마 페블(전자잉크 6인치, 1072×1456, 300dpi) 전용 글자 중심 런처. 패키지 `com.woody.pebbledesk`.
화면 규격·동작의 기준은 **DESIGN.md**다. 코드를 바꾸면 DESIGN.md도 같이 맞춘다.
이북 앱(교보도서관·밀리의서재 등)에서 알아낼 수 있는 것과 없는 것은 **docs/READER_APPS.md** 에 정리했다.
다른 기기(메이북 등) 호환 조사와 펌웨어 받기·풀기 방법은 **docs/DEVICES.md** 에 정리했다. 새 기기를 조사하면 거기에 더한다.
사용자 댓글로 들어온 기기 문제의 진행 상태는 **docs/ISSUES.md** 에서 따라간다(이북 앱 지원·기능 요청은 적지 않는다).
사용자가 직접 보는 문제 해결 안내는 **docs/TROUBLESHOOTING.md**(합니다체, 증상 → 해 볼 것. 읽는 사람은 개발자가 아니라 adb·PC 명령은 넣지 않는다). 기기 문제가 풀리거나 우회 방법이 확인되면 여기에도 사용자 말로 더한다. 메뉴 이름·판 번호가 바뀌면 같이 고친다.

## 빌드·설치

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew -q assembleRelease          # app/build/outputs/apk/release/PebbleDesk-1.5.8-release.apk
adb install -r app/build/outputs/apk/release/PebbleDesk-1.5.8-release.apk </dev/null
```

- 릴리스는 R8 축소(약 540KB, 1.5.3 은 480KB. HTML 라이브러리 없이 YES24 검색 결과를 직접 읽는다). 덮어 설치하면 앱 데이터가 남는다.
- 서명: 1.4.2 까지는 디버그 키. 1.5.0 부터는 APK 를 만드는 `packageRelease`·`packageDebug` 끝에 릴리스 키(`~/Dev/pebble-desk-keys`, 저장소 밖·백업은 iCloud `PebbleDesk-서명키`)와 교체 이력(lineage, 디버그 키 → 릴리스 키)으로 v3 서명만 다시 하고(v1·v2·v4 끔), 인증서·이력이 맞는지 `apksigner verify`·`lineage` 로 확인한다(`app/build.gradle.kts`). 그래서 1.4.2 위에 덮어 깔리고, 디버그 키로만 서명한 APK 는 그 위에 깔리지 않는다. 키 폴더가 없으면 디버그 빌드는 디버그 키 그대로, 릴리스 빌드는 실패한다(`-Ppebbledesk.debugSignedRelease=true` 면 디버그 키로). assemble·install·Android Studio 실행 모두 다시 서명된 APK 를 쓴다. 키는 저장소(공개)에 넣지 않는다.
- 앱 데이터는 **기기 보호 저장소**(`/data/user_de/0/com.woody.pebbledesk/`)에 있다(`Storage`). 홈이 잠금 해제 전에도 뜨게(directBootAware) 하려고 옮겼다.
  - DB `databases/pebble.db`(`Db`, 안드로이드 기본 SQLite, 버전 4): 읽은 시간 `reading_day`(날·앱 `pkg`·편 책 제목 `title`·붙은 책 `book_id`(모르면 "", 저장할 때 `bookFor` 로 한 번 정하고 다시 정하지 않는다)·ms. 버전 3 에서 `패키지/제목` 한 칸을 나누고 책 id 를 더함)·`reading_meta`, 편 책 `book_open`(그 책 id 도), Libby 읽는 화면이 뜨고 닫힌 때·KOReader 책을 열고 닫은 때 `viewer_mark`(버전 3), 읽고 있는 책 `shelf`, 나간 책 `past`, 완독 `finish`(한 번에 한 줄·줄 id 와 기기를 옮겨도 그대로인 `uid`, 같은 날 두 번도(순서는 누른 때 `at`). 회차 번호는 저장하지 않고 순서로 센다. 그 회차를 다시 읽기한 날 `started`·별점·감상 `stars`·`text`. 버전 3), 진행 중인 마지막 회차의 별점·감상 `review`(버전 2), 다시 읽기한 날 `round_start`(버전 3), 파일로만 여는 이북 앱(네오리더)에서 연 책 파일 경로 `book_file`(책 id → 경로, 버전 4). 기록은 지우지 않는다. 릴리스된 DB 는 1.3.0~1.4.2 가 버전 1, 1.5.0~1.5.3 이 버전 2, 1.5.4 가 버전 3, 1.5.5~1.5.8 이 버전 4(그 전은 JSON). 버전을 올리면 이 모든 판에서 올라오는 경로(`onUpgrade`)와 새 설치(`onCreate`)를 단위 테스트(`./gradlew testDebugUnitTest`, `app/src/test`: 회차 계산·마이그레이션·JSON 옮기기·저장과 캐시, Robolectric)와 에뮬에서 시험한다. 낮은 버전으로 되돌리기는 지원하지 않는다(판 낮춘 시험 빌드는 깨끗한 에뮬에서).
  - 파일: 설정 `shared_prefs/home.xml`, 표지 `files/books/<id>.jpg`(줄인 표지 `<id>_<폭>t<대비>[c].png`, 컬러면 c. 시각은 줄일 때의 원본 시각), 앱 목록 캐시 `files/apps.json`, 아이콘 캐시 `files/icons/<앱>@<px><g|c>@<업데이트한 때>.png`.
  - 1.2.1 까지의 JSON(`reading.json`·`books/books.json`)은 이 실행에서 처음 DB를 열 때 옮기고 `.migrated`로 이름을 바꿔 둔다(읽지 못하면 `.bad`). DB 확인: `adb exec-out run-as … cat …/databases/pebble.db > x.db` 후 `sqlite3`(디버그 빌드).
- **릴리스 설명**(GitHub 릴리스 본문): 앱의 설정 › 업데이트 창과 홈의 새 판 창에 **18줄까지** 보이고 넘으면 끝이 `…` 로 잘린다(`UpdateSheets.NOTES_LINES`). 크레마 페블에서 한 줄은 한국어 약 28자, 절 제목은 한 줄, 두 번째 절부터 위에 반 줄이 더 든다. 그래서 `## 새로 생긴 것` / `## 고친 것` 두 절, 항목(`- `)은 한두 줄짜리로 모두 합쳐 18줄 안에 쓴다(자세한 설명은 README·문서로). `**`·`` ` `` 는 앱에서 지워지니 써도 된다. 태그는 `v<판>`, 자산은 `PebbleDesk-<판>-release.apk`(APK 의 versionName 과 같아야 앱이 받는다).
- 릴리스는 `run-as` 가 안 된다. 앱 데이터를 직접 고쳐야 하면 같은 키로 서명된 디버그 빌드를 잠깐 깔아 `run-as` 로 넣고 다시 릴리스를 깐다.
- 성능은 꼭 **릴리스 빌드**로 잰다(디버그는 몇 배 느리다).

## 기기 작업 요령

- adb 를 부르는 스크립트는 **bash 파일**로 쓴다(zsh 는 변수를 나누지 않고 `path` 가 특수 변수다). adb 명령에는 `</dev/null` 을 붙인다(heredoc 입력을 먹는다).
- 화면이 꺼져 있으면 `input keyevent KEYCODE_WAKEUP` 먼저. `am start -W` 는 화면이 꺼져 있으면 멈출 수 있어 `perl -e 'alarm 90; exec @ARGV' adb ...` 로 시간 제한을 둔다.
- 기기가 잠깐 offline 이 되면 기다렸다 다시 한다.
- my YES 는 USB 디버깅이 켜져 있으면 닫힌다. 크레마는 USB 디버깅을 끄면 무선 디버깅도 멈춰(설정에는 켜짐으로 보여도) adb 로 시험할 수 없다 → 디버그 빌드를 깔고 사용자가 디버깅을 끈 채 눌러 본 뒤, 다시 켜고 DB 로 확인한다. Mac 의 adb 서버가 `No route to host` 로 무선 기기에 못 붙으면 터미널에서 서버를 다시 띄운다(로컬 네트워크 권한).
- 글자로 누르기: `uiautomator dump` 후 `text="..."` 의 bounds 가운데를 탭(이중 공백은 하나로 접힌다). 이미지 뷰는 dump 에 안 나온다.
- `uiautomator dump`·`events` 를 돌리면 접근성 서비스가 새로 만들어져(메모리의 지금 화면·누른 칸이 지워진다) 읽는 책 자동 추가를 시험할 때는 쓰지 않는다. 화면 캡처와 좌표로 누르고, 결과는 DB(디버그 빌드 `run-as`)로 본다.
- **사용자가 기기를 쓰는 중일 수 있다.** 자동 조작은 필요한 만큼만, 와이파이·블루투스 등 기기 설정은 건드리지 않는다. 다른 앱 화면에서 길게 누르기·탭이 엉뚱한 곳(다른 이북 앱, Play 스토어)을 누른 적이 있다.
- **`am force-stop com.woody.pebbledesk` 를 하지 않는다**: 접근성 서비스(읽는 책 자동 추가)가 시스템 설정에서 꺼진다. 다시 켜는 것은 사용자가 한다(adb 로 켜지 않는다). 덮어 설치는 괜찮다.
- 캡처: `adb exec-out screencap -p > x.png`. 시안·비교 이미지는 `design-review/`(git 제외)에 둔다.

## 기기 특성(크레마 페블)

- 1dp = 1.875px(300dpi, 572×777dp). 모든 dp 값은 이 폭 기준이고, 다른 기기는 짧은 변이 572dp 가 되게 앱 밀도를 다시 정한다(`Ui.designDensityDpi`. 메이북 E6 는 320 → 300). 패널 맨 아래 약 8px 는 테두리에 가려 보이지 않는다 → `Crema.reserveHiddenBottom()` 으로 모든 화면·아래 메뉴의 그쪽을 비운다(가로로 돌리면 왼쪽·오른쪽, 회전 방향을 본다. 실기기 미확인).
- 연한 회색을 실제보다 어둡게 그린다(#e6 → 약 #c1). 아주 연한 회색은 디자인 값보다 밝게 잡는다(링 바탕 #f2).
  - 원인은 크레마 **화면 대비**(`Settings.System hq_contrast`, 기본 56): 앱 화면 전체를 밝기^(1/s) 로 어둡게 그리고(s = 1 − 대비×90/8000 = 0.37, SystemUI `EinkSettingsProvider`) ImageView 그림만 같은 만큼 다시 밝힌다(bleach).
  - 그래서 직접 그리는 표지는 `Crema.undoContrast` 로 미리 밝힌다(`Crema.contrastTone`). 검색 결과처럼 ImageView 에 넣는 그림은 밝히지 않는다(두 번 밝아짐).
- 홈은 안드로이드 상단바를 숨기고 자체 상단(로고·시계·상태 줄)을 그린다. 상태 줄을 누르면 크레마 빠른 설정(`com.epd.drop_down` 브로드캐스트).
- 크레마가 고친 안드로이드는 앱이 바뀔 때마다 모든 `ImageView` 와 배경 있는 뷰 묶음에 명암(bleach) 필터를 다시 걸고 다시 그린다. 그래서 그림은 `PictureView`(직접 그림)로 그리고, 홈의 흰 바탕은 창 배경 대신 직접 칠한다.
- 화면 갱신(깜빡임)의 실제 횟수는 커널 로그 `ebc-dev: frame start` 로 센다. 그리기 횟수와 다르다(갱신 중 들어온 그리기는 합쳐진다).
- 부팅: 홈이 directBootAware 라 시스템 임시 홈(FallbackHome) 없이 부팅 화면 직후 바로 뜬다. 잠금 해제 전에는 앱 목록·아이콘을 `MATCH_DIRECT_BOOT_AWARE|UNAWARE` 로 찾고, 지워진 앱 정리와 읽은 시간 계산은 잠금이 풀린 뒤에 한다. SD 카드가 꽂혀 있으면 부팅 몇 초 뒤 시스템 알림 때문에 한 번 더 깜빡인다(시스템 동작).
- 잔상 제거 횟수는 `persist.vendor.fullmode_cnt`(리플렉션 SystemProperties). 로고는 크레마 런처(`com.wetao.cremalauncher`)의 리소스를 불러온다(상표 이미지를 앱에 넣지 않음).

## 코드 구조 (`app/src/main/java/com/woody/pebbledesk/`)

| 파일 | 역할 |
|---|---|
| HomeActivity | 홈: 상단 · 책 자리(스켈레톤/한 권/여러 권) · 자주 쓰는 앱(고정 3줄) · 하단 줄(모든 앱 · 오늘 읽은 시간 · 설정) |
| ReadingActivity | ⑫ 독서 기록: 연속일·오늘 링, 4주 링 달력·막대그래프(각각 뷰 하나로 그림, 같은 높이), 합계, 이번 주 읽은 책 |
| YearBooksActivity | ⑬ 올해 읽은 책(목록·격자, 해 고르기): 읽는 중·완독한 책(표지에 띠)·뺀 책, 완독·다시 읽기·기록에서 지우기 |
| BookRecord / BookDetail | 책 한 권의 기록(읽은 날·회차 `rounds`(읽기 시작해 완독까지, 회차마다 별점·감상), `BookRecord.all`) / 홈·올해 읽은 책이 같은 책 상세 `showBookDetail`(머리·마지막 회차 기록·마지막 회차 별점·감상·메뉴·`책 정보 고치기`, 메뉴만 상태로 갈림. 회차가 둘 이상이면 기록 칸을 누르면 전체 기록 `showHistory`(세로 타임라인, 줄을 누르면 회차 메뉴 `editRound`: 완독한 날·별점·감상, 닫으면 상세로). 완독은 늘 오늘, 완독한 날은 회차 메뉴에서만 고친다) |
| Review | 회차마다 별점·감상(`Review`, 완독하지 않은 회차는 `BookShelf.setReview`, 완독한 회차는 완독한 날과 함께 `setFinish`, 완독할 때 `BookShelf.finish` 가 옮김), 별 다섯 `StarBar`(반 개, 누르기·밀기), 별점·감상 메뉴 `reviewSheet`(완독·책 빼기의 확인을 겸함), 상세의 칸 `reviewBlock` |
| Reading | `ReadingLog`: 사용 기록에서 이북 앱 **읽는 화면** 시간을 책별로 계산(편 책 `book_open`, Libby 는 읽는 화면이 보인 동안, KOReader 는 책이 열려 있던 동안 `viewer_mark` 로 자름), DB `reading_day` 저장(칸 `ReadKey`, 책을 모르는 칸은 `bookFor` 로 정하고 목록에 없던 책이 책이 되면 `adopt`), 연속일 |
| ReaderWatch | `ReaderWatchService`(접근성): 이북 앱 서재·읽는 화면에서 편 책 자동 추가, 진행률·반납일, 서재 표지 잘라 오기 |
| WatchLog / WatchLogActivity | 자동 추가 서비스가 받은 알림·한 일 기록(최근 60줄 + 앱별 알림 수) / 기기 정보 › `기록 보기 ›` 화면(쪽 없이 한 화면, 사진으로 받는다) |
| readers/ | 이북 앱마다 파일 하나(`KyoboLibrary`·`KyoboEbook`(e-ink)·`KyoboEbookMobile`(휴대폰)·`Millie`(e-ink)·`MillieMobile`(휴대폰)·`Aladin`·`Bookers`·`Ridi`·`Yes24Library`·`Yes24Ebook`·`MyYes`·`MoonReader`·`Kindle`·`Libby`·`NeoReader`·`KOReader`. 패키지만 다른 판은 같은 클래스에 패키지만 줘서 객체 하나 더: 리디 원스토어판 `RidiOneStore`, 문리더 유료판 `MoonReaderPro`, YES24 도서관 휴대폰판 `Yes24LibraryMobile`, KOReader F-Droid 판 `KOReaderFdroid`). 바탕 `ReaderSpec` 이 요소 이름으로 서재 칸·누른 칸·읽는 화면을 읽고, 다르게 읽어야 하는 앱은 그 앱 파일에서 메서드를 바꾼다(리디·휴대폰 밀리 서재, e-ink 밀리 검색 상세, 문리더 제목, 킨들 서재 칸 설명, Libby 서재·읽는 화면). 읽는 화면이 액티비티가 아니라 요소인 앱(Libby)은 `viewerHolder` 로 그 요소 이름을 준다. 읽는 화면 탭마다 책이 다른 앱(네오리더)은 `viewerTabs`, 실행 화면이 없어 파일로만 여는 앱은 `fileViewer`(앱 목록·고르기에는 넣지 않고 자동 추가된 책의 앱으로만, 홈에서 그 파일을 연다. `AppStore.fileReaders`·`find`·`openBook`), 앱 데이터로 책을 아는 앱은 `fileBook`. 화면에 아무것도 드러내지 않아 저장소 파일로만 아는 앱(KOReader)은 `fileState`(지켜보지 않고 그 앱이 앞에 올 때·홈으로 돌아올 때 `fileBooks` 로 지난번 뒤에 열고 닫은 책을 한꺼번에 맞춘다, `ReaderWatchService.syncFileApps`. 열림·닫힘은 `viewer_mark` 로)·`opensBookFile`(홈에서 그 파일을 바로 연다), 파일 읽기는 `BookFiles`(모든 파일 액세스·EPUB 정보·표지)·`LuaData`(KOReader 의 Lua 값 파일). 서비스는 앱 종류로 갈라지지 않는다. 공통 도구 `Nodes`, 앱 목록 `ReaderApps`(이북 앱 패키지·읽는 화면도 여기서). 앱을 더하면 `res/xml/reader_watch.xml` 의 packageNames 와 자동 추가 안내의 앱 `SettingsActivity.GUIDE_APPS`(판이 여럿이면 이름 괄호에 판, 빠지면 단위 테스트 `GuideAppsTest` 가 알린다)도 |
| Books / BooksActivity / BookSearchActivity | 읽고 있는 책(권수 제한 없음, 홈에는 맨 앞 책 + 함께 읽는 책 3권씩 쪽으로, YES24 표지 검색. `책 추가`는 지원하지 않는 이북 앱용: 늘 새 책(id `m-`)이고 제목으로 다른 책·읽은 시간과 잇지 않는다)과 나간 책(완독·뺀 책, `BookShelf.history`), 책 목록 관리 |
| Apps | `AppStore`(앱 목록 캐시·디스크 캐시), `AppIcons`(흑백 아이콘 캐시, 디스크 캐시는 앱을 업데이트하면 다시 그림) |
| AppListActivity / FavoritesActivity / SettingsActivity / DeviceInfoActivity | 모든 앱·고르기·숨긴 앱 / 자주 쓰는 앱 관리 / 설정 / 기기 정보(사진으로 찍어 보내는 진단 화면: 속성·권한·자동 추가 서비스 기록·누른 키·시스템 화면 시험) |
| OnboardingActivity | ⑭ 시작하기: 처음 한 번, 읽는 책 자동 추가·오늘 읽은 시간을 설명하고 켜게 한다(안드로이드 13+ 제한된 설정 안내 포함) |
| PagedRows | 스크롤 없는 페이지 목록(목록·격자, fixedRows), 높이가 제각각인 묶음을 쪽으로 나누는 `PagedBlocks`(자동 추가 안내), 쪽 표시 `pageLabel`·`pagesClick`(`Paged`) |
| EinkActivity / Sheet / Ui / TopBarView / HomePrefs | 공통 화면(밀기·언어·밀도 맞춤)·아래 메뉴(돌아왔을 때 그사이 DB 가 바뀌었으면 떠 있던 메뉴를 닫는 `Sheet.dismissStale`)·규격(`PictureView`, 완독 도장을 찍는 줄 `StampedRow`, 기준 폭 572dp 포함)·상단·설정 저장 |
| Palette | 색(설정 › 화면 › 색): 흑백·컬러('색면의 서가'). 표지·아이콘 컬러 여부, 화면 묶음(`Area`)마다 색면·큰 제목 색, 완독(띠·도장)·큰 별 색. `Palette.current` 는 EinkActivity 가 정한다 |
| Device / Crema | 기기 종류(크레마·메이북·기타)와 기기마다 다른 동작(빠른 설정·조명·시스템 속성·BOOX 얼린 앱 열기) / 크레마 전용 화면 보정(대비·가려지는 아래·로고) |
| Update / UpdateSheets | 설정 › 업데이트·홈의 새 판 알림: GitHub 최신 릴리스 확인, APK 받기·확인(크기·SHA-256·서명 이력), 설치 세션·설치 창, 설치 뒤 홈 다시 띄우기 / 두 화면이 같이 쓰는 창(바뀐 점·`설치`·허용 안내·받는 중·실패, 홈의 `새 버전이 나왔습니다`) |
| Storage / Db | 기기 보호 저장소와 예전 저장소에서 옮기기 / 앱 데이터 DB(SQLite)와 예전 JSON 옮기기 |

## 지금까지 정한 것(요지, 자세한 건 DESIGN.md)

- 전자잉크 원칙: 애니메이션 없음, 스크롤 대신 페이지, 바뀐 것만 다시 그림, 그리기 전에 채워 빈 화면 한 번 그리지 않기.
- 색: 처음은 흑백. 컬러('색면의 서가')는 사용자가 고른다(기기로 판단하지 않음). 화면마다 머리에 옅은 색면 하나와 큰 제목 색, 완독은 어디서나 주홍. 본문 글자·선·작은 별은 무채색, 그래프는 책별로 나누지 않는다(DESIGN.md `색`).
- 책: 권수 제한 없음(1.5.3 까지 10권), 홈에는 맨 앞 책과 `함께 읽는 책` 3칸(3권씩 쪽으로 넘김, 그 줄을 밀면 책을, 다른 곳을 밀면 자주 쓰는 앱을). 빼는 건 사용자만(자동으로 밀어내지 않는다). 맨 앞 책이 크게(표지가 남는 높이만큼), 책이 없으면 스켈레톤(회색 표지 + `+`, 회색 막대).
- 자주 쓰는 앱: 직접 정한 순서, 높이는 목록 3줄로 고정(격자 4×2, 칸 가운데 정렬). 여러 쪽이면 제목 오른쪽 끝 `‹ 1 / 2 ›`. 이름을 따로 붙일 수 있다(`favorite_names`, 모든 앱에는 원래 이름. 빼면 지운다).
- 글자 크기·줄 간격(설정 › 화면 › 글자 · 간격, 슬라이더 10% 씩): 글자 70~150%(기기 글꼴 크기에 곱한다, `Ui.textScale`·`setSp`·`spf`), 줄 간격 80~150%(목록·메뉴·설정 줄 높이, `Ui.rowScale`·`rowDp`). 둘 다 100% 면 1.5.0 과 같은 화면. 화면에 맞춰 자동으로 줄이지 않는다(사용자가 미리 보기를 보며 고른다). 홈은 틀(상단·표지·함께 읽는 책·자주 쓰는 앱·하단 줄) 높이를 100% 기준으로 고정하고 글자만 키운다(줄 간격은 홈에 안 씀). 칸에 다 들지 않는 줄은 그리지 않는다(`FitColumn`). 기기가 앱의 밀도를 덮어썼으면(BOOX 앱별 DPI) 홈에 `화면이 크게 보입니다` 창(`다시 보지 않기`)을 띄우고 틀은 1.5.3 까지처럼 그린다.
- 바형 화면(긴 변 ÷ 짧은 변 1.5 이상, `Ui.tallScreen`. Tango·Palma 등 2.0): 홈 함께 읽는 책 표지 두 배(글자는 아래, 가운데), 독서 기록은 남는 높이가 있으면 달력과 그래프를 함께. 시험은 `Tango_A14` 에뮬레이터(824×1648, 400dpi, API 34).
- 가로 화면(1.5.4 까지는 세로 고정): 세로의 칸 모양은 그대로 두고 남는 폭에 칸을 나란히(홈 책 | 자주 쓰는 앱, 독서 기록 요약 | 달력(넓으면 그래프도), 아래 메뉴 정보 | 항목, 설정 묶음 두 칸, 격자는 표지 크기 그대로 열을 늘림). 바형도 같은 규칙. 판단은 `landscape()`(폭 > 높이), 자세한 건 DESIGN.md `가로 화면`. 에뮬은 `settings put system user_rotation 1`(자동 회전 끄고)로 돌린다.
- 하단 줄 56dp(모든 화면). 홈 가운데에 `오늘 42분 · 5일째`, 누르면 독서 기록.
- 읽는 책 자동 추가·오늘 읽은 시간은 앱에 켜짐 값을 두지 않고 시스템 값(접근성 켜짐 / **사용 기록 액세스**(`PACKAGE_USAGE_STATS`) 허용)이 곧 켜짐이다(어긋나지 않게). 어떤 책인지는 접근성 서비스가 그 앱에서 마지막으로 편 책으로 나누고(그 앱에서 편 책을 한 번도 몰랐으면 앱별. 서비스가 꺼진 동안 읽은 시간은 책 없이 하루 합계·연속일에만 들어간다). 읽는 화면은 클래스 이름 규칙(`READER` 정규식)으로 가린다. 연속일 = 5분 이상 읽은 날. 하루 목표 기본 30분(설정, 없음 / 15분~1시간 / 직접 입력 1~300분). 바꿔도 지난날은 그날의 목표로 센다.
- 기록은 처음 켤 때 7일 전까지 채우고 그날을 기록 시작일로 둔다(그 전은 '기록 없음').
- KOReader 는 화면(접근성)으로 아무것도 알 수 없어 '모든 파일 액세스'(설정 › 독서 › 책 파일 읽기)로 KOReader 가 남기는 파일을 읽는다: 어떤 책은 `koreader/history.lua` 맨 앞 줄, 열림·닫힘은 그 줄의 때와 책 파일의 접근 시각(KOReader 가 열 때만 맞춘다, 저장소는 noatime), 제목·진행률은 책 옆 `.sdr`. 권한 없는 길(koreader 폴더만 허락·플러그인·KOSync)은 조사 뒤 쓰지 않기로 했다(docs/READER_APPS.md).

## 일하는 방식(사용자 선호)

- 답은 한국어로. 짧게, 결과·확인한 것 위주.
- "시안 보여줘"면 **앱을 건드리지 말고** 그림(PIL 등)으로만 보여 준다. 정한 뒤에 구현한다.
- 디자인을 바꾸면 기기에 설치하고 캡처로 확인한다.
- 커밋은 요청할 때만. 메시지는 한국어, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 푸시는 하지 않는다.
- `/audit --fix` 를 자주 쓴다(버그 → 불필요 → 리팩토링 순, 찾은 것 바로 고침).
- 완독 도장 `drawable-nodpi/stamp_completed.png` 는 Pixabay 그림을 고친 것(`tools/stamp_completed.py`, 출처는 README 라이선스 절). 원본 파일은 저장소에 넣지 않는다(Pixabay 는 원본 그대로 따로 배포하는 것을 금지).
- 다른 앱 아이콘·책 표지가 들어간 시안 이미지는 저장소에 넣지 않는다(`design-review/`, `design-*.svg` 는 git 제외).

## 남은 일·아이디어

- 아이콘 크기 맞추기: 아이콘마다 여백이 달라 크기가 들쭉날쭉하다. 보이는 부분만 잘라 꽉 채우는 방법을 제안했고 답을 기다리는 중.
- 책 끝내기 나머지: `다음에 읽을 책` 목록. (완독 자동으로 묻기는 하지 않기로 함)
- 기록 내보내기(백업)·기기 옮기기: 추후. 앱을 지우면 기록(DB)이 모두 사라진다. 계획은 **docs/backup-plan.md**(교체·여러 기기 중 하나 정리, 동기화는 안 함).
- 책 바로 열기(조사만 함, 보류): 사용자는 주로 **교보도서관·밀리의서재**를 쓴다. 둘 다 특정 책 바로 열기는 안 된다(교보도서관은 그냥 열면 서재, 밀리는 코드가 암호화돼 확인 불가).
  - 알라딘(`aladinreader://?view=viewer&itemid=…&isShortcut=true`)·리디(실행 extra `book_id`)는 특정 책을 열 수 있다. 교보eBook은 `kyoboebookeink://mylibrary`(서재)까지.
  - 알라딘·리디·교보eBook은 "홈 화면에 바로가기 추가"를 보내므로, 런처가 핀 바로가기(`ACTION_CONFIRM_PIN_SHORTCUT`)를 받으면 제목·앱·바로 열기 링크를 얻는다. 표지는 알라딘만 쓸 만하고(리디는 아래가 잘림, 교보는 찌그러짐) YES24 검색으로 보완.
  - 받아 둔 책 파일은 다른 앱 저장소(안드로이드 11 제한)·DRM 이라 읽을 수 없다.
- 첫 설치 개선 후보: 빈 자주 쓰는 앱의 추가 동작, 크레마 기본 앱 기본 숨김, 기본 홈 앱 안내. (자동 추가·읽은 시간 켜기는 ⑭ 시작하기로 했다)
- 기기 버튼으로 쪽 넘기기: 기기마다 키가 달라(메이북 291·292, iReader Ocean·Moaan 은 음량 키) 앱의 키 처리를 모두 지웠다. 나중에 설정에서 사용자가 앞 쪽·다음 쪽 버튼을 직접 정하는 안(기본 키 없음, 시안 `design-review/pagekeys.png`)을 검토한다. 그때 화면(액티비티)도 아래 메뉴처럼 초점을 막아야 한다(터치 뒤 첫 키가 터치 모드를 벗어나는 데 쓰여 버려지고 회색 초점이 남는다, 2026-10-18 에뮬에서 확인).
