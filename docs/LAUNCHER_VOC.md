# 다른 런처의 사용자 의견(VOC)

유명 안드로이드 런처와 전자잉크·이북리더 홈에 사용자가 무엇을 칭찬하고 무엇에 불만인지 모은 기록이다(2026-10-28 조사).
세 갈래로 나눠 봤다: 일반 런처(Nova·Lawnchair·Niagara 등), 미니멀·글자 런처(Olauncher·KISS·inkOS 등), 전자잉크 기기 홈(BOOX·Kindle·Kobo 등).

**근거의 한계**
- Reddit 원문과 Play 리뷰 본문은 직접 열지 못했다. 미러·Lemmy·기사 인용·검색 요약으로만 봤다.
- 가장 단단한 근거는 GitHub 이슈다. 공감·댓글 수로 정렬해 직접 확인했다(Olauncher·KISS·mLauncher·inkOS).
- 한국 커뮤니티(네이버 카페·디시·뽐뿌)는 검색에 거의 잡히지 않았다. 크레마 기본 런처에 대한 불만은 **확인하지 못했다**. 한국어 자료는 클리앙의 Pebble Desk 소개 글 댓글 정도다.
- 직접 확인하지 못한 것은 **(미확인)** 으로 적었다. 인용은 모두 바꿔 쓴 것이다.

## 한눈에: 여러 런처에 되풀이되는 것

| 주제 | 사용자가 말하는 것 | 대표 근거 |
|---|---|---|
| 주인·개발이 바뀌면 신뢰가 무너진다 | Nova 는 인수 뒤 광고·추적기·구독·과한 권한이 붙어 10년 사용자가 떠났다. Microsoft·Action Launcher 는 개발이 멈춰 "버려졌다"는 평 | Nova, MS Launcher |
| 구독·유료 장벽 피로 | 평생 구매를 선호한다. 이미 산 라이선스를 무시하거나 유료판에도 광고가 나오면 반발이 가장 크다. 구독이 끝나면 홈이 망가져 보이는 것도 큰 불만 | Nova, Niagara, Smart Launcher, minimalist phone |
| 권한·프라이버시 | 런처가 SMS·통화 기록·위치를 달라고 하면 바로 떠난다. Exodus 추적기 개수로 비교한다 | Nova |
| 꾸민 홈을 잃는 것 | 업데이트·초기화·재설치·앱 데이터 지우기로 설정이 날아가는 것. 백업·내보내기와 다른 런처 백업 가져오기가 "갈아탈 수 있는가"를 가른다 | MS Launcher, Good Lock, Olauncher #713, KISS #1601·#1031 |
| OS·제조사 업데이트마다 깨짐 | 제스처·홈 복귀 애니메이션·위젯·알림 배지가 안드로이드 판·보안 패치마다 깨진다. 결국 기본 런처로 돌아가는 1순위 이유 | Lawnchair, Niagara, Good Lock |
| 전자잉크는 틈새 취급 | 일반 런처는 애니메이션 끄기·고대비 요청을 "너무 틈새"라며 거절하거나 반쯤만 고친다 | Olauncher #152·#675, KISS #243·#2457 |
| 이북리더 홈은 판매 페이지가 아니어야 | Kindle·Kobo 홈의 추천·스토어를 끌 수 없다는 불만이 가장 크다. 홈은 지금 읽는 책으로 가는 길이어야 한다 | Kindle, Kobo |
| 여러 앱의 책을 한 곳에 | 책이 Kindle·Kobo·도서관 앱마다 흩어져 한 서재로 모이지 않는다 | BOOX 사용자 |
| 독서 기록은 모으면서 보여 주지 않는다 | 완독일·책별 기록·달력·주간 그래프·연속일을 원한다. 자동 완독 처리는 싫다. KOReader 통계 플러그인이 기준 | Kindle, Kobo, KOReader |
| 미니멀은 너무 빈약하면 떠난다 | 날짜·배터리·일정처럼 한눈에 보는 정보가 없으면 몇 주 만에 돌아간다 | Olauncher, minimalist phone |

## 1. 일반 런처

