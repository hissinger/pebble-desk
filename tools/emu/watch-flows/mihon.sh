# Mihon(MH_PKG 로 Kahon 도): 서재 첫 칸(Red Moon Story) → 작품 상세 → 둘째 화 → 메뉴 → 상세 → 서재 → 홈.
# 서재는 로컬 저장소(/sdcard/Mihon/local)의 시험 만화 두 권(Red Moon Story·Blue Sky Manga)을 넣어 둔 것. 앱이 작품 상세에 있었으면 뒤로로 서재까지 간다.
P=${MH_PKG:-app.mihon}
launch $P 4
key KEYCODE_BACK 2
key KEYCODE_BACK 2
launch $P 4
tap 106 1380 3
shot 1-shelf
tap 142 360 5
shot 2-detail
tap 300 1110 6
shot 3-viewer
tap 536 728 4
key KEYCODE_BACK 3
key KEYCODE_BACK 3
shot 4-shelf
home 4
