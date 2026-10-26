# R8 mapping (판마다)

배포한 판의 R8 mapping(`PebbleDesk-<판>-mapping.txt.gz`). 기기 정보 › 마지막 앱 오류(와 기록 보기의 `앱 오류로 끝남` 줄)는
릴리스에서 R8 이 바꾼 이름(`h4.b:41`)으로 적혀, 그 판의 mapping 으로 원래 이름을 되찾는다.

- 남기기: 배포할 APK 를 만든 바로 뒤 `tools/release/save-mapping.sh`(APK 와 mapping 의 열쇠가 같은지 확인한다). 커밋한다.
- 되찾기: `tools/release/retrace.sh 1.6.2 'h4.b:41 ← bg.c:88'`

GitHub 릴리스에는 올리지 않는다. 1.6.1 까지는 남기지 않았다.
