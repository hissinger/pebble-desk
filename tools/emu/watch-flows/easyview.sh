# 이지뷰어(.free): 최근문서 탭 첫 칸(/sdcard/NewPlay/New_Play.epub, 파일 칸) → 쪽 넘김 → 뒤로 둘 → 서재 → 홈.
# 시작할 때 이지뷰어는 최근문서 탭을 연 서재여야 한다(그 칸은 열면 늘 맨 앞에 남는다).
launch com.ezne.easyview.free 6
tap 273 77 4
shot 1-shelf
tap 300 330 8
shot 2-viewer
tap 1000 728 3
key KEYCODE_BACK 5
key KEYCODE_BACK 5
shot 3-shelf
home 4
