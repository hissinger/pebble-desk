# 우주(com.inno.woojoo): 서재 목록 보기 첫 쪽에서 둘째 칸(우주 시험 둘, EPUB) → 메뉴 → 책 닫기 → 서재 → 홈.
# 시작할 때 우주는 책을 닫은 서재여야 한다(앞 책 화면이 남은 채 다른 파일을 열면 우주가 죽는다).
launch com.inno.woojoo 6
shot 1-shelf
tap 343 552 7
shot 2-viewer
tap 536 728 4
shot 3-menu
key KEYCODE_BACK 2
tap 402 728 5
shot 4-closed
home 4
