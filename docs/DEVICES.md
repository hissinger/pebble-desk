# 기기 호환 노트

Pebble Desk 를 크레마 페블 말고 다른 전자잉크 기기에서 쓸 수 있는지 조사한 기록이다. 새 기기를 조사하면 **한눈에** 표에 열을 더하고 기기 절을 하나 더 쓴다.
방법: 기기에서 `getprop`·`settings`·`adb pull` 한 시스템 파일, 제조사 OTA 펌웨어를 풀어 jadx 로 디컴파일, 리뷰 영상·설명서.
2026-10 기준. 실제 기기에서 확인하지 않은 것은 **(미확인)**, 같은 회사 다른 기종에서 미루어 본 것은 **(추정)** 으로 표시했다.

## 한눈에

| | 크레마 페블 | Meebook M6C | Meebook E6 |
|---|---|---|---|
| 근거 | 실제 기기 | 전체 OTA 펌웨어 분석 | 리뷰·설명서 + M6C 에서 추정 |
| 화면 | 6" 1072×1456, 밀도 300 (1dp = 1.875px) | 6" 컬러 1072×1448, 밀도 320 (1dp = 2px) | 6" Carta 1300 1072×1448 300ppi, 밀도 320 **(추정)** |
| dp 크기 | 572×777dp | 536×724dp | 536×724dp **(추정)** |
| 칩 · OS | RK3566 · 안드로이드 11 (API 30) | RK3566 · 안드로이드 11 (API 30, 64비트) | RK3566 **(추정)** · 안드로이드 11 |
| `Build.MODEL` | `CREMA PEBBLE` | `M6C` | ? |
| `Build.DEVICE` | `CREMA_PEBBLE` | `M6C` | ? |
| `Build.MANUFACTURER` · `BRAND` | `into1` · `YES24` | `Haoqing` · `Haoqing` | `Haoqing` **(추정)** |
| 펌웨어 만든 곳 | wetao / into1 (`ZlSystemUI`, `ZlSettings`) | Haoqing (`SystemUI` 수정판, `Haoqing-*` 앱) | Haoqing |
| 안드로이드 하단 내비게이션 바 | 없음 | 없음 (`config_showNavigationBar=false`) | 없음 (영상) |
| 빠른 설정 | 크레마 전용 앱 `com.inno.quicksetting` | 안드로이드 기본 패널을 고친 것, 타일 2줄×6칸 | M6C 와 같음 (영상) |
| 빠른 설정 여는 방송 | `com.epd.drop_down` | `com.haoqing.action.QUICK_SETTINGS` | 같음 **(추정)** |
| 안드로이드 기본 펼치기(`expandSettingsPanel`) | 열림: 크레마식 시스템 알림창(밝기·음량·대비) | **(미확인)** (에뮬레이터에서는 열림) | **(미확인)** |
| 화면 대비 | `hq_contrast`(기본 56), 앱 화면을 밝기^(1/s) 로 어둡게, s = 1 − c×90/8000 | `hq_contrast`(기본 34), 하드웨어 합성 단계 1.5 − c×0.018 | ? |
| 페이지 버튼 | 없음 | 없음 (키 코드만 있음: 291·292) | 2개 + 화면 아래 정전식 버튼 |
| 기본 홈 바꾸기 | 됨 (지금 쓰는 중) | 표준 안드로이드 11 방식, 막는 코드 없음 | **(미확인)** |
| Pebble Desk 판정 `Device.kind` | `CREMA` (`Build.DEVICE` 가 `CREMA` 로 시작) | `MEEBOOK` (`ro.haoqing.brand` 있음) | `MEEBOOK` **(추정)** |

- 둘 다 **Rockchip 전자잉크 개발 키트**에서 나왔다. 안드로이드 빌드 기반(`RQ2A.210505.003`), `android.os.EinkManager`, SystemUI 의 `EinkSettingsManager`·`EinkSettingsProvider`(`content://com.android.systemui.eink/einksettings`) 이름이 같다. 그 위를 회사마다 따로 고쳤다.
- `Build.DEVICE` 는 기기 코드명(`ro.product.device`)이다. 안드로이드 11 은 `ro.product.device` 가 직접 설정돼 있지 않으면 `ro.product.property_source_order`(기본 product, odm, vendor, system_ext, system) 순서로 `ro.product.<파티션>.device` 중 처음 비어 있지 않은 값을 쓴다(`init/property_service.cpp`). `MODEL`·`MANUFACTURER`·`BRAND` 도 같다.
- 앱 설정 › **기기 정보** 화면을 사진으로 찍어 받는다. 모델(코드명)·제조사·브랜드·안드로이드·빌드·화면(px·dpi·dp)·기기 종류·앱 버전, 메이북 속성·조명/대비 설정 값, 권한, **마지막으로 누른 버튼의 키 번호·스캔 코드**, **최근에 앞에 뜬 다른 화면**(사용 기록. 기본 런처에서 빠른 설정을 열었다 돌아오면 그것이 따로 된 화면인지 시스템 알림창인지 알 수 있다), 빠른 설정 세 방법(크레마·메이북 방송, 안드로이드 기본 펼치기) 시험이 한 화면에 있다. 새 기기는 이 사진부터 받는다.
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
- E6 는 `updatemodel` 을 몰라 받지 못했다(`MEEBOOK-E6-RK3566` 등 추측은 모두 빈 답). E6 사용자에게 위 두 `getprop` 값을 받으면 된다.
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
- **(미확인)** 모델·코드명·`updatemodel`, 밀도, 페이지 버튼·정전식 버튼의 키 코드, 빠른 설정 방송이 일반 앱에서 통하는지, 눈송이가 접근성 서비스에 주는 영향.

