# Pebble Desk — 작업 노트

크레마 페블(전자잉크 6인치, 1072×1456, 300dpi) 전용 글자 중심 런처. 패키지 `com.woody.pebbledesk`.
화면 규격·동작의 기준은 **DESIGN.md**다. 코드를 바꾸면 DESIGN.md도 같이 맞춘다.

## 빌드·설치

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew -q assembleRelease          # app/build/outputs/apk/release/PebbleDesk-1.0.0-release.apk
adb install -r app/build/outputs/apk/release/PebbleDesk-1.0.0-release.apk </dev/null
```

- 릴리스는 R8 축소(약 377KB)이고 디버그 키로 서명한다. 덮어 설치하면 앱 데이터가 남는다.
- 릴리스는 `run-as` 가 안 된다. 앱 데이터(설정 `shared_prefs/home.xml`, `files/books/`, `files/reading.json`)를 직접 고쳐야 하면 같은 키로 서명된 디버그 빌드를 잠깐 깔아 `run-as` 로 넣고 다시 릴리스를 깐다.
- 성능은 꼭 **릴리스 빌드**로 잰다(디버그는 몇 배 느리다).

## 기기 작업 요령

- adb 를 부르는 스크립트는 **bash 파일**로 쓴다(zsh 는 변수를 나누지 않고 `path` 가 특수 변수다). adb 명령에는 `</dev/null` 을 붙인다(heredoc 입력을 먹는다).
- 화면이 꺼져 있으면 `input keyevent KEYCODE_WAKEUP` 먼저. `am start -W` 는 화면이 꺼져 있으면 멈출 수 있어 `perl -e 'alarm 90; exec @ARGV' adb ...` 로 시간 제한을 둔다.
- 기기가 잠깐 offline 이 되면 기다렸다 다시 한다.
- 글자로 누르기: `uiautomator dump` 후 `text="..."` 의 bounds 가운데를 탭(이중 공백은 하나로 접힌다). 이미지 뷰는 dump 에 안 나온다.
- **사용자가 기기를 쓰는 중일 수 있다.** 자동 조작은 필요한 만큼만, 와이파이·블루투스 등 기기 설정은 건드리지 않는다. 다른 앱 화면에서 길게 누르기·탭이 엉뚱한 곳(다른 이북 앱, Play 스토어)을 누른 적이 있다.
- 캡처: `adb exec-out screencap -p > x.png`. 시안·비교 이미지는 `design-review/`(git 제외)에 둔다.

## 기기 특성(크레마 페블)

- 1dp = 1.875px. 패널 맨 아래 약 8px 는 테두리에 가려 보이지 않는다 → `Crema.reserveHiddenBottom()` 으로 모든 화면·아래 메뉴의 아래를 비운다.
- 연한 회색을 실제보다 어둡게 그린다(#e6 → 약 #c1). 아주 연한 회색은 디자인 값보다 밝게 잡는다(링 바탕 #f2).
- 홈은 안드로이드 상단바를 숨기고 자체 상단(로고·시계·상태 줄)을 그린다. 상태 줄을 누르면 크레마 빠른 설정(`com.epd.drop_down` 브로드캐스트).
- 잔상 제거 횟수는 `persist.vendor.fullmode_cnt`(리플렉션 SystemProperties). 로고는 크레마 런처(`com.wetao.cremalauncher`)의 리소스를 불러온다(상표 이미지를 앱에 넣지 않음).

## 코드 구조 (`app/src/main/java/com/woody/pebbledesk/`)

| 파일 | 역할 |
|---|---|
| HomeActivity | 홈: 상단 · 책 자리(스켈레톤/한 권/여러 권) · 자주 쓰는 앱(고정 3줄) · 하단 줄(모든 앱 · 오늘 읽은 시간 · 설정) |
| ReadingActivity | ⑫ 독서 기록: 연속일·오늘 링, 4주 링 달력(뷰 하나로 그림), 합계, 이번 주 앱별 |
| Reading | `ReadingLog`: 사용 기록에서 이북 앱 **읽는 화면** 시간 계산, `reading.json` 저장, 연속일 |
| Books / BooksActivity / BookSearchActivity | 읽고 있는 책(최대 4권, YES24 표지 검색), 책 목록 관리 |
| Apps | `AppStore`(앱 목록 캐시·디스크 캐시), `AppIcons`(흑백 아이콘 캐시) |
| AppListActivity / FavoritesActivity / SettingsActivity | 모든 앱·고르기·숨긴 앱 / 자주 쓰는 앱 관리 / 설정 |
| PagedRows | 스크롤 없는 페이지 목록(목록·격자, fixedRows) |
| EinkActivity / Sheet / Ui / TopBarView / Crema / HomePrefs | 공통 화면·아래 메뉴·규격·상단·기기 기능·설정 저장 |

## 지금까지 정한 것(요지, 자세한 건 DESIGN.md)

- 전자잉크 원칙: 애니메이션 없음, 스크롤 대신 페이지, 바뀐 것만 다시 그림, 그리기 전에 채워 빈 화면 한 번 그리지 않기.
- 책: 최대 4권, 맨 앞 책이 크게(표지가 남는 높이만큼), 나머지는 `함께 읽는 책` 3칸. 책이 없으면 스켈레톤(회색 표지 + `+`, 회색 막대).
- 자주 쓰는 앱: 직접 정한 순서, 높이는 목록 3줄로 고정(격자 4×2, 칸 가운데 정렬). 여러 쪽이면 제목 오른쪽 끝 `1 / 2 ›`.
- 하단 줄 56dp(모든 화면). 홈 가운데에 `오늘 42분 · 5일째`, 누르면 독서 기록.
- 오늘 읽은 시간: 설정에서 켜고 **사용 기록 액세스** 권한(`PACKAGE_USAGE_STATS`)이 필요. 어떤 책인지는 알 수 없어 하루 합계·앱별로만 센다. 읽는 화면은 클래스 이름 규칙(`READER` 정규식)으로 가린다. 연속일 = 1분 이상 읽은 날. 하루 목표 기본 30분(설정, 없음/15~60분).
- 기록은 처음 켤 때 7일 전까지 채우고 그날을 기록 시작일로 둔다(그 전은 '기록 없음').

## 일하는 방식(사용자 선호)

- 답은 한국어로. 짧게, 결과·확인한 것 위주.
- "시안 보여줘"면 **앱을 건드리지 말고** 그림(PIL 등)으로만 보여 준다. 정한 뒤에 구현한다.
- 디자인을 바꾸면 기기에 설치하고 캡처로 확인한다.
- 커밋은 요청할 때만. 메시지는 한국어, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 푸시는 하지 않는다.
- `/audit --fix` 를 자주 쓴다(버그 → 불필요 → 리팩토링 순, 찾은 것 바로 고침).
- 다른 앱 아이콘·책 표지가 들어간 시안 이미지는 저장소에 넣지 않는다(`design-review/`, `design-*.svg` 는 git 제외).

## 남은 일·아이디어

- 아이콘 크기 맞추기: 아이콘마다 여백이 달라 크기가 들쭉날쭉하다. 보이는 부분만 잘라 꽉 채우는 방법을 제안했고 답을 기다리는 중.
- 독서 기록 화면 아래가 조금 빈다(4주로 줄인 뒤). 링을 키울지 검토.
- 책 끝내기: `다 읽음` → 올해 읽은 책 목록, `다음에 읽을 책` 목록. 교보 앱은 `ReadCompleteActivity`(다 읽음 화면)가 사용 기록에 남으므로 자동으로 물어볼 수 있다.
- 이북 앱에서 특정 책을 바로 여는 방법(딥링크)은 아직 조사하지 않았다. 지금은 앱만 연다.
- 첫 설치 개선 후보: 빈 자주 쓰는 앱의 추가 동작, 크레마 기본 앱 기본 숨김, 기본 홈 앱 안내.
