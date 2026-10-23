# KOReader 새 책: 파일 보기로 $KO_FILE(Download 안, 처음 보는 책)을 KOReader 로 연다 → 홈(데이터로 맞춰 새 책으로 들어와 맨 앞) →
# 홈의 맨 앞 책을 누른다(KOReader 로 그 파일을 바로 연다) → 홈. 책 파일 읽기가 허용돼 있어야 한다.
a shell am start -a android.intent.action.VIEW -d "file:///storage/emulated/0/Download/${KO_FILE:-ko-a.epub}" -t application/epub+zip -n org.koreader.launcher/.MainActivity >/dev/null
sleep 10
shot 1-viewer
home 6
shot 2-home
tap 214 395 10
shot 3-opened
home 6
