# Readest(com.bilingify.readest, 에뮬 CremaPebble_A11_Play_Readest): 서재 '최근에 읽은 책' 첫 칸(열면 늘 맨 앞에 남는다) → 다음 쪽·앞 쪽 → 뒤로(서재) → 홈.
launch com.bilingify.readest 10
shot 1-shelf
tap 187 447 10
shot 2-viewer
tap 1000 728 4
tap 70 728 4
key KEYCODE_BACK 5
shot 3-shelf
home 5