### Nova Launcher
- 칭찬: 제스처, 폴더, 세밀한 홈 조정, 아이콘 모양. `.novabackup` 은 다른 런처들이 가져오기를 지원할 만큼 표준이 됐다. https://bgr.com/1965762/nova-launcher-abandoned-best-alternative-android-launchers
- 일어난 일:
  - 2022 분석 회사 Branch 가 샀다. 2024 감원 뒤 창업자 혼자 남았고, 2025-09 그도 떠났다. "개발자가 떠나면 코드를 공개한다"던 약속은 취소됐다. https://www.neowin.net/news/nova-launchers-future-in-doubt-after-last-remaining-dev-quits-project
  - 2026-01 Instabridge 가 인수했다. 무료판 검색·앱 서랍에 광고, 추적기 2개 → 6~8개. Prime 에서도 광고를 봤다는 보고(회사는 부인). https://9to5google.com/2026/01/20/nova-launcher-new-owners-ads/
  - 2026-02 신규 사용자는 연 $4.99 구독. AI 비서 베타가 SMS·통화 기록·정밀 위치 권한을 요구한다는 분해 결과. https://piunikaweb.com/2026/02/06/nova-launcher-rolls-out-stability-fixes-confirms-4-99-annual-subscription-for-new-users/
- 떠나는 이유: 새 기능 없이 추적기만 늘고, 권한이 과하고, 구독으로 바뀜. 옮겨 가는 곳은 Lawnchair·Smart Launcher(Nova 백업을 가져온다)와 Niagara(처음부터 다시 꾸민다). 남는 사람은 옛 판을 고정하거나 추적기를 막는다(막으면 검색이 깨진다). https://www.androidauthority.com/used-nova-launcher-10-years-done-with-it-why-3659417/

### Lawnchair
- 칭찬: 무료·오픈소스, Pixel 런처 느낌. Nova 대안 중 무료 1순위. 15 베타에서 Nova 백업 가져오기(위젯은 자리만, 설정은 빠짐). https://docs.lawnchair.app/core-features/backup-restore-and-reset
- 불만: 몇 년째 베타만 나오고 Play 최신판이 없다. 아이콘이 저절로 움직임, 알림 배지 회귀, 설정 단계가 있는 위젯이 안 들어감. 아이콘 팩이 홈에만 적용돼 그만둔 사람. https://lawnchair.app/blog/lawnchair-15-beta-1/
- 제스처: 앱 열고 닫는 애니메이션 끊김, 홈 직후 터치 무시, 아이콘 잔상. Xiaomi 등은 제스처를 막는다. 공식 문서가 원인을 안드로이드·제조사로 인정한다. https://docs.lawnchair.app/troubleshooting/gesture-navigation-issues

### Niagara Launcher
- 칭찬: 한 손 세로 목록, 목록 안에서 알림 보기·답장, 가볍고 무료판에도 광고 없음. 전자잉크·미니멀 폰에도 많이 쓴다. https://gigazine.net/gsc_news/en/20240217-android-niagara-launcher
- 불만: 연 $13.99·평생 $42.99 가 비싸다. 구독이 끝난 아침에 홈이 흉하게 바뀌어 있었다는 경험담. 위젯 사라짐·회색으로 굳음(One UI 7), 알파벳 스크롤이 예민해 위젯을 밀다 앱 목록이 열림, Nova 백업을 못 가져옴. https://help.niagaralauncher.app/article/163-commonly-reported-bugs
- 안드로이드 15 12월 패치 뒤 홈 복귀 애니메이션이 깨져 개발사가 구글 이슈 트래커로 넘겼다. https://help.niagaralauncher.app/article/174-bug-reports-on-androids-issue-tracker

### Smart Launcher 6
- 칭찬: 앱 범주 자동 분류, 배경화면 색 테마, 통합 검색, Nova 백업 가져오기, 적당한 평생 라이선스. https://docs.smartlauncher.net/faq/start-here/how-to-migrate-from-other-launchers
- 불만: 무료판 광고, 핵심 기능이 Pro. 구독이 끝났다는 오보로 프리미엄이 꺼짐(공식 FAQ). SL5 Pro 를 SL6 에서 다시 사라고 했다는 보고 **(미확인)**. https://docs.smartlauncher.net/faq/license-and-purchases/problems-and-troubleshooting

