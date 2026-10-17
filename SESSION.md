# SESSION.md — 세션 인수인계

최종 갱신: 2026-10-17 (Asia/Seoul)

## 현재 상태

PR #8 head `bc26364`의 [Windows CI 39012958849](https://github.com/AtraFelis/ebook_viewer/actions/runs/39012958849)는 성공했다. 이후 자동 승인 검토 사용량 한도로 Git 기록/브랜치 생성이 실행되지 않았지만 로컬 문서는 보존됐다. 20:12 KST 재개 시 승인 도구가 다시 동작함을 확인했다. 개인 수용/한도 기록을 PR #8에 push한 뒤 별도 역방향 설정 브랜치를 만든다. 실제 리뷰는 한도 복구 후 새 head로 한 번 재요청해 확인한다.

사용자가 수명 보완 APK에서 개인 PDF의 홈 복귀·확대/스크롤·중앙 탭 설정(방향/배경색/번호 표시)이 “다 잘 돼”라고 확인했다. 기존 P1 개인 사용 수용을 기록하며 자동 계측과 구분한다. 추가 요청한 가로 역방향/세로 역방향 고정은 다음 작은 변경으로 구현/검증한다. PR #8 수동 리뷰는 [요청](https://github.com/AtraFelis/ebook_viewer/pull/8#issuecomment-6231097690) 후 한도 제한으로 실제 미실행이다. 봇이 16:16 KST 당시 9분 후 복구를 안내했으며 성공 체크를 실제 리뷰로 보고하지 않는다. 최신 CI는 PR checks로 구분한다.

