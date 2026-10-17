# ADR 0003 — 몰입형 읽기와 화면 방향 설정

2026-10-17 (Asia/Seoul). P1 후속 범위. 기존 플랫폼 Activity/View 구성 안에서 구현하며 새 런타임 의존성/스택 변경 없음.

## 결정

목록/열기 화면, PDF Canvas, 읽기 도구를 FrameLayout으로 분리한다. 기본 읽기 상태에는 Canvas만 보인다. 중앙 1/3 영역의 단일 탭으로 도구를 토글하고 설정·문서 목록·읽기 복귀를 제공한다. 도구는 Canvas 위에 겹치므로 열기/닫기에 따라 PDF viewport 크기나 줌/앵커를 바꾸지 않는다. 드래그/핀치/플링 중단 탭은 메뉴 열기와 구분한다. 중앙 밖의 탭은 메뉴를 열지 않으며 접근성 performClick은 메뉴에 접근할 수 있다.

API 30 이상은 플랫폼 WindowInsetsController로 시스템 바를 숨기고 가장자리 스와이프의 일시 노출을 허용한다. API 23~29는 기존 immersive sticky/layout flags를 사용한다. 실제 디스플레이 cutout만 PDF 안전 영역으로 예약하고 일시적인 시스템 바는 Canvas를 줄이지 않는다. 목록 화면은 시스템 바와 안전 여백을 복구한다. 설정/포커스 복귀는 읽기 상태의 바 숨김을 다시 적용한다.

Android 13 이상은 플랫폼 OnBackInvokedDispatcher, 이전 버전은 onBackPressed로 메뉴 닫기 → 목록 복귀 → Activity 종료를 처리한다. manifest의 API 33 opt-in은 구버전에서 무시되며 tools:targetApi는 이 의도적 호환 경로를 Lint에 명시한다. 검사를 전역 비활성화하지 않는다.

설정은 자동(기기 정책), 세로/가로 고정과 세로/가로 역방향 고정이다. 별도 Application 소유 I/O 큐와 AtomicFile의 버전 1 파일 `reader-settings.bin`에 enum 이름을 저장한다. 쓰기가 성공한 뒤 방향을 적용한다. Activity 재생성은 기존 문서/페이지 내부 앵커/줌 Bundle을 사용한다. 자동 모드는 명시적으로 선택할 때 UNSPECIFIED로 돌아가고 재생성 중에는 현재 플랫폼 정책을 유지한다. 알 수 없는 설정 버전/손상은 자동 초기화로 덮어쓰지 않고 오류를 알린다.

## 목적·대안·영향

2026-10-17 사용자 추가 요청: 가로 역방향/세로 역방향 고정을 제공한다. 기존 AUTO/PORTRAIT/LANDSCAPE 뒤에 REVERSE_PORTRAIT/REVERSE_LANDSCAPE를 추가하고 플랫폼의 SCREEN_ORIENTATION_REVERSE_PORTRAIT/REVERSE_LANDSCAPE에 매핑한다. 직접 Canvas를 회전시키지 않는다. 이름 기반 버전 1 저장의 magic/버전은 그대로이며 기존 세 선택을 읽을 수 있다. 예전 앱 바이너리는 새 enum 이름을 읽지 못할 수 있고 기존 손상/미지원 설정 보호 경로로 오류를 알리며 덮어쓰지 않는다. 저장 성공 후 적용과 Activity Bundle의 앵커/줌 복원 정책은 유지한다.

방향 항목이 다섯 개가 되어 가로 화면에서 목록 스크롤이 필요할 수 있다. 플랫폼 선택 목록을 유지하며 기기 테스트는 실제 목록을 스크롤해 역방향 항목을 선택한다. 검증은 요청 상수 외에 실제 display rotation의 180도 차이, 읽기 줌/앵커 유지·전체 화면, 재실행 선택 보존과 다섯 설정의 디스크 왕복을 확인한다. 현재 API 33 범위이며 실제 결과는 별도 이력을 따른다.

플랫폼 Activity/Canvas의 동작을 유지하면서 개인용 읽기에서 UI 간섭을 없앤다. AndroidX WindowInsetsCompat, Compose 셸, DataStore는 이 범위에 필요하지 않아 도입하지 않았다. APK에 새 라이브러리/라이선스가 추가되지 않으며 런타임 lock/verification 파일도 바꾸지 않는다. 플랫폼 API 사용은 기존 Android SDK 약관/고지 범위다.

읽기 기록과 설정 파일은 독립적이다. 앱 데이터 삭제/제거 시 설정도 사라지며 별도 서비스/계정은 없다. 방향 UI의 디스크 완료를 기다리는 동안 메인 스레드는 차단하지 않는다. 저장소 손상 복구 UI, 대형 화면/데스크톱 windowing 정책, 다른 API의 실제 동작은 후속 검증이다. 물리 센서 검증과 requestedOrientation/주입 입력 계측을 구분한다.

## API 근거

[몰입형 UI 가이드](https://developer.android.com/develop/ui/views/layout/immersive)의 시스템 바 숨김/일시 노출 방식과 [플랫폼 WindowInsetsController](https://developer.android.com/reference/android/view/WindowInsetsController), [ActivityInfo 방향 상수](https://developer.android.com/reference/android/content/pm/ActivityInfo)를 확인했다. Context7 조회는 legacy flags/Compat 위주의 간접 자료여서 플랫폼 API 근거는 공식 참조와 compileSdk 35의 실제 빌드로 대조했다. API 23~29 분기는 컴파일/Lint만 확인하며 현재 기기 API 33 실행과 구분한다.
