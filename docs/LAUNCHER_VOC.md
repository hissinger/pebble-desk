# 다른 런처의 사용자 의견(VOC)

유명 안드로이드 런처와 전자잉크·이북리더 홈에 사용자가 무엇을 칭찬하고 무엇에 불만인지 모은 기록이다(2026-10-28 조사, 2026-10-29 다시 조사해 보강).
네 갈래로 나눠 봤다: 일반 런처(Nova·Lawnchair·Niagara 등), 미니멀·글자 런처(Olauncher·KISS·inkOS 등), 전자잉크 기기 홈(BOOX·Kindle·Kobo 등), 한국 이북리더 사용자(디지털감성 e북카페).

**근거의 한계**
- Reddit 원문은 먼저 Arctic Shift 보관본으로 읽고, 로그인한 뒤 인용한 글 16개의 점수·댓글을 Reddit 에서 다시 확인했다(2026-10-29 값). Reddit 검색(전체 기간 인기순)으로 보관본 제목 검색에서 빠진 글도 더 찾았다. 검색 API(`search.json`)는 로그인해도 막혀 검색 화면으로 봤다.
- Play 리뷰는 스토어 페이지에 보이는 "도움이 됨" 상위 몇 개만 봤다. 별점 분포나 최근 리뷰 전체는 보지 못했다.
- 가장 단단한 근거는 GitHub 이슈다. 공감·댓글 수로 정렬해 직접 확인했고, 2026-10-29 에 상태(열림·닫힘)를 다시 봤다(Olauncher·KISS·mLauncher·inkOS).
- 한국 자료는 네이버 카페 **디지털감성 e북카페**(회원 58만)를 로그인해 직접 읽었다. 글 본문과 댓글 첫 쪽까지다(댓글이 많은 글은 일부만). 디시·뽐뿌는 여전히 보지 않았다.
- 직접 확인하지 못한 것은 **(미확인)** 으로 적었다. 인용은 모두 바꿔 쓴 것이다.

## 한눈에: 여러 런처에 되풀이되는 것

| 주제 | 사용자가 말하는 것 | 대표 근거 |
|---|---|---|
| 주인·개발이 바뀌면 신뢰가 무너진다 | Nova 는 인수 뒤 광고·추적기·구독·과한 권한이 붙어 10년 사용자가 떠났다. Play 평점 3.7. Microsoft·Action Launcher 는 개발이 멈춰 "버려졌다"는 평 | Nova, MS Launcher |
| 구독·유료 장벽 피로 | 평생 구매를 선호한다. 이미 산 라이선스를 무시하거나 유료판에도 광고가 나오면 반발이 가장 크다. 구독이 끝나면 홈이 망가져 보이는 것도 큰 불만 | Nova, Niagara, Smart Launcher, minimalist phone |
| 권한·프라이버시 | 런처가 SMS·통화 기록·위치를 달라고 하면 바로 떠난다. Exodus 추적기 개수로 비교한다 | Nova |
| 꾸민 홈을 잃는 것 | 업데이트·초기화·재부팅·앱 데이터 지우기로 설정이 날아가는 것. 백업·내보내기가 "계속 쓸 수 있는가"를 가른다. 한국 사용자는 토탈런처 백업 파일로 버틴다 | MS Launcher, Good Lock, Olauncher #713, KISS #1601·#1031, 토탈런처(카페) |
| OS·제조사 업데이트마다 깨짐 | 제스처·홈 복귀 애니메이션·위젯·알림 배지가 안드로이드 판·보안 패치마다 깨진다. 결국 기본 런처로 돌아가는 1순위 이유 | Lawnchair, Niagara, Good Lock |
| 전자잉크는 틈새 취급(조금씩 바뀌는 중) | 일반 런처는 애니메이션 끄기·고대비 요청을 오래 "너무 틈새"로 미뤘다. Olauncher 는 2026-04 부터 갱신 빈도 30Hz 미만 화면에서 애니메이션을 저절로 끈다. KISS 의 E-Ink 모드 요청은 아직 열려 있다 | Olauncher #152·#675·v6.4.2, KISS #243·#2457 |
| 기기 기본 홈은 고칠 수 없다 | 크레마 A 는 홈 앱 순서를 못 정하고, 크레마 페블은 홈 모양을 못 바꾼다. 메이북은 홈 그림·시계를 못 뺀다. 그래서 다른 런처를 깐다 | 크레마, 메이북, iReader(카페) |
| 대체 런처는 어렵고 깨지기 쉽다 | 토탈런처가 사실상 표준인데, 설정이 어렵고 재부팅·잠자기 뒤 꾸민 홈이 날아가거나 기본 런처로 돌아간다. 원래 홈으로 돌아가는 법을 몰라 헤맨다 | 토탈런처(카페), BOOX·Bigme |
| 이북리더 홈은 판매 페이지가 아니어야 | Kindle·Kobo 홈의 추천·스토어를 끌 수 없다는 불만이 가장 크다. Kindle 사용자는 홈 대신 서재 탭에 머문다 | Kindle, Kobo |
| 여러 앱의 책을 한 곳에 | 책이 Kindle·Kobo·도서관 앱마다 흩어져 한 서재로 모이지 않는다. 기기 하나에만 도는 연속일은 쓸모없다는 말도 | BOOX 사용자, Kindle |
| 독서 기록은 모으면서 보여 주지 않는다 | 완독일·책별 기록·달력·주간 그래프·연속일을 원한다. 자동 완독 처리는 싫다. KOReader 통계 플러그인이 기준 | Kindle, Kobo, KOReader |
| 손으로 적는 독서 기록은 지친다 | 쪽수를 매일 적는 것은 일처럼 느껴지고 병렬 독서면 더하다. 앱을 몇 개씩 옮겨 다니며 정착하지 못한다. 폰을 바꾸다 몇 년 기록을 잃었다 | 카페 독서기록 글 |
| 미니멀은 너무 빈약하면 떠난다 | 날짜·배터리·일정처럼 한눈에 보는 정보가 없으면 몇 주 만에 돌아간다. 날씨·달력 위젯이 그립다는 리뷰 | Olauncher, minimalist phone |

