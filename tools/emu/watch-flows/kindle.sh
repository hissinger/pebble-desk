# 킨들(로그인·받은 책이 있는 에뮬, CremaPebble_A11): 서재(목록 보기, 최근 읽은 순) 둘째 칸 → 읽는 화면 → 가운데(메뉴 제목·진행률) → 뒤로(서재)
# → 다시 둘째 칸(앞서 맨 앞이던 책) → 메뉴 → 뒤로 → 홈. 두 책을 번갈아 열어 서재 순서가 바퀴마다 같다.
a shell am start -n com.amazon.kindle/.UpgradePage >/dev/null
sleep 10
tap 666 1383 5
shot 1-library
tap 364 546 10
tap 536 728 4
shot 2-menu
key KEYCODE_BACK 5
tap 364 546 10
tap 536 728 4
shot 3-menu
key KEYCODE_BACK 5
home 5
