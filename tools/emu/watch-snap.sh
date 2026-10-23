#!/bin/bash
# 읽는 책 자동 추가 시험의 결과를 가져온다(디버그 빌드, run-as). docs/emulator-test-cases.md `M. 자동 추가 앱별`.
#   tools/emu/watch-snap.sh <결과 폴더>
# DB·자동 추가 기록·서비스 prefs 를 가져와 tools/emu/watch-norm.py 로 때를 뺀 요약(summary.txt)을 만든다.
# 같은 순서로 시험한 두 결과의 summary.txt 를 diff 하면 동작이 같은지 본다.
set -e
OUT=$1
[ -n "$OUT" ] || { echo "결과 폴더를 주세요" >&2; exit 1; }
P=com.woody.pebbledesk
DD=/data/user_de/0/$P
mkdir -p "$OUT"
a() { adb "$@" </dev/null; }
a exec-out run-as $P cat $DD/databases/pebble.db > "$OUT/pebble.db"
# WAL 에만 있는 쓰기도 가져온다
a exec-out run-as $P cat $DD/databases/pebble.db-wal > "$OUT/pebble.db-wal" 2>/dev/null || rm -f "$OUT/pebble.db-wal"
a exec-out run-as $P cat $DD/files/watch_log.txt > "$OUT/watch_log.txt" 2>/dev/null || true
a exec-out run-as $P cat $DD/shared_prefs/reader_watch.xml > "$OUT/reader_watch.xml" 2>/dev/null || true
a exec-out run-as $P ls $DD/files/books > "$OUT/covers.txt" 2>/dev/null || true
python3 "$(dirname "$0")/watch-norm.py" "$OUT" > "$OUT/summary.txt"
echo "$OUT/summary.txt"
