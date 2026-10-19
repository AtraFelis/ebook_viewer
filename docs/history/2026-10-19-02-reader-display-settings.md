# 2026-10-19 — P2 테마·읽기 밝기·화면 유지

## 기준과 사용자 변경 보존

AGENTS→SESSION→CHECKLIST→P2 범위를 읽고 기존 Figma 블록/이력 두 개만 미커밋인 것을 확인했다. `git fetch origin`/`gh pr view 14`에서 PR #14의 사용자 병합(2026-10-19 05:27:04 UTC), 병합 d35ff36을 확인했다. Figma tracked 두 블록을 ignored 패치/복구용 stash로 보존하고 현재 체크아웃에서 main에 `git pull --ff-only origin main` 후 `codex/p2-reading-settings`를 만들었다. stash를 적용하고 복구용 원본은 유지했다. 두 이력 SHA는 기존과 같다(01 0be1a8cb…/02 03dff523…). reset/clean/강제 push/새 worktree/병합은 하지 않았다.

## 구현과 조사

[ADR 0014](../adr/0014-reader-display-settings.md)에 따라 플랫폼 테마/Window/별도 AtomicFile v1을 사용했다. 새 런타임 의존성·SDK/Gradle/CI·시스템 설정 권한/기기 밝기 변경은 없다. 기존 PdfWorker/타일·64MiB 캐시는 변경하지 않는다. 테마 변경은 기존 viewport 복원과 Bundle 테마 전달, 밝기/화면 유지는 준비된 문서/reading/resumed 상태에 한정했다. 설정 메뉴/새 대화상자는 스크롤 가능하게 하고 방향 5개 선택은 별도 목록으로 분리해 가로에서 항목이 늘어나는 문제를 피했다.

Context7 Android Developers resolve 후 `/websites/developer_android_reference` 두 질의에서 관련 수명/상속 속성만 확인했다. 해당 API 버전 고정 자료가 부족해 ADR의 공식 문서로 교차 확인했다. SDK 35 컴파일 성공과 API 33 실행을 혼동하지 않는다.