### Microsoft Launcher · Action Launcher
- Microsoft: 2024 Copilot 추가 말고 의미 있는 업데이트가 없어 "버려졌다". 2023-06 업데이트가 런처를 망가뜨렸고 해결책인 초기화가 홈을 지웠다. https://www.windowscentral.com/software-apps/as-nova-launcher-loses-its-last-dev-microsoft-launcher-feels-abandoned-for-the-first-time-in-years-i-need-a-new-launcher
- Action: 수입이 줄자 광고 붙은 자체 검색을 넣고, 일회 구매자에게 Google 검색용 연 구독을 따로 요구해 반발(2019). v50(2022)에서 유료 기능 절반을 풀었다. https://www.androidpolice.com/action-launcher-v50-freemium/

### Samsung One UI Home + Good Lock · Pixel Launcher
- Good Lock(Home Up): One UI 판이 바뀔 때마다 모듈이 꺼지거나 열리지 않고, 다시 깔면 홈 설정이 사라졌다는 보고. https://www.androidauthority.com/samsung-one-ui-8-5-good-lock-not-working-3624220
- Pixel: 최근 앱·애니메이션이 가장 매끄럽다(서드파티는 QuickStep API 를 못 받는다). 대신 아이콘 팩 불가, 검색창 못 지움, 격자 선택이 좁다. 이것이 서드파티 런처를 쓰는 가장 큰 이유다. https://www.androidauthority.com/pixel-hide-search-bar-3617699/

## 2. 미니멀·글자 런처