## Pebble Desk 에 넣은 것

| 내용 | 기기 구분 | 확인 |
|---|---|---|
| 상단 줄을 누르면 `com.epd.drop_down` 과 `com.haoqing.action.QUICK_SETTINGS`(`com.android.systemui` 지정)를 둘 다 보낸다(`Device.openQuickSettings`). 받는 쪽이 없는 방송은 아무 일도 하지 않는다 | 가리지 않음 | 페블은 그대로 열림. 메이북은 **(미확인)** |
| 기기 종류 `Device.kind`: 크레마(`Build.DEVICE`) → 메이북(`ro.haoqing.brand` 있음) → 기타. 모델명을 몰라도 메이북 전 기종을 잡는다. 설정 › 기기 정보의 `기기 종류` | — | 에뮬레이터에 `ro.haoqing.brand` 를 넣어 `메이북` 확인 |
| 조명 켜짐: 크레마 `screen_brightness`·`warm_light` > 0, 메이북 `isLightOn == "true"`(꺼도 밝기 값은 남는다). 그 키를 지켜보다 바뀌면 상태 줄을 다시 그린다 | 기기 종류 | 에뮬레이터에서 `isLightOn` 을 바꿔 아이콘이 나타나고 사라지는 것 확인 |
| 페이지 버튼: 메이북 키 291(위)·292(아래)와 표준 `PAGE_UP`·`PAGE_DOWN` → `EinkActivity.onSwipe`. 모든 목록 화면에 적용. 누르고 있을 때 오는 반복은 넘기지 않는다 | 가리지 않음 | 표준 키·길게 누르기(한 쪽만)는 에뮬레이터에서 확인. 단 표준 PAGE 키는 이동 키라, 터치 모드를 벗어나면서 포커스 받을 뷰가 생기면 첫 누름을 안드로이드가 먹는다(`ViewRootImpl.checkForLeavingTouchModeAndConsume`). 에뮬레이터 모든 앱 화면에서 그랬다. 291·292 는 일반 안드로이드에 없는 키 코드라 메이북 실기기에서만 확인 가능 **(미확인)** |
| 밀도 320(536×724dp) 배치 | — | 안드로이드 11 에뮬레이터에서 홈·설정·기기 정보가 들어가는 것 확인 |
| 기기 정보 화면(사진으로 찍어 보내는 진단) | — | 에뮬레이터(M6C 이름)에서 한 화면에 들어감, 볼륨 키가 `24 KEYCODE_VOLUME_UP` 로 기록됨 |

넣지 않은 것: `com.haoqing.action.FULL_REFRESH`(잔상 제거 버튼).

- **안드로이드 기본 빠른 설정 펼치기**: `getSystemService("statusbar")` 의 `expandSettingsPanel()`(숨은 함수, `EXPAND_STATUS_BAR` 는 일반 권한)를 일반 앱(릴리스, targetSdk 34)이 리플렉션으로 불러 안드로이드 11 에뮬레이터와 페블에서 열렸다(페블은 크레마 빠른 설정 앱이 아니라 밝기·음량·대비가 있는 시스템 알림창). 메이북 빠른 설정은 안드로이드 기본 패널을 고친 것이라 메이북 방송 대신 이 방법으로도 열릴 가능성이 크다 **(메이북 실기기 미확인)**. 지금은 기기 정보의 시험 버튼에만 쓴다.

## 에뮬레이터로 화면 크기 보기

- 펌웨어 자체는 에뮬레이터에서 부팅되지 않는다(Rockchip 커널·HAL). 펌웨어 앱만 옮겨 심는 것도 수정된 `framework.jar`(`android.util.haoqing`, `EinkManager`)·플랫폼 서명에 묶여 있어 사실상 시스템 이미지를 통째로 바꾸는 일이다. 전자잉크 갱신·대비는 어차피 재현되지 않는다.
- 화면 규격만 맞춘 AVD 로 배치를 본다. 예: `MeebookE6_A11` = `system-images;android-30;google_apis;arm64-v8a`, `hw.lcd.width=1072`, `hw.lcd.height=1448`, `hw.lcd.density=320`, `hw.mainKeys=yes`(내비게이션 바 없음). 페블은 `CremaPebble`(밀도 300).
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