## 1. 일반 런처

### Nova Launcher
- 칭찬: 제스처, 폴더, 세밀한 홈 조정, 아이콘 모양. `.novabackup` 은 다른 런처들이 가져오기를 지원할 만큼 표준이 됐다. https://bgr.com/1965762/nova-launcher-abandoned-best-alternative-android-launchers
- 일어난 일:
  - 2022 분석 회사 Branch 가 샀다. 2024 감원 뒤 창업자 혼자 남았고, 2025-09 그도 떠났다. "개발자가 떠나면 코드를 공개한다"던 약속은 취소됐다. https://www.neowin.net/news/nova-launchers-future-in-doubt-after-last-remaining-dev-quits-project
  - 2026-01 Instabridge 가 인수했다. 무료판 검색·앱 서랍에 광고, 추적기 2개 → 6~8개. Prime 에서도 광고를 봤다는 보고(회사는 부인). https://9to5google.com/2026/01/20/nova-launcher-new-owners-ads/
  - 2026-02 신규 사용자는 연 $4.99 구독. AI 비서 베타가 SMS·통화 기록·정밀 위치 권한을 요구한다는 분해 결과. https://piunikaweb.com/2026/02/06/nova-launcher-rolls-out-stability-fixes-confirms-4-99-annual-subscription-for-new-users/
- Play(2026-10 기준): 평점 3.7, "광고 포함" 표시, 업데이트는 계속(2026-10-24). 도움이 됨 상위 리뷰는 "앱 서랍에 큰 광고를 넣고 산 유료판이 사라졌다"(2026-02), "10년 넘게 새 폰마다 처음 깔던 앱이 배터리를 먹고 데이터를 마구 주고받는다"(2026-03). https://play.google.com/store/apps/details?id=com.teslacoilsw.launcher
- 떠나는 이유: 새 기능 없이 추적기만 늘고, 권한이 과하고, 구독으로 바뀜. 옮겨 가는 곳은 Lawnchair·Smart Launcher(Nova 백업을 가져온다)·Octopi 와 Niagara(처음부터 다시 꾸민다). 그래도 "결국 Nova 만 한 게 없어 돌아왔다"는 댓글이 같은 글에서 공감 2위였다. https://www.reddit.com/r/androidapps/comments/1wfkxql
- 공개 요구: 코드를 공개하라는 Change.org 청원이 8천 명을 넘었다는 글(2026-10, r/NovaLauncher) **(청원 숫자 미확인)**. https://www.reddit.com/r/NovaLauncher/comments/1x7gkqc

### Lawnchair
- 칭찬: 무료·오픈소스, Pixel 런처 느낌. Nova 대안 중 무료 1순위. 15 베타에서 Nova 백업 가져오기(위젯은 자리만, 설정은 빠짐). https://docs.lawnchair.app/core-features/backup-restore-and-reset
- 불만: 몇 년째 베타만 나오고 Play 최신판이 없다("개발이 좀 멈춰 있었다"는 2026-09 댓글). 아이콘이 저절로 움직임, 알림 배지 회귀, 설정 단계가 있는 위젯이 안 들어감. 아이콘 팩이 홈에만 적용돼 그만둔 사람. https://lawnchair.app/blog/lawnchair-15-beta-1/
- 제스처: 앱 열고 닫는 애니메이션 끊김, 홈 직후 터치 무시, 아이콘 잔상. Xiaomi 등은 제스처를 막는다. 공식 문서가 원인을 안드로이드·제조사로 인정한다. https://docs.lawnchair.app/troubleshooting/gesture-navigation-issues

### Niagara Launcher
- 칭찬: 한 손 세로 목록, 목록 안에서 알림 보기·답장, 가볍고 무료판에도 광고 없음. 전자잉크·미니멀 폰에도 많이 쓴다. Play 상위 리뷰는 "처음엔 목록뿐이라 밋밋했는데 쓸수록 설계 철학이 보인다"(공감 649). https://play.google.com/store/apps/details?id=bitpit.launcher
- 불만: 연 $13.99·평생 $42.99 가 비싸다. 구독이 끝난 아침에 홈이 흉하게 바뀌어 있었다는 경험담. 위젯 사라짐·회색으로 굳음(One UI 7), 알파벳 스크롤이 예민해 위젯을 밀다 앱 목록이 열림, Nova 백업을 못 가져옴. https://help.niagaralauncher.app/article/163-commonly-reported-bugs
- "앞에 떠 있는 줄 모르고 화면이 반응하지 않는 버그가 1년 넘게 FAQ 에만 있고 안 고쳐진다"는 리뷰(공감 165). 개발사는 기기마다 재현이 어렵다고 답했다.
- 안드로이드 15 12월 패치 뒤 홈 복귀 애니메이션이 깨져 개발사가 구글 이슈 트래커로 넘겼다. https://help.niagaralauncher.app/article/174-bug-reports-on-androids-issue-tracker

### Smart Launcher 6
- 칭찬: 앱 범주 자동 분류, 배경화면 색 테마, 통합 검색, Nova 백업 가져오기, 적당한 평생 라이선스. https://docs.smartlauncher.net/faq/start-here/how-to-migrate-from-other-launchers
- 불만: 무료판 광고, 핵심 기능이 Pro. 구독이 끝났다는 오보로 프리미엄이 꺼짐(공식 FAQ). KWGT 위젯이 갱신되지 않아 돌아섰다는 글, Niagara 와 비교해 둔하고 무겁다는 댓글. https://docs.smartlauncher.net/faq/license-and-purchases/problems-and-troubleshooting
- 옛 구매: 공식 FAQ 는 SL3 Pro 를 산 사람은 SL6 Pro 를 다시 사지 않아도 된다고 한다(별도 앱으로 샀으면 SL3 Pro 를 같이 깔아 둬야 확인). 구독을 연·평생으로 바꾸는 것은 안 된다. SL5 Pro 를 다시 사라고 했다는 보고는 여전히 **(미확인)**. https://docs.smartlauncher.net/products/faq/purchases-and-plans/license-and-purchases

