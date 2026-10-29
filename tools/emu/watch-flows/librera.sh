# Librera(Pro, com.foobnix.pro.pdf.reader. LB_PKG=com.foobnix.pdf.reader 면 무료판): 라이브러리 격자 첫 줄 셋째 칸(코리더 새 책 갑, EPUB) → 처음 뜬 메뉴 닫기 →
# 메뉴 없이 다음 쪽·앞 쪽(바퀴마다 같은 쪽에 남게) → 메뉴 → 뒤로(책 닫기) → 라이브러리 → 홈.
# 시작할 때 Librera 는 책을 닫은 라이브러리여야 한다. 뒤로 나올 때 Play 스토어 평가 창이 뜨면 그 창을 닫는 뒤로가 한 번 더 든다.
launch "${LB_PKG:-com.foobnix.pro.pdf.reader}" 6
shot 1-shelf
tap 536 290 8
shot 2-viewer
tap 536 700 3
tap 1000 728 4
tap 70 728 4
tap 536 700 3
shot 3-menu
key KEYCODE_BACK 3
shot 4-closed
home 4
