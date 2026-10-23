#!/bin/bash
# 자동 추가 앱별 순서를 한 바퀴(우주 → BookFusion → 이지뷰어) 돌리고 결과를 <결과 폴더>/<순서>/diff.txt 에 둔다.
#   tools/emu/watch-cycle.sh <결과 폴더> [<비교할 앞 결과 폴더>]
# 결과는 앞 상태에 따라 달라지므로(이미 맨 앞인 책은 다시 적지 않는다) 한 바퀴를 돈 뒤의 바퀴끼리 비교한다.
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$1; PREV=$2
for f in woojoo bookfusion easyview; do
  "$HERE/watch-e2e.sh" "$f" "$OUT/$f" >/dev/null || { echo "$f 실패"; exit 1; }
  if [ -n "$PREV" ]; then
    if diff -u "$PREV/$f/diff.txt" "$OUT/$f/diff.txt" >"$OUT/$f/compare.txt"; then echo "$f 같음"; else echo "$f 다름: $OUT/$f/compare.txt"; fi
  else echo "$f 끝"; fi
done
