#!/bin/bash
# 홈 배치 시험: 여러 화면 크기 × 설정(자주 쓰는 앱 격자·목록, 기본·낮춤, 홈 시계·기기 상단 바)으로 홈을 찍고,
# 맨 앞 책 진행률 줄과 함께 읽는 책 줄이 있는지 본다(DESIGN.md `홈 배치 규칙`). 디버그 빌드에서만(run-as).
#   ANDROID_SERIAL=emulator-5556 tools/emu/home-sizes.sh <DB 파일> <결과 폴더> [맨 앞 책 진행률 글자, 기본 "64% 읽음"]
#   하나만: ONLY_SIZE=h720 ONLY_PREF=grid-short …   글자 크기: TEXT=150(설정 › 글자 · 간격의 글자, 70~150)
# DB 는 맨 앞 책에 진행률을 두고(가장 빡빡한 경우: 저자·반납일·읽고 있는 앱도) 함께 읽는 책이 있게 만든다.
# 결과 폴더에 <크기>-<설정>.png 와 summary.txt(PASS / WARN: 함께 읽는 책 줄을 뺌 / FAIL: 맨 앞 책 진행률이 빠짐), sheet-*.png(한눈에 보기, /usr/bin/python3 의 PIL).
#   LIVE=1: 세로 크기마다 앱을 띄운 채 가로 → 세로로 돌려 찍는다(<크기>-rot-·<크기>-back-).
# 화면 크기는 wm size/density 로 바꾸고 끝나면 되돌린다. uiautomator dump 를 쓰므로 자동 추가 시험 중에는 돌리지 않는다.
set -e
case "$ANDROID_SERIAL" in emulator-*) ;; *) echo "ANDROID_SERIAL=emulator-… 로 에뮬을 지정하세요(실기기 금지)" >&2; exit 1 ;; esac
DB=$1; OUT=$2; PCT=${3:-64% 읽음}
[ -f "$DB" ] && [ -n "$OUT" ] || { echo "사용: $0 <DB 파일> <결과 폴더> [진행률 글자]" >&2; exit 1; }
mkdir -p "$OUT"
HERE=$(cd "$(dirname "$0")" && pwd)
P=com.woody.pebbledesk
D=/data/user_de/0/$P

# 이름 크기 밀도 회전. 짧은 변을 572dp 로 맞추므로 비율만 다르면 된다(내비 바가 있는 기기는 그 높이를 뺐다).
SIZES=(
  "pebble 1072x1456 300 0"
  "ireader 1440x1920 320 0"
  "ireader-bar 1440x1890 320 0"
  "ocean 1264x1680 300 0"
  "h720 1072x1350 300 0"
  "mipad 1200x1824 320 0"
  "yeoodop 720x1184 320 0"
  "tango 824x1648 400 0"
  "alloo 1080x2274 420 0"
  "pebble-land 1072x1456 300 1"
  "ireader-land 1440x1920 320 1"
  "tango-land 824x1648 400 1"
)
# 설정: 이름 격자 낮춤 상단바 (가로 화면은 낮춤을 쓰지 않아 낮춤 없는 것만)
PREFS=(
  "grid true false false"
  "grid-short true true false"
  "list-short false true false"
  "grid-bar true false true"
  "grid-short-bar true true true"
  "list-short-bar false true true"
)

stop_app() {
  adb shell input keyevent HOME </dev/null; sleep 1
  adb shell am start -a android.settings.SETTINGS </dev/null >/dev/null 2>&1; sleep 2
  local pid; pid=$(adb shell pidof $P </dev/null | tr -d '\r')
  [ -n "$pid" ] && adb shell "run-as $P kill $pid" </dev/null || true
  for _ in $(seq 1 20); do [ -z "$(adb shell pidof $P </dev/null | tr -d '\r')" ] && break; sleep 0.5; done
}

# home.xml 의 세 값과 글자 크기(TEXT, 70~150, 없으면 100)를 바꾼다(앱이 꺼져 있을 때)
set_prefs() {
  adb shell "run-as $P sh -c 'f=$D/shared_prefs/home.xml; sed -i -e \"/name=\\\"fav_grid\\\"/d\" -e \"/name=\\\"fav_short\\\"/d\" -e \"/name=\\\"show_status_bar\\\"/d\" -e \"/name=\\\"text_scale\\\"/d\" \$f; sed -i \"s#</map>#    <boolean name=\\\"fav_grid\\\" value=\\\"$1\\\" />\\n    <boolean name=\\\"fav_short\\\" value=\\\"$2\\\" />\\n    <boolean name=\\\"show_status_bar\\\" value=\\\"$3\\\" />\\n    <int name=\\\"text_scale\\\" value=\\\"${TEXT:-100}\\\" />\\n</map>#\" \$f'" </dev/null
}

