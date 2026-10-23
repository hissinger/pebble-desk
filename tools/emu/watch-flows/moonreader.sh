# 문리더(com.flyersoft.moonreader): 파일로 책 A 열기 → 가운데를 눌러 메뉴(제목) → 메뉴 닫기 → 홈 → 파일로 책 B 열기 → 메뉴 → 홈.
# 바퀴마다 두 책을 바꿔 편다. 문리더에 모든 파일 액세스를 허용해 두면 저장소의 EPUB 을 찾아 표지로 쓴다.
mview() { a shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/Download/$1" -t application/epub+zip -p com.flyersoft.moonreader >/dev/null; sleep "${2:-8}"; }
mview bf-fifth.epub 8
tap 536 728 4
shot 1-menu
key KEYCODE_BACK 3
home 5
mview bf-first.epub 8
tap 536 728 4
shot 2-menu
key KEYCODE_BACK 3
home 5
