# P1 후속 — 전체 화면·중앙 탭·화면 방향

2026-10-17 (Asia/Seoul), `C:\Users\cotjs\workspace\ebook_viewer`.

## 승인과 브랜치

사용자가 CI 진행 중에도 개발을 이어갈 수 있는지 요청했고, 전체 화면·중앙 탭 메뉴·방향 고정을 다음 범위로 진행했다. 현재 체크아웃에 `codex/p1-immersive-reading`을 생성했으며 새 worktree/reset/clean/강제 push 없음. 시작은 PR #1 최종 head `df3ef15`다. 새 런타임 의존성을 도입하지 않았다.

기존 [PR #1](https://github.com/AtraFelis/ebook_viewer/pull/1)의 최종 head `df3ef15` [Windows CI 38923209449](https://github.com/AtraFelis/ebook_viewer/actions/runs/38923209449)는 6분 34초에 성공했다. 사용자 직접 병합 여부를 묻는 메시지에 사용자가 병합하면 된다고 답했고, 이후 GitHub MERGED 상태 및 `git fetch origin`으로 `a67b0a8` 병합 커밋을 확인했다. main의 부모가 `08011b3`, `df3ef15`이므로 후속 브랜치가 이전 기능을 중복 제안하지 않고 main 대상으로 PR을 만들 수 있다. 에이전트가 병합하지 않았다.

## 구현

- 홈/문서 목록과 전체 화면 Canvas를 분리하고 PDF 열기·샘플·상태 문구를 읽기 중 숨겼다. 중앙 탭의 도구는 겹쳐 표시해 메뉴 토글 시 viewport 크기/좌표를 유지한다.
- 중앙 1/3 영역의 탭만 인식하고 핀치/드래그/플링 중단 탭과 구분한다. 메뉴의 설정/문서 목록/읽기 복귀, 뒤로가기로 메뉴 닫기와 목록 복귀를 제공한다.
- API 30+ 플랫폼 WindowInsetsController, 구버전 immersive sticky를 분기한다. 시스템 바의 일시 노출은 허용하고 포커스/설정 복귀 시 읽기 정책을 적용한다. 카메라 cutout만 PDF 안전 영역으로 확보한다.
- API 33+ 플랫폼 뒤로가기 등록, 23~32 legacy 콜백. 처음 Lint는 API 33 manifest 속성 UnusedAttribute 1건으로 실패했고, 구버전 무시/legacy 동작을 명시한 tools:targetApi로 의도를 설명했다. 전역 검사/런타임 API guard는 끄지 않았다.
- 자동/세로/가로 방향을 AtomicFile 별도 설정 파일에 I/O 큐로 저장한 뒤 적용한다. 문서 내부 위치·줌 복원을 유지한다. 손상/알 수 없는 버전의 설정은 덮어쓰지 않는다([ADR 0003](../adr/0003-immersive-reader-and-orientation.md)).

## 실제 검증

`. ./scripts/android-env.ps1` 후:

```powershell
./gradlew.bat ktlintFormat --dependency-verification strict --console=plain
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain
adb shell am instrument -w -e suite immersive dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation
./scripts/device-smoke.ps1
```

- strict debug 앱/계측 APK 빌드, 단위 9개, Lint 오류/경고 0, ktlintCheck 통과. 새 테스트 긴 줄 스타일 오류는 줄바꿈/이름 정리로 해결했다.
- 기기가 Dozing/잠금 상태여서 사용자 잠금 해제를 요청했고 “됐어” 응답 후 UI 테스트를 시작했다. 테스트 Activity 창에만 FLAG_KEEP_SCREEN_ON을 적용해 OS timeout/사용자 설정은 바꾸지 않는다.
- 새 API 33 계측 4개 `P0 tests=4, failures=0`: 중앙 탭 메뉴/바 숨김/뒤로가기와 viewport 불변, 핀치·드래그·외부 탭·플링 중단 구분, 세로↔가로·자동 UI 전환과 앵커·줌/Activity 재실행, 설정 파일 재읽기/알 수 없는 버전 보존.
- 첫 전체 실행은 방향 복원의 기존 15초 대기 초과와 이어진 메인 스레드 assert 실패로 테스트 프로세스가 종료됐다. crash buffer의 예외는 JUnit AssertionError였다. 첫 실패 로그는 `p1-immersive-full-before-wait-fix.log`/`p1-immersive-crash.log`에 보존한다. Activity launch/방향 변경에서 새 인스턴스·포커스·화면 비율·짧은 안정 구간을 기다리고, 메인 스레드 검증 예외를 테스트 스레드로 전달해 JUnit이 실패로 보고하게 했다. 전체 카탈로그 복원 대기만 30초로 구분하며 준비 타일/좌표/바/설정 검증 조건을 유지했다.
- 보완한 전체 기본 계측은 `P0 tests=19, failures=0` 통과했고 앱 실행도 확인했다. probe/프로세스 복원과 독립적인 `-e suite immersive` 선택 실행을 추가했고 기본 suite는 19개다. 테스트 harness 변경 후 신규 테스트만 선택해 통과로 대체하지 않고 기존 포함 전체를 다시 실행했다.

로컬 원본: `local-artifacts/p1-immersive-build.log`, `p1-immersive-test-build.log`, `p1-immersive-focused.log`, `p1-immersive-full.log`. SDK·APK·로그·합성 PDF를 Git에 넣지 않는다. 현재 debug APK SHA-256 `20fb029dcf77657c7c983f5fd6ee6c64a7784c8983647c892d234b055f463b1f`.

## 다음 행동과 한계

19개 회귀와 SESSION/CHECKLIST/사용법을 갱신해 별도 기능 PR로 정리한다. 이 후속 브랜치의 원격 CI는 PR 등록 후 확인한다. P1 전체 완료가 아니며 다음 범위는 자동/사용자 지정 외부 배경색, 이후 간격/크기 모드·페이지 이동/북마크다. 실제 개인 PDF 손입력·물리 회전 센서·설정의 강제 프로세스 종료/기기 재부팅·다른 API는 미검증이다. 별도 성능 최적화와 장시간 벤치마크는 사용자 정정에 따라 반복하지 않았다.