### Microsoft Launcher · Action Launcher
- Microsoft: 2024 Copilot 추가 말고 의미 있는 업데이트가 없어 "버려졌다". 2023-06 업데이트가 런처를 망가뜨렸고 해결책인 초기화가 홈을 지웠다. https://www.windowscentral.com/software-apps/as-nova-launcher-loses-its-last-dev-microsoft-launcher-feels-abandoned-for-the-first-time-in-years-i-need-a-new-launcher
- Action: 수입이 줄자 광고 붙은 자체 검색을 넣고, 일회 구매자에게 Google 검색용 연 구독을 따로 요구해 반발(2019). v50(2022)에서 유료 기능 절반을 풀었다. https://www.androidpolice.com/action-launcher-v50-freemium/

### Samsung One UI Home + Good Lock · Pixel Launcher
- Good Lock(Home Up): One UI 판이 바뀔 때마다 모듈이 꺼지거나 열리지 않고, 다시 깔면 홈 설정이 사라졌다는 보고. https://www.androidauthority.com/samsung-one-ui-8-5-good-lock-not-working-3624220
- Pixel: 최근 앱·애니메이션이 가장 매끄럽다(서드파티는 QuickStep API 를 못 받는다). 대신 아이콘 팩 불가, 검색창 못 지움, 격자 선택이 좁다. 이것이 서드파티 런처를 쓰는 가장 큰 이유다. https://www.androidauthority.com/pixel-hide-search-bar-3617699/

## 2. 미니멀·글자 런처

