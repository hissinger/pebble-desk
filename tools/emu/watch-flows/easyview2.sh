# 이지뷰어(.free): 다른 앱처럼 파일 보기로 $EV_FILE(Download 안 EPUB)을 연다(서재에서 연 것이 아니라 넣지 않고 책 모름) → 뒤로 →
# ▶(마지막 파일) 열기 확인 창 → 확인(확인 창으로 안 파일이라 넣는다) → 뒤로 → 최근문서 탭 → 홈.
a shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/Download/${EV_FILE:-ezview_test3.epub}" -t application/epub+zip -n com.ezne.easyview.free/com.ezne.easyview.actMain >/dev/null
sleep 8
shot 1-viewer
key KEYCODE_BACK 4
key KEYCODE_BACK 4
shot 2-main
tap 960 1315 3
shot 3-confirm
tap 620 890 7
shot 4-viewer
key KEYCODE_BACK 4
key KEYCODE_BACK 4
tap 273 77 3
home 4