처음 두 strict 명령은 새 코드/계측의 줄바꿈·미사용 import 등 ktlint 오류로 실패했다. `ktlintFormat`은 실행하지 않고 직접 수정했다. 세 번째 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain`는 통과했다. 이후 설정 메뉴의 방향 분리를 추가했으므로 최신 재검증 결과는 아래 후속을 따른다. 기본 44개 검증을 새 기능 통과로 세지 않는다.

계측 전 상태 조회에서 승인 기기 1대/Awake/잠금 해제/MemAvailable 2,208,548KiB를 확인했다. 개인 PDF/본문/기기 식별자를 출력하지 않았다. 새 4개 기기 검사는 저장 보존/테마 픽셀·위치/실제 HOME 왕복 Window 수명/설정 취소·저장·재실행을 대상으로 한다. 실제 실행/실패/수정·사용자 확인과 원격 CI/PR/리뷰는 후속으로 기록한다.

설정 메뉴 분리 뒤 전체 strict 명령도 통과했고 실제 XML 합계 단위 41개/실패·오류 0, Lint 0 errors/0 warnings를 확인했다. 별도 계측 도구 DeviceGestures의 강제 keep-awake는 기본 기존 검사에 유지하되 새 표시 suite에는 끌 수 있도록 했다. 도구가 FLAG_KEEP_SCREEN_ON을 덮어써 앱 수명 검증을 오염시키는 것을 막는다. 이 보완 중 주석 앞 공백 ktlint 1건을 직접 수정한 뒤 strict 계측 빌드/ktlint도 통과했다. 데이터 보존 debug 앱/계측 설치 후 `am instrument -w -e suite display .../P0Instrumentation` 4개를 시작했다. 실제 결과는 후속으로 구분한다.

## 최초 기기 결과와 방향 메뉴 회귀 수정

새 표시 suite는 **4개/실패 0**이었다. AtomicFile 미완료 쓰기 복구/알 수 없는 버전 원본과 다른 설정 유지, 밝기 25%/화면 유지의 홈·실제 HOME/복귀·destroy 수명, 밝음→어두움→시스템 선택의 줌/앵커와 합성 PDF bitmap 동일성, 취소/적용·재실행 보존을 확인했다. 이 실행은 이후 Force Dark 보호/메뉴 전환 수정 전 앱이다.

공식 Force Dark 문서에서 플랫폼 Light 테마가 자동 변환에 참여할 수 있는 것을 추가 대조했다. PDF 픽셀/수동 밝은 테마를 보호하도록 Activity/AlertDialog의 API 29 이상 테마에서 변환을 껐고, 계측에 해당 속성 거부를 추가했다. API 23~25 밝은 navigation 바의 흰 아이콘 문제는 검은 바탕으로 대체하며 구형 실기기 통과로 확대하지 않는다. 스타일 import/함수 줄바꿈 2건을 직접 수정한 뒤 최신 전체 strict 빌드/41개 단위/Lint/ktlint도 통과했다.

이전 APK로 먼저 시작한 `./scripts/device-smoke.ps1`은 **48개/실패 2개**로 끝나 성공 판정하지 않았다. 방향 메뉴 첫 선택을 찾는 기존 `orientationSettingsPreserveAnchorAndSurviveRelaunch`와 `reverseDirectionsRotateTheDisplayAndKeepAnchorAndSavedChoice`가 실패했고 다른 46개는 통과했다. Dialog dismiss listener가 메인 큐에 게시되기 전에 새 방향 메뉴의 non-null guard가 걸리는 경로를 확인했다. 설정 메뉴 전환에서 이전 listener를 분리하고 참조를 동기로 비운 뒤 새 대화상자를 열도록 앱을 수정했다. 방향 assertion이나 테스트를 제거하지 않았다. 같은 정리는 배치/배경/표시 하위 메뉴의 이전 콜백이 새 소유자를 지우는 것도 막는다.

수정 뒤 strict 앱/계측 빌드·Lint·ktlint를 통과하고 최신 app SHA-256 `41093bcf089abe7c7ec8f96104667ea1a2ff78b2d678f7af003b8ec90f744e99`, 계측 `cadababf5bb5d9776f13d95113478d9196a83845ce08c42b0f92937cfaa20c0b`로 업데이트 설치했다. `-e suite immersive` 재검증과 후속 전체 결과는 아래 기록으로 구분한다. 설치/검사 로그는 ignored `local-artifacts/reading-settings-*.log`이며 개인 PDF/설정을 clear하지 않는다.

## 최종 기기 회귀·개인 수용·PR

최신 APK 쌍의 방향/전체 화면 suite는 **5개/실패 0**이었다. 이후 strict 단위 재실행도 통과했고 `./scripts/device-smoke.ps1`을 재실행해 같은 app 41093bcf/계측 cadababf의 **전체 48개/실패 0·앱 실행 Status ok**를 확인했다. 별도 5개와 기본 48개를 하나의 53개 실행으로 합치지 않는다. 새 4개도 이번 48개에 포함돼 Force Dark 차단 속성/PDF bitmap 불변·소유권 전환을 포함한 최신 앱에서 통과했다. 원시 로그는 ignored `reading-settings-final-unit.log`, `reading-settings-orientation.log`, `reading-settings-final-smoke.log`다. 새 프레임 성능/실제 OOM/다른 API 기기·프로세스 복원 전체를 실행한 것은 아니다.

검사 진행 중 검토 병행용으로 f3b1816 `feat(reader): add theme brightness and screen-on settings`를 만들고 같은 브랜치에 일반 push해 [Draft PR #15](https://github.com/AtraFelis/ebook_viewer/pull/15)을 등록했다. 상태/검사 미완료를 본문에 명시했으며 Figma 두 블록을 뺀 SESSION/CHECKLIST index 복사본과 명시한 24개 파일만 커밋했다. unstaged diff는 Figma 두 블록 각 6줄만 남은 것을 확인했다. PR 채팅 연결 도구는 응답이 지연돼 대기를 중단했으므로 실제 attachment 성공은 미확인이다. PR 생성/push 성공과 혼동하지 않는다.

최초 [CodeRabbit 수동 요청 6263631101](https://github.com/AtraFelis/ebook_viewer/pull/15#issuecomment-6263631101)을 등록했다. 구현 head f3b1816의 [push CI 39227054763](https://github.com/AtraFelis/ebook_viewer/actions/runs/39227054763)·[PR CI 39227060932](https://github.com/AtraFelis/ebook_viewer/actions/runs/39227060932)는 모두 SUCCESS였다. 접수/리뷰/건너뜀 체크는 없고 봇 응답도 아직 없었다. 성공한 두 GitHub Actions 체크를 CodeRabbit 검토 완료로 세지 않는다.

사용자에게 현재 설치 앱에서 중앙 탭→설정→테마·밝기·화면 유지의 테마 전환/읽기 밝기 조절/목록에서 시스템 밝기 복귀를 확인하도록 요청했고 **“잘돼.”**라고 수용했다. 파일명/본문/이미지를 받지 않았고 이후 추가 기기 조작은 하지 않았다. 자동 Window flag 검사와 실제 가독성/밝기 자기보고를 구분하며 물리 절전 시간/기기 정책 전반 보장은 아니다. 기기 검사·개인 확인 완료 뒤 `gh pr ready 15`로 Draft를 해제했다. 사용자만 병합한다.

사용자가 CodeRabbit이 보이지 않는 이유를 물어 GitHub 댓글·review·head check-runs를 대조했다. 실제 GitHub Actions 두 건만 있으며 봇 접수는 없었다. 저장소 추적 `.coderabbit.yaml`도 없다. [공식 문제 해결 문서](https://kb.coderabbit.ai/articles/1442026547-troubleshoot-why-coderabbit-reviews-might-not-trigger)의 Draft/상태 전환 자동 리뷰 제한을 확인했지만, 무응답의 내부 원인이 Draft라고 확정하지 않는다. 설치/권한/웹훅 변경은 하지 않는다. 최종 결과 기록을 올린 새 head에서 정식 PR 상태의 수동 요청을 한 번 수행하고 실제 응답을 확인한다. 같은 head의 접수/진행 중 요청을 중복하지 않는다.