### Olauncher (GitHub 별 3.9k, 무료·오픈소스)
- 칭찬: 2MB 안쪽, 권한·광고 없음(Play 데이터 보안도 "수집 없음"). 글자를 쳐야 앱이 열려 충동 실행이 준다. 앱 숨기기·이름 바꾸기. https://maketecheasier.com/olauncher-minimalist-android-launcher
- 불만: 아이콘·위젯·알림 점·아이콘 팩이 없다(리뷰어도 처음엔 세 시간 만에 그만둠). Play 상위 리뷰도 "위젯이 그립다", "구글 날씨·달력 위젯이 아쉽다", "아이콘이 없어 이름이 같은 앱을 구별 못 한다"(이름 바꾸기로 해결). 홈으로 못 돌아옴·데이터를 지우면 설정을 다 잃음(#713, 2026-07 v6.7.19 에서 고침), 런처가 사라짐(#436). https://play.google.com/store/apps/details?id=app.olauncher
- **화면 사용 시간이 틀림**: Digital Wellbeing 은 55분인데 3시간 12분, 자정에 0으로 안 돌아감. 댓글 37개로 댓글 수 2위 이슈(#545·#569). 사용자는 시스템 값과 직접 비교한다. 2026-03 에 닫혔다. https://github.com/tanujnotes/Olauncher/issues/545
- 기능 요청: 웹사이트·웹앱을 홈에(#20, 공감 77로 압도적 1위. 2026-02 닫힘, v6.4.2 에서 PWA 지원을 고침), 날씨, 스크롤 바, 홈 앱 8개 넘게, 배터리 표시, 새로 깐 앱 찾기·설치순 정렬(#208), 많이 연 순 정렬, 설정을 여는 길게 누르기가 너무 김(#131).
- 전자잉크: 애니메이션 끄기 요청은 처음에 "너무 틈새"로 거절(#152, Hisense A5). 앱 목록 페이드가 계속 보인다는 #675 는 2026-03 닫혔고, v6.4.2(2026-04)부터 **갱신 빈도 30Hz 미만 화면이면 애니메이션을 저절로 끈다**. v6.9.1(2026-09)에 한국어 번역도 들어갔다. https://github.com/tanujnotes/Olauncher/releases
- Meebook M6 에서 Olauncher 밝은 테마가 화면을 망가뜨렸다는 댓글(eLauncher 글). https://www.reddit.com/r/eink/comments/1f10eyr

### KISS Launcher (검색 중심, 오픈소스)
- 칭찬: 빠르고 가볍고, 앱·연락처·웹을 한꺼번에 검색하며 자주 고른 것을 배운다. https://f-droid.org/packages/fr.neamar.kiss/
- 불만: 안드로이드 14 뒤 잦은 튕김(#2188)·Pixel 8 멈춤(#2308), 키보드에 결과가 가려짐, 위젯 추가 회귀. 관리자 한 명이라 이슈를 자동으로 닫는다(#1688).
- 기능 요청: 아이콘 직접 바꾸기(#803, 댓글 51), 자동 다크 모드, 격자 보기, 즐겨찾기 순서 바꾸기, 설정 내보내기·백업(#1601·#1031), 웹앱.
- 전자잉크: BOOX·Minimal 폰 사용자의 고대비 "E-Ink 모드" 요청(#2457, 2026-10 에도 열림). 2015년부터 입력 글자가 안 보인다·애니메이션 없음·큰 글자 요청(#243), 개발자는 기기가 없어 못 고친다고 했다. https://github.com/Neamar/KISS/issues/2457

### mLauncher (Olauncher 포크)
- 요청: 웹앱 바로가기, Niagara 같은 위젯, 물리 키보드로 바로 거르기(#474·#476), 사용자 글꼴, **날짜·배터리 글자 크기를 따로**(#498), 홈 앱 이름 옆 사용 시간(#475), 시계 옆 간단한 일정(#288). https://github.com/CodeWorksCreativeHub/mLauncher/issues
- 버그: 업데이트 뒤 제스처 망가짐, 시스템 테마 무시, 정확히 맞는 검색 결과가 뒤로 감.

### inkOS (전자잉크 전용 글자 런처, 별 519)
- 내세우는 것: 애니메이션·부드러운 스크롤 없음, 쪽 단위 목록, 최소 갱신, 흑/백/따뜻한 회색, D-pad·T9 지원, 홈 알림 표시. 홈으로 돌아올 때 전체 새로 고침(다크 모드에는 필수라고). https://inkos.gez.im/
- 고친 이슈: **사용자 글꼴이면 쪽이 세로를 다 못 채움**(#30, 2025-10 닫힘), **쪽 맨 아래 항목을 누를 수 없음**(#37, 2025-11 닫힘), HiBreak Pro 에서 대기 뒤 알림 수신이 끊김(#85, 제조사의 백그라운드 제한, 2026-07 닫힘).
- 최근 요청(2026-08~10): 앱마다 아이콘 바꾸기(#116), 앱 이름 바꾸기(#115), 홈 앱 목록을 위에 붙이기(#114), 설정 항목을 홈 앱으로(#108), 무음·진동 표시(#104). 마지막 커밋은 2026-06 이라 개발이 느려졌다. https://github.com/gezimos/inkOS/issues
- Reddit 에서 BOOX Note Air 4 사용자가 "기본 런처는 깔끔과 거리가 멀다"며 추천했다. https://www.reddit.com/r/Onyx_Boox/comments/1wgh75e

### 전자잉크용 소형 런처 글에 달린 요청 (Reddit r/eink)
- Think Launcher(2025-05): 앱 검색도 스크롤 대신 쪽으로, 물리 키보드로 치면 바로 검색, 날짜·배터리·시계를 따로 켜고 끄기(상단바를 켜 두면 날짜만 필요), 컬러용 아이콘이 전자잉크에서 흉하니 아이콘 팩·흑백 변환, 목록을 촘촘히 해 한 화면에, 두 칸으로 하면 이름 사이가 너무 벌어짐. https://www.reddit.com/r/eink/comments/1kp01hu
- eLauncher(2024-08): 검색창을 아래로, 밀면 바로 키보드, 좌우 밀기 동작 지정. 다크 모드는 시스템 설정을 따르라는 개발자 답에 HiBreak 는 앱 색을 따르지 않는다는 반론. https://www.reddit.com/r/eink/comments/1f10eyr
- 같은 Think Launcher 글에 "기록 없는 계정, 이모지 가득한 ChatGPT 식 글은 AI 스팸"이라는 댓글이 달렸고, 글은 이후 운영진이 지웠다(2026-10-29 확인). 소개 글 모양 자체가 신뢰를 깎는다.

### 그 밖
- minimalist phone: 평점 4.5 지만 월 $4.99 구독, 체험 뒤 웹사이트 가격의 두 배가 청구됐다는 불만. Play 상위 리뷰도 "며칠 만에 자주 쓰는 앱 바로가기를 막고 기능을 하나씩 줄이며 구독을 너무 일찍 민다". https://play.google.com/store/apps/details?id=com.qqlabs.minimalistlauncher
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
  - 앱 숨기기·위젯·모양 바꾸기가 없어 Unlauncher·AIO Launcher·Nova·Niagara·Olauncher·inkOS 로 바꾼다. https://ryaid.pages.dev/blog/boox.html
  - 홈이 BOOX 생태계를 밀고, Kindle·Kobo·도서관 책이 앱마다 흩어진다. https://lemmy.linuxuserspace.show/post/348624
  - 펌웨어 4.0 에서 노트형 기기의 홈이 일반 태블릿 같은 모양으로 바뀐다는 소식에 "3.5.4 런처를 남겨 달라"는 글(2024-10). 반기는 사람도 있어, BOOX 가 만든 두 모양 중 고르게 해 달라는 쪽으로 모였다. https://www.reddit.com/r/Onyx_Boox/comments/1ge9wau
  - 펌웨어 4.0 홈 서재 위젯이 서재 정렬을 따라가, 서재를 가나다순으로 하면 위젯에도 최근 책 대신 가나다순 앞 책이 뜬다(검색 요약).
  - "Niagara 가 판을 바꿨다" 글(2026-08, 공감 197·댓글 88): BOOX 기본 런처는 "안드로이드가 돌아가는 이북리더를 헤매는 느낌"이었고, 전자잉크에서 런처는 꿾미기쯤으로 알았는데 아니었다는 것. 반대로 "BOOX 앱이 따로 열리는 앱이 될 때까지는 위젯으로 우회하는 게 별로"(공감 13), 기본 런처를 건드리면 NeoReader 를 못 열까 봐 못 바꾼다는 댓글도(NeoReader 는 따로운 앱으로 보이지 않는다). https://www.reddit.com/r/Onyx_Boox/comments/1vz2a98
  - 다른 런처로 바꾸면: 메모·서재가 기본 런처 안에 묶여 있어 다른 런처에서는 위젯으로만 닿고, **얼리기(auto freeze)** 를 끄지 않으면 앱이 목록에 안 보인다(2026-09 글의 해결 댓글). Bigme B7 은 런처를 바꾸면 기본 메모 앱이 사라진다(Niagara 가 못 찾음). Nova 로 옮긴 사람이 누르는 횟수가 늘어 돌아갈까 고민. https://www.reddit.com/r/Onyx_Boox/comments/1wgh75e https://www.mobileread.com/forums/showthread.php?p=4384257
  - 반대로 "기본 런처·NeoReader 로 충분하다"는 사람도 많다(2026-08 "다들 기본 런처 쓰나요" 글). https://www.reddit.com/r/Onyx_Boox/comments/1vkwilx
- **Bigme·Meebook·Hisense**: Think Launcher 개발자는 Bigme 기본 런처가 너무 짜증 나서 직접 만들었다(애니메이션 없음, 이름·아이콘 보기 고르기, 고정 목록). Meebook M8 사용자도 Think Launcher 로 바꾸고 기본 메모 앱을 앱으로 꺼내느라 고생했다. Hisense A9 는 창 관리가 기본 런처에만 있어 바꾸면 최근 앱 버튼이 쓸모없어진다. https://blog.sarasantillan.com/hisense-a9-review-august-2022/ https://www.reddit.com/r/ereader/comments/1u7bz2t
- **Musnap·Viwoods**: Musnap 사용자는 기본 책·서재 배치가 "끔찍하다"며 KOReader 와 inkOS 로 바꿨다("왜 Musnap 이야기는 안 하나" 글, 2026-08, 공감 187). Viwoods AIpaper Reader 리뷰어도 기본 런처를 Nova + 흰 선 아이콘 팩으로 바꿨다. https://www.reddit.com/r/ereader/comments/1vtjm4j https://www.reddit.com/r/ereader/comments/1p11sic
- **Kindle**:
  - 2021 업데이트로 서재를 첫 화면으로 고르는 옵션이 사라졌다. 광고 없는 모델도 홈이 추천 13줄로 차 있다. https://blog.the-ebook-reader.com/2025/06/06/kindles-have-the-worst-home-screen-layout-by-far/
  - "Signature Edition 홈에서 추천을 끌 수 있나" 글(댓글 100)의 답은 "못 끈다, 서재 탭에 머물러라". 공감 1위가 "그냥 서재 탭에 두면 되지 않나"(205), 2위가 "나는 늘 서재 탭"(84), "처음 설정 뒤로 홈 탭을 쓴 적이 없다"(30). 광고 없는 판을 우회한 것이라는 불만도. https://www.reddit.com/r/kindle/comments/1gpun65
  - 홈의 "내 서재에서" 칸에 **다 읽은 책이 계속 뜨고 뺄 수 없다**는 불만(지우는 것 말고는 방법이 없다). https://www.reddit.com/r/kindle/comments/ugz46w
  - 홈을 다시 그려 본 콘셉트 글(2026-06)에 "적어도 독서 통계는 홈에 있었으면" 이 공감 1위(33). "홈 탭에는 몇 초만 있다, 책 끝나면 다음 책으로"(26), "Kindle 에서만 세는 연속일은 다른 데서 읽는 나에겐 쓸모없다"는 반응도. https://www.reddit.com/r/kindle/comments/1ue2pfd
- **Kobo**:
  - 2017 홈 개편 투표 찬성 46%·반대 54%. 좋은 쪽은 지금 책·최근 책에 바로 가고 표지 중심이 보기 좋다는 것, 싫은 쪽은 원치 않는 칸을 끌 수 없고 누르는 횟수가 늘고 추천이 광고 같다는 것. 표지 밑에 제목·저자와 읽은 % 를 원했다. https://blog.the-ebook-reader.com/2017/05/30/do-you-like-the-new-home-screen-on-kobo-ereaders/
  - **표지 크기가 책마다 달라 서재가 톱니처럼 보인다**는 글(2026-09). Calibre 로 0.65 비율에 맞춰 자르는 사람, "Kobo 가 썸네일 높이를 맞춰 주면 좋겠다"는 댓글. https://www.reddit.com/r/kobo/comments/1wmoupx
  - 홈 화면에는 다크 모드가 없다는 불만(2026-08). https://www.reddit.com/r/kobo/comments/1vpibjw

### 이북리더 홈에서 원하는 것
- 지금 읽는 책과 최근 책이 맨 위, 최근 읽은 순(다른 정렬 설정에 끌려가지 않게). 표지와 제목·저자·진행률. 다 읽은 책은 빠지거나 뺄 수 있게.
- 광고·추천·스토어 없음. 칸을 사용자가 끄고 켬. 업데이트로 홈을 강제로 바꾸지 않음(바꾸면 고르게).
- 여러 서점 앱의 책을 한 서재로. 기록·연속일도 앱·기기를 가리지 않고.
- 앱 숨기기와 꼭 쓰는 앱 몇 개만. 순서는 내가 정함.
- 상단에 배터리·시계·네트워크, 조명 켜고 끄기(Katapult 는 홈을 두 번 누르면 조명 토글), 빠른 설정. https://forum.mudita.com/t/katapult-launcher-simple-grid-launcher/13951
- 홈에서 책 파일을 바로 추가·열기, 채리뷰어 지원(클리앙 Pebble Desk 글 댓글). https://www.clien.net/service/board/pds/19280760

### 일반 런처를 전자잉크에서 쓸 때
- 애니메이션·부드러운 스크롤이 잔상과 느린 반응을 부른다. 전자잉크용으로 고친 런처(KISS-eink·Katapult·Think Launcher·inkOS)는 모두 애니메이션을 빼고 쪽 넘기기로 바꿨다. https://forum.mudita.com/t/kiss-launcher-fork-for-eink-mudita-kompakt/12012
- 잔상: 홈으로 돌아올 때 전체 새로 고침(inkOS·Katapult). Palma 사용자는 앱을 바꾼 뒤 수동 새로 고침 버튼을 쓴다.
- 아이콘: 컬러·얇은 외곽선 아이콘은 회색조에서 알아보기 어렵다. KISS-eink 는 Arcticons Black 팩을 자동으로 쓰고, Katapult 는 아이콘 모양·테두리를 강제한다. Hisense A9 + Niagara + LineX Black 아이콘 팩 조합 사진이 r/eink 에서 인기였다.
- 기본 홈으로 정해지지 않거나 기본 런처를 끄려면 ADB 가 필요한 기기가 있다. BOOX 홈을 ADB 로 지웠더니 전자잉크 설정을 바꾸려 하면 기기가 재시작한다는 사람도. https://www.reddit.com/r/Onyx_Boox/comments/1k73o7a

### 독서 통계·목표·연속일
- 기기가 데이터는 모으면서 보여 주는 것이 적다는 비판. Kindle 은 완독 날짜를 기기에 보여 주지 않고, Kobo 통계는 시간·속도뿐이며 책마다 들어가야 보이고 부정확하다. https://blog.the-ebook-reader.com/2025/05/19/ebook-readers-lack-useful-reading-history-and-reading-stats/
- 원하는 것: 완독일·마지막 읽은 날, 책별 기록, 주간 막대그래프·월 달력, 완독 직접 표시. **마지막 쪽에 닿았다고 자동 완독 처리하는 것은 싫다.**
- KOReader 통계 플러그인 칭찬: 달력, 책별 시간, 책을 지워도 기록이 남음(Kobo 는 사라짐), 기기 간 동기화. 웹 대시보드(KoInsight)까지 나왔다. "KOReader 를 깔고 Kobo 가 좋아졌다" 글은 공감 1,334. 같은 글에 "이런 걸 만져 보다가 결국 순정으로 돌아간다"(48)도. https://www.mobileread.com/forums/showthread.php?p=4116713 https://www.reddit.com/r/kobo/comments/1typ7uc
- Kindle 의 일·주 연속일과 "목표까지 X분" 이 동기 부여가 된다는 평. https://goodereader.com/blog/kindle/kindle-reading-insights-is-an-excellent-system-that-monitors-reading-habits

### 한국 사용자 (디지털감성 e북카페, 2025-11 ~ 2026-10 글)
카페 검색에서 `런처` 로 2026-07 이후 글만 30개, `토탈런처` 로 2026-04 이후 30개가 나온다(두 쪽까지 본 것). 한국 사용자에게 런처 이야기는 거의 **토탈런처로 꾸미기** 이야기다.

- **기기 기본 홈의 불만**
  - 크레마 A: 홈에 어떤 앱을 둘지 고를 수 없고, 앱 서랍에서 "맨 앞으로 이동"한 앱이 차례로 홈에 들어간다. 원하는 순서로 놓기 어렵고 알라딘 앱 두 개는 늘 맨 앞이다. https://1sang-ingsang.tistory.com/77 https://cafe.naver.com/f-e/cafes/11509651/articles/896202
  - 크레마 페블: 홈 테마를 바꾸는 기능이 없다(밀리의서재를 주로 써서 격자로 바꾸고 싶다). 답은 "따로 런처를 까세요". https://cafe.naver.com/f-e/cafes/11509651/articles/965845
  - 크레마 C(모안 계열 기본 홈): 홈에서 앱을 누르면 **늘 처음부터 다시 열린다**. 토탈런처·나이아가라에서는 하던 화면으로 돌아간다. 기기 설정 › 애플리케이션 관리 › 백그라운드 앱 설정에서 그 앱의 체크를 풀면 기본 홈에서도 이어진다는 해결 댓글(배터리는 더 쓴다). https://cafe.naver.com/f-e/cafes/11509651/articles/966129
  - 메이북: 아팔프 홈 그림을 못 바꾼다(제조사에 문의했지만 소식 없음, "그래서 그림을 아예 뺐다"). E6 는 홈 위젯의 시계를 못 내리고, 앱 두 줄 설정에서 같은 앱이 두 번 뜨는 버그가 있어 "그냥 최근 앱으로 해 뒀다". https://cafe.naver.com/f-e/cafes/11509651/articles/931881 https://cafe.naver.com/f-e/cafes/11509651/articles/963353 https://cafe.naver.com/f-e/cafes/11509651/articles/958186
  - iReader: 홈은 "책장" 아니면 "시계 + 앱" 둘 중 하나이고 세부 설정이 없다. 런처는 엄두가 안 나 투명 그림을 배경으로 깔아 시계·글자를 감추는 요령이 공유된다. https://cafe.naver.com/f-e/cafes/11509651/articles/961036
  - BOOX Go7: 홈 아이콘이 너무 크고 간격이 넓다. 홈을 "라이브러리"에서 "앱"으로 바꾸는 설정을 알려 준 댓글에 고맙다는 답이 줄줄이. https://cafe.naver.com/f-e/cafes/11509651/articles/924723
  - 중국 내수판(한본 등): 기본 런처가 중국어만 지원하거나 "너무 구려서" 토탈런처로 바꾼다. https://cafe.naver.com/f-e/cafes/11509651/articles/964716
- **토탈런처(Play 100만+, 국내 개발)**: 자유도가 가장 높다는 평. 카페에 테마·**백업 파일** 공유 글이 많고, "런처를 받아 백업 파일만 적용하면 된다"가 흔한 안내다. https://play.google.com/store/apps/details?id=com.ss.launcher2 https://cafe.naver.com/f-e/cafes/11509651/articles/964716
- **그런데 대체 런처가 힘든 점**
  - 어렵다: "런처 설정은 엄두가 안 난다", 아이콘·배경을 미리 받아 두고도 "1차 시련", "런처… 어렵네요". https://cafe.naver.com/f-e/cafes/11509651/articles/963386 https://cafe.naver.com/f-e/cafes/11509651/articles/967462
  - 날아간다: Go7 에서 껐다 켜니 기본 런처로 켜지고 토탈런처 배열·이름 바꾼 것이 모두 사라짐(해결은 "백업을 새로 저장"). 샘7 은 잠자기·재부팅 뒤 4~5번에 한 번 앱이 홈에서 사라져 백업을 불러와야 하고 "매번 백업은 귀찮다". 글꼴이 기본으로 풀린다는 댓글도. https://cafe.naver.com/f-e/cafes/11509651/articles/934688 https://cafe.naver.com/f-e/cafes/11509651/articles/939809
  - 기본 런처로 돌아간다: Bigme B7 Pro 에서 "런처를 해도 자꾸 기본으로 돌아온다". https://cafe.naver.com/f-e/cafes/11509651/articles/960359
  - BOOX 얼리기: 새로 깐 밀리의서재가 런처에 안 잡혀 기본 런처에서 unfreeze 로 해결. 토탈런처 안에서 앱이 자꾸 사라지는데 얼리기는 기본 홈에서만 고칠 수 있고, 기본 홈으로 가는 법을 몰라 한 시간을 헤맴(답: 설정 › 앱 및 알림 › 기본 애플리케이션). https://cafe.naver.com/f-e/cafes/11509651/articles/967462 https://cafe.naver.com/f-e/cafes/11509651/articles/963087
  - 원래 런처로 돌아가는 버튼을 만들 줄 몰라 묻는 글(무스냅). https://cafe.naver.com/f-e/cafes/11509651/articles/928748
  - 걱정: 토탈런처를 쓰다 벽돌이 됐는데 업체가 사용자 과실이라 무상 수리를 거절했다는 글을 보고 망설임(다른 회원은 3대에 1년 넘게 문제없었다고 답). 배터리를 더 먹느냐는 질문(체감은 없다는 답). https://cafe.naver.com/f-e/cafes/11509651/articles/957567 https://cafe.naver.com/f-e/cafes/11509651/articles/967587
  - 지친 사람: "꾸미다 질려서 순정"이라는 글에 서점 앱과 슬립 화면만 바꾸고 둔다는 공감. 꾸민 요소가 오히려 눈을 끌어 집중을 방해한다고. https://cafe.naver.com/f-e/cafes/11509651/articles/966692
- **독서 기록 앱**(카페에 이 주제 글이 아주 많고, 회원이 직접 만든 앱 소개도 잦다)
  - 정착을 못 한다: 북덕방·북적북적·고양이책집사·산책·북모리·리더스를 모두 깔고 모든 앱에 다 적는 중. "너무 단순하면 아쉽고 너무 복잡하면 피곤하다". https://cafe.naver.com/f-e/cafes/11509651/articles/970372
  - 손으로 적기가 짐: 읽은 쪽을 매일 적는 것은 일처럼 느껴지고 병렬 독서면 더하다. 이펍은 기기·설정에 따라 쪽이 달라 쪽 기록이 맞지 않는다. 주간·월간 리포트는 좋다. 전자책·종이책·대여·소장·웹소설을 함께 적고 싶다. https://cafe.naver.com/f-e/cafes/11509651/articles/965862
  - 기록을 잃는다: 폰을 바꾸면서 몇 년 쓴 북모리 기록이 초기화됨(앱에서 따로 백업해야 했다). 그래서 두 앱에 이중으로 적는다는 댓글. https://cafe.naver.com/f-e/cafes/11509651/articles/958085
  - "완독한 책이 쌓이는 느낌이 좋아" 완독 책만 등록한다(북적북적). 앱에서 제목·저자가 따로 노는 일이 생겨 수기로 돌아가려는 사람도. https://cafe.naver.com/f-e/cafes/11509651/articles/970749

## 4. Pebble Desk 에 주는 뜻

### 참고: Pebble Desk 글에 달린 반응 (2026-10, Reddit r/eink·r/ereader, 카페)
- 좋아한 것: "이북리더에 꼭 필요한 런처", "안드로이드가 아니라 진짜 이북리더 같다", 접근성으로 Kindle 책 정보를 읽는 방식 자체. 카페에서는 메이북 E6 사용자가 "피드백이 빨라 바로 고쳐지는 게 보인다"며 따로 추천 글을 썼다. inkOS 로 돌아간다던 사용자도 "서드파티 앱 없이 기기 전체 설정에 들어갈 수 있는 점(inkOS 는 못 한다)"과 미니멀함을 좋아했다. https://www.reddit.com/r/ereader/comments/1xexppa https://cafe.naver.com/f-e/cafes/11509651/articles/969621
- 떠난 이유: 그 사용자는 음량 키 쪽 넘김과 선 아이콘 팩(Arcticons) 때문에 inkOS 로 돌아가며 "아이콘 팩이 들어오면 돌아오겠다"고 했다(2026-10-28). 둘 다 이제 있다(아이콘 팩 1.6.5, 페이지 버튼 다음 판).
- 막힌 것: Play 프로텍트·제한된 설정 때문에 설치와 접근성 켜기가 어려움, BOOX·Bigme 에서 자동 추가가 멈춤(얼리기·백그라운드 제한), Kindle 격자 보기의 표지, HiBreak 에서 독서 기록 화면이 비좁음, 안드로이드 8 미지원, Kindle 앱이 고른 책이 아니라 마지막으로 연 책을 연다.
- 요청: BookFusion·Readest·Google Play 북·Mihon/Kahon 지원(모두 이후 판에서 넣음), 독서 통계를 따로 보는 쪽(이미 있음, 찾지 못함), 자주 쓰는 앱 순서 바꾸기(이미 설정 › 앱 › 자주 쓰는 앱의 ↑↓, 찾지 못함), 오디오북·TTS 조작 위젯, Substack(책 단위가 아니라 안 하기로 답함).

### 이미 맞는 방향 (VOC 가 뒷받침)
| VOC | Pebble Desk |
|---|---|
| 애니메이션 없음, 스크롤 대신 쪽 | 전자잉크 원칙(DESIGN.md) |
| 홈은 지금 읽는 책으로, 광고·추천 없음 | 맨 앞 책 + 함께 읽는 책, 스토어·추천 없음 |
| 여러 앱의 책을 한 곳에, 기록도 앱을 가리지 않고 | 읽는 책 자동 추가(25개 남짓 이북 앱), 기록은 앱을 가리지 않고 한 곳에 |
| 기기 홈을 바꿀 수 없다(크레마 A·페블, 메이북, iReader) | 기본 홈을 대신한다. 카페에서도 "페블은 런처를 따로 까야 한다"가 답이다 |
| 꾸미지 않아도 바로 쓰는 홈(토탈런처는 어렵고 날아간다) | 테마·배치 작업 없이 설치 → 시작하기만으로 쓴다 |
| 손으로 적는 기록은 지친다, 쪽수 적기 싫다 | 읽기만 하면 책별 시간·진행률이 쌓인다 |
| 앱 숨기기·이름 바꾸기·소수 고정 앱 | 숨긴 앱, `favorite_names`, 자주 쓰는 앱 3줄 고정 |
| 고대비·큰 글자 | 흑백 기본, 글자 70~150%·줄 간격 슬라이더, 크레마 대비 보정 |
| 아이콘 팩·앱마다 아이콘 바꾸기(KISS #803, inkOS #116) | 1.6.5 아이콘 팩·`아이콘 바꾸기` |
| 상단 배터리·시계·조명·빠른 설정, 상단바와 겹치지 않게 | 자체 상단 상태 줄, 누르면 기기 빠른 설정, `기기 상단 바` 를 켜면 시계·상태 줄을 그리지 않음 |
| 독서 기록: 책별 시간·달력·주간 그래프·연속일·하루 목표 | 독서 기록 화면, 올해 읽은 책 |
| 자동 완독은 싫다, 완독은 직접 | 완독은 사용자가 누른다(자동으로 묻지 않기로 함) |
| 책을 지워도 기록이 남아야 | 기록은 지우지 않는다(나간 책 `past`) |
| 물리 키 쪽 넘기기(mLauncher #474·#476, inkOS #87) | 설정 › 화면 › `페이지 버튼` 으로 사용자가 눌러 정한 키(이슈 #9, 다음 판) |
| BOOX 얼리기로 앱·서비스가 멈춤 | 시작하기·멈춤 안내에 얼리기 끄기, 기기 정보에 얼리기 켜짐/꺼짐 |
| 원래 홈으로 돌아가는 길 | 설정의 `기본 홈 앱 ›` 이 안드로이드 홈 앱 선택 화면을 연다 |
| 무료, 광고·추적기 없음 | 그대로. 이것이 신뢰의 근거라는 점을 README·홈페이지에서 더 드러낼 만하다 |

### 확인하거나 검토할 만한 것 (VOC 가 자주 나오는데 아직 없거나 약한 것)
1. **기록·설정 백업**: 일반·미니멀 런처, 토탈런처, 독서 기록 앱 모두에서 "다 날아갔다"가 가장 아픈 불만이다. 카페 사용자는 폰을 바꾸다 몇 년 기록을 잃었고, 그래서 두 앱에 이중으로 적는다. Pebble Desk 는 앱을 지우면 기록이 모두 사라진다. docs/backup-plan.md 의 우선순위를 올릴 근거.
2. **오늘 읽은 시간의 신뢰**: Olauncher 의 사용 시간 불일치는 댓글 37개가 달린 뒤에야 고쳐졌다(2026-03). 사용자는 시스템 값(Digital Wellbeing)과 비교한다. 자정 넘김·겹치는 구간을 시험해 두고, 숫자가 어디서 오는지(사용 기록, 읽는 화면만) 안내에 적어 둘 만하다.
3. **앱을 이어서 열기**: 크레마 C 기본 홈은 앱을 늘 처음부터 다시 연다는 불만이 있고, 다른 런처로 바꾸면 하던 화면으로 돌아간다고 한다. Pebble Desk 는 `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` 로 여니 이어져야 맞다. 크레마 C·팬톤에서 이북 앱이 읽던 화면으로 돌아오는지 확인해 두면, 소개 글에 장점으로 적을 수 있다.
4. **재부팅 뒤에도 기본 홈인지**: Bigme B7 Pro·BOOX Go7 에서 껐다 켜면 기본 런처로 돌아왔다는 글이 있다. 기기별로 재부팅 뒤 Pebble Desk 가 홈으로 남는지 확인할 만하다(DEVICES.md 에 한 줄씩).
5. **원래 홈으로 돌아가는 안내**: 런처를 쓰다 기본 홈으로 못 돌아가 헤맨 글이 여럿이다. 설정에 `기본 홈 앱 ›` 이 있으니 README·TROUBLESHOOTING 에 "원래 홈으로 돌아가기" 한 줄을 두면 설치를 망설이는 사람(A/S·벽돌 걱정)에게도 도움이 된다.
6. **표지 크기 들쭉날쭉**: Kobo 사용자는 비율이 다른 표지가 나란히 놓인 "톱니" 서재를 싫어한다. 함께 읽는 책 줄·올해 읽은 책 격자에서 비율이 크게 다른 표지(만화·PDF 첫 쪽·정사각 표지)가 어떻게 보이는지 확인해 둘 만하다.
7. **다 읽은 책이 홈에 남는 문제**: Kindle 홈이 다 읽은 책을 계속 보여 줘 불만이다. 완독한 책이 홈·함께 읽는 책에서 언제 빠지는지 지금 동작을 한 번 확인해 둘 만하다.
8. **홈 복귀 시 잔상 정리**: inkOS·Katapult 가 내세운다. 크레마는 펌웨어가 하므로 기기마다 다를 것이다. 다른 기기(BOOX·HiBreak)용 선택 사항으로 검토.
9. **조명 바로 끄고 켜기**: 지금은 상태 줄 → 빠른 설정을 거친다. Katapult 처럼 한 번에 토글하는 요청이 있다.
10. **쪽 목록의 끝 칸**: inkOS 에서 맨 아래 항목을 누를 수 없음(#37), 글꼴을 바꾸면 쪽이 덜 참(#30)이 있었다(둘 다 고쳐짐). 글자 150%·줄 간격 80% 같은 끝값에서 쪽 나누기와 마지막 줄 누르기를 시험해 둘 만하다.
11. **새로 깐 앱 찾기**: 설치순 정렬·새 앱 표시 요청이 많다(Olauncher #208). 모든 앱 목록은 가나다순뿐이다.
12. **있는 기능을 못 찾음**: Reddit 사용자가 독서 통계 화면과 자주 쓰는 앱 순서 바꾸기를 이미 있는 줄 모르고 요청했다. 홈의 앱을 길게 눌렀을 때 순서 바꾸기로 가는 길, 하단 읽은 시간이 눌리는 줄 알 수 있는 표시처럼 길을 드러낼 자리를 검토할 만하다.
13. **웹사이트 바로가기**: 미니멀 런처 공통 최다 요청(Olauncher #20 은 PWA 지원으로 닫힘). 핀 바로가기(`ACTION_CONFIRM_PIN_SHORTCUT`)는 책 바로 열기 조사 때 이미 검토했다(CLAUDE.md `남은 일`). 같이 하면 웹 바로가기도 따라온다.

### 하지 않아도 되는 것
- 위젯·날씨·캘린더: 일반·미니멀 런처에서는 이탈 이유지만, 이북리더 홈 요구에서는 거의 나오지 않는다. inkOS 도 시계·배터리 말고는 일부러 넣지 않았다.
- 알림 점·알림 목록: 휴대폰용 글자 런처의 요구다. 이북리더에서는 드물다.
- 다른 런처 백업 가져오기: 휴대폰 런처 사이의 이동 조건이고, 이북리더 홈과는 상관이 적다.
- 토탈런처식 꾸미기 자유도: 카페에서 인기지만 "어렵다·날아간다"의 원천이기도 하고, 꾸미다 지쳐 순정으로 돌아가는 사람도 있다. 자유도로 겨루기보다 "꾸미지 않아도 되는 홈"으로 다르게 서는 편이 맞아 보인다.
