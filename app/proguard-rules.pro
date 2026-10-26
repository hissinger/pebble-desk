# 받아 쓰는 libarchive(readers/ArchiveLib.kt): 네이티브 함수가 Java 클래스·메서드·필드를 이름으로 찾는다(JNI_OnLoad 의 FindClass, 콜백, 예외 생성자).
-keep class me.zhanghai.android.libarchive.** { *; }

# 클래스를 한 패키지로 모으고 접근 제한을 풀어 이름·연결 코드를 줄인다(이름으로 찾는 libarchive 는 위에서 그대로 둔다).
-repackageclasses
-allowaccessmodification
