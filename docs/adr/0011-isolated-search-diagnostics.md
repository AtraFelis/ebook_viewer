# ADR 0011 — 검색 재개 전 별도 APK·격리 UID 진단

2026-10-18 (Asia/Seoul). 상태: **진단 도구/일부 기기 검증 보존 · 사용자 결정으로 후속 작업 보류 / 검색 UI 재활성화 승인 아님**.

사용자가 검색을 필수로 보지 않아 후속으로 미뤘다. 이 ADR의 추가 진단/제품 통합은 현재 작업의 우선순위나 P2 완료의 선행 조건이 아니다. 기존 코드/수행 이력/안전 제한은 보존하며 검색을 재개할 때 다시 검토한다. [범위 변경](../history/2026-10-18-11-search-deferral.md).

## 배경과 결정

[ADR 0010](0010-on-demand-platform-pdf-search.md)의 앱 프로세스 내 텍스트 보조 renderer는 개인 파일 수용에 실패했다. 사용자는 430페이지, 스캔 이미지 위주/선택·복사 불가, 약 두 글자 질의였다고 추가로 설명했다. 개인 PDF를 분석한 결과와 이 사용자 설명을 구분한다. LOW_MEMORY 종료 사유만으로 native 누수·이미지 디코딩·동시 renderer·페이지 수를 단독 원인으로 확정하지 않는다.

읽기 앱을 실험 대상으로 다시 쓰지 않는다. 같은 AGP 8.7.2/Gradle 8.9/JDK 17/SDK 35로 `:searchprobe` 진단 전용 APK를 만든다. Java/Android 공개 API만 사용하고 런타임 의존성을 추가하지 않는다. min 30은 이 진단 APK의 플랫폼 확장 조회 조건이며 읽기 앱의 min 23을 바꾸지 않는다. 읽기 앱은 진단 모듈을 의존/번들하지 않는다. 진단 APK는 testOnly이고 release variant/launcher/네트워크·저장소 권한이 없다.

서비스는 `isolatedProcess=true`, `exported=false`이며 부모와 다른 격리 UID임을 매 요청 확인한다. 부모는 검증한 내장 합성 파일만 FD로 전달한다. 사용자 URI/파일 경로/질의 입력을 받지 않는다. 바인딩은 `BIND_WAIVE_PRIORITY | BIND_NOT_FOREGROUND`로 부모의 우선순위를 자식에 전달하지 않는다.

## 먼저 고정한 진단 중단 정책

- 호스트 시작 전 및 검색 전 MemAvailable 최소 2,176MiB, 자식 실행 중 최소 2,048MiB.
- 자식 RSS 절대 160MiB 또는 시작 대비 32MiB 증가 시 중단. RSS는 공유 코드/매핑도 포함하므로 private/native 할당량과 다르다.
- 검색 페이지 작업의 2초 기한, 문서 검색 15초 기한. 50ms 간격으로 메모리/시간을 관측한다. 중단 통지 후 자기 프로세스를 종료하고 native 호출과 동시에 renderer를 close하지 않는다.
- 호스트 instrumentation 기한은 guards 40초/search 25초다. 기한 초과/종료 시 **진단 APK만** `am force-stop`한다. 읽기 앱 PID 상태가 달라지면 다음 실험을 중단한다.
- FD 입력 8MiB 상한, 고정된 내장 자료/모드만 허용. 스캔 비교 자료의 이미지는 32×32 RGB다. 고해상도 스캔/개인 파일은 이 휴대폰 실험에 넣지 않는다.

이 값은 실험 전에 고정했으며 실행 거부나 중단 때문에 낮추거나 올리지 않는다. RSS/기한은 **샘플링 기반 중단 정책이며 kernel hard limit가 아니다.** 관측 간격의 초과 할당·VM 전체 정지·OS 스케줄 지연을 완전히 막는다고 주장하지 않는다. 격리 UID도 시스템 전체 RAM을 분리하지 않는다.

## 검증 경계

