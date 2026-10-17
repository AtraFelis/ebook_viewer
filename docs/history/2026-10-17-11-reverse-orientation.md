# P1 역방향 화면 고정

2026-10-17 (Asia/Seoul). 경로 `C:\Users\cotjs\workspace\ebook_viewer`.

## 요청·기준

사용자가 기존 수명 보완 앱은 “다 잘 돼”라고 확인한 뒤 가로 역방향/세로 역방향 고정을 요청했다. 이전 Git 작업의 자동 승인 검토 사용량 제한으로 로컬 문서만 남아 있었고 20:12 KST 재개 시 Git/검사 도구가 정상 동작함을 확인했다. PR #8의 개인 수용·CI 결과를 `db616d5`로 push하고 현재 체크아웃에 `codex/p1-reverse-orientation`을 만들었다. worktree/reset/clean/강제 push/사용자 변경 덮어쓰기 없음.

PR #8 구현 소스는 `8270cde`와 같고 head `bc26364`의 CI 39012958849 및 후속 `db616d5`의 CI 39027868781 성공을 확인했다. 한도 복구 후 [새 head 수동 리뷰](https://github.com/AtraFelis/ebook_viewer/pull/8#issuecomment-6233223698)를 한 번 요청했고 [실제 접수](https://github.com/AtraFelis/ebook_viewer/pull/8#issuecomment-6233224634)를 확인했다. 진행/완료/지적은 후속 실제 조회와 구분한다.

## 구성·검사

[ADR 0003](../adr/0003-immersive-reader-and-orientation.md)을 갱신하고 두 enum/리소스/플랫폼 매핑을 추가했다. enum 이름을 쓰는 설정 v1을 유지해 기존 세 모드를 읽는다. 새 런타임 의존성·SDK/AGP/Kotlin/Wrapper/저장 라이브러리 변경 없음. P2 선명도 복구/자동 배경색은 진행하지 않는다.

Context7 조회는 화면 방향의 간접 자료가 대부분이어서 [공식 ActivityInfo](https://developer.android.com/reference/android/content/pm/ActivityInfo#SCREEN_ORIENTATION_REVERSE_LANDSCAPE)와 설치 compile SDK 35 `android.jar`의 `javap`을 대조했다. 두 역방향 상수는 API 9부터 공개되어 min 23보다 이르다. 다른 API/제조사에서 실기기 검증을 완료했다는 의미는 아니다.

`. ./scripts/android-env.ps1` 후 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain` 실행. 최초 테스트 줄 길이 오류를 수동 보완했고 ktlintFormat은 사용하지 않았다. 최종 명령은 빌드·단위 22개·Lint 오류/경고 0·ktlint 통과했다. 로그 `local-artifacts/reverse-orientation-build.log`는 Git에 넣지 않는다.

신규 계측은 세로→세로 역방향·가로→가로 역방향의 실제 display rotation 차이 180도, 선택 저장·앵커/줌·전체 화면·재실행 보존을 확인하도록 추가했다. 기존 저장 테스트는 다섯 이름의 디스크 왕복과 미지원 버전 보존을 확인한다. 가로 화면에서 일부 목록 항목이 화면 밖에 있으면 실제 플랫폼 목록을 접근성 스크롤하며 테스트 준비 조건/timeout을 완화하지 않는다. 전체 suite에서 제외하지 않고 device-smoke 기대 수를 41로 갱신했다.

## 기기 실행·다음 행동

전용 immersive suite는 기존 4개와 신규 1개를 포함해 **5개/실패 0** 통과했다. 실제 display rotation의 180도 차이를 두 축에서 확인했고 읽기 줌/앵커·전체 화면·재실행 선택, 기존 세 모드와 새 두 모드의 디스크 왕복을 검증했다. 로그 `local-artifacts/reverse-orientation-focused.log`. 이어 같은 APK에서 기본 전체 41개를 실행하며 최종 결과는 별도 기록한다.

승인된 API 33 한 대/잠금 해제를 식별자 출력 없이 확인하고 `adb install --no-streaming -r`로 앱/계측 APK를 설치했다. `adb shell am instrument -w -e suite immersive dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation`의 최종 결과와 이어지는 전체 회귀는 완료 후 기록한다. 합성 PDF만 사용하며 개인 파일명/내용/URI를 기록하지 않는다. PR #8 병합은 사용자가 하고, 후속 PR의 기준 브랜치는 실제 병합 여부를 확인해 정한다.