# 지금 홈을 찍고 맨 앞 책 진행률 줄·함께 읽는 책 줄이 있는지 적는다. 진행률이 없으면 FAIL(맨 앞 책이 먼저다),
# 함께 읽는 책 줄만 없으면 WARN(맨 앞 책을 다 넣으려고 그 줄을 뺀 아주 낮은 화면, DESIGN.md `홈 배치 규칙` 4).
check() {
  sleep 4
  # dump 는 가끔 실패해(빈 파일) 홈 글자가 나올 때까지 세 번. 앱을 끄면 자동 추가 서비스가 멈춰 홈이 `자동 추가가 멈춰 있습니다`
  # 창을 띄우니(에뮬, 실기기에서는 끄지 않는다) 뒤로 키로 닫는다.
  local xml=
  for _ in 1 2 3; do
    adb shell rm -f /sdcard/home-sizes.xml </dev/null
    adb shell uiautomator dump /sdcard/home-sizes.xml </dev/null >/dev/null 2>&1 || true
    xml=$(adb exec-out cat /sdcard/home-sizes.xml </dev/null || true)
    grep -q 'text="자주 쓰는 앱"' <<< "$xml" && break
    grep -q 'text="닫기"' <<< "$xml" && adb shell input keyevent BACK </dev/null
    sleep 2
  done
  adb exec-out screencap -p </dev/null > "$OUT/$1.png"
  local prog=no also=no res=PASS
  grep -q "text=\"$PCT\"" <<< "$xml" && prog=yes
  grep -q 'text="함께 읽는 책"' <<< "$xml" && also=yes
  if [ $prog = no ]; then res=FAIL; elif [ $also = no ]; then res=WARN; fi
  echo "$res $1 진행률=$prog 함께읽는책=$also" | tee -a "$OUT/summary.txt"
}

# 중간에 멈춰도(set -e, Ctrl-C) 에뮬 화면 크기·회전을 되돌린다
trap 'adb shell wm size reset </dev/null; adb shell wm density reset </dev/null; adb shell settings put system user_rotation 0 </dev/null' EXIT
adb push "$DB" /data/local/tmp/seed.db </dev/null >/dev/null
adb shell chmod 644 /data/local/tmp/seed.db </dev/null
adb shell settings put system accelerometer_rotation 0 </dev/null
: > "$OUT/summary.txt"
for pref in "${PREFS[@]}"; do
  read -r pname grid short bar <<< "$pref"
  [ -n "$ONLY_PREF" ] && [ "$pname" != "$ONLY_PREF" ] && continue
  stop_app
  adb shell "run-as $P sh -c 'rm -f $D/databases/pebble.db* && cp /data/local/tmp/seed.db $D/databases/pebble.db'" </dev/null
  set_prefs "$grid" "$short" "$bar"
  for size in "${SIZES[@]}"; do
    read -r name wh dens rot <<< "$size"
    [ -n "$ONLY_SIZE" ] && [ "$name" != "$ONLY_SIZE" ] && continue
    [ "$rot" = 1 ] && [ "$short" = true ] && continue
    # LIVE=1: 세로 크기마다 새로 띄운 뒤 앱을 띄운 채 가로로 돌렸다가 다시 세로로(화면이 바뀔 때도 같게 그리는지). 가로 항목은 건너뛴다.
    [ "$LIVE" = 1 ] && [ "$rot" = 1 ] && continue
    # 크기마다 앱을 새로 띄운다(실기기는 해상도가 바뀌지 않는다)
    stop_app
    adb shell wm size "$wh" </dev/null; adb shell wm density "$dens" </dev/null
    adb shell settings put system user_rotation "$rot" </dev/null
    adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME </dev/null >/dev/null 2>&1
    check "$name-$pname"
    if [ "$LIVE" = 1 ]; then
      adb shell settings put system user_rotation 1 </dev/null
      check "$name-rot-$pname"
      adb shell settings put system user_rotation 0 </dev/null
      check "$name-back-$pname"
    fi
  done
done
/usr/bin/python3 "$HERE/home-sheet.py" "$OUT" || true