guards는 ping, 150ms worker 대기 기한, 메인 looper 대기 기한, 취소, 최대 4MiB의 의도적 할당(1MiB 증가 예산), 자식 강제 종료, 부모의 마지막 바인딩 해제, 과대 FD 거부, 재연결의 9개다. 실제 시스템 OOM을 유발하지 않는다. 종료는 메인 looper에 예약하지 않고 독립 스레드에서 수행하며, terminal IPC보다 먼저 종료를 예약한다. 부모는 `onServiceDisconnected`만으로 자식 종료를 판정하지 않고 Binder death를 기다린다.

바인딩 해제 검사는 worker만 대기시킨 뒤 부모가 취소 명령 없이 연결을 해제하고 2초 안의 Binder death를 요구한다. 이때 서비스 메인 looper는 정상이며 `onDestroy`가 자기 프로세스를 종료한다. 메인 looper와 감시자까지 모두 멈춘 프로세스의 강제 회수 증거가 아니다. [공식 bound service 수명](https://developer.android.com/develop/background-work/services/bound-services)은 마지막 바인딩 해제 후 서비스 파괴를 설명하지만 즉시 프로세스 종료 기한을 보장하지 않는다. Context7 guide의 수명/연결 콜백 설명과 대조했다.

호스트는 단순 성공 문자열/건수만 믿지 않고 각 결과·격리·Binder death·실제 대기 확인과 부모 회수 시간을 대조한다. 호스트 안전 단위 테스트 16개는 기존 12개에 메인 대기 확인/부모 회수 증거 누락 거부와 설치 전 메모리 gate/설치 기한 검사를 추가한다. 9개 guard의 기기 실행 여부는 이력에서 별도로 판정하며 기존 APK의 7개 통과를 재사용하지 않는다.

자식의 대기 주입은 Java sleep이다. native/VM 전체가 멈춘 상황의 완전한 재현이 아니다. 호스트 timeout 경로는 PC 모의 테스트이며 실제 기기에서 강제한 호스트 기한 초과는 아직 없다. 또한 별도 APK를 외부 ADB가 중지하는 현재 방식은 제품 앱 내부의 중단 보장을 대신하지 않는다. 제품 내 프로세스 권한·마지막 바인딩 해제/종료·LMK·시간 초과 복구를 추가로 검증해야 한다.

## 대안과 다음 판단

앱 worker에서 페이지마다 yield하는 방식은 native 메모리/중단 상한을 제공하지 못했다. renderer를 16페이지마다 다시 여는 비교도 1회 표본에서 RSS 증가 예산에 걸려, 이것만을 해결책으로 채택하지 않는다. 같은 UID의 별도 프로세스는 부모가 종료하기 쉽지만 앱 내부 데이터에 접근 가능한 보안 경계가 다르다. 격리 UID를 제품에 적용할 때는 부모가 정말 막힌 자식을 종료/회수할 수 있는지도 확인한다. 현재 어느 제품 구성을 확정한 것은 아니다.

선택한 합성 자료가 원래 문서의 해상도/압축/폰트·구조와 같지 않으므로 수용 실패 원인 재현/해결이나 검색 완료를 선언하지 않는다. 검색은 계속 비활성화한다. 외부 라이브러리가 필요해지면 별도 구성 승인 절차를 따른다.

## 근거

- [PdfRendererPreV](https://developer.android.com/reference/android/graphics/pdf/PdfRendererPreV): FD 소유권, 비신뢰 문서의 최소 권한 격리 프로세스 권고. 이 보안 권고를 RAM hard limit 보장으로 해석하지 않는다.
- [서비스 격리 속성](https://developer.android.com/guide/topics/manifest/service-element#isolated), [BIND_WAIVE_PRIORITY](https://developer.android.com/reference/android/content/Context#BIND_WAIVE_PRIORITY).
- Context7 `/websites/developer_android_reference`에서도 PreV 수명/격리 권고를 조회했고 공식 페이지·설치 SDK 35 서명으로 대조했다. 현재 API 33/S 확장 23에서의 실행과 다른 기기를 구분한다.
- [재현/안전 절차](../testing/search-isolation-probe.md), [실제 명령/결과/한계](../history/2026-10-18-09-search-isolation-probe.md).