### Olauncher (GitHub 별 3.9k, 무료·오픈소스)
- 칭찬: 2MB 안쪽, 권한·광고 없음. 글자를 쳐야 앱이 열려 충동 실행이 준다. 앱 숨기기·이름 바꾸기. https://maketecheasier.com/olauncher-minimalist-android-launcher
- 불만: 아이콘·위젯·알림 점·아이콘 팩이 없다(리뷰어도 처음엔 세 시간 만에 그만둠). 홈으로 못 돌아옴·데이터를 지우면 설정을 다 잃음(#713), 런처가 사라짐(#436).
- **화면 사용 시간이 틀림**: Digital Wellbeing 은 55분인데 3시간 12분, 자정에 0으로 안 돌아감. 댓글 수 2위 이슈(#545·#569). 사용자는 시스템 값과 직접 비교한다. https://github.com/tanujnotes/Olauncher/issues/545
- 기능 요청: 웹사이트·웹앱을 홈에(#20, 공감 76으로 압도적 1위), 날씨, 스크롤 바, 홈 앱 8개 넘게, 배터리 표시, 새로 깐 앱 찾기·설치순 정렬(#208), 많이 연 순 정렬, 설정을 여는 길게 누르기가 너무 김(#131).
- 전자잉크: 앱 목록 페이드가 전자잉크 폰에서 부자연스럽다(#675, 개발자는 기본으로 꺼져 있다고 했지만 계속 보임). 애니메이션 끄기 요청은 처음에 "너무 틈새"로 거절(#152, Hisense A5). https://github.com/tanujnotes/Olauncher/issues/152

### KISS Launcher (검색 중심, 오픈소스)
- 칭찬: 빠르고 가볍고, 앱·연락처·웹을 한꺼번에 검색하며 자주 고른 것을 배운다. https://f-droid.org/packages/fr.neamar.kiss/
- 불만: 안드로이드 14 뒤 잦은 튕김(#2188)·Pixel 8 멈춤(#2308), 키보드에 결과가 가려짐, 위젯 추가 회귀. 관리자 한 명이라 이슈를 자동으로 닫는다(#1688).
- 기능 요청: 아이콘 직접 바꾸기(#803, 댓글 51), 자동 다크 모드, 격자 보기, 즐겨찾기 순서 바꾸기, 설정 내보내기·백업(#1601·#1031), 웹앱.
- 전자잉크: BOOX·Minimal 폰 사용자의 고대비 "E-Ink 모드" 요청(#2457, 열림). 2015년부터 입력 글자가 안 보인다·애니메이션 없음·큰 글자 요청(#243), 개발자는 기기가 없어 못 고친다고 했다. https://github.com/Neamar/KISS/issues/2457

### mLauncher (Olauncher 포크)
- 요청: 웹앱 바로가기, Niagara 같은 위젯, 물리 키보드로 바로 거르기(#474·#476), 사용자 글꼴, **날짜·배터리 글자 크기를 따로**(#498), 홈 앱 이름 옆 사용 시간(#475), 시계 옆 간단한 일정(#288). https://github.com/CodeWorksCreativeHub/mLauncher/issues
- 버그: 업데이트 뒤 제스처 망가짐, 시스템 테마 무시, 정확히 맞는 검색 결과가 뒤로 감.

### inkOS (전자잉크 전용 글자 런처, 별 519)
- 내세우는 것: 애니메이션·부드러운 스크롤 없음, 쪽 단위 목록, 최소 갱신, 흑/백/따뜻한 회색, D-pad·T9 지원, 홈 알림 표시. 홈으로 돌아올 때 전체 새로 고침(다크 모드에는 필수라고). https://inkos.gez.im/
- 이슈: HiBreak Pro 에서 대기 뒤 알림 수신이 끊김(#85, 제조사의 백그라운드 제한), **사용자 글꼴이면 쪽이 세로를 다 못 채움**(#30), **쪽 맨 아래 항목을 누를 수 없음**(#37), 키를 치면 바로 거르기 요청(#87). https://github.com/gezimos/inkOS/issues

### 그 밖
- minimalist phone: 평점 4.5 지만 월 $4.99 구독, 체험 뒤 웹사이트 가격의 두 배가 청구됐다는 불만. https://ie.trustpilot.com/review/minimalistphone.com
- Before Launcher: 알림 거르기는 칭찬, 알림음이 모두 기본음이라 오히려 폰을 더 확인하게 됨. https://www.producthunt.com/posts/before-launcher/reviews
- T-UI: 명령어를 외워야 하고 2019 뒤 사실상 방치.

### 미니멀 런처를 그만두는 이유
1. 너무 빈약함: 날짜·배터리·일정·알림 점 같은 한눈에 보는 정보가 없다.
2. 처음 적응: 그림으로 앱을 찾던 사람에게 글자 목록이 낯설다.
3. 돈: 오프라인 도구에 매기는 구독, 체험 뒤 가격, 구독이 끝나면 바뀌는 화면.
4. 불안정: 홈이 멈추거나 사라지고, 백업이 없어 설정을 잃는다.
5. 벌받는 느낌: 숨긴 앱을 결국 다시 찾아 쓴다.

## 3. 전자잉크·이북리더 홈

### 기기 기본 런처
- **BOOX**:
  - 앱 숨기기·위젯·모양 바꾸기가 없어 Unlauncher·AIO Launcher·Nova·Niagara·Olauncher 로 바꾼다. https://ryaid.pages.dev/blog/boox.html
  - 홈이 BOOX 생태계를 밀고, Kindle·Kobo·도서관 책이 앱마다 흩어진다. https://lemmy.linuxuserspace.show/post/348624
  - 펌웨어 4.0 홈 서재 위젯이 서재 정렬을 따라가, 서재를 가나다순으로 하면 위젯에도 최근 책 대신 가나다순 앞 책이 뜬다(검색 요약).
  - 다른 런처로 바꾸면: 얼린 앱이 안 보이고, 메모 앱이 기본 런처에 묶여 있고, Nova 로 옮긴 사람이 누르는 횟수가 늘어 돌아갈까 고민. https://www.mobileread.com/forums/showthread.php?p=4384257
- **Bigme·Meebook·Hisense**: Think Launcher 개발자는 Bigme 기본 런처가 너무 짜증 나서 직접 만들었다(애니메이션 없음, 이름·아이콘 보기 고르기, 고정 목록). Hisense A9 는 창 관리가 기본 런처에만 있어 바꾸면 최근 앱 버튼이 쓸모없어진다. https://blog.sarasantillan.com/hisense-a9-review-august-2022/
- **Kindle**: 2021 업데이트로 서재를 첫 화면으로 고르는 옵션이 사라졌다. 광고 없는 모델도 홈이 추천 13줄로 차 있다. 홈은 안 쓰고 서재·읽던 책으로 바로 가는 사람이 많다. https://blog.the-ebook-reader.com/2025/06/06/kindles-have-the-worst-home-screen-layout-by-far/
- **Kobo**: 2017 홈 개편 투표 찬성 46%·반대 54%. 좋은 쪽은 지금 책·최근 책에 바로 가고 표지 중심이 보기 좋다는 것, 싫은 쪽은 원치 않는 칸을 끌 수 없고 누르는 횟수가 늘고 추천이 광고 같다는 것. 표지 밑에 제목·저자와 읽은 % 를 원했다. 홈에 엉뚱한 책이 뜬다는 신고도. https://blog.the-ebook-reader.com/2017/05/30/do-you-like-the-new-home-screen-on-kobo-ereaders/

### 이북리더 홈에서 원하는 것
- 지금 읽는 책과 최근 책이 맨 위, 최근 읽은 순(다른 정렬 설정에 끌려가지 않게). 표지와 제목·저자·진행률.
- 광고·추천·스토어 없음. 칸을 사용자가 끄고 켬. 업데이트로 홈을 강제로 바꾸지 않음.
- 여러 서점 앱의 책을 한 서재로.
- 앱 숨기기와 꼭 쓰는 앱 몇 개만.
- 상단에 배터리·시계·네트워크, 조명 켜고 끄기(Katapult 는 홈을 두 번 누르면 조명 토글), 빠른 설정. https://forum.mudita.com/t/katapult-launcher-simple-grid-launcher/13951
- 홈에서 책 파일을 바로 추가·열기, 채리뷰어 지원(클리앙 Pebble Desk 글 댓글). https://www.clien.net/service/board/pds/19280760

### 일반 런처를 전자잉크에서 쓸 때
- 애니메이션·부드러운 스크롤이 잔상과 느린 반응을 부른다. 전자잉크용으로 고친 런처(KISS-eink·Katapult·Think Launcher·inkOS)는 모두 애니메이션을 빼고 쪽 넘기기로 바꿨다. https://forum.mudita.com/t/kiss-launcher-fork-for-eink-mudita-kompakt/12012
- 잔상: 홈으로 돌아올 때 전체 새로 고침(inkOS·Katapult). Palma 사용자는 앱을 바꾼 뒤 수동 새로 고침 버튼을 쓴다.
- 아이콘: 컬러·얇은 외곽선 아이콘은 회색조에서 알아보기 어렵다. KISS-eink 는 Arcticons Black 팩을 자동으로 쓰고, Katapult 는 아이콘 모양·테두리를 강제한다.
- 기본 홈으로 정해지지 않거나 기본 런처를 끄려면 ADB 가 필요한 기기가 있다.

### 독서 통계·목표·연속일
- 기기가 데이터는 모으면서 보여 주는 것이 적다는 비판. Kindle 은 완독 날짜를 기기에 보여 주지 않고, Kobo 통계는 시간·속도뿐이며 책마다 들어가야 보이고 부정확하다. https://blog.the-ebook-reader.com/2025/05/19/ebook-readers-lack-useful-reading-history-and-reading-stats/
- 원하는 것: 완독일·마지막 읽은 날, 책별 기록, 주간 막대그래프·월 달력, 완독 직접 표시. **마지막 쪽에 닿았다고 자동 완독 처리하는 것은 싫다.**
- KOReader 통계 플러그인 칭찬: 달력, 책별 시간, 책을 지워도 기록이 남음(Kobo 는 사라짐), 기기 간 동기화. 웹 대시보드(KoInsight)까지 나왔다. https://www.mobileread.com/forums/showthread.php?p=4116713
- Kindle 의 일·주 연속일과 "목표까지 X분" 이 동기 부여가 된다는 평. https://goodereader.com/blog/kindle/kindle-reading-insights-is-an-excellent-system-that-monitors-reading-habits

## 4. Pebble Desk 에 주는 뜻

### 이미 맞는 방향 (VOC 가 뒷받침)
| VOC | Pebble Desk |
|---|---|
| 애니메이션 없음, 스크롤 대신 쪽 | 전자잉크 원칙(DESIGN.md) |
| 홈은 지금 읽는 책으로, 광고·추천 없음 | 맨 앞 책 + 함께 읽는 책, 스토어·추천 없음 |
| 여러 앱의 책을 한 곳에 | 읽는 책 자동 추가(25개 남짓 이북 앱) |
| 앱 숨기기·이름 바꾸기·소수 고정 앱 | 숨긴 앱, `favorite_names`, 자주 쓰는 앱 3줄 고정 |
| 고대비·큰 글자 | 흑백 기본, 글자 70~150%·줄 간격 슬라이더, 크레마 대비 보정 |
| 아이콘 팩·앱마다 아이콘 바꾸기(KISS #803) | 1.6.5 아이콘 팩·`아이콘 바꾸기` |
| 상단 배터리·시계·조명·빠른 설정 | 자체 상단 상태 줄, 누르면 기기 빠른 설정 |
| 독서 기록: 책별 시간·달력·주간 그래프·연속일·하루 목표 | 독서 기록 화면, 올해 읽은 책 |
| 자동 완독은 싫다, 완독은 직접 | 완독은 사용자가 누른다(자동으로 묻지 않기로 함) |
| 책을 지워도 기록이 남아야 | 기록은 지우지 않는다(나간 책 `past`) |
| 무료, 광고·추적기 없음 | 그대로. 이것이 신뢰의 근거라는 점을 README·홈페이지에서 더 드러낼 만하다 |

### 확인하거나 검토할 만한 것 (VOC 가 자주 나오는데 아직 없거나 약한 것)
1. **기록·설정 백업**: 일반·미니멀 런처 모두에서 "설정을 잃었다"가 큰 불만이다. Pebble Desk 는 앱을 지우면 기록이 모두 사라진다. docs/backup-plan.md 의 우선순위를 올릴 근거.
2. **오늘 읽은 시간의 신뢰**: Olauncher 의 사용 시간 불일치가 댓글 수 2위 이슈였다. 사용자는 시스템 값(Digital Wellbeing)과 비교한다. 자정 넘김·겹치는 구간을 시험해 두고, 숫자가 어디서 오는지(사용 기록, 읽는 화면만) 안내에 적어 둘 만하다.
3. **홈 복귀 시 잔상 정리**: inkOS·Katapult 가 내세운다. 크레마는 펌웨어가 하므로 기기마다 다를 것이다. 다른 기기(BOOX·HiBreak)용 선택 사항으로 검토.
4. **조명 바로 끄고 켜기**: 지금은 상태 줄 → 빠른 설정을 거친다. Katapult 처럼 한 번에 토글하는 요청이 있다.
5. **쪽 목록의 끝 칸**: inkOS 에서 맨 아래 항목을 누를 수 없음(#37), 글꼴을 바꾸면 쪽이 덜 참(#30). 글자 150%·줄 간격 80% 같은 끝값에서 쪽 나누기와 마지막 줄 누르기를 시험해 둘 만하다.
6. **제조사 백그라운드 제한**: HiBreak 에서 대기 뒤 알림 리스너가 끊겼다(inkOS #85). Pebble Desk 의 접근성 서비스도 같은 위험이 있다(기기 정보에 연결·끊김 기록은 이미 있다).
7. **새로 깐 앱 찾기**: 설치순 정렬·새 앱 표시 요청이 많다(Olauncher #208). 모든 앱 목록은 가나다순뿐이다.
8. **웹사이트 바로가기**: 미니멀 런처 공통 최다 요청. 핀 바로가기(`ACTION_CONFIRM_PIN_SHORTCUT`)는 책 바로 열기 조사 때 이미 검토했다(CLAUDE.md `남은 일`). 같이 하면 웹 바로가기도 따라온다.
9. **물리 키 쪽 넘기기**: 키패드·D-pad 요청(mLauncher #474·#476, inkOS #87). CLAUDE.md `남은 일` 의 버튼 직접 정하기 안과 같다.

### 하지 않아도 되는 것
- 위젯·날씨·캘린더: 일반·미니멀 런처에서는 이탈 이유지만, 이북리더 홈 요구에서는 거의 나오지 않는다. inkOS 도 시계·배터리 말고는 일부러 넣지 않았다.
- 알림 점·알림 목록: 휴대폰용 글자 런처의 요구다. 이북리더에서는 드물다.
- 다른 런처 백업 가져오기: 휴대폰 런처 사이의 이동 조건이고, 이북리더 홈과는 상관이 적다.
