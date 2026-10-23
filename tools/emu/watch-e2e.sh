#!/bin/bash
# 읽는 책 자동 추가를 이북 앱마다 같은 순서로 눌러 보고, 그 사이 바뀐 것을 때 없이 요약한다(docs/emulator-test-cases.md `M. 자동 추가 앱별`).
#   tools/emu/watch-e2e.sh <순서 이름> <결과 폴더>
# 순서는 tools/emu/watch-flows/<이름>.sh(CremaPebble_A11_Play 의 화면 좌표·그 에뮬에 넣어 둔 책). 디버그 빌드가 깔려 있고 자동 추가가 켜져 있어야 한다.
# 결과 폴더의 diff.txt 를 옮기기 전 결과와 diff 하면 동작이 같은지 본다(때·책 id 는 빠져 있다).
# uiautomator 는 쓰지 않는다(접근성 서비스가 새로 만들어져 기억이 지워진다).
set -e
case "$ANDROID_SERIAL" in emulator-*) ;; *) echo "ANDROID_SERIAL=emulator-… 로 에뮬을 지정하세요(실기기 금지)" >&2; exit 1 ;; esac
FLOW=$1; OUT=$2
HERE=$(cd "$(dirname "$0")" && pwd)
[ -f "$HERE/watch-flows/$FLOW.sh" ] || { echo "순서 없음: $FLOW" >&2; exit 1; }
mkdir -p "$OUT"
a() { adb "$@" </dev/null; }
tap() { a shell input tap "$1" "$2"; sleep "${3:-3}"; }
key() { a shell input keyevent "$1"; sleep "${2:-3}"; }
launch() { a shell monkey -p "$1" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep "${2:-6}"; }
shot() { a exec-out screencap -p > "$OUT/$1.png"; }
home() { key KEYCODE_HOME "${1:-4}"; }
a shell input keyevent KEYCODE_WAKEUP
a shell svc power stayon true
# 중간에 멈춰도 화면 켜 두기를 되돌린다
trap 'adb shell svc power stayon false </dev/null' EXIT
home 3
"$HERE/watch-snap.sh" "$OUT/before" >/dev/null
source "$HERE/watch-flows/$FLOW.sh"
# 자동 추가 기록은 몇 초 늦게 저장한다
sleep 6
"$HERE/watch-snap.sh" "$OUT/after" >/dev/null
python3 "$HERE/watch-norm.py" "$OUT/after" "$OUT/before" > "$OUT/diff.txt"
cat "$OUT/diff.txt"
