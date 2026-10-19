# 2026-10-19 — 페이지 본문 안전성·접근성·P2 통합 검증

## 범위와 기준

사용자가 선택/복사 UI는 뒤로 미루고 앞서 제시한 1(텍스트 처리 안전성), 3(접근성), 4(최종 통합 검증)를 요청했다. AGENTS/계획/로드맵의 현재 범위와 종료 기준에 반영한다. 본문 읽기와 native 안전 검증은 유지하고 검색을 재활성화하지 않는다. 새 런타임 의존성/도구 버전 변경은 없다.

PR #16 사용자 병합 main 45651e3와 최종 head 51861a3 CI 성공/review 0건을 조회했다. 첫 브랜치 준비 명령은 자동 승인 검토 사용량 한도로 실행되지 않았다. 표시된 복구 시각 뒤 재시도해 fetch→사용자 Figma tracked 블록 백업/stash→main ff-only→현재 체크아웃의 codex/p2-accessibility-safety→stash 재적용을 통과했다. 복구용 stash/디자인 두 미추적 이력은 유지한다. reset/clean/강제 push/새 worktree는 하지 않았다.

승인 기기 한 대/Awake/잠금 해제, MemAvailable 2,366,080KiB를 확인했다. 개인 PDF/기기 식별자는 수집하지 않았다. 이전 사용자 PDF를 자동으로 다시 열어 native 처리하지 않는다.

## 구현·조사와 실제 검사

[ADR 0015](../adr/0015-bounded-current-page-text-and-accessibility.md)의 한 페이지·격리 서비스·시간/RSS/시스템 여유 메모리 감시·본문 결과 상한·취소/프로세스 종료를 구현 중이다. 본문 읽기/Canvas 접근성 actions·PageUp/Down·스크롤 가능한 홈/도구/페이지 설정을 추가한다. 계측은 공개 합성 자료만 사용한다.

Context7 resolve Android Developers/query `/websites/developer_android_reference`를 사용했다. SDK 고정 자료가 아니고 일부 생성 서명이 실제 SDK와 달라 공식 Page/Service/AccessibilityNodeInfo 및 설치 SDK 컴파일로 교차 확인한다. 첫 두 strict 검사에서 새 코드의 ktlint 줄바꿈/주석 간격 오류를 확인해 직접 수정했다. ktlintFormat은 실행하지 않았다. 해당 실패를 빌드/기기 통과로 기록하지 않는다.

현재 검증/수정 중이며 최종 통합 성능·10분 읽기·프로세스 복원·실제 TalkBack/큰 글꼴 사용자 확인·CI/PR는 아직 통과로 보고하지 않는다. 후속 실제 결과를 이 파일에 추가한다.

## 본문/접근성 기기 검사와 글꼴 계측 수정

strict debug 앱/계측 빌드·단위 **44개/실패·오류 0**·Lint **0 errors/0 warnings**·ktlint를 통과했다. 초기 줄바꿈 외에 SDK 확장 API에 숫자 31을 사용한 Lint WrongConstant와 계측 PdfDocument의 Closeable 가정을 수정했다. SDK 상수 VERSION_CODES.S와 명시적 finally close로 고쳤으며 검사를 제거하지 않았다.

앱의 서로 다른 UID/요청한 둘째 페이지 내용·첫째 페이지 부재/빈 이미지 430페이지의 마지막 한 페이지/손상 PDF와 잘못된 페이지·새 요청 복구/취소 뒤 늦은 FD 해제/클라이언트 deadline을 포함한 첫 본문 suite **6개/실패 0**을 확인했다. 이후 서비스의 bind부터 시작하는 독립 main-loop deadline과 관측 실패 시 중단, 요청자가 RSS 예산을 낮출 수만 있는 정책을 보강했다. 과도한 메모리를 실제 할당하지 않고 1KiB 예산으로 실제 격리 프로세스 Binder 사망·다음 새 요청 복구를 검증했다. 무요청 서비스 deadline도 실제 Binder 사망으로 확인해 후속 본문 suite **8개/실패 0**이었다. 6개와 8개는 별도 실행이며 하나의 14개로 보고하지 않는다. 실제 native OOM/사용자 고해상도 PDF 재현은 아니다.

첫 글꼴 suite 1개는 샘플의 `Visible tiles not ready`, 안정화만 추가한 두 번째는 configuration settle 실패였다. 계측에서 요청한 방향이 Activity 재생성 때 앱의 저장 방향 설정으로 다시 적용되는 경로를 확인하고, 원래 방향을 snapshot한 뒤 조합별 저장 방향을 적용/Activity 안정화 후 검사하도록 수정했다. 조건/assertion을 완화하지 않았다. 후속 **글꼴 suite 1개/실패 0**은 100/150/200%×가로/세로의 6조합에서 홈/도구/표시·페이지 배치·배경 설정의 실제 전체 control bounds가 스크롤로 접근 가능한지 확인했다. 테스트 finally에서 원래 앱 방향과 시스템 배율을 복원했고 PC 외부 finally에서도 원래 배율 **1.1**을 재확인했다. 실제 TalkBack 및 큰 글꼴의 개인 가독성 수용과 구분한다.

