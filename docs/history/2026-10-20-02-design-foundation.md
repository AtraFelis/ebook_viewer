# 디자인 적용 계획과 공통 스타일·홈 1차

2026-10-20. 사용자는 P2 다음 작업으로 Figma 디자인 적용을 선택했다. `design.md`를 읽고 계획 후 진행하며 한도에 걸린 Figma MCP는 사용하지 않도록 지시했다.

## 기준·자료·Git

AGENTS → SESSION → CHECKLIST → 디자인 명세/관련 계획·Git/개발 규칙과 기존 UI/기기 검사 소스를 읽었다. 디자인 구현·computer-use 스킬을 읽되 사용자의 명시적 제한을 우선해 Figma MCP 호출은 생략했다. 기존 Chrome 로그인 세션에서 Figma 홈 `30:147` → 읽기 `30:176` → 중앙 탭 도구 `30:190`을 클릭하고 시각 확인했다. Figma 파일의 노드/공유 권한/디자인은 수정하지 않았다. 후속 자료는 주요 프레임 PNG + 필요한 아이콘 SVG가 적합하며 현재는 명세와 브라우저로 착수할 수 있다.

시작 작업 트리는 깨끗했고 `codex/p2-completion`에 사용자 디자인 인계 커밋 `ea91122`가 원격보다 1개 앞서 있었다. `git fetch origin`은 제한 환경의 FETCH_HEAD 쓰기 권한 때문에 처음 실패했고 승인된 재실행으로 성공했다. origin/main은 PR #18 병합 `8d0731b`로 갱신됐다. 현재 체크아웃에서 `git switch -c codex/design-foundation` → `git merge --no-edit origin/main`을 수행해 디자인 커밋을 보존했다. 별도 main checkout/pull, reset/clean·강제 push·새 worktree는 실행하지 않았다. 기존 사용자 디자인 worktree도 건드리지 않았다. `git diff ea91122 -- docs/design.md`는 비어 있어 명세 원문을 유지했다.

[적용 계획](../plan/design-implementation.md)은 검토 가능한 5단계와 단계별 인수 조건을 나눈다. 첫 범위는 공통 토큰/홈이며 기존 입력·설정 폼 재배치와 읽기 도구/슬라이더/첫 안내/실패 초안은 후속이다. 전체 디자인 완료나 최종 시안 수용으로 보고하지 않는다.

## 구현

- Light/dark 배경·surface·텍스트·경계·accent·pressed·disabled 토큰과 테마 상속을 추가했다. API 29 이상 Force Dark 차단도 유지한다. 플랫폼 대화상자는 공통 기본 색상을 받고 폼 재배치는 후속이다.
- 시스템 글꼴 sp, 카드 16dp/버튼 12dp 모서리, 48dp 이상 버튼, 홈의 24dp 내용 여백을 적용했다. inset은 외부 컨테이너에만 반영해 내용 여백을 덮어쓰지 않는다.
- 마지막 읽던 문서 카드/실제 페이지 진행/이어 읽기, SAF 열기, 최근 문서 전체 목록, 빈 상태, 기록 관리의 명시적 삭제와 기존 길게 누르기를 제공한다. 시안의 최근 두 건 예시를 실제 목록 개수 상한으로 적용하지 않았다. 문서 관리는 첫 단계에서 동일 홈의 삭제 버튼 표시로 구현하며 기록 선택/삭제 확인 로직은 Activity가 소유한다.
- 제목은 시각적으로 2줄 말줄임하고 문서 조작의 접근성 설명에 전체 제목/위치를 보존했다. 불러오기/저장 실패는 기존 안전한 메시지/기록 보존 경로를 유지하며 성공한 읽기 목록만 빈 상태로 표시한다. 진행 정보가 없으면 0/0을 만들지 않는다.
- 홈 표현은 `ReaderHomeView`, 공통 표현은 `ReaderStyle`로 분리했다. PdfRenderer/큐/캐시/뷰포트·읽기 기록/설정 형식/SAF·현재 페이지 본문 읽기는 변경하지 않았다. 새 런타임 의존성/서체 패키지/Compose를 추가하지 않았다.
- 기존 Library/프로세스 복원 검사는 Button의 제목 대신 카드 ID/접근성 제목으로 문서를 선택한다. 큰 글꼴 검사는 카드도 접근 가능한 조작 범위에 포함한다. 인수 조건이나 assertion을 제거하지 않았다.

## 실제 검증과 USB 해제

PowerShell에서 `. ./scripts/android-env.ps1` 후 다음을 실행했다. 첫 Gradle은 사용자 캐시 lock 쓰기 제한으로 실패했고 승인된 재실행을 사용했다.

```powershell
./gradlew.bat --dependency-verification strict :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck
```

초기 코드의 ktlint 줄바꿈을 수동 수정했다. ktlintFormat은 실행하지 않았다. Lint가 프로그램 생성 전용 custom View의 도구 생성자 부족을 지적해, 억제/가짜 생성자 대신 플랫폼 LinearLayout을 조합하는 표현 객체로 정리했다. 이후 전체 명령은 성공했다. 단위 XML 집계는 **44개/실패0/오류0/건너뜀0**, Lint는 **오류0/경고0**(버전 정보 별도), ktlint 통과다. 계측 빌드의 기존 PageTextDeviceTest 접근성 API deprecated 경고 2개는 그대로다. 앱·계측 APK 빌드는 기기 실행/시각 수용과 다르다.

사용자가 USB 안전 제거 중 ‘SAMSUNG Android ADB Interface 사용 중’ 오류를 알렸다. 이 세션에서는 기기 검사를 실행하지 않았으나 이전 ADB 서버가 연결을 점유할 수 있어 `adb kill-server`를 실행했다. 최초는 제한 환경의 localhost 소켓 접근 오류10013, 승인된 재실행은 종료됐다. 이어 `Get-Process adb`와 `Get-NetTCPConnection -LocalPort 5037 -State Listen`으로 **프로세스0/listener0**을 확인했다. 이후 ADB 기기 조회/설치/자동 테스트는 실행하지 않으며 PC 개발만 진행한다. Windows 안전 제거 성공 자체는 사용자 확인 전이다.

## 남은 검증

이번 홈 앱의 실제 스크린샷/밝음·어둠/긴 제목·200%/TalkBack, SAF 열기·이어 읽기/오류/기록 삭제 취소·확정과 프로세스 복원은 기기에서 미실행이다. 기존 P2 결과로 대신하지 않는다. 홈 시각/기기 검증 전에는 단계1을 수용 완료로 표시하지 않는다. APK 설치/검사를 원할 때 USB 재연결과 잠금 해제를 요청한다. 디자인 변경만으로 기존 성능 20그룹/장시간 검사를 반복하지 않는다.

PR/CI/CodeRabbit 실제 결과는 게시 후 별도로 기록한다. 기기 검증 전에는 Draft로 둔다. 사용자만 병합한다.

기존 LibraryRestart/큰 글꼴 검사의 카드 선택자 보완 후 같은 strict 명령을 다시 실행해 **BUILD SUCCESSFUL(4초)**을 확인했다. 단위는 변경 없어 up-to-date이며 직전 실제 실행의 44개 결과를 유지한다. 변경 문서 5개의 로컬 링크 **101개/누락0**과 `git diff --check`도 통과했다. 이는 기기 검사 실행을 뜻하지 않는다.
