#!/bin/bash
# 홈페이지 화면 캡처: 홈 · 독서 기록 · 올해 읽은 책을 찍어 <폴더>/<이름>-home.png … 로 둔다.
#   ANDROID_SERIAL=emulator-5554 tools/site/shoot.sh <mock 폴더> <출력 폴더> <이름>
# SITE_LANG=en 이면 영어 페이지용(load.sh·mock.py 도 같은 값을 본다).
set -e
here=$(dirname "$0")
bash "$here/load.sh" "$1" >/dev/null
mkdir -p "$2"
size=$(adb shell wm size </dev/null | grep -o '[0-9]*x[0-9]*' | tail -1)
case $size in
  1072x1456) reading="470 1402"; year="900 120" ;;
  824x1648) reading="370 1606"; year="700 100" ;;
  *) echo "모르는 화면 $size" >&2; exit 1 ;;
esac
cap() { adb exec-out screencap -p > "$2/$3-$1.png"; }
sleep 2; cap home "$2" "$3"
adb shell input touchscreen tap $reading </dev/null; sleep 4; cap reading "$2" "$3"
adb shell input touchscreen tap $year </dev/null; sleep 4; cap year "$2" "$3"
adb shell input keyevent BACK </dev/null; sleep 1; adb shell input keyevent BACK </dev/null
# 페블만: 맨 앞 책을 길게 눌러 상세 → `완독` 을 눌러 별점·감상 메뉴(mock 의 별점·감상이 채워져 뜬다). 완독은 누르지 않고 닫는다
if [ "$size" = 1072x1456 ]; then
  sleep 2
  adb shell input touchscreen swipe 215 400 215 400 900 </dev/null; sleep 3
  adb shell input touchscreen tap 116 1045 </dev/null; sleep 3; cap review "$2" "$3"
  adb shell input keyevent BACK </dev/null; sleep 1
fi
