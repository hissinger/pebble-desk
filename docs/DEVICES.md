# 기기 호환 노트

Pebble Desk 를 크레마 페블 말고 다른 전자잉크 기기에서 쓸 수 있는지 조사한 기록이다. 새 기기를 조사하면 **한눈에** 표에 열을 더하고 기기 절을 하나 더 쓴다. 댓글로 들어온 문제의 진행 상태는 **docs/ISSUES.md** 에서 따라간다.
방법: 기기에서 `getprop`·`settings`·`adb pull` 한 시스템 파일, 제조사 OTA 펌웨어를 풀어 jadx 로 디컴파일, 리뷰 영상·설명서.
2026-10 기준. 실제 기기에서 확인하지 않은 것은 **(미확인)**, 같은 회사 다른 기종에서 미루어 본 것은 **(추정)** 으로 표시했다.

## 한눈에

|  | 크레마 페블 | Meebook E6 | Viwoods AiPaper Mini | iReader Ocean 5 Pro | Minimal Phone | Moaan MIX7S |
|---|---|---|---|---|---|---|
| 근거 | 실제 기기 | 실제 기기 사진(2026-10-07, 기기 정보 화면) + 리뷰 | 실제 기기 사진(2026-10-07, 홈 화면·기기 정보 화면) + 리뷰·판매 페이지·다른 개발자 기록 | 실제 기기 사진(2026-10-10, 기기 정보·접근성 화면) + Musnap Ocean 설명서(2025-12, 만든 곳 Shenzhen Zhangyue) + 리뷰 | 실제 기기 사진(2026-10-10, 기기 정보 화면) + 리뷰(HelenTech·Tom's Guide·Liliputing) | 실제 기기 사진(2026-10-10, 기기 정보·설정 › 독서 화면, 사용자 댓글) + Good e-Reader 기사 |
| 화면 | 6" 1072×1456, 밀도 300 (1dp = 1.875px) | 6" Carta 1300 1072×1448, 밀도 320 (1dp = 2px) | 8.2" Carta 1000 1440×1920, 약 293ppi(제조사는 297·292), 밀도 320 (1dp = 2px) | 7" Carta 1300 1264×1680, 300ppi, 밀도 320 (632×840dp) | 4.3" 600×800(4:3), 약 230ppi, 밀도 240 (1dp = 1.5px) | 7" 1264×1680, 300ppi(기사, Carta 1200), 기기 밀도 **254** |
| dp 크기 | 572×777dp | 536×724dp | 720×960dp → 앱 기준 572×762dp(밀도 맞춤 403) | 앱 기준 571×759dp(밀도 맞춤 354, 기기 정보) | 400×533dp → 앱 기준 571×762dp(밀도 맞춤 168, 1dp = 1.05px) | 796×1058dp → 앱 기준 674×896dp(밀도 300. 맞춤대로면 354·572dp 여야 한다, 아래) |
| 칩 · OS | RK3566 · 안드로이드 11 (API 30) | RK3566 (`updatemodel`) · 안드로이드 11 (API 30) | MediaTek MT8788 (판매 페이지) · 안드로이드 13 (API 33), `Build.DISPLAY=3.15.15` | 8코어 2.2GHz(Ocean 5 Pro·Musnap. 표준 Ocean 5 는 4코어) · 안드로이드 14 (API 34, 빌드 `UP1A.231005.007`, SmartOS 3.1) | MediaTek Helio G99 (리뷰) · 안드로이드 14 (API 34), 빌드 `MP01_20260104_1412` | RK3566(`rk3566_ebook`) · 안드로이드 14 (API 34, 빌드 `UQ1A.240205.004.B1`). 기사의 MIX 7S 는 4코어 A55 · 안드로이드 11 이라 펌웨어나 판이 다르다 |
| `Build.MODEL` | `CREMA PEBBLE` | `E6` | `Viwoods AiPaper Mini`. 제조사 모델 번호는 `SE05`(FCC ID `2BKO6-SE05`) | `Ocean 5 Pro`(기기 정보). Musnap Ocean 모델 번호 `SM07A-AKDW`(FCC ID `2BQD6-SM07A-OCEAN`) | `MP01` | `MoaanMIX7S` |
| `Build.DEVICE` | `CREMA_PEBBLE` | `E6` | `Viwoods` | `SM07A-CKDA`(Ocean 5 Pro 기기 정보, 모델 번호와 같다) | `MP01` | `rk3566_ebook` |
| `Build.MANUFACTURER` · `BRAND` | `into1` · `YES24` | `Haoqing` · `MEEBOOK` | `Viwoods` · `Viwoods AiPaper Mini` | `iReader` · `iReader`. 만든 곳 Shenzhen Zhangyue Technology(掌阅 iReader) | `ALONG` · `Minimal_Phone`. 파는 곳은 Minimal Company(미국) | `rockchip` · `rockchip`. 만든 곳 Moaan(墨案, 샤오미 생태계) |
| 펌웨어 만든 곳 | wetao / into1 (`ZlSystemUI`, `ZlSettings`) | Haoqing | ODM **Wisky**(`ro.build.user=wisky`, 시스템 앱 `com.wisky.*`), 위에 Viwoods 앱. 펌웨어 3.x, 두 달쯤마다 업데이트. Rockchip 계열이 아니다 | 掌阅 iReader **SmartOS** 3.x. Musnap 은 같은 기기에 이름만 바꾼 것 | `ALONG`(제조사 값. 만든 곳으로 보인다 **(추정)**), 위에 Minimal 런처(`com.example.minimallauncher`)·빠른 설정 앱. AOSP Launcher3(Quickstep)도 들어 있다 | Moaan(시스템 앱 `com.moan.*`, 설정 값 `APPS_WHITE_LIST_KEY`·`APPS_HIDDEN_LIST_KEY`). Rockchip 개발 키트 계열(`init.svc.vendor.*-rockchip`) |
| 안드로이드 하단 내비게이션 바 | 없음 | 없음. 대신 **상단 바**에 홈·뒤로·새로고침·조명 버튼과 최근 앱 버튼(약 50dp, 사진으로 어림) | 없음(사진). 화면 아래 정전식 버튼 3개: 뒤로·홈·AI | 없음 **(추정)**. 아래 가장자리 끌어올리기: 왼쪽 = 작업 관리자, 가운데 = 홈, 오른쪽 = 표시 최적화(리뷰). 모든 화면 위에 시스템 상단 바(시각 · BT · 조명 · 배터리 · `···`, 설명서 그림) | 없음. 화면 아래 **물리 버튼 3개**(뒤로·홈·최근, 리뷰). 안드로이드는 3버튼 모드인데 화면 버튼을 숨겼다(리뷰). 화면 위에 안드로이드 기본 상단 바(사진) | 없음(사진. 아래 가운데 손잡이 줄만, `gesture_hidden=1`) |
| 빠른 설정 | 크레마 전용 앱 `com.inno.quicksetting` | 안드로이드 기본 패널을 고친 것, 타일 2줄×6칸 (실기기 사진) | Viwoods **제어 센터**: 오른쪽 위에서 손가락으로 끌어내림, 화면 오른쪽 절반 세로 패널. Wi-Fi·BT·새로고침·대비·버튼 잠금·새로고침 모드·밝기 등. 알림창은 없다. 왼쪽 위 끌어내리기 = 새로고침 | **제어 패널**: 화면 위에서 끌어내린다. 오른쪽 위 모서리에서 끌어내리면 캡처·노트·작업 전환 패널(Musnap 리뷰). 손동작은 설정 › 보조 기능 › 전역 손동작에서 바꿀 수 있다 | 안드로이드 기본 알림창(끌어내리기, 리뷰) + 왼쪽 옆 **새로고침 버튼 길게** = Minimal 설정 창(밝기·색온도·키보드 조명·새로고침 속도 3단계, HelenTech) | **(미확인)** |
| 빠른 설정 여는 방송 | `com.epd.drop_down` | `com.haoqing.action.QUICK_SETTINGS`. `메이북 ›` 로 열림(2026-10-07 사용자 확인). `크레마 ›` 는 아무 일 없음 | 없음. 크레마·메이북 방송으로는 안 열리고 안드로이드 기본 펼치기로 열린다 | **(미확인)**. 크레마·메이북 방송으로는 안 열릴 것 **(추정)** | 없음 **(추정)**. 기기 정보 사진에 `크레마 ›`·`메이북 ›` 결과 글자가 없다 | **(미확인)**. `크레마 ›`·`메이북 ›` 결과 글자 없음 |
| 안드로이드 기본 펼치기(`expandSettingsPanel`) | 열림: 크레마식 시스템 알림창(밝기·음량·대비) | 열림. 메이북 방송과 같은 패널 | 열림: Viwoods 제어 센터(기기 정보 `안드로이드 ›`, 사용자 확인) | **(미확인)** | `보냄`(함수는 불렸다). 패널이 열렸는지 **(미확인)** | `보냄`. 열렸는지 **(미확인)** |
| 화면 대비 | `hq_contrast`(기본 56), 앱 화면을 밝기^(1/s) 로 어둡게, s = 1 − c×90/8000 | `Settings.System hq_contrast` 키가 **없다**(기기 정보에 `-`). 다른 곳에 두는 듯 **(미확인)** | 제어 센터의 대비 + 펌웨어 3.12(2026-01)부터 앱별 `App Display`(대비·연한 색 지우기). 키 **(미확인)** | 앱별 표시 최적화(대비·선명도·전체 새로고침 간격·다크 모드). 키 **(미확인)** | **(미확인)** | **(미확인)** |
| 페이지 버튼 | 없음 | 2개 + 화면 아래 정전식 버튼. `persist.haoqing.pagekeys=41`. 키 코드 **(미확인)** | 없음. 정전식 뒤로·홈·AI 버튼, 전원 겸 지문 버튼. AI 버튼 키 코드 **(미확인)** | 오른쪽 손잡이에 2개. 페이지 넘김이 필요 없는 앱에서는 **음량 키**로 동작(Musnap 리뷰) → 기기 정보에서 `24 KEYCODE_VOLUME_UP`(scan 115) 확인. Pebble Desk 목록은 안 넘어간다 | 없음. 물리 QWERTY 키보드(35키, 단축키 1개), 왼쪽 옆 음량·새로고침, 오른쪽 전원 겸 지문. 아래 물리 뒤로 = `4 KEYCODE_BACK`(scan 158, 리눅스 `KEY_BACK`) 확인 | 있음 → 기기 정보에서 `25 KEYCODE_VOLUME_DOWN`(scan 0). iReader 처럼 **음량 키**로 들어와 Pebble Desk 목록은 안 넘어간다. 설정 값 `long_side_key_a=0`·`long_side_key_b=5`(옆 버튼 배정으로 보인다 **(추정)**) |
| 기본 홈 바꾸기 | 됨 (지금 쓰는 중) | 됨 (기기 정보 `기본 홈 켜짐`, 홈 화면 사진) | 됨 (Pebble Desk 홈 사진. 리뷰에서도 Nova·inkOS 를 안드로이드 설정 › 앱 › 기본 앱 › 홈 으로 씀) | 됨 (기기 정보 `기본 홈 켜짐`) | 아직 안 함 (기기 정보 `기본 홈 꺼짐`. 원래 홈은 Minimal 런처) | 됨 (기기 정보 `기본 홈 켜짐`) |
| Pebble Desk 판정 `Device.kind` | `CREMA` (`Build.DEVICE` 가 `CREMA` 로 시작) | `MEEBOOK` (기기 정보 `메이북`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) | `OTHER` (기기 정보 `기타`) |

- 크레마와 메이북은 둘 다 **Rockchip 전자잉크 개발 키트**에서 나왔다(Viwoods 는 MediaTek 이라 다르다). 안드로이드 빌드 기반(`RQ2A.210505.003`), `android.os.EinkManager`, SystemUI 의 `EinkSettingsManager`·`EinkSettingsProvider`(`content://com.android.systemui.eink/einksettings`) 이름이 같다. 그 위를 회사마다 따로 고쳤다.
- `Build.DEVICE` 는 기기 코드명(`ro.product.device`)이다. 안드로이드 11 은 `ro.product.device` 가 직접 설정돼 있지 않으면 `ro.product.property_source_order`(기본 product, odm, vendor, system_ext, system) 순서로 `ro.product.<파티션>.device` 중 처음 비어 있지 않은 값을 쓴다(`init/property_service.cpp`). `MODEL`·`MANUFACTURER`·`BRAND` 도 같다.
- 앱 설정 › **기기 정보** 화면을 사진으로 찍어 받는다. 모델(코드명)·제조사·브랜드·안드로이드·빌드·화면(px·dpi·dp)·기기 종류·앱 버전, 메이북 속성·조명/대비 설정 값, 권한, **마지막으로 누른 버튼의 키 번호·스캔 코드**, **최근에 앞에 뜬 다른 화면**(사용 기록. 기본 런처에서 빠른 설정을 열었다 돌아오면 그것이 따로 된 화면인지 시스템 알림창인지 알 수 있다), 빠른 설정 세 방법(크레마·메이북 방송, 안드로이드 기본 펼치기) 시험이 한 화면에 있었다. 1.4.0 부터는 빠른 설정 시험을 빼고(결과가 `보냄` 뿐이라 열렸는지는 어차피 물어야 했다. 지금은 홈 상단 줄을 눌러 열리는지 묻는다), 앱이 밀도를 정할 때 본 크기, 자동 추가 서비스 연결·끊김과 앱이 끝난 까닭·배터리 최적화·대기 그룹·백그라운드 제한, 이 앱이 여는 시스템 화면(접근성·사용 기록·이 앱 사용 기록·앱 정보) 시험을 더했다. 새 기기는 이 사진부터 받는다.
- 기기마다 다른 동작은 `Device`(종류 판정·빠른 설정·조명·시스템 속성)에, 크레마 전용 화면 보정은 `Crema` 에 둔다.

## 크레마 페블 (YES24)

- 패널 맨 아래 약 8px 가 테두리에 가려 보이지 않는다 → `Crema.reserveHiddenBottom()`.
- 화면 대비: 앱 화면 전체를 어둡게 그리고 `ImageView` 그림만 다시 밝힌다(bleach). 직접 그리는 표지는 `Crema.undoContrast` 로 미리 밝힌다. 자세한 건 CLAUDE.md.
- 빠른 설정: SystemUI(`ZlSystemUI` 의 `StatusBar`)가 `com.epd.drop_down` 방송을 받아 크레마 빠른 설정 앱을 띄운다. 화면 위에서 끌어내릴 때도 `DisplayPolicy` 가 같은 방송을 보낸다.
- 조명: `Settings.System` 의 `screen_brightness`(백색)·`warm_light`(온색).
- 잔상 제거 횟수: `persist.vendor.fullmode_cnt`.
- 로고: 크레마 런처(`com.wetao.cremalauncher`)의 `ic_icon_crema` 를 불러온다.

## Meebook (Haoqing 펌웨어)

Meebook 은 선전 Haoqing Technology(`haoqingtech.com`)의 브랜드다. E6 안내서의 연락처가 `meebook@haoqingtech.com`·선전 바오안구 주소이고, MEEBOOK 상표 주인도 이 회사다. Good e-Reader 는 Boyue(Likebook) 출신들이 세운 회사라고 소개한다. 펌웨어 코드에 밀리의서재 기기 분기(`Build.BRAND == "Millie"`)와 한국어 문구, 한국 이북 앱 이름(교보·YES24·리디·알라딘·크레마 앱)이 들어 있어 국내 기기에도 같은 펌웨어를 쓰는 것으로 보인다 **(추정)**.

### 펌웨어 얻기

- 업데이트 서버: `POST http://api.haoqingtech.com/update/v1` (https 는 연결 안 됨), 폼 값(`x-www-form-urlencoded`) `model`·`packageDate`·`packageCode`·`serialNumber`(13자)·`language`. 원래 요청(MobileRead)에는 일련번호가 들어가지만 빼고 보내도 답이 왔다(2026-10 직접 확인).
  - `model` = 기기의 `getprop persist.haoqing.updatemodel` (예: `MEEBOOK-M6C-RK3566`, `MEEBOOK-M8-RK3576`)
  - `packageCode` = `getprop ro.build.display.id` (예: `MEEBOOK-V2.0.0-2025042911`), `packageDate` 는 그 뒤 10자리
  - 답의 `retobj.packageUrl` 이 다운로드 주소(`http://update.haoqingtech.com/...`), `packageMd5`·`size` 도 온다. 없으면 `"retobj":""`.
- 기종마다 다르다: M6C·M8 은 옛 버전 번호를 넣으면 **전체 OTA** 를 준다. M6 은 정확한 현재 버전에서 올리는 **증분**만 줘서 버전을 맞혀야 한다.
- E6 는 받지 못했다. 실기기 값은 `updatemodel=MEEBOOK-E6-RK3566`, `Build.DISPLAY=V2.0.0-2026071718`(M6C 와 달리 `MEEBOOK-` 접두가 없다)인데, `packageCode` 를 `V2.0.0-2026071718`·`MEEBOOK-V2.0.0-2026071718`·옛 날짜(`V2.0.0-2025010100`)로 보내도 모두 `"retobj":""` 였다(2026-10-07). 지금 버전이 최신이거나 일련번호가 필요한 듯하다.
- 출처: 4pda Meebook M6 토론방 #165, MobileRead Meebook M8 펌웨어 글.

### 펌웨어 풀기

```bash
brew install jadx sevenzip
python3 tools/ota_payload.py update.zip img system system_ext product vendor odm
7zz x -snl -ofs/system img/system.img          # ext4. 파티션마다
jadx --no-res -d src/SystemUI fs/system_ext/priv-app/SystemUI/SystemUI.apk
```

- M6C 의 `update.zip` 은 A/B 전체 OTA(`payload.bin`)이고 파티션은 모두 ext4 다.
- 시스템 앱 리소스(타일 목록·문구·아이콘)는 `--no-res` 를 빼고 `--no-src` 로 푼다.

### M6C 에서 확인한 것 (V2.0.0, 2025-04)

- **속성**: `ro.haoqing.brand=haoqing-meebook`, `persist.haoqing.updatemodel=MEEBOOK-M6C-RK3566`, `ro.sf.lcd_density=320`, `ro.product.cpu.abilist=arm64-v8a,armeabi-v7a,armeabi`, `persist.sys.eink.color=1`(컬러 화면), `persist.haoqing.pagekeys=0`(버튼 없음).
- **시스템 앱**: 런처 `com.haoqing.app.hqreader`(`Haoqing-Launcher`, HOME), 설정 `com.haoqing.settings`, 떠 있는 버튼 `Haoqing-FloatingButton`, 앱 장터·파일 관리자·와이파이 전송 등. AOSP `Launcher3QuickStepGo` 도 들어 있다.
- **빠른 설정**: 안드로이드 기본 패널(`QSPanel`)을 고친 것.
  - 기본 타일 `refresh,wifi,bt,sound,autorotation,batteryusage,screenrecord,location,screenshot,cast,battery,airplane`, 한 쪽 6칸×2줄, 전자잉크용 아이콘(`by_ic_qs_*`).
  - 상단 바 왼쪽에 홈·뒤로·새로고침·조명(·A2) 버튼, 오른쪽에 최근 앱 버튼(`haoqing_status_bar_left/right_btn_layout`).
  - 조명은 패널 대신 상단 바 버튼으로 따로 뜨는 창: 꺼짐/차가운/따뜻한/은은한/수동 + 슬라이더 2개(`haoqing_brightness`).
  - 새로고침 설정 창: 갱신 모드(Regal·Normal·Fast·Top speed/A2), 전역 256단계 회색, 대비 슬라이더(`pop_view_refresh_mode`).
- **SystemUI 가 받는 방송** (`com.android.systemui.haoqing.Haoqing`, 권한 없이 동적 등록):
  - `com.haoqing.action.QUICK_SETTINGS` → `instantExpandNotificationsPanel()`
  - `com.haoqing.action.FULL_REFRESH` → 화면 전체 잔상 제거
  - `TOGGLE_RECENTS`, `SHOW_GLOBAL_ACTIONS_MENU`, `SETUP_REFRESH_MODE`(extra `refresh_mode`, `global`), `toggleBrightness`, `closepanel` 등
  - `PackageManagerService.isProtectedBroadcast` 가 `com.haoqing` 로 시작하는 방송을 보호 대상에서 뺀다 → 일반 앱도 보낼 수 있다 **(미확인: 실제 기기에서 일반 앱으로 보내 보기)**.
- **화면 대비**: SystemUI `ContrastController` 가 `Settings.System hq_contrast`(기본 34)를 `persist.hq.hwc.contrast_level` 과 `persist.vendor.hwc.contrast_key = 1.5 − c×0.018`(앞 4글자)로 넘긴다. 하드웨어 합성 단계에서 화면 전체에 적용된다. 크레마와 키 이름은 같고 식은 다르다.
- **앱별 전자잉크 설정** (`android.util.haoqing.PaintConf`, `/data/haoqing/paint.conf`): 앱마다 DPI, 전체 화면, 대비, 밝게 바꾸기(bleach), 갱신 모드, 페이지 버튼을 음량 키로 바꾸기. `Canvas` 의 그림 감마 보정(`ImageProcesser.gammaCorrection`)은 앱별 설정이 있을 때만 돈다.
- **페이지 키**: `KeyEvent.KEYCODE_HAOQING_PAGE_UP = 291`, `KEYCODE_HAOQING_PAGE_DOWN = 292`(키 배치 파일에서 스캔 코드 603·604).
- **앱을 강제 종료하는 경로** (`forceStopPackage`. 크레마에서는 이러면 접근성 서비스가 꺼진다):
  - 떠 있는 버튼의 메모리 정리: 최근 앱 목록의 시스템 앱 아닌 앱(맨 앞 하나 빼고). Pebble Desk 화면은 모두 `excludeFromRecents` 라 대상이 될 가능성이 낮다.
  - 앱별 전자잉크 설정을 바꿀 때(`com.haoqing.action.clearapp` → `Haoqing-Settings` 의 `HaoqingReceiver`), 시스템 글꼴을 바꿀 때.
  - 접근성 서비스 목록을 지우는 코드는 못 찾았다.
- **기본 홈**: 표준 `RoleManager`(`android.app.role.HOME`)·권한 관리 앱 경로 그대로. 메이북 런처로 되돌리는 코드는 못 찾았다.

### E6 (2026-07 출시)

- 리뷰([Good e-Reader 영상](https://www.youtube.com/watch?v=hsDaqbdV1-I) 2:10)의 빠른 설정은 **M6C 와 타일·순서·배치까지 같다**: Refresh Settings · Wi-Fi · Bluetooth · Sound · Rotation Manually · 배터리% / Screen Record · Location · Screenshot · Screen Cast · Battery Saver · Airplane mode. 상단 바 버튼도 같다.
- 하드웨어: 페이지 버튼 2개, 화면 아래 정전식 버튼 1개(helpix), 1.8GHz 4코어·3GB·32GB·2200mAh, 180g, $189.99~199. 칩은 helpix 도 RK3566 으로 추정만 한다. M6(2023)와 거의 같고(helpix: "один к одному") 버튼·패널이 다르다.
- 빠른 시작 안내서(한 장을 12면으로 접은 것, 중국어·영어)는 A2 빠른 갱신 켜기("[A2] super speed refreshing"), 상단 바 【작업 관리】("[Task manage] at top bar")로 작업 정리, 【앱】 화면 왼쪽 【눈송이】("[Snow] icon", 백그라운드 앱 관리)만 언급한다. 빠른 설정 설명은 없다.
- **(미확인)** 페이지 버튼·정전식 버튼의 키 코드, 대비 설정 키, 눈송이가 접근성 서비스에 주는 영향.

### E6 에서 확인한 것 (V2.0.0-2026071718, 2026-10-07 사용자 사진 5장)

Pebble Desk 1.2.0 을 기본 홈으로 쓰는 E6 의 홈·설정·기기 정보 화면과, 기기 정보 위로 빠른 설정이 열린 사진.

- **기기**: `Build.MODEL=E6`, `Build.DEVICE=E6`, `MANUFACTURER=Haoqing`, `BRAND=MEEBOOK`, 안드로이드 11 (API 30), `Build.DISPLAY=V2.0.0-2026071718`. 화면 1072×1448 · 320dpi · 536×724dp. `Device.kind` 는 `메이북`.
- **속성**: `ro.haoqing.brand=haoqing-meebook`, `persist.haoqing.updatemodel=MEEBOOK-E6-RK3566`, `persist.haoqing.pagekeys=41`(M6C 는 0. 버튼 구성 값인 듯), `ro.sf.lcd_density=320`.
- **설정 값**(`Settings.System`): `isLightOn=false`, `light_mode=0`, `screen_brightness=0`, `warm_light` 없음, `haoqing_warm_light=0`, **`hq_contrast` 없음**. 대비는 M6C 와 다른 곳에 두거나 한 번도 바꾸지 않은 것일 수 있다.
  - 홈 사진(11:31)의 상태 줄에는 조명 아이콘 ☀ 이 있고, 그 앞뒤 기기 정보 사진(11:25·11:34)에는 `isLightOn=false` 다. 그 사이에 조명을 켰다 껐는지, 아니면 E6 가 `isLightOn` 을 쓰지 않는지 **확인 필요**(조명을 끈 채 홈에 ☀ 가 보이면 다른 키를 봐야 한다).
- **상단 바**: 홈이 아닌 화면에는 메이북 시스템 상단 바가 보인다. 왼쪽 홈·뒤로·새로고침·조명(꺼짐이면 사선 표시), 오른쪽 Ⓕ 표시·배터리·시각·최근 앱 버튼. 높이는 사진으로 어림해 약 54dp(페블의 크레마 상단 바는 약 43dp). 홈은 `hideStatusBar()` 로 숨긴 자체 상단이 그대로 나온다.
  - 그래서 하위 화면의 세로는 724dp 가 아니라 **약 670dp** 다. 1.2.0 에서는 설정 화면의 마지막 줄 `기기 정보` 가 `Pebble Desk 1.2.0` 아래로 잘렸고(제목 80 + 소제목·줄 11개 + 바닥글 44 ≈ 708dp), 홈도 책이 3권이면 함께 읽는 책 줄 아래가 잘렸다(큰 표지 최소 136dp). → 밀도를 572dp 폭으로 맞춰 고쳤다(아래 표).
- **글꼴**: 시스템 기본 글꼴(`Typeface.DEFAULT`)이 페블의 고딕과 달리 **명조 계열**이다. 같은 sp 에서 글자가 넓고 줄 높이가 커서(함께 읽는 책 제목 16sp 두 줄이 약 57dp), 홈 `함께 읽는 책` 칸(글자 폭 82dp. 페블은 94dp)에 한 줄 5자만 들어가 `수학이 가 / 르쳐 준 …` 처럼 끊긴다.
- **권한**: `자동 추가 꺼짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`. 사용 기록 권한을 주면 홈 하단에 `오늘 28분 · 2일째` 가 나오고, 밀리의서재 E ink(`kr.co.millie.eink`)의 `EPubViewActivity` 가 읽는 화면으로 잡혔다.
- **최근에 뜬 다른 화면**: `kr.co.millie.eink / EPubViewActivity · BookshelfActivity · SplashActivity`, `com.haoqing.wallpaper / DreamActivity`(슬립 화면이 Activity 로 뜬다 → 사용 기록에 남는다), `com.android.systemui / RecentsActivity`(상단 바의 최근 앱 버튼). 빠른 설정 패널은 Activity 가 아니라 남지 않았다.
- **버튼**: `4 KEYCODE_BACK · scan 0` 만 기록됐다(스캔 코드 0 = 상단 바의 뒤로 버튼으로 보인다). 페이지 버튼·정전식 버튼은 사진에 없다. 다음에 그 버튼을 누른 기기 정보 사진을 받는다.
- **빠른 설정**: 기기 정보 화면 위로 열렸다. 먼저 아이콘 한 줄(새로고침·Wi-Fi·BT·소리·회전·배터리)과 손잡이만 보이는 접힌 상태, 당기면 두 줄 타일에 한국어 이름 `새로고침 모드 · Wi-Fi · 블루투스 · 소리 · 수동 회전 · 83% / 화면 녹화 시작 · 위치 · 캡처 · 화면 전송(…지 않음) · 절전 모드 · 비행기 모드` 와 아래 연필(편집)·톱니(설정). M6C 펌웨어의 타일 목록과 같다. `메이북 ›`(`com.haoqing.action.QUICK_SETTINGS`)과 `안드로이드 ›`(`expandSettingsPanel`) 둘 다 이 패널을 연다(사용자 확인). 홈 상단 줄 누르기(`Device.openQuickSettings`)도 그래서 통한다.

## Viwoods AiPaper Mini

선전 Viwoods Technology & Design(深圳, FCC 신청 회사 코드 `2BKO6`)의 8.2인치 필기용 전자잉크 태블릿, 모델 번호 **SE05**(2024-08 출시, 펜·케이스 포함 $429~549). 같은 회사의 10.65인치 AiPaper(조명 없음), 7.8인치 AiPaper Reader 와 펌웨어 계열이 다르다(아래).

### 사양 (리뷰·판매 페이지)

- 화면 8.2" E Ink Carta 1000, 1440×1920(세로 모양 3:4), 제조사 297ppi(대각선으로 계산하면 약 293). 유리 덮개, 조명 20단계(색온도 조절 없음).
- MediaTek MT8788(2.0GHz 8코어, Amazon 상품 설명) · 4GB · 128GB(SD 없음) · 2450mAh · 191×138×5.2mm · 230~234g.
- 안드로이드 13. Google Play 는 기본으로 없고 설정에서 Google 서비스를 켜 깔 수 있다. 자체 앱 장터가 있다.
- 버튼: 화면 아래 정전식 **뒤로·홈·AI** 3개, 오른쪽 위 전원 겸 지문. 페이지 버튼 없음. 가로 화면을 지원하지 않는다(Parka Blogs).
- 펌웨어: 3.x(안드로이드 13), 두 달쯤마다 업데이트. 3.12(2026-01)에서 새로고침 모드에 `Quick Refresh` 추가, **다른 앱 표시 설정(`App Display`: 대비, 연한 색 지우기)** 추가(eWritable).
- 새로고침 모드: Best · Fast · Ultra-fast 세 가지(+3.12 의 Quick Refresh), 세부 조정은 없다.
- **제어 센터** (Viwoods 공식 블로그 설명 그림, eWritable 펌웨어 3.9 글):
  - 화면 **오른쪽 위에서 손가락으로** 끌어내리면 열린다. 같은 자리를 **펜**으로 끌어내리면 Picking 메뉴(자르기 캡처·화면에 쓰기·떠 있는 메모), **왼쪽 위**에서 끌어내리면 손으로 하는 화면 새로고침. 네 손가락으로 끌어내리면 캡처.
  - 화면 전체가 아니라 **오른쪽 절반쯤의 세로 패널**이 위에서 내려온다(왼쪽에 굵은 테두리, 뒤 화면은 그대로 보인다).
  - 위에서부터: 배터리% · 날짜·요일·시각 · 톱니(설정) / 큰 버튼 `WLAN` · `Bluetooth`(짧게 = 켜고 끄기, 길게 = 그 설정 화면) / 원형 버튼 `Refresh` · `WLAN Cast` · `Contrast` · `Button Lock`(아래 정전식 버튼 끄기) · `WLAN Transfer` · `Memo` (+ 캡처·비행기 모드·Picking 켜기) / `Refresh mode`: `Auto Ghosting Removal` 스위치, `Best Display` · `Fast Mode` · `Ultra-fast Mode` / **미니만** `Brightness` 0~20 막대(헤드폰을 꽂으면 음량도).
  - 안드로이드 알림창은 없고, 기본 런처에서는 위에서 끌어내려도 알림이 아니라 이 패널이나 새로고침이 된다(inkBell 설명).
  - Pebble Desk 와의 관계: 기기 정보의 `안드로이드 ›`(`expandSettingsPanel`)로 이 제어 센터가 열린다(사용자 확인). 크레마·메이북 방송으로는 안 열려 홈 상단 줄 누르기는 아무 일도 하지 않지만, 원래 기기처럼 끌어내리기로 열면 되므로 따로 맞추지 않는다. Pebble Desk 홈 위에서 끌어내리기가 한 번에 여는지는 **(미확인)**.
- 다른 런처: Nova Launcher·inkOS 를 안드로이드 설정 › 앱 › 기본 앱 › 홈 으로 바꿔 쓰는 사용자 글이 있다(svartling.net, inkOS).

### 시스템 (다른 개발자들이 미니 실기기에서 본 것)

출처: `jdkruzr/ViwoodsAppDev`(미니에서 빠른 펜을 쓰려고 펌웨어 앱을 디컴파일한 기록, `VIWOODS_APP_DEV.md`), `FoxesRCool1/Margin-Eink-Launcher`(미니용 런처, `docs/decisions/0014`·`0004`), `TrevTV` 블로그(10.65" AiPaper 의 앱·통신 분석). 둘 다 라이선스 없는 참고 자료다.

- **ODM 은 Wisky**: `ro.build.user=wisky`, `ro.build.host=wisky3`, Viwoods 시스템 앱은 모두 `com.wisky.*`(메모 `com.wisky.notewriter`, AI `com.wisky.wiskyai`, OCR `com.wisky.ocr` 등). 커널 4.19, MediaTek.
- **속성**: `ro.eink.model=SE05`, `ro.eink.type=SoftSolution`, `ro.wisky.has_backlight=true`, `persist.eink.mode_default`(기본 갱신 모드, GL16=3). → `Device.kind` 를 나눈다면 `Build.MANUFACTURER == "Viwoods"` 나 `ro.wisky.*` 가 쓸 만하다.
- **시스템 앱**: 런처 `WiskyLauncher`(`/product/priv-app/`, 패키지 이름은 **(미확인)**. AiPaper Reader 는 `com.viwoods.launcher`), 설정 `setting`(Viwoods 설정. 안드로이드 기본 설정 `com.android.settings` 도 남아 있어 직접 열면 뜬다), 메모 `WiNote`·`Wmemo`, 손글씨 키보드 `WSmartIME`, AI `wiskyAi`.
- **전자잉크 API**: 숨은 `android.os.enote.ENoteSetting`(바인더 서비스 `ENoteSetting`). 갱신 모드 `setPictureMode`(3 GL16 · 4 FAST · 17 GC 등), 감마·앱별 연한 색 지우기, T1000 빠른 펜(AutoDraw). 일반 앱도 리플렉션·`service call` 로 부를 수 있다고 한다. Pebble Desk 는 쓰지 않는다.
- **접근성**: Viwoods 의 `FocusMonitorService` 가 접근성으로 원노트·킵 등의 화면을 보고 빠른 펜을 켠다(`persist.sys.focusmonitor.config=1`). 접근성 서비스 자체는 일반 앱도 쓸 수 있다. Pebble Desk 의 자동 추가도 켜졌다(기기 정보 사진).
- **기본 홈**: 안드로이드 설정 › 앱 › 기본 앱 › 홈 으로 바꾼다. Viwoods 설정 화면에는 이 메뉴가 없어 안드로이드 설정을 따로 열어야 한다(Margin 은 `RoleManager.ROLE_HOME` 요청 → `ACTION_HOME_SETTINGS` 순으로 연다).
- **상단 바와 제어 센터**: Margin 은 모든 화면에서 상단 바를 숨기는데, 위에서 끌어내리면 바가 잠깐 나오고 "조명·Wi-Fi 패널이 그 바에 달려 있다"고 적었다. 즉 제어 센터는 SystemUI 상단 바 쪽 동작으로 보인다 **(추정)**. Pebble Desk 홈도 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` 로 상단 바를 숨기므로 **첫 끌어내리기는 바만 꺼내고, 한 번 더 끌어내려야 패널이 열릴 수 있다** **(미확인)**.
- **adb**: 미니도 `adb shell`·`install`·`pull`·`logcat` 이 막혀 있다(`error: not support command`). `adb forward` 와 앱 안의 `service call` 은 된다. AiPaper Reader 에서는 Viwoods 디버그 도구가 쓰는 `persist.wisky.hold_enable_adb` 속성으로 잠금을 푸는 방법이 공개됐다(yowmamasita 블로그 "Bypassing ADB Restrictions"). 키 앞부분이 10.65" AiPaper 모델 번호(`SE03`)라 미니에도 통할 수 있다 **(미확인)**.
- **AI 버튼**(AiPaper Reader, `equwal/assistkey`): 입력 장치 `AI KEY`, 키 `KEY_F1`. 펌웨어가 `Settings.System CustomAiKey` 에 정한 화면을 띄우고 앱에는 잘 안 온다. 미니도 같은지 **(미확인)**.

### 펌웨어 얻기

- **공개된 미니 펌웨어 파일은 못 찾았다**(2026-10-07, XDA·4PDA·MobileRead·GitHub 검색).
- 업데이트 확인은 `https://api.viwoods.com/api/v1/system/version`, 릴리스 노트는 `…/v1/system/details`(TrevTV 블로그). 요청마다 데이터와 비밀 값으로 만든 서명이 붙어, 서명 없이 보내면 `{"code":10032,"status":"error"}` 만 온다(직접 확인). 메이북처럼 모델·버전만 넣어 받을 수 없다.
- 남은 길: (1) 위 adb 잠금 풀기가 미니에서 되면 `framework.jar`·`services.jar`·SystemUI·`WiskyLauncher` 를 `adb pull`, (2) 펌웨어 앱(설정·런처)에서 서명 방식을 찾아 업데이트 서버에 묻기, (3) 기기에서 업데이트를 받을 때 내려받는 주소를 잡기. 부트로더를 풀거나 `mtkclient` 로 통째로 읽는 방법은 기기를 지울 수 있어 하지 않는다.

### 같은 회사 AiPaper Reader 에서 알려진 것 (다른 펌웨어, 미니에 같은지는 **(미확인)**)

AiPaper Reader(7.8", MediaTek Helio G99 MT8781, 펌웨어 1.x · 안드로이드 16)를 루팅해 고친 사람들의 기록(GitHub `magcrider/Viwoods-Notification-Unlocker`, MobileRead).

- **알림 막기**: `services.jar` 의 `NotificationManagerService.enqueueNotificationInternal` 에 허용 목록(`ALLOWED_PKGS`)이 박혀 있어 목록 밖 앱 알림을 버린다(로그 `eink project,Blocked notification from package:`). Pebble Desk 는 알림을 쓰지 않아 상관없다.
- **백그라운드 앱 정리**: `com.viwoods.refresh.service` 가 백그라운드 프로세스를 세게 죽인다. 접근성 서비스(읽는 책 자동 추가)가 꺼지거나 멈출 수 있다 **(미확인)**. 대응은 배터리 최적화 제외(`cmd deviceidle whitelist +패키지`).
- **adb 제한**: 1.3.4 에서 adb 와 버튼 바꾸기(Keymapper)를 막았다가 1.3.8 에서 Viwoods 인증 도구(PC 프로그램)로 기기를 매번 승인해야 쓰게 했다. 접근성 서비스도 중간 베타에서 막혔다가 1.3.8 에서 돌아왔다.
- 부트로더 잠김, AVB 강제. 펌웨어 백업·쓰기는 `mtkclient`(BROM 모드).

### 실기기 사진에서 본 것 (2026-10-07, 홈 화면 1장)

Pebble Desk(1.2.x)를 기본 홈으로 쓰는 홈 화면. 책 1권(밀리의서재 E ink `독서의 기술`), 자주 쓰는 앱 3개(밀리의서재 E ink · 윌라 · Kindle).

- **기본 홈**: 됨. 홈을 누른 뒤 Pebble Desk 가 뜬 것으로 보인다. 시스템 상단 바는 `hideStatusBar()` 로 숨겨지고 자체 상단(시계·`10월 7일 수요일 · 83% · Wi-Fi · BT`)이 나온다. 크레마 로고 자리는 비어 있다(크레마 런처 없음, 예상대로).
- **내비게이션 바 없음**: 앱이 화면 맨 아래까지 쓰고 하단 줄(`모든 앱 › · 오늘 2분 · 4일째 · 톱니`)이 다 보인다. 크레마처럼 아래가 가려지는 일은 없어 보인다.
- **밀도 맞춤**: 기기는 320dpi(720×960dp)인데 짧은 변 1440px → 앱 밀도 403dpi(1dp ≈ 2.52px), 572×762dp 로 페블(572×777dp)과 거의 같은 배치가 그대로 들어간다. 다만 화면이 커서 1dp 가 페블의 약 1.37배(0.21mm, 페블 0.16mm) 크기로 보인다. 글자·줄이 큼직하다.
- **글꼴**: 시스템 기본 글꼴이 고딕 계열로, E6(명조 계열)처럼 줄이 넘치는 일은 없다.
- **오늘 읽은 시간**: `오늘 2분 · 4일째` → 사용 기록 권한을 줬고, 밀리의서재 E ink 의 읽는 화면이 잡힌다.
- **상태 줄에 조명 아이콘 없음**: `Device.kind` 가 `OTHER` 이면 조명 키를 모른다(`lightKeys` 비어 있음). 조명을 켜 둔 상태인지는 사진으로 알 수 없다.
- 그림(표지)은 회색이 고르게 나온다. 대비를 따로 보정하지 않는다.

### 기기 정보 사진 (2026-10-07 21:38, Pebble Desk 1.2.1)

- **기기**: `Build.MODEL=Viwoods AiPaper Mini`, `Build.DEVICE=Viwoods`, `MANUFACTURER=Viwoods`, `BRAND=Viwoods AiPaper Mini`, 안드로이드 13 (API 33), `Build.DISPLAY=3.15.15`(펌웨어 3.15). 화면 1440×1920 · 320dpi(`ro.sf.lcd_density=320`) · 720×960dp, 앱 기준 403dpi · 572×762dp. `기기 종류` 는 `기타`.
- **속성·설정 값**: 1.2.1 의 기기 정보는 크레마·메이북 키만 보여서 Viwoods 고유 값은 나오지 않았다. 메이북 속성(`ro.haoqing.*`)은 모두 없고, `Settings.System` 은 `screen_brightness=1` 만 있다(`isLightOn`·`light_mode`·`warm_light`·`hq_contrast` 없음).
- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`. 접근성 서비스를 켤 수 있다(Viwoods 가 막지 않는다). 홈 사진의 밀리의서재 책은 자동 추가가 아니라 **직접 넣은 것**이었다(아래 2026-10-10 댓글).
- **최근에 뜬 다른 화면**: `kr.co.millie.eink / BookshelfActivity · EPubViewActivity`.
- **버튼**: 기록 없음(아직 누르지 않음).
- **빠른 설정 시험**: `안드로이드 ›`(`expandSettingsPanel`)를 누르면 빠른 설정(제어 센터)이 뜬다(사용자 확인). 크레마·메이북 방송으로는 열리지 않는다. 홈 상단 줄 누르기는 방송만 보내 열리지 않지만, 원래 기기처럼 끌어내리기로 열면 되므로 따로 맞추지 않는다.
- **상단 바**: 하위 화면에는 Viwoods 시스템 상단 바(오른쪽에 시각 · Wi-Fi · 배터리%)가 얇게 보인다(사진으로 어림해 앱 기준 약 21dp). 기기 정보 화면은 맨 아래 빠른 설정 시험 줄까지 다 들어간다.

### 사용자가 알려 준 문제 (2026-10-10 18:25 댓글, 1.4.0)

- **접근성을 켰는데 읽는 책 자동 추가가 안 된다**: 안내대로 접근성을 켰고(기기 정보도 `자동 추가 켜짐`), 밀리의서재·밀리의서재 E ink·리디에서 책을 열어도 들어오지 않는다. 재부팅·재설치해도 같다. 전에 "됐다"고 한 것은 책을 직접 넣은 것이었다.
- 이 기기는 **Play 인증 기기가 아니고** GSF ID 를 등록해 Google 서비스를 켰다고 한다. 접근성 서비스는 Play 인증과 상관이 없어 이것이 원인이라고 보기는 어렵다 **(추정)**.
- Leaf2(켤 수가 없다)와 달리 **켜졌는데 동작하지 않는** 경우다. 생각해 볼 원인 **(미확인)**:
  1. 시스템이 서비스에 연결하지 않거나 곧 끊는다(백그라운드 정리 `com.viwoods.refresh.service` 같은 것, 위 AiPaper Reader 절).
  2. 연결은 되는데 이벤트가 오지 않는다(Viwoods 의 `FocusMonitorService` 처럼 접근성을 쓰는 시스템 서비스와 겹침, 또는 펌웨어가 다른 앱 접근성 이벤트를 거름).
  3. 이벤트는 오는데 이 기기의 이북 앱 화면을 못 읽는다(같은 앱이 페블에서는 되니 가능성은 낮다).
- 1.4.0 기기 정보의 `자동 추가 서비스 기록`(연결·끊김 때, 앱 종료 까닭, 배터리 최적화·대기 그룹·백그라운드 제한)으로 1 과 2·3 을 가를 수 있다. 마지막 댓글에 아직 답하지 않았다.

### 다음에 받을 것

1. 기기 정보 사진은 받았다(위). 기기 정보가 기기 고유 값을 찾아 보이게 고친 뒤, 조명을 켠 채·끈 채 한 장씩 더.
2. Pebble Desk 홈에서 오른쪽 위 끌어내리기가 제어 센터를 여는지(한 번에 열리는지, 상단 바가 먼저 나오고 두 번째에 열리는지). 빠른 설정 시험은 `안드로이드 ›` 로 열림을 확인했다.
3. 정전식 AI 버튼(·뒤로·홈)을 누른 뒤 기기 정보의 키 번호·스캔 코드.
4. 제어 센터를 열었다 돌아온 뒤 `최근에 뜬 다른 화면`(따로 된 Activity 인지).
5. **자동 추가 기록 사진**(1.4.0 다음 판, 기기 정보 › `기록 보기 ›`): `기록 지우기` → 밀리 E ink 에서 책 열기 → 돌아와 찍기. 요약의 `첫 알림`·`받은 알림` 이 없으면 시스템이 알림을 보내지 않는 것(Viwoods 접근성 잠금 의심), 알림은 오는데 `화면 못 읽음`·`창 … (화면 아님)` 뿐이면 화면을 못 읽는 것이다. adb 가 되는지.
   - Viwoods 는 2026 초 AiPaper Reader 베타에서 adb·접근성을 기기마다 잠갔다(설정에는 켜져 있는데 Keymapper 가 동작하지 않음, 고객지원에 요청하면 그 기기를 풀어 준다. MobileRead `t=372886`). 미니도 같다면 Viwoods 고객지원에 시리얼 번호로 잠금 해제를 요청하게 한다 **(추정)**.

## iReader Ocean 5 Pro

중국 掌阅(Zhangyue, 브랜드 iReader)의 7인치 전자잉크 리더. 국내 사용자는 `오션`, `아이리더 오션5` 로 부른다(해외 구매·오픈마켓). 2025-10 에 Ocean 5 · 5 Pro · 5 장기 배터리판 · 5C(컬러) 가 나왔다. 앞 세대 Ocean 4 Turbo 도 같은 모양이다. 모두 Kindle Oasis 처럼 한쪽 손잡이가 두껍고 거기에 페이지 버튼이 있다. **Musnap Ocean / Ocean C**(2025-12, 검은색, 영문판)는 같은 회사가 만든 같은 기기다(설명서의 만든 곳 Shenzhen Zhangyue Technology, SmartOS).

사용자가 보낸 기기 정보로 **Ocean 5 Pro**(흰색, 오른쪽 손잡이에 버튼 두 개)를 확인했다(2026-10-10): `Build.MODEL=Ocean 5 Pro`, `Build.DEVICE=SM07A-CKDA`(모델 번호), `MANUFACTURER`·`BRAND` `iReader`, 안드로이드 14(API 34, 빌드 `UP1A.231005.007`), 화면 1264×1680 · 320dpi(632×840dp) → 앱 기준 354dpi · 571×759dp, `기기 종류` 기타. Musnap Ocean(`SM07A-AKDW`)과 모델 번호 앞부분이 같다. 다른 Ocean 모델(5 · 5C · 4 Turbo)은 기기 정보를 보지 못했다.

### 사양 (IT之家·Musnap 설명서·리뷰)

- 화면 7" E Ink Carta 1300, 1264×1680, 300ppi(5C 는 Kaleido 3 컬러, 흑백 300 · 컬러 150ppi). 조명 30단계, 색온도 조절.
- Ocean 5 Pro: 8코어 · 4GB · 64GB · 2400mAh · 185g. Ocean 5 표준: 4코어 · 4GB · 64GB · 2000mAh · 179g. 장기 배터리판: 1GB · 64GB. Musnap Ocean: 8코어 2.2GHz · 4GB · 64GB · 3000mAh. SD 카드 없음.
- OS: 掌阅 **SmartOS**. Musnap Ocean 은 `SmartOS 3.1.0`, 시스템 버전 `3.1.10.183.0`, 소프트웨어 `3.1.2702.183(127021)`(설명서 그림), 안드로이드 14 · 보안 패치 2024-07(jpwhiteside 리뷰). 다른 앱을 깔 수 있다. Google Play 는 설정 › `Google Settings` 에서 Google 서비스를 켜면 나타난다(설명서·리뷰). 중국판도 같은지는 **(미확인)**.
- 설정(`Mine` 탭): WLAN · Bluetooth · Google Settings · System Settings(Display: 홈 보기·테마·화면 보호기·배경·조명·잠자기 / System: 음량·날짜·언어·입력기·글꼴·화면 회전 / **Auxiliary functions: Global Gestures**·Smart Assistant·펜 버튼·덮개 깨우기 / Privacy / System & Updates) · Read · Notes · Schedule · Apps · Help · About.
- 앱별 표시 최적화: 대비·선명도·전체 새로고침 간격·다크 모드. 새로고침 모드는 The Clearest · Quick · Quick Brush · Intelligent 넷(Good e-Reader).

### 빠른 설정(제어 패널) 여는 법

설명서(27쪽)에는 손동작 설명이 없다. 리뷰·사용기에서 본 것:

- **화면 위에서 끌어내리면 제어 패널**이 열린다(Ocean 4 Turbo 는 밝기·색온도·화면 모드·캡처·회전·BT·Wi-Fi, 知乎). Musnap Ocean 리뷰(jpwhiteside)에는 `swipe down from the top` 로, Ocean C 사용기(클리앙)에는 "상단 중간에서 우측 방향으로 쓸어내리면 오닉스와 비슷한 설정 메뉴" 로 나온다. Ocean 4 Turbo 사용기(知乎)에는 **오른쪽 위에서 끌어내린다**로 되어 있다. 펌웨어마다 자리가 조금 다른 듯하다.
- 오른쪽 위 **모서리**에서 끌어내리면 캡처·노트·작업 전환 패널(Musnap 리뷰). 아래 가장자리에서 끌어올리면 왼쪽 = 작업 관리자, 가운데 = 홈, 오른쪽 = 표시 최적화.
- 손동작은 7방향 + 오른쪽 위 대각선, 모두 8방향을 설정 › 보조 기능 › 전역 손동작(Global Gestures)에서 바꿀 수 있다(클리앙). SmartOS 2.1·2.2 에서 들어온 기능이다.
- 시스템 상단 바(시각 · BT · 조명 · 배터리 · `···`)가 모든 화면 위에 있다(설명서 그림). 그래서 제어 패널은 SystemUI 상단 바의 끌어내리기 동작으로 보인다 **(추정)**. 그렇다면 `expandSettingsPanel` 로도 열릴 수 있다 **(미확인)**.
- Pebble Desk 와의 관계: 홈 상단 줄 누르기는 크레마·메이북 방송만 보내므로 아무 일도 하지 않을 것이다 **(추정)**. 홈은 상단 바를 숨기니(`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) 첫 끌어내리기는 바만 꺼내고 두 번째에 패널이 열릴 수 있다 **(미확인)**. Viwoods 처럼 원래 기기의 끌어내리기에 맡길지, `expandSettingsPanel` 이 열리면 상단 줄 누르기에 쓸지는 기기 정보 사진을 받고 정한다.

### 페이지 버튼

- 오른쪽(돌리면 왼쪽) 손잡이에 2개. "페이지를 넘기는 앱이 아닐 때는 **음량**을 바꾼다"(Musnap 리뷰). 그렇다면 키는 `VOLUME_UP`·`VOLUME_DOWN` 이고, 읽는 앱에서만 펌웨어가 넘김으로 바꿔 주는 것이다 **(추정)**.
- Ocean 5 Pro 기기 정보의 `마지막으로 누른 버튼`: `24 KEYCODE_VOLUME_UP`(scan 115). 아래 버튼은 `VOLUME_DOWN` **(추정)**. Pebble Desk 목록은 `PAGE_UP`·`PAGE_DOWN`·메이북 291·292 만 받으므로 이 버튼으로는 **안 넘어간다**. 음량 키를 넘김으로 쓰면 음량 조절을 빼앗으니 따로 정해야 한다(CLAUDE.md 남은 일).
- 짧게·길게 누르기 동작을 각각 정할 수 있다(Ocean C 사용기: 짧게 = 다음/이전, 길게 = 새로고침/뒤로).

### 시스템 (다른 사람들이 본 것)

- iReader 는 중국판에서 다른 앱 설치·adb 를 막아 왔다. Ocean 2 는 iReader 앱을 죽여 안드로이드 설정으로 들어가 USB 디버깅을 켜는 방법이 공개됐다(GitHub `akhan23wgu/iReader-Zhangyue-USBDebug`, MobileRead). Ocean 4 Turbo 이후(SmartOS 2.2~)는 앱 장터와 APK 설치를 공식으로 허용한다. adb 가 되는지는 **(미확인)**.
- 국내 사용기에서 Musnap Ocean 은 Onyx Go 7 과 거의 같고 "플레이스토어를 선택적으로 켤 수 있다" 고 했다(DC 전자책 갤러리).
- 펌웨어 파일은 찾지 않았다. OTA 는 기기 설정 › 시스템 및 업데이트.

### 실기기 사진에서 본 것 (2026-10-10 00:16, Pebble Desk 1.3.0)

- **기기 정보**(사진 1장, 화면 전체가 들어감): 위 표에 넣었다. `모델 Ocean 5 Pro (SM07A-CKDA)` · `제조사 iReader · iReader` · `안드로이드 14 (API 34)` · `빌드 UP1A.231005.007 release-keys` · `화면 1264×1680 · 320dpi · 632×840dp / 앱 기준 354dpi · 571×759dp` · `기기 종류 기타 · Pebble Desk 1.3.0`.
  - 시스템 속성: `persist.eink.dither256.disable=0` · `persist.sys.dither.contrast=102` · `persist.sys.eink.color.filter=0` · `persist.sys.eink.contrast=30` · `persist.sys.eink.gesture.width=2.0` · `persist.sys.eink.regal=1` · `ro.eink.fpga.bridg…`(잘림).
  - 설정 값: `screen_brightness=255` · `EinkLightStep=0` · `KEY_AUTO_SWITCH_THEME=0` · `clock_wallpaper_lock_screen_refresh_interval=600000` · `down_key_long_function=4` · `eink_wallpaper_name=시스템 캘린더` · `gesture_ins…`(잘림). 조명 키는 `EinkLightStep` 일 가능성이 있다 **(미확인)**. 상태 줄 조명 표시에 쓰려면 조명을 켠 채·끈 채 사진이 더 필요하다.
  - 권한: `자동 추가 꺼짐 · 오늘 읽은 시간 켜짐 · 기본 홈 켜짐`.
  - 하위 화면 위에 SmartOS 시스템 상단 바(왼쪽 시각 `오전 12:16`, 오른쪽 Wi-Fi · `75%` · 배터리)가 얇게 있다. 그래도 기기 정보는 빠른 설정 시험 줄까지 다 들어간다.
- **오늘 읽은 시간**: 켜짐(사용 기록 권한 됨). 최근 화면에 `kr.co.millie.millieshelf/MillieViewerActivity`, `kr.co.kyobobook.KEL/B2BViewerEpubMainActivity` 가 잡혔다. 둘 다 `READER` 정규식에 맞으니 읽은 시간은 쌓인다.
- **읽는 책 자동 추가가 켜지지 않는다**: 접근성 › 다운로드한 앱 › Pebble Desk 가 흐리게 `사용 안함`, 누르면 `제한된 설정 — 보안을 위해 이 설정은 현재 사용할 수 없습니다`. iReader 고유 제한이 아니라 **안드로이드 13+ 의 제한된 설정**이다: 파일(APK)로 직접 깐 앱은 접근성·알림 접근 같은 권한을 켜지 못하게 막는다(앱 장터의 세션 설치는 해당 없음). 같은 화면의 Smart Launcher Gestures 는 흐리지 않다(Play 로 깐 것으로 보인다).
  - 푸는 법: 접근성에서 Pebble Desk 를 한 번 눌러 `제한된 설정` 창을 본 뒤(그래야 `⋮` 에 항목이 생긴다. 에뮬레이터 API 33 에서 확인: 창을 보면 `ACCESS_RESTRICTED_SETTINGS` 가 `deny` → `ignore`), 설정 › 앱 › Pebble Desk › 앱 정보 오른쪽 위 `⋮` › **제한된 설정 허용** → 다시 접근성에서 켠다. SmartOS 앱 정보 화면에 `⋮` 가 있는지는 **(미확인)**.
  - Pebble Desk 1.3.2 는 ⑭ 시작하기 1쪽에 이 순서를 단계로 보이고 `앱 정보 열기 ›` 로 앱 정보를 직접 연다(기기 설정 아이콘을 숨긴 SmartOS 에서도 열릴 것 **(추정)**. 안드로이드 13 설정 앱은 extra `uId` 가 있어야 직접 연 앱 정보에 `⋮` 를 만든다. 14 도 같은지 **(미확인)**). 설정 › 독서 › 읽는 책 자동 추가 창에도 같은 안내와 버튼이 있다.
  - 안 되면 PC 에서 `adb shell appops set com.woody.pebbledesk ACCESS_RESTRICTED_SETTINGS allow`(adb 가 되는 경우) 또는 adb 로 다시 설치(`adb install` 은 제한이 걸리지 않는다).
  - 페블·메이북(안드로이드 11)에는 이 제한이 없다. AiPaper Mini(안드로이드 13)는 같은 제한이 있을 텐데 보고가 없었다 **(미확인)**.

### 다음에 받을 것

1. ~~설정 › 기기 정보 사진~~ 받음(2026-10-10). 빠른 설정 시험 결과는 아직.
2. 기기 정보의 빠른 설정 시험 세 버튼(`크레마 ›` · `메이북 ›` · `안드로이드 ›`) 가운데 무엇이 제어 패널을 여는지.
3. ~~페이지 버튼 키 번호~~ 위 버튼 = `VOLUME_UP`(scan 115). 아래 버튼은 `VOLUME_DOWN` **(추정)**.
4. Pebble Desk 홈에서 위에서 끌어내리기가 제어 패널을 한 번에 여는지.
5. ~~접근성(읽는 책 자동 추가): 제한된 설정을 푼 뒤 켜지는지~~ 풀어서 켰다(2026-10-10 댓글). 하루 뒤에도 켜져 있는지, 1.3.2 의 `앱 정보 열기` 로 연 화면에 `⋮` 가 나왔는지는 아직.
6. 사진의 기기 정보 화면 아래쪽(빠른 설정 시험)과 홈 화면.

## Minimal Phone

미국 Minimal Company 의 4.3인치 전자잉크 **폰**(물리 QWERTY 키보드, 전화·LTE). 이북 리더가 아니라 '방해 없는 폰'으로 나왔다. 2024 Indiegogo, 2025 초 출하, $399~(6GB·128GB / 8GB·256GB). 기기의 모델 번호는 `MP01`(Punkt. 의 2G 폰 MP01 과는 다른 기기). 후속 Minimal Phone 2 는 OLED 라 해당 없다.

### 사양 (리뷰)

- 화면 4.3" 흑백 E Ink 600×800(4:3), 약 230ppi(HelenTech. 대각선으로 계산하면 233). 조명·색온도 조절.
- MediaTek Helio G99 · 6/8GB · 128/256GB(microSD) · 3000mAh · 15W 무선 충전 · 168g. 카메라 앞뒤, NFC, 3.5mm.
- 버튼: 화면 아래 **물리 내비게이션 3개**(뒤로·홈·최근. 키보드 조명이 켜져도 이것은 안 빛난다), 그 아래 QWERTY 35키(단축키 1개, 조명 있음), 왼쪽 옆 음량·**새로고침**, 오른쪽 옆 전원 겸 지문. 페이지 버튼은 없다.
- 안드로이드 14, Google Play 있음. 원래 홈은 글자 목록만 있는 **Minimal Launcher**. AOSP Launcher3(Quickstep)도 들어 있어 바꿔 쓸 수 있다(HelenTech).
- 새로고침 버튼: 누르면 잔상 지우기, **길게 누르면 Minimal 설정 창**(밝기·색온도·키보드 밝기·새로고침 속도 Slow/…/Ultra 3단계). 빠른 모드는 2·3줄마다 한 줄만 갱신해 거칠다(리뷰).
- 2024 Indiegogo 렌더(밝은 몸체, 화면 위 앞 카메라, `9:41` 아이폰식 상단 바, 위젯 홈)는 출하품과 다르다. 출하품은 검은 몸체, 화면 아래 버튼 3개(가운데 로고), 앞 카메라는 키보드 왼쪽 아래(HelenTech·Tom's Guide 사진). 비교할 때는 리뷰 사진을 본다.
- 알림창은 안드로이드 기본 것을 끌어내려 연다(리뷰). 안드로이드는 3버튼 내비게이션 모드인데 화면의 버튼은 숨겼다(Keyboard Vagabond 사용기).

### 기기 정보 사진 (2026-10-10 07:51, Pebble Desk 1.3.1)

- **기기**: `Build.MODEL=MP01`, `Build.DEVICE=MP01`, `MANUFACTURER=ALONG`, `BRAND=Minimal_Phone`, 안드로이드 14 (API 34), `Build.DISPLAY=MP01_20260104_1412`. 화면 600×800 · 240dpi · 400×533dp, 앱 기준 168dpi · 571×762dp. `기기 종류` 는 `기타`.
  - `ALONG` 은 Minimal Company 가 아닌 이름이라 실제로 만든 곳(ODM)으로 보인다 **(추정)**.
- **밀도 맞춤 때문에 작다**: 572dp 폭으로 맞추느라 밀도를 240 → 168 로 낮춰 1dp = 1.05px ≈ 0.11mm 다. 페블(0.16mm)과 이 폰의 다른 앱(240dpi, 0.16mm)보다 **약 0.7배**로 보인다. 16sp 글자가 약 1.9mm(페블 2.5mm). 사진에서도 기기 정보 글자가 상단 바 글자보다 작다. 6~8인치 리더를 기준으로 한 맞춤이라 4.3인치 폰에는 맞지 않는다 → 정할 것(아래).
- **시스템 속성·설정 값**: 기기 고유 값으로 보이는 것은 없다. 나온 것은 대부분 잡음이다: 브랜드 `Minimal_Phone` 을 나눈 낱말 `phone` 이 `cache_key.telephony.phone_account_to_subid`·`gsm.current.phone-type`·`persist.log.tag.GsmCdmaPhone`·`volume_music_headphone` 에, 조명 낱말 `warm` 이 `ota.warm_reset` 에 걸렸다(`DeviceInfoActivity.makerWords`·`VENDOR_KEY`. 1.3.2 에서 고침). 조명은 `screen_brightness=8` 만 있다. 색온도 키는 **(미확인)**.
- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 켜짐 · 기본 홈 꺼짐`. 안드로이드 14 인데 접근성이 켜졌다. 제한된 설정을 풀었는지, 막지 않는 설치 방법이었는지는 **(미확인)**.
- **버튼**: `4 KEYCODE_BACK · scan 158`. 스캔 코드 158 은 리눅스 `KEY_BACK` 이라 화면 아래 **물리 뒤로 버튼**이다(E6 의 상단 바 뒤로는 scan 0).
- **최근에 뜬 다른 화면**: `com.example.minimallauncher / MainActivity`(원래 홈. 패키지 이름이 안드로이드 예제 이름 그대로다), `com.android.launcher3 / RecentsActivity`(최근 버튼 → Launcher3 Quickstep 의 최근 앱 화면).
- **빠른 설정 시험**: `안드로이드 ›` 에 `보냄`(함수를 불렀다)만 보이고 열렸는지는 사진으로 알 수 없다 **(미확인)**. 상단 바가 안드로이드 기본 것이라 기본 알림창이 열릴 것이다 **(추정)**. `크레마 ›`·`메이북 ›` 는 결과 글자가 없다.
- **상단 바**: 하위 화면 위에 안드로이드 기본 상단 바(시각 · 알림 아이콘 / BT · 방해 금지 · Wi-Fi · 배터리 `62%`)가 있다. 그래도 기기 정보는 빠른 설정 시험 줄까지 다 들어간다. 아래 내비게이션 바는 없다.
- 글꼴은 고딕 계열이다(E6 의 명조 문제 없음).

### 다음에 받을 것·정할 것

1. **밀도 맞춤을 화면 크기로 제한할지**: 지금은 짧은 변이 무조건 572dp. 이 폰처럼 작은 화면은 기기 밀도(400dp 폭)를 그대로 쓰면 홈·목록 배치가 들어가지 않으니, 물리 크기 하한(예: 1dp ≥ 0.14mm)을 둘지 정한다. 홈 화면 사진을 먼저 받는다.
2. ~~기기 정보의 잡음 줄이기~~ 고침(1.3.2): 제조사·브랜드 낱말에서 `phone`·`mobile`·`tech` 같은 흔한 낱말(`COMMON_WORDS`)을 빼고, `warm` 은 `warm_reset`·`warmboot` 를 뺀다. 고친 판의 기기 정보 사진을 다시 받는다.
3. `안드로이드 ›` 를 눌렀을 때 알림창이 열렸는지. 홈 상단 줄 누르기에 쓸지 정한다.
4. 새로고침 버튼(짧게·길게)·키보드 단축키의 키 번호. 새로고침 길게로 뜨는 설정 창을 열었다 돌아온 뒤 `최근에 뜬 다른 화면`.
5. 물리 키보드로 홈·목록에서 글자를 치면 어떻게 되는지(Pebble Desk 는 키보드 입력을 받지 않는다).
6. 기본 홈으로 바꿨을 때 홈 버튼·최근 버튼이 그대로 동작하는지.

## Moaan MIX7S

중국 Moaan(墨案, 샤오미 생태계 브랜드)의 7인치 이북 리더. 사용자 댓글로 들어온 기기다(2026-10-10, Pebble Desk 1.3.0).

### 사양 (Good e-Reader)

- 7" E Ink Carta 1200, 1264×1680, 300ppi, 256단계. 조명(색온도 24단계). 새로고침 모드 균형·선명·빠름.
- 4코어 Cortex-A55 1.8GHz · 2GB · 64GB(microSD 없음) · 2300mAh. Wi-Fi · BT 5.1 · USB-C. $319.99.
- 기사에는 안드로이드 11, Google Play 없음(직접 설치나 다른 앱 장터), 중국 독서 앱(WeChat 读书·多看·京东) 기본 설치로 나온다.
- 기기 정보는 안드로이드 14(API 34)·RK3566 이다. 기사 이후 판(또는 업데이트)으로 보인다 **(추정)**.

### 기기 정보 사진 (2026-10-10 14:39, Pebble Desk 1.3.0)

- **기기**: `Build.MODEL=MoaanMIX7S`, `Build.DEVICE=rk3566_ebook`, `MANUFACTURER`·`BRAND` 모두 `rockchip`. 안드로이드 14 (API 34), 빌드 `UQ1A.240205.004.B1 release-keys`. `기기 종류` 는 `기타`.
- **화면**: `1264×1680 · 254dpi · 796×1058dp`, `앱 기준 300dpi · 674×896dp`.
  - 짧은 변 1264px 를 572dp 로 맞추면 밀도는 354 여야 하는데 **300** 이 나왔다. 앱이 밀도를 정할 때(`EinkActivity.attachBaseContext` 의 `displayMetrics`) 짧은 변을 1072px 로 받은 것과 같은 값이다. 기기의 앱별 해상도·DPI 설정(Rockchip 전자잉크 키트의 앱별 DPI) 때문인지 **(미확인)**.
  - 그래서 화면이 674dp 폭으로 그려져, 글자·줄이 맞춤대로보다 **약 0.85배** 작다.
- **시스템 속성·설정 값**: `init.svc.vendor.{health,light,power-aidl,thermal}-rockchip=running`(Rockchip 키트). Moaan 고유 설정 값:
  - `APPS_WHITE_LIST_KEY=["com.moan.sdmanage","info.plate…`(목록 앞부분만 보인다) — 백그라운드에서 살려 두는 앱 목록으로 보인다 **(추정)**.
  - `APPS_HIDDEN_LIST_KEY=["com.google.android.verifier",…` — 숨긴 앱 목록.
  - `gesture_hidden=1`, `long_side_key_a=0`·`long_side_key_b=5`, `wallpaper_path_key=["/mnt/sdcard/Wallpaper/IMG_3025…`·`wallpaper_type_key=3`, `screen_brightness=3`.
- **권한**: `자동 추가 켜짐 · 오늘 읽은 시간 꺼짐 · 기본 홈 켜짐`.
- **버튼**: `25 KEYCODE_VOLUME_DOWN · scan 0` → 페이지 버튼이 음량 키로 온다(iReader Ocean 과 같다).
- **최근에 뜬 다른 화면**: 사용 기록 권한이 없어 안 나옴.
- **빠른 설정 시험**: `안드로이드 ›` 에 `보냄`. `크레마 ›`·`메이북 ›` 는 결과 글자 없음.
- 시스템 글꼴이 손글씨체라 앱 글자도 그 글꼴로 보인다.

### 사용자가 알려 준 문제 (2026-10-10 댓글, 1.3.0)

1. **설정 › 독서 › 오늘 읽은 시간을 누르면 `설정이(가) 계속 중단됨`**: 앱이 여는 사용 기록 액세스 화면(`Settings.ACTION_USAGE_ACCESS_SETTINGS`)에서 **시스템 설정 앱이 멈춘다**(사진). Pebble Desk 쪽은 화면을 열었을 뿐이라 실패를 알 수 없다. 그래서 오늘 읽은 시간을 켤 수 없다.
   - 1.4.0 기기 정보의 시스템 화면 시험으로 확인(2026-10-10 19:10 댓글): `사용 기록 ›`(목록)은 그대로 멈추고, **`이 앱 기록 ›`(이 앱만의 사용 기록 화면, 같은 동작 + `package:` 주소)은 열려** 거기서 허용하니 오늘 읽은 시간이 켜졌다.
   - → 다음 판부터 앱이 처음부터 이 앱만의 화면을 연다(그 화면이 없는 기기는 목록, `ReadingLog.openAccessSettings`).
   - PC 우회(필요 없어짐): `adb shell appops set com.woody.pebbledesk GET_USAGE_STATS allow`.
2. **읽는 책 자동 추가를 켰는데 다시 꺼지는 때가 있다**: 1.3.0 은 앱 안의 켜짐 값과 접근성이 따로라(1.3.1 에서 하나로 함) 어긋나 보였을 수 있다. 실제로 접근성 서비스가 꺼진다면, 백그라운드 정리로 앱이 강제 종료돼 시스템이 서비스를 끄는 것으로 보인다(`APPS_WHITE_LIST_KEY` 에 없는 앱) **(추정)**.
   - 2026-10-10 18:25 기준: 지금은 켜져 있고, 사용자가 메모리 정리를 한 적은 없다고 한다. 꺼진다면 사용자 정리가 아니라 시스템의 자동 정리일 것이다 **(추정)**.
   - 사용자 답(2026-10-10 19:10): 재부팅하면서 백그라운드에서 앱이 꺼진 것 같다. **Moaan 설정에서 Pebble Desk 를 백그라운드에서 꺼지지 않게 바꾼 뒤로는 괜찮다**. 어느 화면의 어떤 설정인지는 **(미확인)**(`APPS_WHITE_LIST_KEY` 에 들어가는지는 다음 기기 정보 사진의 자동 추가 서비스 기록 끝에 보인다).

### 다음에 받을 것

1. 최신 판(1.4.0)의 기기 정보 사진: `앱 기준` 밀도가 그대로 300 인지와 `… 로 셈`(앱이 밀도를 정할 때 본 크기), 홈 화면 사진.
   - (받음) 시스템 화면 시험: 목록은 멈추고 이 앱만의 화면은 열린다 → 앱이 그 화면을 열게 바꿨다.
2. 자동 추가가 또 꺼지면 그때의 기기 정보 `자동 추가 서비스 기록`(끊긴 때·앱 종료 까닭·배터리 최적화·대기 그룹·백그라운드 제한, `APPS_WHITE_LIST_KEY` 에 들었는지).
3. 사용자가 바꾼 '백그라운드에서 꺼지지 않게' 설정 화면 사진(README·안내에 쓸 경로).
4. 자동 추가가 꺼졌을 때 안드로이드 접근성 화면에서 Pebble Desk 가 꺼져 있는지(앱 표시만 꺼진 것인지).
5. 옆 페이지 버튼 두 개의 키 번호(위·아래), `long_side_key` 설정 화면.

## Onyx BOOX Leaf2 (사용자 보고)

오닉스(Onyx) BOOX 의 7인치 이북 리더(2022). 리뷰 기준 안드로이드 11, 오닉스 자체 런처를 얹은 안드로이드이고 Google Play 는 설정에서 켜야 한다. 기기 정보 사진은 아직 없어 **한눈에** 표에는 넣지 않는다.

### 사용자가 알려 준 문제 (2026-10-10 댓글 2개, 사진 1장)

- 시작하기·설정에서 접근성을 켜려 하면(시스템 접근성 › Pebble Desk › `Use Pebble Desk`) 안드로이드의 확인 창 **`Allow Pebble Desk to have full control of your device?`** 가 뜨는데, 설명과 두 권한(`View and control screen`, `View and perform actions`) 아래에 **`Allow`·`Deny` 단추가 보이지 않는다**(창 아래쪽이 비어 있다). 그래서 켤 수 없다.
- 이 창은 안드로이드 설정 앱의 것(안드로이드 11 `AccessibilityServiceWarning`)이다. 단추가 그 빈자리에 있는데 글자색(테마의 강조색)이 전자잉크에서 흰색에 가깝게 그려져 안 보이는 것으로 보인다 **(추정)**. 제한된 설정(안드로이드 13+)과는 다르다(Leaf2 는 11).
- Pebble Desk 가 고칠 수 있는 화면이 아니다. 해 볼 것:
  1. 권한 목록 바로 아래 빈 곳(첫 단추 `Allow` 자리)을 눌러 본다. 그 아래가 `Deny` 라 잘못 눌러도 창이 닫힐 뿐이다 **(미확인)**.
  2. BOOX 앱 최적화(설정 앱에 대비·진하게)로 단추 글자가 보이게 한다 **(미확인)**.
  3. PC 에서 켠다: `adb shell settings put secure enabled_accessibility_services com.woody.pebbledesk/com.woody.pebbledesk.ReaderWatchService` 와 `adb shell settings put secure accessibility_enabled 1`.
- 처음 보고(15:42)에서 "권한을 아무리 주고 싶어도 안 된다"고 한 것이 이것이다. 시작하기에서 켜지 못해 나갈 수 없던 문제는 1.4.0 의 `나중에 하기` 로 풀었다.
- 2026-10-10 18:25 기준: 위 1 을 해 봤는데 **빈자리를 다 눌러도 반응이 없다**. 단추가 안 보일 뿐 아니라 눌러도 안 되므로, 창 아래가 잘려 단추가 화면 밖에 있거나 창이 터치를 받지 않는 것일 수 있다 **(추정)**. 남은 길은 2(앱 최적화)·3(adb).
- 같은 사용자의 **BOOX Poke6 에서는 된다**. 다른 오닉스 기기(Go 10.3 Lumi)도 된다는 보고가 있다. Leaf2 의 펌웨어(또는 화면 크기·회전)에서만 생기는 문제로 보인다 **(추정)**.

### 다음에 받을 것

1. 기기 정보 사진(안드로이드 버전·밀도·키 번호).
2. ~~빈자리를 눌렀을 때 켜지는지~~ 안 된다(18:25). 접근성 확인 창 전체 사진(화면 아래 끝까지), 화면을 돌리면 단추가 보이는지.
3. adb 를 쓸 수 있는지(위 3 의 명령으로 켤 수 있는지).

## Onyx BOOX 펌웨어 (Leaf2·Poke6·Palma2·Tab Ultra C Pro 에서 확인)

### 펌웨어 얻기·풀기

- 업데이트 서버: `GET http://data.onyx-international.cn/api/firmware/update?where={"buildNumber":0,"buildType":"user","deviceMAC":"","lang":"en_US","model":"Leaf2","submodel":"","fingerprint":""}`(`where` 는 URL 인코딩). 답의 `downloadUrlList` 가 다운로드 주소, `md5`·`size`·`fingerprint` 도 온다. 없으면 빈 답.
  - Leaf2: V4.0(`2025-04-02_13-00_v4.0-rel_77d3ae190`, 1.76GB, `en_US`). Poke6 은 `en_US` 로는 빈 답이고 `zh_CN` 으로만 3.5.4(2024-10-17) 가 왔다. Poke6S 는 `zh_CN` 으로 v4.0-beta4.
  - 2026-10-10 에 본 다른 기종(모두 `BooxKeys.csv` 에 키 있음): Poke5·Leaf3C 3.5.4(`zh_CN`), Page·Tab Mini C 4.0, Palma 4.0.2, Note Air3 4.1.1, Go 10.3·Go Color 7·Go6·Note Air4 C 4.2, Palma2 4.2(2026-08), Tab Ultra C Pro 4.2.1(2026-08).
  - `firmware.boox.com`(중국 CDN) 주소는 연결이 멈추곤 한다. `curl -C - --speed-limit 30000 --speed-time 30` 으로 이어받기를 되풀이하면 받아진다. `firmware-us.boox.com` 에는 같은 파일이 없다(404).
- 받은 파일은 `~/Downloads/Pebble Desk/boox/<기종>/update.upx`(+ `source.txt`: 주소·md5). 푼 것은 분석 뒤 지웠다.
- 파일 `update.upx` 는 AES-128-CFB 로 암호화돼 있다. 기종별 키·IV 는 GitHub `Hagb/decryptBooxUpdateUpx` 의 `BooxKeys.csv`(Leaf2·Poke6 둘 다 있다). 푼 결과는 A/B 전체 OTA(`payload.bin`)라 메이북과 같은 순서로 푼다.

```bash
openssl enc -d -aes-128-cfb -K <키> -iv <IV> -nopad -in update.upx -out update.zip   # 첫 4바이트가 PK 면 맞는 키
python3 tools/ota_payload.py update.zip img system system_ext product
7zz x -snl -ofs/system img/system.img
jadx --no-res -d src/framework fs/system/system/framework/framework.jar
```

- BOOX 런처는 `system/priv-app/kcb-release`(패키지 `com.onyx`). Onyx 가 더한 시스템 코드는 `framework.jar` 의 `android.onyx.*`.

### 기종·버전별로 맞춰 본 것

| | Poke6 3.5.4 | Leaf2 4.0 | Palma2 4.2 (휴대폰형) | Tab Ultra C Pro 4.2.1 (태블릿형, 런처를 Kotlin 으로 새로 씀) |
|---|---|---|---|---|
| 얼리기 = `DISABLED_USER`(3), Onyx·시스템 앱 제외 | 같음 | 같음 | 같음 | 같음 |
| 런처 `com.onyx`(`priv-app/kcb-release`) | 같음 | 같음 | 같음 | 같음 |
| 받는 곳 `onyx.action.open_frozen_app`, extra `args_pkg`·`args_class`·`args_user_id`, `Application.onCreate` 에서 등록 | 같음 | 같음 | 같음 | 같음 |
| 데이터 창구 `com.onyx.system.database.ContentProvider`(exported, 권한 없음) | 같음 | 같음 | 같음 | 같음 |
| 얼리기 설정 `com.onyx.app.freeze.page` | 같음 | 같음 | 같음 | 같음 |
| 매니페스트의 BOOT_COMPLETED 받는 곳 | 없음 | 없음 | 없음 | 없음 |

- 같은 회사가 2024-09 ~ 2026-08 사이에 낸 3.5 ~ 4.2 계열이 모두 같아, 다른 기종도 같을 것으로 본다 **(추정)**. 확인 스크립트는 펌웨어마다 `framework.jar` 의 `ApplicationFreezeHelper`, 런처의 `EACStatusChangedReceiver`·`BroadcastHelper`·`ContentBrowserApplication` 만 `jadx --single-class` 로 풀어 본다.

### 앱 얼리기 (2026-10-10 댓글: 자주 쓰는 앱·책의 앱이 사라진다, Leaf2·Poke6)

- 얼리기 = 앱을 **꺼 둔다**(`setApplicationEnabledSettingAsUser(pkg, COMPONENT_ENABLED_STATE_DISABLED_USER)`, `android.onyx.utils.ApplicationFreezeHelper`). 숨김(hidden)·정지(suspend)가 아니다. Onyx·시스템 앱과 `appFreezeWhiteList` 는 얼리지 않는다. 단 Google Play 끄기(`disableGooglePlay`, Play 스토어·GMS·GSF)도 같은 `DISABLED_USER` 라, Pebble Desk 는 시스템 앱을 얼린 앱으로 보지 않는다.
- 언제: 앱마다 `autoFreeze` 설정(BOOX 설정의 얼리기 관리). 그 앱에서 다른 앱으로 넘어가면 얼릴 차례에 넣고, 약 30초마다 보다가 그 앱이 화면 맨 위가 아니고 포그라운드 서비스도 없으면 작업을 지우고 끈다(`ApplicationFreezeController`). `새로 설치한 앱 얼리기`(`isFreezeNewlyInstalledApplication`)가 켜져 있으면 설치하는 앱마다 켜진다.
- 그래서 앱을 쓰는 동안만 런처 조회(`queryIntentActivities`)에 나오고, 끄고 나면 빠진다. 꺼 둔 앱은 `getPackageInfo` 로는 찾아져 Pebble Desk 설정에서 지워지지는 않는다(목록에서만 사라짐).
- 얼린 앱 열기: BOOX 런처가 동적으로(권한 없이) 받는 방송 `onyx.action.open_frozen_app`(extra `args_pkg`·`args_class`·`args_user_id`)을 받으면 앱을 풀고 연다(`EACStatusChangedReceiver`). BOOX 플로팅 버튼은 같은 일을 `com.onyx.floatbutton.open.apps` 로 한다.
- Pebble Desk 는 `com.onyx` 가 있으면 앱 단위로 꺼진(`DISABLED_USER`) 앱의 실행 화면도 목록에 넣고, 누르면 위 방송을 보낸다(아래 표). 실기기 **(미확인)**.
- BOOX 런처 띄워 두기: 방송을 받는 곳은 런처가 떠 있을 때 코드로 등록한 것이고(`ContentBrowserApplication` → `EACStatusChangedReceiver`), 런처에는 부팅 완료를 받는 매니페스트 항목이 없어 Pebble Desk 가 기본 홈이면 부팅 뒤 꺼져 있을 수 있다(시스템 UI 도 사용자가 `앱 최적화` 를 누를 때만 런처를 부른다). 공개 서비스 `EACService` 는 붙으면 포그라운드 알림을 띄워 쓰지 않는다. 대신 권한 없이 열린 데이터 창구 `com.onyx.system.database.ContentProvider` 에 불안정(unstable) 연결만 해 둔다(`Device.keepBooxLauncher`). 창구는 시작할 때 DB 라이브러리 초기화만 한다(`FlowManager.init`). 안드로이드가 런처를 띄우고 이 앱과 같은 중요도로 살려 둔다(불안정 연결도 `OomAdjuster` 가 똑같이 센다. 런처가 죽어도 이 앱은 죽지 않는다). 홈이 보일 때·잠금이 풀릴 때·화면이 켜질 때 다시 잡는다(잠금 전에는 런처를 띄울 수 없다). 연결은 한 줄(단일 스레드)로 차례대로 잡고, 못 잡으면 전 연결을 둔다.
- 얼린 앱을 누르면: 연결을 다시 잡아(런처가 죽었으면 띄운다) 방송을 보낸다. 막 뜬 런처는 창구를 내놓은 뒤 `Application.onCreate` 에서 받는 곳을 등록하므로 그 사이 방송을 놓칠 수 있다. 그래서 2초 뒤에도 홈이 초점을 갖고 있으면 — 앱이 이미 풀렸으면 직접 열고, 아직 얼려 있으면 한 번 더 보낸다. 그래도 2초 안에 안 열리면 안내 창(`얼리기 설정 열기` → `com.onyx.app.freeze.page`, `앱 정보`).
- 얼린 앱 판정에서 뺀 것: 시스템 앱(`FLAG_SYSTEM`·`FLAG_UPDATED_SYSTEM_APP`), 이름에 `com.onyx` 가 든 앱(펌웨어 `isOnyxOrSystemApp` 과 같게), Google Play·GMS·GSF(`disableGooglePlay` 가 같은 방법으로 끈다. 누르면 사용자가 끈 Play 가 다시 켜진다).
- codex 리뷰(2026-10-10, gpt-5.6-sol high): 방법은 "조건부"로 맞음. 반영한 것 — 누를 때 다시 띄우기·재시도, 연결 직렬화·실패 시 전 연결 유지, 시스템 판정을 펌웨어와 같게, Google Play 제외. 기각한 것 — "얼리기·풀기 때 앱 변경 순번이 안 바뀐다"(Leaf2 `PackageManagerService.setEnabledSetting` 명령 덤프에 `updateSequenceNumberLP` 호출이 있다), "런처를 늘 띄워 두는 비용"(기본 홈이 BOOX 런처일 때와 같은 상태).
- 재현: 에뮬레이터에서 `pm disable-user <패키지>` 가 얼리기와 같다(시스템 앱이 아닌 시험 앱으로. 끈 직후 재부팅하면 상태가 아직 파일에 안 써져 풀린다). BOOX 런처 대신 가짜 `com.onyx` 앱(같은 이름의 데이터 창구, `Application.onCreate` 에서 `onyx.action.open_frozen_app`·`com.onyx.app.freeze.page` 받는 곳을 등록해 로그를 남기고 자기 화면을 띄움)을 깐다. "열림"은 `appops set com.onyx SYSTEM_ALERT_WINDOW allow`(백그라운드에서 화면 띄우기 허용), "안 열림"은 `deny` 로 흉내 낸다. 꺼진 런처는 `am force-stop com.onyx` 나 재부팅으로 만든다.

## Pebble Desk 에 넣은 것

| 내용 | 기기 구분 | 확인 |
|---|---|---|
| 상단 줄을 누르면 `com.epd.drop_down` 과 `com.haoqing.action.QUICK_SETTINGS`(`com.android.systemui` 지정)를 둘 다 보낸다(`Device.openQuickSettings`). 받는 쪽이 없는 방송은 아무 일도 하지 않는다 | 가리지 않음 | 페블은 그대로 열림. E6 는 메이북 방송으로 패널이 열림(사용자 확인). AiPaper Mini 는 방송으로는 안 열리고 기기 정보의 `안드로이드 ›` 로 열린다. 원래 기기도 끌어내리기로 여니 상단 줄 누르기는 따로 맞추지 않고 끌어내리기에 맡긴다 |
| 기기 종류 `Device.kind`: 크레마(`Build.DEVICE`) → 메이북(`ro.haoqing.brand` 있음) → 기타. 모델명을 몰라도 메이북 전 기종을 잡는다. 설정 › 기기 정보의 `기기 종류` | — | 에뮬레이터에 `ro.haoqing.brand` 를 넣어 `메이북` 확인. E6 실기기에서도 `메이북` |
| 조명 켜짐: 크레마 `screen_brightness`·`warm_light` > 0, 메이북 `isLightOn == "true"`(꺼도 밝기 값은 남는다). 그 키를 지켜보다 바뀌면 상태 줄을 다시 그린다 | 기기 종류 | 에뮬레이터에서 `isLightOn` 을 바꿔 아이콘이 나타나고 사라지는 것 확인. E6 는 `isLightOn=false` 인 사진과 ☀ 가 보이는 홈 사진이 섞여 있어 **확인 필요** |
| 페이지 버튼: 메이북 키 291(위)·292(아래)와 표준 `PAGE_UP`·`PAGE_DOWN` → `EinkActivity.onSwipe`. 모든 목록 화면에 적용. 누르고 있을 때 오는 반복은 넘기지 않는다 | 가리지 않음 | 표준 키·길게 누르기(한 쪽만)는 에뮬레이터에서 확인. 단 표준 PAGE 키는 이동 키라, 터치 모드를 벗어나면서 포커스 받을 뷰가 생기면 첫 누름을 안드로이드가 먹는다(`ViewRootImpl.checkForLeavingTouchModeAndConsume`). 에뮬레이터 모든 앱 화면에서 그랬다. 291·292 는 일반 안드로이드에 없는 키 코드라 메이북 실기기에서만 확인 가능 **(미확인)** |
| 화면 폭 572dp 맞춤: 짧은 변이 572dp 가 되도록 앱 밀도를 다시 정한다(`Ui.designDensityDpi`, `EinkActivity.attachBaseContext`). E6 는 320 → 300dpi, 572×772dp 로 페블과 같은 물리 크기. 기기 정보의 `화면` 줄에 기기 값과 `앱 기준` 값을 함께 보인다 | — | E6 크기 에뮬레이터(536×724dp)에서 1.2.0 은 책 3권일 때 **함께 읽는 책 줄 아래 32dp 가 잘렸고**(큰 표지가 최소 136dp 에 걸려 책 자리가 58dp 넘침), 상단 바 54dp 를 흉내 내면(`wm size 1072x1388`) 설정 마지막 줄이 40dp 잘렸다. 밀도 맞춤 뒤 셋 다 들어감(설정은 상단 바 54dp 를 빼도 약 714dp 라 708dp 가 여유 6dp 로 들어간다). 페블 실기기 **(미확인)**. AiPaper Mini(1440px → 403dpi, 572×762dp) 실기기 홈 사진에서 그대로 들어감. Minimal Phone(4.3", 600px → 168dpi)은 거꾸로 밀도를 낮춰 모든 것이 페블의 약 0.7배로 작아진다(아래 기기 절) |
| 기기 정보 화면(사진으로 찍어 보내는 진단). 시스템 속성·설정 값은 아는 키에 더해 `getprop`·`Settings.System` 전체에서 전자잉크·조명 낱말이나 제조사 이름이 든 키를 찾아 있는 것만 보인다(1.2.1 까지는 크레마·메이북 키만 보여 AiPaper Mini 의 고유 값이 안 나왔다). 제조사·브랜드 낱말 중 `phone` 같은 흔한 낱말은 쓰지 않는다(Minimal Phone 에서 전화 관련 기본 키만 잔뜩 나왔다) | — | 에뮬레이터(M6C 이름)에서 한 화면에 들어감, 볼륨 키가 `24 KEYCODE_VOLUME_UP` 로 기록됨. E6 실기기에서도 상단 바 아래에 다 들어감 |
| 안드로이드 13+ **제한된 설정** 안내: ⑭ 시작하기 1쪽의 `켜는 방법` 단계(접근성에서 눌러 보기 → 막힌 창 닫기 → `앱 정보 열기` › ⋮ › 제한된 설정 허용 → 다시 켜기)와 설정 › 독서의 자동 추가 창(늘 `앱 정보 열기`). 앱 정보는 `ACTION_APPLICATION_DETAILS_SETTINGS` + extra `uId`(안드로이드 13 설정 앱 `AppInfoDashboardFragment.getUid()` 는 인자 `uid` 나 extra `uId` 만 읽어, 없으면 -1 로 묻다 실패해 ⋮ 메뉴가 빠진다) | 안드로이드 13+ | 에뮬레이터 API 33(`CremaPebble_A13`): 파일 앱으로 깐 APK 가 `packageSource=3`·`ACCESS_RESTRICTED_SETTINGS: deny` 로 막힘 → 막힌 창 → 시작하기에 안내·`앱 정보 열기` → ⋮ `Allow restricted settings` → 접근성 켜짐 → `켜짐 ✓`. `uId` 없이 열면 ⋮ 가 없다(설정 › 앱 목록에서 들어가면 있다). iReader(안드로이드 14) **(미확인)** |
| BOOX 앱 얼리기: `com.onyx`(BOOX 런처)가 있으면 BOOX 가 얼린 앱(위 판정)의 실행 화면도 앱 목록에 넣는다(앱이 스스로 끈 실행 화면은 빼고). 누르면 `onyx.action.open_frozen_app` 을 `com.onyx` 로 보내 BOOX 런처가 풀고 연다(`AppStore.openFrozen`, 재시도·안내 창). 방송을 받도록 BOOX 런처를 띄워 둔다(`Device.keepBooxLauncher`). 숨긴(hidden) 앱도 지워진 앱으로 보지 않는다 | `com.onyx` 설치 여부 | 에뮬레이터 API 30 + 가짜 `com.onyx`: 얼린 시험 앱이 자주 쓰는 앱에 남음, 시스템 앱(Calendar)·실행 화면만 끈 앱은 안 나옴, 가짜 런처가 없으면 전처럼 빠짐. 누르기: 열림 → 방송 1번 / 안 열림 → 재시도 뒤 안내 창 / 런처 죽은 채로 → 다시 떠서 열림 / 풀렸는데 안 열림 → 직접 열림. 런처를 끄고 홈 복귀·재부팅(기본 홈 Pebble Desk)·화면 끄고 켜기 → 런처가 다시 뜸. BOOX 실기기 **(미확인)** |

넣지 않은 것: `com.haoqing.action.FULL_REFRESH`(잔상 제거 버튼).

- **안드로이드 기본 빠른 설정 펼치기**: `getSystemService("statusbar")` 의 `expandSettingsPanel()`(숨은 함수, `EXPAND_STATUS_BAR` 는 일반 권한)를 일반 앱(릴리스, targetSdk 34)이 리플렉션으로 불러 안드로이드 11 에뮬레이터와 페블에서 열렸다(페블은 크레마 빠른 설정 앱이 아니라 밝기·음량·대비가 있는 시스템 알림창). 메이북 빠른 설정은 안드로이드 기본 패널을 고친 것이라 메이북 방송 대신 이 방법으로도 열릴 가능성이 크다 E6 실기기에서 열렸다(메이북 방송과 같은 패널). Viwoods AiPaper Mini 에서도 제어 센터가 열렸다. 지금은 기기 정보의 시험 버튼에만 쓴다.

## 에뮬레이터로 화면 크기 보기

- 펌웨어 자체는 에뮬레이터에서 부팅되지 않는다(Rockchip 커널·HAL). 펌웨어 앱만 옮겨 심는 것도 수정된 `framework.jar`(`android.util.haoqing`, `EinkManager`)·플랫폼 서명에 묶여 있어 사실상 시스템 이미지를 통째로 바꾸는 일이다. 전자잉크 갱신·대비는 어차피 재현되지 않는다.
- 화면 규격만 맞춘 AVD 로 배치를 본다. 예: `MeebookE6_A11` = `system-images;android-30;google_apis;arm64-v8a`, `hw.lcd.width=1072`, `hw.lcd.height=1448`, `hw.lcd.density=320`, `hw.mainKeys=yes`(내비게이션 바 없음). 페블은 `CremaPebble_A11`(같은 이미지, `hw.lcd.height=1456`, `hw.lcd.density=300`, `hw.mainKeys=yes`). AVD 는 실제 기기와 같게 만든다(안드로이드 버전·해상도·밀도·내비게이션 바 없음). 예전 `CremaPebble`(안드로이드 15·1448px·내비게이션 바 있음)은 앱 높이가 실기기보다 45px+ 작아 홈 책 자리가 잘려 보인다.
- 깔고 홈으로: `adb install -r …apk`, `adb shell cmd package set-home-activity com.woody.pebbledesk/.HomeActivity`.
- **기기 이름을 메이북으로 바꾸기**(앱의 `Build.*`·`Device.kind` 시험용). 에뮬레이터 `-prop` 으로는 `ro.product.*` 가 바뀌지 않는다. 시스템을 쓸 수 있게 켜서 `build.prop` 을 고친다.
  ```bash
  emulator -avd MeebookE6_A11 -writable-system
  adb root && adb remount && adb reboot        # 처음 한 번은 다시 켜야 덮어쓰기가 된다
  adb root && adb remount
  adb shell sed -i -e 's/^ro.product.product.model=.*/ro.product.product.model=M6C/' \
      -e 's/^ro.product.product.device=.*/ro.product.product.device=M6C/' \
      -e 's/^ro.product.product.manufacturer=.*/ro.product.product.manufacturer=Haoqing/' \
      -e 's/^ro.product.product.brand=.*/ro.product.product.brand=Haoqing/' /product/build.prop
  adb shell 'echo ro.haoqing.brand=haoqing-meebook >> /product/build.prop'
  adb reboot                                   # 이후에도 -writable-system 으로 켠다
  ```
  조명은 `adb shell settings put system isLightOn true|false` 로 흉내 낸다.
- 이름만 바뀔 뿐 SystemUI 는 일반 안드로이드 것이라, 메이북 방송(`QUICK_SETTINGS`·`FULL_REFRESH`)을 받는 쪽이 없다. 빠른 설정·전자잉크 동작은 에뮬레이터로 시험할 수 없다.

## 새 기기를 조사할 때

1. 앱 설정 › 기기 정보 사진. 버튼마다 눌러 키 번호를 받고, 기본 런처에서 빠른 설정을 열었다 돌아와 최근 화면을 찍고, 빠른 설정 시험 세 버튼 중 무엇이 열리는지 듣는다.
   - adb 가 있으면 빠른 설정을 연 직후 `adb shell dumpsys activity broadcasts` 의 `Historical broadcasts` 에서 오간 방송 이름·보낸 앱·받은 앱을 볼 수 있다(페블: `com.epd.drop_down` → `com.inno.quicksetting`). 같은 출력의 등록된 수신기 목록에는 SystemUI 등이 받는 방송 이름이 다 있다.
2. `adb shell getprop` 전체, `settings list system`, `pm list packages -f`, `wm size`·`wm density`.
3. 펌웨어 또는 기기의 `framework.jar`·`services.jar`·SystemUI·설정 앱을 jadx 로 풀어 본다:
   - 빠른 설정을 여는 방법(SystemUI 가 받는 방송, `isProtectedBroadcast` 예외)
   - 대비·밝게 바꾸기(bleach)가 앱 그림에 어떻게 걸리는가
   - 앱을 강제 종료하는 경로(`forceStopPackage`), 백그라운드 제한
   - 기본 홈을 막거나 되돌리는 코드
   - 페이지 버튼 키 코드(키 배치 파일 `usr/keylayout/*.kl`, `KeyEvent`)
4. 화면 규격 AVD 에 Pebble Desk 를 깔아 배치를 본다.
5. 이 문서의 **한눈에** 표에 열을 더하고 기기 절을 쓴다.
