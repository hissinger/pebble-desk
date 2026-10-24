# KOReader(org.koreader.launcher): 파일로 책 A 열기 → 다음 쪽·앞 쪽 → 홈(데이터로 맞춤) → 파일로 책 B 열기 → 홈. 바퀴마다 두 책을 바꿔 편다.
# KOReader 와 이 앱 모두 모든 파일 액세스가 허용돼 있어야 한다(KOReader 안내서는 책에서 뺀다).
view() { a shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/Download/$1" -t application/epub+zip -n org.koreader.launcher/.MainActivity >/dev/null; sleep "${2:-10}"; }
view bf-third.epub 10
shot 1-viewer
tap 1000 728 4
tap 70 728 4
home 6
view bf-fourth.epub 10
shot 2-viewer
home 6
