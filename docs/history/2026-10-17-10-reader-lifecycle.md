# P1 백그라운드·캐시 취소·문서 수명

2026-10-17 (Asia/Seoul). 작업 경로 `C:\Users\cotjs\workspace\ebook_viewer`.

## 시작 상태와 범위

사용자가 다음 P1 작업을 요청했다. 작업 트리는 깨끗했고 PR #7 최종 문서 head `a282718`의 Windows CI 39010146312 성공 및 사용자 병합을 확인했다. fetch 후 `origin/main`은 `29d0763`이며 기존 head가 조상임을 확인한 뒤 현재 체크아웃에 `codex/p1-reader-lifecycle`을 생성했다. 사용자 변경/reset/clean/강제 push/worktree 생성 없음.

기존 trimCache는 캐시를 비운 직후 타일을 다시 요청했다. 정지/메모리 정리 후 동일 키를 재요청하면 취소 전 UI 큐의 결과도 다시 수용할 수 있었다. [ADR 0008](../adr/0008-reader-lifecycle-and-cache-cancellation.md)에 따라 onStop의 렌더 정지/캐시 해제/위치 저장과 onStart의 같은 뷰포트 재개, 파괴 시 캐시 해제, 별도 타일 취소 epoch를 구현했다. 문서 교체/FD 종료는 기존 single worker 수명을 유지한다. 백그라운드의 열린 renderer/FD·페이지 크기 조사는 유지하며 타일 렌더링만 정지한다. 새 런타임 의존성/저장 스키마/렌더 배율·캐시 상한 변경 없음. P2 선명도 복구/자동 배경색은 진행하지 않는다.

직전 주석 규칙 질문에 답변한 바와 같이 AGENTS의 “주석은 이유와 제약을 설명”은 함수 설명문이나 80% 기준을 금지하지 않는다. PR #7의 일반 비율 권고를 일괄 적용하지 않은 것은 에이전트의 판단이며 저장소의 명시적 금지로 표현하지 않는다.

## 조사·실행

- Context7의 Android Reference query로 ComponentCallbacks2 수준/수명 관리를 조회하고 공식 Activity lifecycle/ComponentCallbacks2와 대조했다(ADR 링크). API 35에서 일부 수준 상수가 deprecated이고 API 34부터 미전달인 점을 확인했다. 이 때문에 캐시 정지를 메모리 알림에만 의존하지 않는다. 새 onLowMemory 의존은 추가하지 않았다.
- `. ./scripts/android-env.ps1` 후 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain` 실행. 최초 줄 길이/줄바꿈 오류는 수동 수정했다. 프로세스 복원 v2 검증 추가 후에도 줄 길이를 수동 보완했다. 최종 strict 명령은 빌드·단위 **22개/실패 0**·Lint **오류 0/경고 0**·ktlint 통과했다. ktlintFormat/검사 억제/의존성 변경 없음. raw 로그 `local-artifacts/reader-lifecycle-build.log`.
- 승인된 API 33 기기 한 대와 잠금 해제를 식별자 출력 없이 확인했다. `adb install --no-streaming -r`로 앱/계측 APK를 설치하고 `adb shell am instrument -w -e suite lifecycle dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation` 실행. 신규 **4개/실패 0** 통과. raw 로그 `local-artifacts/reader-lifecycle-focused.log`.
- 신규 테스트는 70MiB 타일 삽입 중 64MiB 캐시 상한, 정지·trim·메타데이터 갱신 중 캐시 0과 뷰포트 유지, 취소 전 UI 큐의 동일 키 타일 배제, 적색/청색 문서 교체의 올바른 픽셀·반복 FD 종료·종료 후 늦은 전달 배제, 실제 HOME/동일 Activity 복귀·전경 trim·파괴 시 캐시 해제를 검증했다. 메모리 알림은 API 33 콜백 주입이며 OS의 실제 저메모리/OOM/프로세스 강제 종료를 재현한 것은 아니다. Bitmap GC 시점/PSS 감소나 장시간/다른 API 성능을 통과로 주장하지 않는다.

## 전체 회귀와 별도 프로세스 복원

전체 suite에 신규 4개를 포함하고 device-smoke 기대 수를 40으로 갱신했다. 전체 회귀 실행과 별도 seed/force-stop/verify 결과는 완료 후 기록한다. 별도 복원 검증은 기록 v2의 너비 통일/간격 24 pt를 추가해 위치·줌과 함께 확인한다. 전체 40개 suite의 소스와 앱 코드는 그대로이며 별도 프로세스용 테스트만 보완했다. 사용자 개인 PDF/내용/URI는 읽거나 로그/외부 서비스에 올리지 않는다. 이전 개인 PDF 열기·목록→이어 읽기 사용자 확인은 앞선 APK의 수용 결과로 유지하며 새 APK 전체 수용으로 확대하지 않는다.

같은 앱 APK에서 `adb shell am instrument -w dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation`의 **40개/실패 0**을 확인했다. 기존 36개도 제외/완화하지 않았다. 이후 `adb shell am start -W -n dev.example.ebookviewer.p0/dev.example.ebookviewer.MainActivity`는 **Status: ok**다. raw 로그 `local-artifacts/reader-lifecycle-device-full.log`. 앱 debug APK SHA-256은 `92e946db857441a023b7aa3be261811922b7cb560b7b6161254fe577b1b58401`이다. 별도 복원용 테스트만 보완한 APK를 이후 재설치하며 앱/기본 suite 소스는 변경하지 않았다.

최종 계측 APK만 `adb install --no-streaming -r`로 재설치한 뒤 `adb shell am instrument -w -e libraryRestart seed dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation` → `adb shell am force-stop dev.example.ebookviewer.p0` → 같은 runner의 `-e libraryRestart verify` 순서로 실행했다. **seed 1개/실패 0, verify 1개/실패 0**이다. PID가 실제 달라진 것을 테스트가 확인했고 위치·줌·문서별 너비 통일/간격 24 pt를 복원했다. test fixture/해당 URI 기록만 정리했다. 로그는 `local-artifacts/reader-lifecycle-restart-seed.log`, `local-artifacts/reader-lifecycle-restart-verify.log`. 이후 앱 시작 **Status: ok**를 확인했다. 새 APK의 개인 홈 복귀/확대·스크롤/중앙 탭 설정은 사용자에게 정상 여부만 요청했으며 이 시점에는 응답 대기다.

## 다음 행동

검증이 끝나면 SESSION/CHECKLIST를 실제 결과로 갱신하고 기능별 PR·수동 CodeRabbit 요청·원격 CI를 진행한다. 병합은 사용자가 한다. 남은 현재 기기 수용과 API/제조사 provider 전반·재부팅/실제 시스템 메모리 압박 검증은 구분한다.

## PR 등록

실제 결과/결정을 `8270cde`로 커밋했다. fetch 후 origin/main이 여전히 `29d0763`이며 현재 head에 포함됨을 확인한 뒤 일반 push했다. [PR #8](https://github.com/AtraFelis/ebook_viewer/pull/8)을 main 대상으로 등록하고 대화에 연결했다. 이 등록 기록을 push한 뒤 현재 head에 CodeRabbit 수동 리뷰를 한 번 요청한다. 원격 CI/실제 리뷰는 [PR checks](https://github.com/AtraFelis/ebook_viewer/pull/8/checks)와 본문에서 구분하며 로컬 통과로 대신하지 않는다. 개인 새 APK 수용은 응답 대기다. 사용자 변경 덮어쓰기/reset/clean/강제 push/PR 병합 없음.
