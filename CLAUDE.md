# Pebble Desk — 작업 노트

크레마 페블(전자잉크 6인치, 1072×1456, 300dpi) 전용 글자 중심 런처. 패키지 `com.woody.pebbledesk`.
화면 규격·동작의 기준은 **DESIGN.md**다. 코드를 바꾸면 DESIGN.md도 같이 맞춘다.
이북 앱(교보도서관·밀리의서재 등)에서 알아낼 수 있는 것과 없는 것은 **docs/READER_APPS.md** 에 정리했다.
다른 기기(메이북 등) 호환 조사와 펌웨어 받기·풀기 방법은 **docs/DEVICES.md** 에 정리했다. 새 기기를 조사하면 거기에 더한다.

## 빌드·설치

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew -q assembleRelease          # app/build/outputs/apk/release/PebbleDesk-1.3.1-release.apk
adb install -r app/build/outputs/apk/release/PebbleDesk-1.3.1-release.apk </dev/null
```

- 릴리스는 R8 축소(약 235KB, HTML 라이브러리 없이 YES24 검색 결과를 직접 읽는다)이고 디버그 키로 서명한다. 덮어 설치하면 앱 데이터가 남는다.
- 앱 데이터는 **기기 보호 저장소**(`/data/user_de/0/com.woody.pebbledesk/`)에 있다(`Storage`). 홈이 잠금 해제 전에도 뜨게(directBootAware) 하려고 옮겼다.
  - DB `databases/pebble.db`(`Db`, 안드로이드 기본 SQLite, 버전 1): 읽은 시간 `reading_day`·`reading_meta`, 편 책 `book_open`, 읽고 있는 책 `shelf`, 나간 책 `past`, 완독한 날 `finish`(여러 번). 기록은 지우지 않는다.
  - 파일: 설정 `shared_prefs/home.xml`, 표지 `files/books/<id>.jpg`, 앱 목록 캐시 `files/apps.json`.
  - 1.2.1 까지의 JSON(`reading.json`·`books/books.json`)은 이 실행에서 처음 DB를 열 때 옮기고 `.migrated`로 이름을 바꿔 둔다(읽지 못하면 `.bad`). DB 확인: `adb exec-out run-as … cat …/databases/pebble.db > x.db` 후 `sqlite3`(디버그 빌드).
- 릴리스는 `run-as` 가 안 된다. 앱 데이터를 직접 고쳐야 하면 같은 키로 서명된 디버그 빌드를 잠깐 깔아 `run-as` 로 넣고 다시 릴리스를 깐다.
- 성능은 꼭 **릴리스 빌드**로 잰다(디버그는 몇 배 느리다).

## 기기 작업 요령

- adb 를 부르는 스크립트는 **bash 파일**로 쓴다(zsh 는 변수를 나누지 않고 `path` 가 특수 변수다). adb 명령에는 `</dev/null` 을 붙인다(heredoc 입력을 먹는다).
- 화면이 꺼져 있으면 `input keyevent KEYCODE_WAKEUP` 먼저. `am start -W` 는 화면이 꺼져 있으면 멈출 수 있어 `perl -e 'alarm 90; exec @ARGV' adb ...` 로 시간 제한을 둔다.
- 기기가 잠깐 offline 이 되면 기다렸다 다시 한다.
- 글자로 누르기: `uiautomator dump` 후 `text="..."` 의 bounds 가운데를 탭(이중 공백은 하나로 접힌다). 이미지 뷰는 dump 에 안 나온다.
- **사용자가 기기를 쓰는 중일 수 있다.** 자동 조작은 필요한 만큼만, 와이파이·블루투스 등 기기 설정은 건드리지 않는다. 다른 앱 화면에서 길게 누르기·탭이 엉뚱한 곳(다른 이북 앱, Play 스토어)을 누른 적이 있다.
- **`am force-stop com.woody.pebbledesk` 를 하지 않는다**: 접근성 서비스(읽는 책 자동 추가)가 시스템 설정에서 꺼진다. 다시 켜는 것은 사용자가 한다(adb 로 켜지 않는다). 덮어 설치는 괜찮다.
- 캡처: `adb exec-out screencap -p > x.png`. 시안·비교 이미지는 `design-review/`(git 제외)에 둔다.

## 기기 특성(크레마 페블)

- 1dp = 1.875px(300dpi, 572×777dp). 모든 dp 값은 이 폭 기준이고, 다른 기기는 짧은 변이 572dp 가 되게 앱 밀도를 다시 정한다(`Ui.designDensityDpi`. 메이북 E6 는 320 → 300). 패널 맨 아래 약 8px 는 테두리에 가려 보이지 않는다 → `Crema.reserveHiddenBottom()` 으로 모든 화면·아래 메뉴의 아래를 비운다.
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
| BookRecord / BookDetail | 책 한 권의 기록(읽은 날·완독 회차, `BookRecord.all`) / 홈·올해 읽은 책이 같이 쓰는 책 상세 `showBookDetail`(머리·기록·메뉴·`책 정보 고치기`, 상태로만 갈림. 홈은 지금 읽는 것만, 올해 읽은 책은 완독한 회차 전체) |
| Reading | `ReadingLog`: 사용 기록에서 이북 앱 **읽는 화면** 시간을 책별로 계산(편 책 `book_open`), DB `reading_day` 저장, 연속일 |
| ReaderWatch | `ReaderWatchService`(접근성): 교보도서관·교보eBook·밀리·알라딘·북커스·리디·YES24(전자도서관·my YES) 서재·읽는 화면에서 편 책 자동 추가, 진행률·반납일, 서재 표지 잘라 오기. 앱별 화면 요소 이름은 `SPECS` |
| Books / BooksActivity / BookSearchActivity | 읽고 있는 책(최대 10권, 홈에는 4권, YES24 표지 검색)과 나간 책(완독·뺀 책, `BookShelf.history`), 책 목록 관리 |
| Apps | `AppStore`(앱 목록 캐시·디스크 캐시), `AppIcons`(흑백 아이콘 캐시) |
| AppListActivity / FavoritesActivity / SettingsActivity / DeviceInfoActivity | 모든 앱·고르기·숨긴 앱 / 자주 쓰는 앱 관리 / 설정 / 기기 정보(사진으로 찍어 보내는 진단 화면: 속성·권한·누른 키·빠른 설정 시험) |
| OnboardingActivity | ⑭ 시작하기: 처음 한 번, 읽는 책 자동 추가·오늘 읽은 시간을 설명하고 켜게 한다(안드로이드 13+ 제한된 설정 안내 포함) |
| PagedRows | 스크롤 없는 페이지 목록(목록·격자, fixedRows) |
| EinkActivity / Sheet / Ui / TopBarView / HomePrefs | 공통 화면(밀기·페이지 버튼·언어·밀도 맞춤)·아래 메뉴·규격(`PictureView`, 완독 도장을 찍는 줄 `StampedRow`, 기준 폭 572dp 포함)·상단·설정 저장 |
| Device / Crema | 기기 종류(크레마·메이북·기타)와 기기마다 다른 동작(빠른 설정·조명·시스템 속성) / 크레마 전용 화면 보정(대비·가려지는 아래·로고) |
| Storage / Db | 기기 보호 저장소와 예전 저장소에서 옮기기 / 앱 데이터 DB(SQLite)와 예전 JSON 옮기기 |

## 지금까지 정한 것(요지, 자세한 건 DESIGN.md)

- 전자잉크 원칙: 애니메이션 없음, 스크롤 대신 페이지, 바뀐 것만 다시 그림, 그리기 전에 채워 빈 화면 한 번 그리지 않기.
- 책: 최대 10권, 홈에는 앞 4권. 빼는 건 사용자만(다 차면 새 책을 넣지 않고 밀어내지 않는다). 맨 앞 책이 크게(표지가 남는 높이만큼), 다음 3권은 `함께 읽는 책` 3칸. 책이 없으면 스켈레톤(회색 표지 + `+`, 회색 막대).
- 자주 쓰는 앱: 직접 정한 순서, 높이는 목록 3줄로 고정(격자 4×2, 칸 가운데 정렬). 여러 쪽이면 제목 오른쪽 끝 `‹ 1 / 2 ›`.
- 하단 줄 56dp(모든 화면). 홈 가운데에 `오늘 42분 · 5일째`, 누르면 독서 기록.
- 읽는 책 자동 추가·오늘 읽은 시간은 앱에 켜짐 값을 두지 않고 시스템 값(접근성 켜짐 / **사용 기록 액세스**(`PACKAGE_USAGE_STATS`) 허용)이 곧 켜짐이다(어긋나지 않게). 어떤 책인지는 접근성 서비스가 그 앱에서 마지막으로 편 책으로 나누고(그 앱에서 편 책을 한 번도 몰랐으면 앱별. 서비스를 끈 동안 읽은 시간도 마지막으로 편 책으로 들어간다). 읽는 화면은 클래스 이름 규칙(`READER` 정규식)으로 가린다. 연속일 = 1분 이상 읽은 날. 하루 목표 기본 30분(설정, 없음 / 15분~1시간 / 직접 입력 1~300분). 바꿔도 지난날은 그날의 목표로 센다.
- 기록은 처음 켤 때 7일 전까지 채우고 그날을 기록 시작일로 둔다(그 전은 '기록 없음').

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
- 기록 내보내기(백업): 추후. 앱을 지우면 기록(DB)이 모두 사라진다.
- 책 바로 열기(조사만 함, 보류): 사용자는 주로 **교보도서관·밀리의서재**를 쓴다. 둘 다 특정 책 바로 열기는 안 된다(교보도서관은 그냥 열면 서재, 밀리는 코드가 암호화돼 확인 불가).
  - 알라딘(`aladinreader://?view=viewer&itemid=…&isShortcut=true`)·리디(실행 extra `book_id`)는 특정 책을 열 수 있다. 교보eBook은 `kyoboebookeink://mylibrary`(서재)까지.
  - 알라딘·리디·교보eBook은 "홈 화면에 바로가기 추가"를 보내므로, 런처가 핀 바로가기(`ACTION_CONFIRM_PIN_SHORTCUT`)를 받으면 제목·앱·바로 열기 링크를 얻는다. 표지는 알라딘만 쓸 만하고(리디는 아래가 잘림, 교보는 찌그러짐) YES24 검색으로 보완.
  - 받아 둔 책 파일은 다른 앱 저장소(안드로이드 11 제한)·DRM 이라 읽을 수 없다.
- 첫 설치 개선 후보: 빈 자주 쓰는 앱의 추가 동작, 크레마 기본 앱 기본 숨김, 기본 홈 앱 안내. (자동 추가·읽은 시간 켜기는 ⑭ 시작하기로 했다)
- 페이지 버튼이 음량 키(`VOLUME_UP`·`DOWN`)로 오는 기기(iReader Ocean): 지금은 목록이 안 넘어간다. 음량 키를 넘김으로 쓸지 정해야 한다.
