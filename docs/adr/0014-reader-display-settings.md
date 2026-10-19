# ADR 0014 — 앱 테마·읽기 밝기·화면 켜짐 유지

2026-10-19. P2의 기존 Activity/View/AtomicFile을 유지한다. 외부 런타임 의존성, 시스템 설정 쓰기 권한, wake lock을 추가하지 않는다. [P2 수용 기준](../plan/p2-scope-and-completion.md)의 읽기 설정 부분이다. Figma 디자인 적용/선택·본문 읽기/최종 통합 완료와 구분한다.

## 저장과 기본값

기존 설정 repository의 직렬 executor에서 별도 `reader-settings.bin.display`를 읽고 쓴다. v1 magic 0x45564431 뒤 version 1, 테마 enum 0 시스템/1 밝게/2 어둡게, 시스템 밝기 0/1, 수동 밝기 정수 1~100, 화면 유지 0/1을 저장한다. 기존 파일이 없으면 시스템 테마/시스템 밝기/수동 보관값 50%/화면 유지 끔이다. 0%로 화면을 읽을 수 없게 만들지 않는다. 시스템 밝기로 바꿔도 수동 값을 보존한다.

AtomicFile로 비동기 저장하고 성공한 뒤 UI에 적용한다. 처음 읽기에 실패하거나 알 수 없는 형식/버전/값·잔여 바이트이면 저장도 거부해 원본을 보존한다. 방향/외부 배경색/페이지 번호·기록 파일은 변경하지 않는다. 읽기만으로 파일을 만들지 않는다.

## 테마와 읽기 상태

플랫폼 Material Light/Dark NoActionBar 테마를 View 생성 전에 선택한다. SYSTEM은 현재 Configuration의 night 비트를 따른다. 최초 저장 설정 로드는 비동기로 하며 실제 명암이 달라지면 Activity를 재생성한다. 재생성 Bundle에는 테마 선택을 전달해 새 View 생성 전에 같은 테마를 선택한다. 저장 파일은 항상 다시 비동기로 확인한다. 프로세스 시작 직후 저장 테마 로드 전에는 시스템 테마가 잠깐 보일 수 있다.

기존 source/문서별 배치·viewport 저장/복원 경로를 사용한다. 테마 변경 때문에 사용자 줌을 초기화하지 않는다. 홈·도구 배경과 시스템 바 색/아이콘을 테마에 맞추고 플랫폼 버튼/대화상자 텍스트 색을 사용한다. PDF 렌더 픽셀과 수동/자동 페이지 외부 배경색은 테마로 덮어쓰지 않는다. 페이지 번호의 검은 반투명 바탕/흰 글자는 PDF 위 가독성 표현으로 유지한다.

플랫폼 Light 테마의 [Force Dark](https://developer.android.com/develop/ui/views/theming/darktheme#force-dark)가 명시적 밝은 선택이나 GPU의 PDF 픽셀을 바꾸지 않도록 API 29 이상 Activity/AlertDialog 테마에서 forceDarkAllowed=false를 지정한다. 새 테마는 플랫폼 테마를 상속하며 외부 UI 라이브러리를 추가하지 않는다. 어두운 navigation 아이콘을 지원하지 않는 API 23~25의 밝은 테마는 navigation 바만 검은색/흰 아이콘을 유지한다. 해당 구형 기기를 실제 검증한 것으로 보고하지 않는다.

설정 항목이 늘어나므로 설정 메뉴는 ScrollView로 만들고 방향의 5개 선택은 별도 목록 대화상자로 분리한다. 기존 중앙 탭 진입/전체 화면 복귀와 방향/배경색/번호·배치 기능을 유지한다. 새 표시 설정도 스크롤 가능한 대화상자에서 적용/취소하며 적용 전에는 화면/저장 상태를 바꾸지 않는다.

## Window 수명

문서가 준비되고 열람 중이며 Activity가 resumed일 때만 해당 Window에 밝기/FLAG_KEEP_SCREEN_ON을 적용한다. 시스템 밝기는 screenBrightness=-1, 수동은 0.01~1.0이다. 홈/오류 복귀·onPause/onDestroy에서 -1/플래그 해제, onResume에서 현재 열람 상태에 맞춰 재적용한다. 설정 자체는 유지한다. PDF 로딩 중/파일 선택기·백그라운드에서는 화면 유지/밝기 강제를 하지 않는다. 기기 전체 밝기/자동 밝기·절전/잠금 정책을 변경하지 않는다.

## 자료와 검증 경계

Context7의 Android reference 조회는 관련 상속 속성/수명 일부만 반환했고 API 23/35 고정 스냅샷이 아니다. 공식 [screenBrightness/Window flags](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness), [화면 켜짐 유지](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on), [View 생성 전 setTheme](https://developer.android.com/reference/android/content/Context#setTheme(int))로 교차 확인한다. 설치 SDK 35 빌드와 대상 API 33 실행 결과는 별도다.

저장/거부·Window 상태 단위 검사, AtomicFile 복구/다른 설정 불변·UI 적용/취소/재실행·테마별 PDF 픽셀/줌/앵커·홈/실제 백그라운드 왕복 계측을 실행한다. 실제 개인 기기 가독성/밝기 체감은 사용자 확인과 구분해 기록한다. UI 속성 검사만으로 TalkBack/200% 글꼴·다른 API/기기·최종 P2 통합 성능을 완료 처리하지 않는다. 실제 명령·실패·결과는 history에 남긴다.
