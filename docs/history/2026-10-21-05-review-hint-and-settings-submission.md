# PR #22 리뷰 수정·가로 이동 잠금 후속 계획

2026-10-21 (Asia/Seoul). 사용자 요청은 가로 이동 잠금을 계획에 추가하되 확대·축소를 막지 않고, 우선 현재 PR 리뷰부터 수정하는 것이다.

## 범위와 기반

- 현재 `codex/design-hint-settings-retry`/PR #22의 head490eaeb와 미커밋 게시 상태 기록을 보존했다. `git fetch origin` 후 원격/현재 head를 확인했다. 사용자 `docs/design.md`의 별도 아이콘 변경은 수정/커밋하지 않는다. 새 worktree/강제 작업/의존성 변경 없음.
- head490eaeb의 원격 Android CI39570792964/39570760112 모두 **SUCCESS** 확인. 실제 CodeRabbit 리뷰5597112432는20개 파일/지적2개, source490eaeb를 검토했다. 자동 skip 성공 체크와 구분한다.
- [가로 이동 잠금 계획](../plan/horizontal-pan-lock.md)을 로드맵/디자인 후속 계획에 연결했다. 한 손가락 드래그·관성의 X만 제한하고 핀치 확대·축소/중심 보존·핀치 후 새 가로 위치를 허용한다. 기존 줌 초기화/페이지 스냅/원본 비율 변경 없음. 이번 PR에서 잠금 기능은 **계획만 추가/구현·검증 미실행**이다.

## 실제 지적과 판단

1. **4334766878/MainActivity**: 조작 전 Activity 재생성의 `showReader → showTools`가 `dismissReadingHint`로 Application 안내 상태를 소비한다. 실제 경로와 일치해 채택했다. 프로그램에 의한 도구 표시에서는 레이블만 숨기고 메모리 상태를 유지한다. 실제 사용자 입력의 기존 ACTION_DOWN/뷰어 조작 경로는 안내를 해제한다. 첫 안내가 보이는 상태에서 실제 Activity 재생성→다시 안내 표시→중앙 탭 도구·해제→다른 열기에서 재표시 안 함을 검사한다.
2. **4334766910/SettingsSaveUi**: 비동기 결과 이전에 저장 제출 자체가 동기 예외를 던지면 컨트롤이 잠긴 채 남는다. 현재 저장소는 주로 Result로 실패하지만 공통 UI의 제출 계약에서 유효한 복구 경로이므로 채택했다. 완료 함수를 분리하고 Exception을 기존 실패 UI로 전환한다. 기존 시도별 completed 가드로 중복 결과/완료 후 예외가 새 결과를 덮지 못하게 한다. 임의 타임아웃으로 진행 중 쓰기를 실패로 오인하거나 중복 저장을 시작하지 않는다.

테스트 전용 폼에서 첫 제출 IOException→입력/자동 선택/버튼 재활성·오류→재시도 성공을 검증하고, 성공 callback 뒤의 예외도 종료 결과를 바꾸지 않는지 확인한다. 사용자 저장 파일이나 디스크 용량/권한을 손상시키지 않는다.

## 검증

```powershell
. ./scripts/android-env.ps1
./gradlew.bat --dependency-verification strict :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck
```

- **BUILD SUCCESSFUL 11초**, 단위51개/실패0/오류0/건너뜀0, Lint 오류0/경고0/정보6, ktlint 통과. ktlintFormat 미실행.
- app SHA-256 `ce2868b0cd83c80319775296e155fc969b042ea99cbd3838269cd3d17ba3a7d6`, 계측 `53703b9ea8f805c3e34226584bf08b5172c5261a5a4f368d0df77e5a145a8798`.
- 새 계측2개 추가로 guidance9개/전체 기대79개다. 기대 수는 실제 전체 실행 결과가 아니며 전체·성능·다른 API를 반복하지 않는다. 앞선26개 통과/사용자 수용은 변경 전 결과로 보존한다.

## 기기 결과와 게시 절차

- 연결·승인·잠금 해제된 API33 한 대를 확인한 뒤 데이터 보존 `--no-streaming -r -t` 설치했다. 같은 최종 앱/계측으로 `local-artifacts/design-device.ps1 -Mode suite -Suite guidance` **9개/실패0**, `-Suite immersive` **7개/실패0**, 합 **16개/실패0**. 새 두 재현 검사와 기존 첫 조작/초안/중복/배치 저장 경합·도구/핀치·정/역방향 앵커를 확인했다. 이번 새 검사 실패 없음.
- 설치 앱 SHA-256이 위 PC APK와 일치했다. 원래 font_scale **1.1** 확인, 앱·계측 force-stop/ADB kill-server 및 ADB 프로세스 **0** 확인. 개인 PDF/목록 캡처·로그/식별자 수집 없음.
- 전체79개/성능/새 개인 수용은 반복하지 않았다. 앞선 사용자 수용은 유지하되 이번 자동 최소 회귀 결과와 구분한다. 두 코드 경로 수정이며 UI 시각 스타일/렌더러를 바꾸지 않았다.
- 검증을 마친 수정과 계획/상태 기록을 같은 PR 브랜치에 일반 push하고 두 관련 스레드에 근거를 답변한다. 실제 포함 리뷰가 잔여0이고 소규모 수정이므로 추가 수동 리뷰 요청은 하지 않는다. 수정 head의 원격 CI·게시/스레드 확인은 후속 결과로 기록하며 병합은 사용자다.

## 게시 확인

- 수정 fdd442b06e6171d9c9e9cdbb765801146f1bf682 일반 push·PR #22 설명 갱신 완료. 답변4334861747/4334862030을 게시하고 `PRRT_kwDOVLKYJM6usAon`/`PRRT_kwDOVLKYJM6usApE`의 **isResolved=true**를 확인했다.
- 새 CI39572813829/39572806292는 확인 당시 IN_PROGRESS다. 새 CodeRabbit 성공 체크를 수정 head의 실제 재검토 완료로 취급하지 않는다. 추가 수동 요청/Autofix/병합 없음.
- 작은 게시 상태 문서만을 위한 재push로 CI를 다시 시작하지 않고 이 결과는 로컬에 모아 둔다. 사용자 디자인 변경도 계속 보존한다. 다음은 수정 head CI 확인/사용자 병합이며 가로 이동 잠금은 별도 후속 계획이다.

## 최종 확인

- 사용자의 남은 작업 질문에 실제 원격을 다시 읽어 fdd442b의 CI39572813829/39572806292 모두 **SUCCESS**, PR #22 **MERGED**/merge `e069dc9eedba37a602e40b77549fb6c560c2a662`/2026-10-21 18:01:04 KST를 확인했다.
- 두 스레드는 모두 resolved=true이고 CodeRabbit의 마지막 답변이 각각 안내 레이블/상태 분리 및 동기 Exception 복구·회귀 검사에 대한 수정 확인임을 확인했다. 이는 독립적인 새 전체 재리뷰 실행으로 보고하지 않는다.
- 현재 디자인 적용 계획1~5단계의 구현·관련 자동 검사·기록된 사용자 수용·리뷰 수정·사용자 병합을 완료했다. 가로 이동 잠금은 확대·축소 유지 조건이 있는 별도 후속 미구현 계획이다. 완료 문서는 로컬에 갱신했고 새 코드·기기 테스트/ADB를 시작하지 않았다. 사용자 디자인 변경을 보존했다.
