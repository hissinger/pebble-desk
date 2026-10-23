# 우주(com.inno.woojoo): 서재 목록 보기 첫 쪽에서 셋째 칸(우주 시험 둘, EPUB. 맨 위는 KOReader 안내서) → 메뉴 없이 다음 쪽·앞 쪽(우주 기록의 진행률, 바퀴마다 같은 쪽에 남게) → 메뉴 → 책 닫기 → 서재 → 홈.
# 시작할 때 우주는 책을 닫은 서재여야 한다(앞 책 화면이 남은 채 다른 파일을 열면 우주가 죽는다).
launch com.inno.woojoo 6
shot 1-shelf
tap 350 690 7
shot 2-viewer
tap 1000 728 4
tap 70 728 4
tap 536 728 4
shot 3-menu
key KEYCODE_BACK 2
tap 402 728 5
shot 4-closed
home 4