수명/캐시 보완 구현 `8270cde`를 main 대상 [PR #8](https://github.com/AtraFelis/ebook_viewer/pull/8)로 등록했다. 새 head의 원격 CI/CodeRabbit은 [최신 checks](https://github.com/AtraFelis/ebook_viewer/pull/8/checks)와 실제 봇 응답으로 확인한다. 이 등록 기록 push 후 수동 리뷰를 한 번 요청한다. 앱 설치/계측 검증은 아래와 같으며 병합은 사용자가 한다.

사용자 병합된 PR #7 최종 head a282718의 Windows CI 39010146312 성공과 origin/main `29d0763`를 확인했다. 깨끗한 현재 체크아웃의 `codex/p1-reader-lifecycle`에서 정지 중 타일 렌더/캐시 재충전 차단, 같은 앵커의 복귀, 취소 전 동일 키 타일 배제, 파괴 시 캐시 해제를 구현했다. strict 빌드·단위 22개·Lint 오류/경고 0·ktlint 및 신규 API 33 계측 4개/최종 전체 40개·앱 실행 통과. 기록 v2 크기/간격을 포함하는 별도 seed→force-stop→다른 PID verify도 각 1개/실패 0, 이후 앱 실행을 통과했다. [ADR 0008](docs/adr/0008-reader-lifecycle-and-cache-cancellation.md), [명령·범위·결과](docs/history/2026-10-17-10-reader-lifecycle.md). 새 런타임 의존성 없음. 실제 메모리 고갈/OOM·다른 API·새 APK의 개인 수용을 자동 계측으로 대체하지 않는다. 새 APK의 개인 홈 복귀/확대·스크롤/중앙 탭 설정 수용을 요청했다.

PR #7 head `32bd52b`의 [Windows CI 39009827260](https://github.com/AtraFelis/ebook_viewer/actions/runs/39009827260)가 성공했다. CodeRabbit도 같은 head의 19개 파일 실제 리뷰를 완료했고 actionable 지적은 없었다([결과](https://github.com/AtraFelis/ebook_viewer/pull/7#issuecomment-6230655962)). 함수 설명문 80% 비율의 일반 권고는 계약의 이유/제약 중심 주석과 별개이며 구체적 누락 계약/오류를 지적하지 않아 기계적으로 추가하지 않았다. 결과 기록만 담는 후속 문서 head의 CI는 [최신 checks](https://github.com/AtraFelis/ebook_viewer/pull/7/checks)로 구분한다. 앱/테스트 코드는 그대로이며 불필요한 재설치/중복 리뷰 요청은 하지 않는다. 다음 P1 범위는 백그라운드 복귀·메모리 압박·문서 교체 시 상태/자원 회귀와 남은 개인 기기 수용이다.

파일 접근·오류 복구 구현 `c238fda`에 origin/main `b41b0c9`를 일반 merge한 `c206098`을 [PR #7](https://github.com/AtraFelis/ebook_viewer/pull/7)로 등록했다. 병합 전후 검증된 앱/스크립트 차이는 없다. 아래 로컬/기기 결과와 원격 검사를 구분하며 [최신 CI/리뷰](https://github.com/AtraFelis/ebook_viewer/pull/7/checks)는 게시 후 확인한다. 이 문서 갱신을 push한 뒤 CodeRabbit 수동 리뷰를 한 번 요청한다. 병합은 사용자가 한다.

사용자가 최종 접근 기능 APK에서 개인 PDF 열기·몇 페이지 이동·목록→이어 읽기가 “정상”이라고 확인했다. 내용/파일명은 받지 않았다. 자동 계측과 사용자 수용 자기보고를 구분하며 전체 설정/물리 회전/재부팅/다른 API 수용 완료로 확대하지 않는다.

`codex/p1-document-access`에서 원인별 오류·목록 복귀·재시도/재선택과 취소 가능한 provider 접근을 구현했다. 비탐색 파일은 256MiB 상한/30초 무응답 기준으로 익명 임시 inode에 복사하고, 탐색 가능한 파일은 그대로 연다. provider 조회/열기는 PDF worker에 두고 기록 큐를 막지 않는다. strict 빌드·단위 22개·Lint 오류/경고 0·ktlint 통과. 첫 신규 6개 중 1개 실패는 테스트 monitor의 Intent category/MIME 누락으로 진단했고 수정 후 provider 단독 1개는 통과했다. 최종 전체 API 33 계측 36개/실패 0·앱 설치/실행을 통과했다. [ADR 0007](docs/adr/0007-document-access-and-failure-recovery.md), [명령·실패 수정·결과](docs/history/2026-10-17-09-document-access.md). 새 런타임 의존성 없음. 실제 DocumentsUI/제조사 provider 전반·재부팅·암호화 파일은 별도 미검증이며, 개인 PDF 기본 열기/이어 읽기는 위 사용자 확인 범위다.

PR #6 최종 head `a45d2d9`의 Windows CI 39007984185가 성공했고 사용자가 병합했다. fetch 후 origin/main은 `b41b0c9`다. CodeRabbit 실제 리뷰는 당시 무료 한도 소진으로 수행되지 않았다. 다음 기능 PR도 생성 시 수동 리뷰를 요청하고 현재 head/응답을 확인한다.

구현 소스가 같은 `b35a962`의 [Windows CI 39007698593](https://github.com/AtraFelis/ebook_viewer/actions/runs/39007698593)는 성공했다. 이 결과와 최종 상태 문서만 갱신한 후속 head의 [실시간 checks](https://github.com/AtraFelis/ebook_viewer/pull/6/checks)를 구분한다. CodeRabbit 실제 리뷰는 한도 소진으로 미실행이며, 다음 일은 한도 복구 후 리뷰 확인과 P1 파일 접근/오류 UX·개인 PDF 수용이다.

PR #6에 [수동 리뷰 댓글](https://github.com/AtraFelis/ebook_viewer/pull/6#issuecomment-6230354554)을 한 번 등록했고 접수 응답을 확인했다. 그러나 CodeRabbit이 무료 OSS 리뷰 한도 소진과 당시 29분 후 재사용 가능을 안내해 **실제 리뷰는 미실행**이다. 성공 상태를 리뷰 완료로 보고하지 않으며 중복 재요청하지 않았다. CI는 별도로 진행하며 최신 결과는 [PR checks](https://github.com/AtraFelis/ebook_viewer/pull/6/checks)/본문을 확인한다. 한도 복구 후 후속 작업에서 현재 head/접수 이력을 다시 확인하고 수동 리뷰를 진행한다.

페이지 크기·간격 구현 `8be274b`에 origin/main `1aece35`를 일반 merge한 `7cd490f`를 push하고 [PR #6](https://github.com/AtraFelis/ebook_viewer/pull/6)을 등록했다. merge 전후 검증한 앱/스크립트 차이는 없었다. 로컬/기기 결과는 아래와 같으며 [원격 checks](https://github.com/AtraFelis/ebook_viewer/pull/6/checks)는 게시 후 별도 확인한다. PR마다 수동 CodeRabbit 요청 규칙을 적용하고 접수/실제 리뷰를 구분한다. 병합은 사용자가 한다.

PR #5는 CodeRabbit이 head `751dbc0`의 23개 파일 실제 검토를 완료해 지적이 없었고 CI도 통과했다. 사용자가 병합한 origin/main `1aece35`와 부모 head 포함을 확인했다. 다음 브랜치 `codex/p1-page-layout`에서 문서별 원본/너비 통일·간격 설정과 v1→v2 읽기 기록 호환을 구현했다. strict 빌드·단위 22개·Lint 오류/경고 0·ktlint 및 신규 API 33 계측 3개·최종 전체 30개·앱 실행을 통과했다. 첫 전체 2개 실패는 저장된 가로 고정과 테스트의 직접 회전/고정 스와이프 횟수 충돌을 보완한 뒤 재검증했다. PR #6을 등록했고 앱/계측 소스가 같은 b35a962의 원격 CI 39007698593도 성공했다. 최신 문서 head checks는 별도 확인한다. [ADR 0006](docs/adr/0006-page-layout-and-reading-records-v2.md), [실패 수정·명령·결과](docs/history/2026-10-17-08-page-layout.md). 개인 PDF/실제 provider와 P1 전체 완료는 별도다.

사용자 지시로 모든 PR 생성 시 CodeRabbit 수동 요청, 후속 리뷰의 타당성 판단·수정/검증 후 같은 브랜치 push를 AGENTS/[Git 규칙](docs/rule/git.md)에 저장했다. PR #5는 자동 리뷰 제외를 확인해 수동 명령을 한 번 등록했고 실제 검토 완료까지 확인했다. [절차/실제 대응 이력](docs/history/2026-10-17-07-pr-review-workflow.md).

`codex/p1-navigation-bookmarks`에서 페이지 이동·북마크 및 사용자 지정 번호 표시/숨김을 구현/검증했다. 번호는 기본 숨김이며 설정을 켜도 Canvas 크기/배율은 유지한다. 페이지/북마크 이동은 현재 줌·유효 가로 위치를 보존한다. strict 빌드·단위 19개·Lint·ktlint 및 신규 API 33 계측 4개 통과, 저장 경로 보완 후 최종 전체 27개 회귀·앱 설치/실행까지 통과했다. 구현 `8861ae7`의 [Windows CI 38935046856](https://github.com/AtraFelis/ebook_viewer/actions/runs/38935046856)는 성공했다. 중단돼 있던 PR 등록을 마무리해 [PR #5](https://github.com/AtraFelis/ebook_viewer/pull/5)를 만들었다. [최종 head checks](https://github.com/AtraFelis/ebook_viewer/pull/5/checks)는 별도 확인하며 병합은 사용자 몫이다. [ADR 0005](docs/adr/0005-navigation-bookmarks-and-page-numbers.md), [명령·결과](docs/history/2026-10-17-06-navigation-bookmarks.md). 새 런타임 의존성 없음.

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

런타임은 승인된 stdlib 2.1.20과 전이 annotations 13.0뿐이다. JUnit/ktlint는 검사 전용. P1은 플랫폼 AtomicFile을 사용하며 읽기 기록은 v1 호환/v2 쓰기([ADR 0006](docs/adr/0006-page-layout-and-reading-records-v2.md)), 방향/배경색/번호와 북마크는 별도 v1이다. Compose 최종 선택, 새 저장/PDF 라이브러리는 미선정이며 새 런타임 의존성 승인 규칙을 유지한다.

1. P0 현재 범위 완료: 구현 `0863969` 및 결과 문서 `08011b3`를 main에 push했고 CI 38885324826 성공. P1은 사용자 승인으로 기능별 PR 방식을 사용한다. 첫 [PR #1](https://github.com/AtraFelis/ebook_viewer/pull/1)은 구현/문서 최종 head `df3ef15`의 [Windows CI 38923209449](https://github.com/AtraFelis/ebook_viewer/actions/runs/38923209449)까지 통과했고 사용자가 `a67b0a8`로 병합했다. 전체 화면/방향 기능은 PR #2로 분리했다.
2. 사용자 P1 요구사항을 [로드맵](docs/plan/roadmap.md)에 통합했고, 목록/이어 읽기, 전체 화면·중앙 탭 도구·방향 설정, 수동 외부 배경색까지 구현했다. 읽기 도구에는 설정/목록/읽기 복귀와 페이지 이동/북마크를 제공하며 페이지 번호 표시는 사용자 선택이다. 페이지 이동/북마크 검증과 PR 등록을 완료했다. 페이지 간격/원본 크기·너비 통일 모드는 로컬 및 API 33 기기 검증을 완료했다. 파일 접근/오류 UX와 합성 provider 검증까지 구현/검증했다. 다음은 실제 파일 선택 UI/개인 PDF 수용과 수명·메모리 압박 회귀다.
3. 2026-10-17 사용자 결정: 스크롤 선명도 복구 지연은 [P2 전용 계획](docs/plan/p2-sharpness-recovery.md)에 문서화하고 개선은 P2에서 한다. 자동 배경색도 P2로 이관하며 P1은 수동 색상/설정 보존으로 완성한다. 현재 성능은 충분하므로 별도 렌더링/스크롤 최적화 및 빠른 이동의 미준비 영역 개선은 P1 필수에서 제외한다. 기존 성능과 줌/앵커·캐시/수명 계약은 유지하고 새 기능에 따른 회귀는 검증한다. 손입력·물리 회전·개인 PDF로 기능 수용을 확인한다.
4. 새 Windows CI의 CLI 도구 복원은 검증했지만 실제 새 PC의 드라이버/Android Studio/기기 연결 복원과 다른 기기/API는 미검증이다. API 33 암호화 읽기·검색 등은 P2 범위다. 새 저장/UI 라이브러리를 채택하지 않았으며 필요 시 별도 구성 승인 후 도입한다.
5. 실제 SAF provider 영구 권한·재부팅·권한 소실 재연결과 개인 PDF 손입력/물리 회전 센서 수용은 남아 있다. 비탐색 provider 제한 복사는 합성 provider로 검증했고, 실제 제조사 파일 선택 UI/개인 PDF 수용은 후속이다. 방향 설정의 별도 강제 프로세스 종료/기기 재부팅은 미검증이다. 갑작스러운 종료 직전 미제출/미완료 이동은 유실될 수 있으며 손상된 기록/설정의 복구 UI는 아직 없다. 이번 통과는 API 33 합성 자료/앱 내부 파일 URI와 주입 입력 기준이다.

[push·CI 결과](docs/history/2026-10-16-10-github-push-and-ci.md), [최종 검증·명령·원본 위치](docs/history/2026-10-16-09-p0-local-validation.md), [첫 표시 개선](docs/history/2026-10-16-08-first-page-priority.md), [Windows 실행](docs/windows-development.md), [ADR](docs/adr/0001-p0-toolchain-and-renderer.md).
