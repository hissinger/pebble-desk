# 문리더 새 책: 파일 보기로 $MN_FILE(Download 안, 처음 보는 책)을 문리더로 연다 → 메뉴(제목으로 새 책) → 홈.
# 문리더는 연 파일을 내놓지 않아 저장소에서 그 제목의 EPUB 을 찾아 그 책의 파일로 적는다(뒤에서). 책 파일 읽기가 허용돼 있어야 한다.
a shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/Download/${MN_FILE:-mn-a.epub}" -t application/epub+zip -p "${MN_PKG:-com.flyersoft.moonreader}" >/dev/null
sleep 8
tap 536 728 4
shot 1-menu
key KEYCODE_BACK 3
home 10