현재 debug app SHA-256은 `84fbe376559e1b9097e5a73d073a19470f85a3a3e085a3323ebec108ff0a8db1`이다. 기본 57개(기존 48+본문 8+글꼴 1) 회귀를 시작했다. 이번 전체 회귀의 계측 SHA는 `2dcd1b2694d445924359ccec9af5a3ed7fa0e993324b9123f9fdb838a8db18d1`이며 결과는 후속으로 확인한다. 이후 추가한 성능 전용 probe/합성 complex 자산의 profile 계측 APK는 별도이며 기본 suite 결과와 SHA를 혼동하지 않는다.

최종 성능 계측은 초기 보통 스크롤 조건을 유지하고 추가 플링/32배율(방향별 10표본×3회), 서로 다른 1440×2240 RGB JPEG 12개/총 48페이지·2,721,378byte의 공개 합성 문서도 사용한다. 이 자료는 임의 문서 복잡도·사용자 문서의 재현이 아니다. 기존 baseline APK를 보존하고 최종 profile app `dbd3ab71265740c3a27117990b1f35b23611d2c6b1b0a0d1017f0a1e6f7d7cd0`, 공통 계측 `5b9c312aaa9a512480f601a1748a2d62dbd3b67cd74aa235c17a394d2e022e83`을 ignored p2-integration에 동결했다. 실제 실행/비교/통과는 아직 미확인이다. 프레임의 frozen P1 경로는 자동 배경색이 없어 수동 대체색을 사용하며 새 v2 설정 원본을 보존한다. 최종 앱은 자동 배경색 켜짐을 별도 확인한다.

## 2026-10-20 후속 — 전체 회귀와 짧은 문서 본문 접근

첫 전체 57개는 기존 DisplayDeviceTest의 keep-screen-on 체크박스가 스크롤 바깥에 있어 1개 실패/56개 통과였다. 기존 click/slider helper에서 ACTION_SHOW_ON_SCREEN으로 먼저 스크롤하고 기존 저장/취소/값 assertion을 그대로 확인하게 수정했다. 표시 suite 4개/실패 0, 한글 포함 본문 8개/실패 0, 화질 대조 1개/실패 0, 별도 실제 프로세스 복원 seed 1개→force-stop→verify 1개 통과를 확인했다. Canvas 페이지 action에 TYPE_VIEW_SCROLLED/fromIndex/toIndex/itemCount를 추가한 후 전체 57개/실패 0·앱 실행도 통과했다.

짧은 문서를 크게 축소해 전부 표시하면 center 기반 viewport는 마지막 페이지에 머물 수 있어, 본문 창에서 읽을 페이지 번호를 별도로 지정하도록 했다. 해당 UI는 PDF 줌/위치를 변경하지 않으며 선택/복사 UI를 재개하지 않는다. 이전 Dialog dismiss가 새 요청을 취소하지 못하도록 소유권을 먼저 비우고 콜백도 owner를 확인한다. API 30 이상 본문 경로에서 Context.bindIsolatedService(API 29)의 고유 instanceName으로 요청을 분리했다. Context7/공식 Context 문서와 설치 SDK/기기 실행으로 대조했으며 공유 인스턴스 플래그/새 권한·라이브러리는 없다.

새 본문 10개 최초 실행은 접근성 이벤트 1개 실패였다. 독립 suite 시작 시 UiAutomation 연결 전에 action이 먼저 실행될 수 있어 연결/AccessibilityManager 활성화를 먼저 기다리도록 계측을 고쳤고, timeout의 타 앱 window 텍스트가 trace에 포함되지 않도록 일반 실패로 바꿨다. 기대하는 정확한 TYPE_VIEW_SCROLLED/페이지 인덱스 필터는 유지했다. 이어서 본문 **10개/실패 0**(빠른 교체에서 이전 FD/결과 폐기, 짧은 2페이지 문서의 첫 페이지 본문 접근과 줌/위치 불변 포함), 본문 창까지 포함한 글꼴 **1개/실패 0**(6조합)을 확인했고 원래 배율 1.1을 복원했다.

최종 debug app **35c52b4b40124d038cb19ddcc90fed167a38db92d23ce134d23daa96412bb881**/계측 **84455f8ba364ed119f1ebf9649fe5cfe9d7052890507b24c6951f5b091c7cbfd**의 strict 빌드·단위 44개/Lint/ktlint를 통과한 뒤 기본 **59개**와 최종 별도 화질·프로세스 복원을 실행 중이다. 이전 57개/별도 검사를 최종 59개 통과로 합산하지 않는다. 앱은 pause 중 새 본문 요청도 시작하지 않는다. 상세 재현/통합 측정 판정은 [최종 통합 절차](../testing/p2-integration.md)를 따른다.
