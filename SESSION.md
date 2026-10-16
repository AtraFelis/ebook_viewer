# SESSION.md — 세션 인수인계

최종 갱신: 2026-10-17 (Asia/Seoul)

## 현재 상태

`codex/p1-navigation-bookmarks`에서 페이지 이동·북마크 및 사용자 지정 번호 표시/숨김을 구현/검증했다. 번호는 기본 숨김이며 설정을 켜도 Canvas 크기/배율은 유지한다. 페이지/북마크 이동은 현재 줌·유효 가로 위치를 보존한다. strict 빌드·단위 19개·Lint·ktlint 및 신규 API 33 계측 4개 통과, 저장 경로 보완 후 최종 전체 27개 회귀·앱 설치/실행까지 통과했다. 이 기능의 원격 CI는 별도 PR 등록 후 확인한다. [ADR 0005](docs/adr/0005-navigation-bookmarks-and-page-numbers.md), [명령·결과](docs/history/2026-10-17-06-navigation-bookmarks.md). 새 런타임 의존성 없음.

`codex/p1-background-color`에서 P1 수동 외부 배경색을 구현/검증했다. 기본/임의 RGB 색상, 미리보기·입력 검증·취소, 별도 AtomicFile 설정 저장을 추가했다([ADR 0004](docs/adr/0004-custom-reader-background.md)). strict 빌드·단위 15개·Lint·ktlint 통과, API 33 전체 23개 회귀·앱 설치/실행까지 통과했다. 구현 `e6753bf`를 main 대상 [PR #4](https://github.com/AtraFelis/ebook_viewer/pull/4)로 등록했다. 최종 head `be0de9d`의 [CI 38932215957](https://github.com/AtraFelis/ebook_viewer/actions/runs/38932215957)가 통과했고 사용자가 병합했다. fetch/조상 검사로 origin/main `24ef93b`를 확인했다. 자동 색 추출과 스크롤 선명도 복구 지연은 사용자 범위 조정으로 P2에 남긴다. [이번 결과](docs/history/2026-10-17-05-custom-background.md), [선명도 P2 계획](docs/plan/p2-sharpness-recovery.md).

사용자의 확대 한도/과도한 축소 피드백을 `codex/reader-zoom-range`에서 구현/기기 검증을 완료했다(PR #2 문서 head `3d7b6d5`부터 분리). 최대 배율 8→32, 짧은 변 너비 맞춤의 75% 핀치 축소 하한이며 회전/기존 기록의 작은 배율은 강제 재맞춤하지 않는다. strict 빌드·단위 12개·Lint·ktlint 통과, API 33 전체 20개 회귀/앱 설치·실행까지 통과했다. 첫 중앙 반복 탭/뒤로가기 실패를 이중 탭 간섭 제거로 보완한 뒤 전체를 재검증했다. 구현 `cb2cc6c`를 main 대상 [PR #3](https://github.com/AtraFelis/ebook_viewer/pull/3)으로 등록했다. 최종 head `5c3550b`의 [CI 38929386952](https://github.com/AtraFelis/ebook_viewer/actions/runs/38929386952) 통과와 사용자 병합을 확인했다. fetch 결과 origin/main은 `af90f29`다. [변경·검증 이력](docs/history/2026-10-17-04-zoom-range.md). 새 런타임 의존성 없음.

후속 브랜치 `codex/p1-immersive-reading`에서 전체 화면·중앙 탭 메뉴·방향 설정을 구현했다. 새 API 33 UI/설정 테스트 4개와 기존 포함 전체 19개 회귀, strict 빌드·단위 9개·Lint·ktlint 통과. 기기에 새 debug APK 설치/실행 완료. 새 런타임 의존성 없음. [ADR 0003](docs/adr/0003-immersive-reader-and-orientation.md), [진행/검증 이력](docs/history/2026-10-17-03-immersive-reading.md). 구현 `ff1ea45`를 [PR #2](https://github.com/AtraFelis/ebook_viewer/pull/2)로 등록했다. 최종 문서 head `3d7b6d5`의 [Windows CI 38927859821](https://github.com/AtraFelis/ebook_viewer/actions/runs/38927859821)까지 성공했다. 사용자가 PR #2를 병합했고 fetch로 origin/main `70829a7` 및 부모 head 포함을 확인했다.

P1 첫 범위인 최근/읽던 문서 목록과 문서별 이어 읽기를 구현했고 당시 API 33 debug 계측 15개, 별도 프로세스 복원 seed/force-stop/verify를 통과했다. 구현 `9fdb405`를 `codex/p1-reading-library`에 push하고 [PR #1](https://github.com/AtraFelis/ebook_viewer/pull/1)을 만들었다. [구현 검증](docs/history/2026-10-17-01-p1-reading-library.md), [PR/CI 이력](docs/history/2026-10-17-02-p1-library-pr.md).

현재 개인용 SM-N981N(API 33)·합성 자료 범위의 P0 기술 검증과 원격 CI를 완료했다. 사용자는 이 기기에서 동작하면 충분하다고 결정했다. API 23·35/다른 기기는 이번 완료 조건에서 제외하며 minSdk 23 선언을 기기 검증 완료로 표현하지 않는다. 사용자가 P0 변경의 main push를 “push 해”로 명시 승인했고 실제 원격 CI도 통과했다.

전체 화면 기반 브랜치 `codex/p1-immersive-reading`은 PR #1의 `df3ef15`에서 현재 체크아웃을 사용해 생성했다. PR #1은 사용자 직접 병합으로 origin/main `a67b0a8`에 포함됐다. P0 구현 커밋 `0863969`를 origin/main에 일반 push했고 GitHub Actions 실행 38885324826이 새 Windows runner에서 도구 설치/Wrapper 검증/앱·테스트 APK 빌드/단위/Lint/ktlint strict를 통과했다. reset/clean/강제 push 없음. 작업 경로는 `C:\Users\cotjs\workspace\ebook_viewer`.

- 승인 구성: Kotlin/stdlib 2.1.20, AGP 8.7.2, Gradle 8.9, Temurin 17.0.20.1+1, compile/target 35/min 23, Platform 35 rev2, Build Tools 34.0.0. Android Studio는 미설치, CLI 사용. 시스템 Java는 바꾸지 않고 프로젝트 설치본을 사용한다.
- 플랫폼 Activity/SAF + PdfRenderer + View/Canvas. 첫 4페이지를 먼저 제공하고 나머지 크기를 single worker에서 점진 조사한다. 사용자 줌/좌표 유지. 회전 복원은 정확한 전체 크기를 먼저 확보하므로 새 문서 첫 표시보다 오래 걸릴 수 있다.
- 가시 페이지 미리보기, 512px 타일, 배율 단계/이전 타일이 같은 64MiB 캐시를 공유한다. 진행 중 native render는 즉시 중단할 수 없으며 문서/요청 검사와 worker 순서로 수명을 관리한다.
- 최종 debug/profile 빌드, 단위 6개, Lint 오류/경고 0, ktlint strict 통과. API 33 비디버그 계측 11개 통과. 제스처는 MotionEvent 주입, 방향 변경은 requestedOrientation이며 물리 센서/손입력 검증과 구분한다.
- 최종 비디버그 30초 3회: 첫 고해상도 타일 486/818/869ms, 프레임 p95 5.6~5.9ms, 캐시 64MiB 이내.
- 같은 최종 APK로 10분 1회 완주: p95 6.692ms/p99 7.322ms, OOM 없음, 최대 캐시64MiB 이내. PSS 최고 표본302,958KiB/종료159,765KiB. 빠른 이동의 미준비 페이지 표본426/2,456과 FD106→135는 한계로 기록하며 완전한 누수/공백 제거를 선언하지 않는다.

## 승인과 다음 행동

런타임은 승인된 stdlib 2.1.20과 전이 annotations 13.0뿐이다. JUnit/ktlint는 검사 전용. P1 읽기 기록/방향/배경색 설정은 각각 플랫폼 AtomicFile/버전 1을 사용한다([ADR 0002](docs/adr/0002-local-reading-records.md), [ADR 0003](docs/adr/0003-immersive-reader-and-orientation.md)). Compose 최종 선택, 새 저장/PDF 라이브러리는 미선정이며 새 런타임 의존성 승인 규칙을 유지한다.

1. P0 현재 범위 완료: 구현 `0863969` 및 결과 문서 `08011b3`를 main에 push했고 CI 38885324826 성공. P1은 사용자 승인으로 기능별 PR 방식을 사용한다. 첫 [PR #1](https://github.com/AtraFelis/ebook_viewer/pull/1)은 구현/문서 최종 head `df3ef15`의 [Windows CI 38923209449](https://github.com/AtraFelis/ebook_viewer/actions/runs/38923209449)까지 통과했고 사용자가 `a67b0a8`로 병합했다. 전체 화면/방향 기능은 PR #2로 분리했다.
2. 사용자 P1 요구사항을 [로드맵](docs/plan/roadmap.md)에 통합했고, 목록/이어 읽기, 전체 화면·중앙 탭 도구·방향 설정, 수동 외부 배경색까지 구현했다. 읽기 도구에는 설정/목록/읽기 복귀와 페이지 이동/북마크를 제공하며 페이지 번호 표시는 사용자 선택이다. 이번 페이지 이동/북마크 검증 다음 범위는 간격/크기 모드·파일/오류 UX다.
3. 2026-10-17 사용자 결정: 스크롤 선명도 복구 지연은 [P2 전용 계획](docs/plan/p2-sharpness-recovery.md)에 문서화하고 개선은 P2에서 한다. 자동 배경색도 P2로 이관하며 P1은 수동 색상/설정 보존으로 완성한다. 현재 성능은 충분하므로 별도 렌더링/스크롤 최적화 및 빠른 이동의 미준비 영역 개선은 P1 필수에서 제외한다. 기존 성능과 줌/앵커·캐시/수명 계약은 유지하고 새 기능에 따른 회귀는 검증한다. 손입력·물리 회전·개인 PDF로 기능 수용을 확인한다.
4. 새 Windows CI의 CLI 도구 복원은 검증했지만 실제 새 PC의 드라이버/Android Studio/기기 연결 복원과 다른 기기/API는 미검증이다. API 33 암호화 읽기·검색 등은 P2 범위다. 새 저장/UI 라이브러리를 채택하지 않았으며 필요 시 별도 구성 승인 후 도입한다.
5. 실제 SAF provider 영구 권한·재부팅·권한 소실 재연결과 개인 PDF 손입력/물리 회전 센서 수용은 남아 있다. 비탐색 provider 임시 복사와 간격/모드, 개인 PDF 수용은 후속이다. 방향 설정의 별도 강제 프로세스 종료/기기 재부팅은 미검증이다. 갑작스러운 종료 직전 미제출/미완료 이동은 유실될 수 있으며 손상된 기록/설정의 복구 UI는 아직 없다. 이번 통과는 API 33 합성 자료/앱 내부 파일 URI와 주입 입력 기준이다.

[push·CI 결과](docs/history/2026-10-16-10-github-push-and-ci.md), [최종 검증·명령·원본 위치](docs/history/2026-10-16-09-p0-local-validation.md), [첫 표시 개선](docs/history/2026-10-16-08-first-page-priority.md), [Windows 실행](docs/windows-development.md), [ADR](docs/adr/0001-p0-toolchain-and-renderer.md).
