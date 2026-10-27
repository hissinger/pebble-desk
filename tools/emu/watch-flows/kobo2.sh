# Kobo Books(게스트) 책 상세 길: 홈 탭 Latest Purchases 둘째 칸 → 책 상세 → Read Preview → 메뉴 → 돌아옴 → 홈.
# Kobo 만 강제 종료해 홈 탭 맨 위에서 시작한다(`kobo` 참고). Latest Purchases 는 최근 활동 순이라 둘째 칸이 어느 책인지는 앞 상태에 달렸고,
# Read Preview 는 두 책 상세에서 자리가 달라(The Hollow 는 부제 줄이 있다) 둘 다 걸리는 곳을 누른다.
P=com.kobobooks.android
a shell am force-stop $P
launch $P 8
shot 1-home
tap 415 870 6
shot 2-detail
tap 536 1305 10
shot 3-viewer
tap 536 600 4
key KEYCODE_BACK 2
key KEYCODE_BACK 4
key KEYCODE_BACK 3
home 4
