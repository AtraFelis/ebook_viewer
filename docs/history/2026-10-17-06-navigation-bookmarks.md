# P1 — 페이지 이동·북마크·번호 표시 설정

2026-10-17 (Asia/Seoul). `C:\Users\cotjs\workspace\ebook_viewer`.

## 요청·브랜치

사용자가 PR #4 CI 동안 P1 개발을 계속하도록 요청했다. 페이지 이동/북마크를 다음 범위로 선택했고, 페이지 번호 표시/숨김을 사용자가 정할 수 있어야 한다는 추가 요구를 반영했다. 기본 숨김, 설정 켜기/끄기 및 재실행 보존을 구현한다. 자동 배경색·선명도 복구 지연은 계속 P2다.

기존 체크아웃에 `codex/p1-navigation-bookmarks`를 PR #4 head `be0de9d`부터 생성했다. PR #4의 최종 [CI 38932215957](https://github.com/AtraFelis/ebook_viewer/actions/runs/38932215957)는 성공했고 사용자가 병합했다. fetch/조상 검사로 origin/main `24ef93b`에 부모 head가 포함된 것을 확인했다. 에이전트 병합/reset/clean/강제 push 없음.

## 구현

- 중앙 탭 도구에 페이지 이동·북마크. 1 기반 표시 번호 입력/범위 검증, 현재 줌·유효 가로 위치 유지. 미조사 페이지는 크기 조사 완료 후 이동하며 화면 직접 조작/문서 전환 시 대기를 취소한다.
- 북마크는 문서별/페이지별 앵커를 저장하고 목록에서 이동/길게 눌러 삭제한다. 별도 AtomicFile과 Application I/O 큐를 사용하며 원본 PDF와 기존 읽기 기록/방향/배경색 파일을 수정하지 않는다.
- 번호 표시는 별도 설정으로 보존하고 작은 overlay로 표시한다. 숨김 상태에서도 메뉴 기능에 접근할 수 있고 viewport 크기는 그대로다. 저장 실패는 오류를 안내하며 체크박스를 이전 상태로 돌린다.
- [ADR 0005](../adr/0005-navigation-bookmarks-and-page-numbers.md). 새 런타임 의존성/lock/verification 변경 없음. 북마크 재연결은 기존 영구 URI 재선택 경로에 연결했다.

## 검증

`. ./scripts/android-env.ps1` 후:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain
adb shell am instrument -w -e suite navigation dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation
./scripts/device-smoke.ps1
```

strict 빌드·단위 19개·Lint 오류/경고 0·ktlint 통과. 초기 긴 줄은 수동으로 정리했고 숫자 입력의 Lint SetTextI18n은 리소스 형식+Locale.ROOT로 해결했다. 기존 페이지 프레임과 숫자 입력을 혼동하지 않고 입력은 그룹 없는 숫자로 유지한다. 검사 비활성화/ktlintFormat 실행 없음.

신규 기기 4개 선택 실행은 `P0 tests=4, failures=0`, 첫 전체 회귀는 `P0 tests=27, failures=0` 통과했다. 미조사 페이지/고정 배율·가로 위치, 북마크 중복/재연결/원자 복구/알 수 없는 파일 보존, 페이지 번호 표시/숨김·디스크/Activity 재실행, 페이지 이동 입력/북마크 목록 이동·삭제를 검증했다.

코드 점검으로 URI 재선택 직후의 북마크 이동을 PDF 열기 성공 이후로 미뤘다. 북마크 이전이 성공한 뒤 읽기 기록을 재연결하고, 대기 중 재연결 URI를 Bundle에 보존한다. 번호 설정의 알 수 없는 버전 덮어쓰기 방지 계측도 보강했다. strict 검사를 재통과했고 최종 코드의 전체 27개를 다시 실행해 `P0 tests=27, failures=0` 및 앱 설치/실행까지 통과했다. 첫 전체 로그는 `navigation-full-before-relink-guard.log`로 보존한다. 실제 provider 재연결의 end-to-end 수용을 완료했다고 표현하지 않는다.

첫 선택/전체 debug APK SHA-256: `725bb0ab27ddca03c770f3b7b6eca336f9b466691be743af6d73c5072b9a136d`. 저장 경로 보완 후 최종 APK: `f3214cdf36b64f51131d35aa408e8ff50672869176f4064c55caa7420d445dd4`.

원본은 Git 제외 `local-artifacts/navigation-build.log`, `navigation-focused.log`, 이후 `navigation-device-full.log`. 개인 PDF·URI·기기 식별자를 로그/Git에 넣지 않는다. 개인 PDF 손입력·실제 provider 재연결·별도 강제 프로세스 종료/재부팅·다른 API/기기와 장시간 성능은 미검증이다.

## 다음 행동

최종 기기 회귀와 문서 확인 후 별도 기능 PR로 올린다. P1 전체 완료는 아니며 이후 간격/크기 모드·파일/오류 UX 등 남은 수용을 진행한다.

## 중단 작업 마무리

사용자가 이어서 진행하고 완료/다음 범위를 정리하도록 요청했다. Git 작업 트리는 깨끗했고 구현 `8861ae7`은 이미 origin에 push돼 있었다. [push CI 38935046856](https://github.com/AtraFelis/ebook_viewer/actions/runs/38935046856)의 head가 해당 구현 커밋이며 success임을 확인했다. PR 미등록만 남아 있어 main 대상 [PR #5](https://github.com/AtraFelis/ebook_viewer/pull/5)를 만들고 채팅에 연결했다. fetch/조상 검사로 부모 PR #4가 main에 포함됨을 재확인했다.

이번 마무리는 PR 등록과 상태 문서 갱신뿐이며 앱 코드를 바꾸지 않았으므로 앱/기기 테스트를 반복하지 않았다. 구현 커밋 CI 성공과 [PR 최종 head checks](https://github.com/AtraFelis/ebook_viewer/pull/5/checks)를 구분한다. 에이전트 병합 없음. 다음 구현 순서는 페이지 간격/원본 크기·너비 통일 모드 → 파일 접근/오류 UX와 실제 provider 수용 → 개인 PDF 기능 확인이다. 자동 색상/선명도 복구 지연은 P2다.
