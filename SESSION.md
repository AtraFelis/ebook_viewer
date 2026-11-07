# SESSION.md — 세션 인수인계

최종 갱신: 2026-11-07 (Asia/Seoul).

## 현재 상태

PR #37 사용자 병합4e707f9을 확인했다. 변경5개를 ignored local-artifacts/recent-history-20261107에 백업하고 origin fetch 후 같은 체크아웃의 codex/recent-history-removal에서 후속을 진행한다. 사용자 docs/design.md/gradle.properties와 stash7개는 보존한다. 아래 PR #37 대기 문구는 당시 이력이다.

[최근 목록 삭제 분리·일반 설정 방향](docs/plan/recent-history-removal.md)을 구현했다. 최근 항목의 길게 누르기는 전용 중앙 확인 후 목록에서만 숨기며 문서·복사본·앵커/줌·북마크·소속은 보존한다. 실제 열기에 성공하면 다시 표시한다. 기록 v8/첫 before-v8 백업과 v1~v7 읽기를 유지한다. 일반 설정·세부창은 기기 정책으로 회전하고 닫으면 본문 저장 방향/위치로 복귀한다. 작은 보기 설정/빠른 방향 도구는 기존 본문 정책이다. 새 의존성은 없다.

strict debug/계측/release 빌드·단위100개(실패/오류/건너뜀0)·두 Lint0/0·ktlint·APK ID 검사가 통과했다. API33 관련6개를 개별 실행해 모두 실패0이며 최초 최근 목록 검사 탐색 실패와 보완은 [이번 이력](docs/history/2026-11-07-04-recent-history-and-settings-orientation.md)에 기록한다. 앱/계측·ADB 종료와 설정 복원 완료. 사용자는 두 기능을 ‘좋아. 괜찮은 것 같아’로 수용했다. 게시/원격 CI/리뷰는 아직 별도다. 로컬 사용자 버전0.1.3/code5는 보존했고 정식 서명/설치는 하지 않았다.

PR #37 실제 리뷰5810562680의 잔여 파일 회수 실패/복사 finally의 원래 예외 가림 지적2개를 채택했다. 자동 정리는 실패 항목을 남겨 재시도하고 명시적 삭제 실패 보고는 유지했다. 단위95개와 CI 동일 strict debug/계측/release 빌드·두 Lint0/0·ktlint·APK ID 검사를 통과했다. 이번 수정에는 기기 설치/검사를 반복하지 않았다. [리뷰 보완 이력](docs/history/2026-11-07-03-managed-storage-review-fixes.md)을 따르며 같은 PR 브랜치에 검증한 변경을 모아 게시한다. 아래 한도 거부는 최초 요청 당시 기록이다.

리뷰 수정 d50fde6을 같은 브랜치에 일반 push하고 PR 설명을95개 결과/기기 미실행으로 갱신했다. 인라인 답변4501261660에 두 문제/실제 검증을 함께 기록했다. 최신 head CI42363080616/42363076955는 실행 중이며 봇 후속 확인과 사용자 병합은 별도다. 작은 두 정리 수정의 전체 수동 리뷰는 중복 요청하지 않았다. 외부 결과 기록은 head 변경 없이 로컬에 모은다.

봇 후속4501267277은 d50fde6의 실제 코드/추가 검사2개를 확인해 두 수정과 명시적 삭제 오류 유지에 동의했고 스레드를 해결했다. 봇 자체는 테스트를 재실행하지 않았다고 명시했다. 미해결 인라인0이며 최신 CI 두 건은 여전히 실행 중이다. 이 최종 확인 기록3파일은 로컬에만 모아 두었으며 상태만의 추가 push는 하지 않는다.

PR #36 사용자 병합 e8e8fd1을 확인하고 변경5개를 ignored local-artifacts/managed-storage-20261107에 백업했다. origin fetch 후 파일 내용이 동일한 병합 커밋 기준으로 현재 체크아웃에서 codex/managed-pdf-storage를 만들었다. 사용자 docs/design.md/gradle.properties와 stash7개는 보존한다.

현재 후속은 [앱 전용 복사본·파일 오류 팝업](docs/plan/managed-pdf-storage-and-recovery.md)/[ADR0022](docs/adr/0022-managed-pdf-copies.md)다. v7 기록에 안전한 UUID 토큰을 추가하고 원래 URI/앵커·북마크·책장 키는 유지한다. 새 등록은 앱 filesDir/documents로 복사하며 기존 등록은 다음 읽기에 전환한다. 최초 v7 쓰기 전 before-v7 백업과 기존 before-v6를 보존한다. 본문/표지는 복사본을 읽고 본문 추출은 같은 열린 FD를 복제한다. 원본을 건드리지 않고 앱 등록 삭제 뒤 복사본을 정리한다. 책장 관계 삭제로는 복사본을 지우지 않는다. 실패 시에는 목록 사이 안내 대신 중앙 복구 창에서 다시 시도·파일 다시 선택·문서 삭제를 제공한다. 앱 제거/데이터 초기화 시 복사본도 지워진다.

strict CI 동일 debug/계측/release 빌드·단위93개(실패/오류/건너뜀0)·두 Lint0/0·ktlint·APK ID/로컬 버전 확인이 성공했다. API33 관련 검사와 최초 실패·보완/최종 앱 설치·수용/게시 여부는 [이번 실행 이력](docs/history/2026-11-07-02-managed-pdf-storage.md)에 별도 기록한다. 아래 방향/최근 문서 문단은 PR #36의 완료 이력이다.

API33 관련 고유11개를 분할 성공했고 마지막 보완 뒤5개가 다시 성공했다. 앱/계측·ADB 종료0, 사용자 방향 선택/글꼴 복원과 이전 합성 잔여물의 엄격한 조건 확인/정리·나머지 카탈로그 보존을 확인했다. 최종 APK의 전체11개/기본96개 단일 성공은 아니다. 사용자는 평소 PDF 열기·읽기·이어 읽기를 ‘괜찮은 것 같아’로 수용했다. 정식판 서명/설치는 별도다.

9b029e8을 기능 브랜치에 일반 push하고 [PR #37](https://github.com/AtraFelis/ebook_viewer/pull/37)을 main 대상으로 게시/채팅 첨부했다. 사용자 수용 후 Ready 상태다. Android checks42346212477/42346151798 두 건 모두 completed/success를 확인했다. CodeRabbit 수동6703255826 한 번에 봇6703256647이 Review rate limited/Action not completed로 거부했다. 무료 OSS 한도 안내는 약3분 뒤 초기화였고 자동 SUCCESS를 실제 리뷰로 세지 않는다. 연속 재요청은 하지 않았다. 사용자 디자인/로컬 버전만 원래 dirty로 남고, 실제 CI/리뷰 결과 기록은 상태만의 head 변경 없이 로컬에 모은다. 남은 외부 확인은 실제 리뷰와 사용자 병합이며 정식판 APK 빌드/설치는 별도다.

사용자 후속3개를 [계획](docs/plan/home-orientation-and-recent-documents.md)/[ADR0021](docs/adr/0021-reader-orientation-scope-and-recent-documents.md)로 정리하고 구현했다. 본문 고정 방향은 저장/복원하되 메인·책장은 기기 정책을 따르고, 최근 읽은 실제 기록 최대5개를 메인에서 연다. 최신 카드는 유지하고 나머지는 작은 항목이다. 합성100페이지 사용자 메뉴/콜백은 제거하고 내부 openSample 계측 경로는 유지했다. 새 의존성/저장 형식·원본 PDF/사용자 버전 변경은 없다.

PR #35는 사용자 병합 d5f7fd3을 확인했다. 기존 변경5개를 로컬 백업/해시로 보존하고 같은 체크아웃의 codex/home-orientation-recent-documents에서 구현한다. 단위86개(실패/오류/건너뜀0) 실제 실행과 strict debug/계측·Lint0/0·ktlint를 통과했고 후속 계측만의 보완 뒤에도 재빌드/Lint/서식이 통과했다. 기기/사용자 수용·게시 상태는 [이번 이력](docs/history/2026-11-07-01-home-orientation-and-recent-documents.md)에 실제 결과로 기록한다. 아래 문서 PR 단계는 과거 완료 근거다.

P0·P1·합의한 P2/P3, 읽기 도구와 책장 후속 개선은 완료했다. [PR #33](https://github.com/AtraFelis/ebook_viewer/pull/33)은 책장 구현/리뷰 보완을, [PR #34](https://github.com/AtraFelis/ebook_viewer/pull/34)는 개인용 정식판 완료 문서를 반영했다. 이번 GitHub 조회에서 #34 MERGED/병합 `674c8d8`을 확인했다. PR #34의 문서 지적은 `9c4df30`과 봇의 실제 문서 확인으로 해소됐다. Markdown 전용 Android checks 미실행을 앱 검사 통과로 세지 않는다.

문서 정리 당시 체크아웃은 `C:\Users\cotjs\workspace\ebook_viewer`, 브랜치는 `codex/project-docs-cleanup`, 기준은 main의 `674c8d8`이었다. 당시 main 브랜치 자체는 갱신하지 않았다. 정리 검증은 [정리 이력](docs/history/2026-10-29-01-documentation-and-cleanup.md)에 기록한다.

문서 34개를 e15629e로 일반 push하고 [PR #35](https://github.com/AtraFelis/ebook_viewer/pull/35)를 main 대상으로 게시했다. CodeRabbit 수동 요청6488610493/접수6488612097 뒤 실제 완료 리뷰5693252929의 지적2개를 확인했다. 검색 보류와 검증 표의 충돌, 기기 검사/정리 오류 보존 안내를 채택해 수정했다. 문서 예제의 성공/검사 실패/정리 실패/양쪽 실패 합성4개는 모두 기대 조건을 통과했다. [리뷰 판단·실행 이력](docs/history/2026-10-31-01-documentation-review-fixes.md)을 따른다. a0053c4로 같은 PR 브랜치에 일반 push해 원격 head 일치를 확인했다. 두 지적 답변4431858855/4431859182에 봇4431859734/4431859835가 실제 문서를 확인해 해소를 확인했고 미해결 스레드0·병합 가능 상태다. Markdown 전용 Android checks는 경로 필터 대상이며 앱 검사는 반복하지 않았다. 작은 지적 보완의 전체 수동 리뷰나 상태만의 추가 push는 하지 않는다. 병합은 사용자다.

API33 관련 고유9개를 분할 실행해 각 실패0이다. 신규4개/기존 방향2개가 성공한 뒤 설정 검사의 오래된 글자 버튼 탐색 실패를 현재 톱니바퀴 탐색으로 고치고 남은 설정·줌 회전·큰 글꼴3개를 성공했다. 실패 원문을 보존했다. 최신 계측 APK의 전체9개/기본96개 단일 실행으로 합산하지 않는다. 앱/계측·ADB 종료와 원래 글꼴 복원은 완료했다. 사용자는 세 항목 모두 ‘다 괜찮은 것 같아’로 수용했다.

구현 b55d76f를 같은 기능 브랜치에 일반 push하고 [PR #36](https://github.com/AtraFelis/ebook_viewer/pull/36)을 게시/채팅 첨부했다. 사용자 수용 뒤 Draft를 해제하고 설명을 현재 결과로 갱신했다. CodeRabbit 수동6702728200 한 번/봇6702731007 Review triggered 실제 접수 확인, 완료 리뷰는 대기다. 현재 head 일치·Android checks42342176243/42342148133 두 건 IN_PROGRESS다. CodeRabbit SUCCESS를 실제 리뷰 완료로 세지 않으며 상태만의 추가 push/중복 요청은 하지 않는다. 병합은 사용자다.

## 현재 구현

- Kotlin 2.1.20 / AGP 8.7.2 / Gradle 8.9 / JDK 17 / compile·target 35 / min 23. Activity + View/Canvas, PdfRenderer, SAF, AtomicFile을 사용한다.
- 연속 스크롤·줌/원본 앵커 유지, 문서별 크기·간격·여백, 한 손가락 가로 잠금/두 손가락 이동.
- 전체 화면·작은 읽기 도구·방향 드롭다운, 자동/수동 외부 배경·PDF 색반전·테마·밝기·화면 유지, 북마크·슬라이더·번호 표시 선택.
- 메인 상단/이어 읽기 아래 책장 격자. 미분류는 내용이 있을 때만 표시하고, 책장 추가는 편집 안에 둔다. 문서는 격자/목록과 이름/최근순, 책장은 사용자 순서/이름순을 제공한다.
- 기록 v6: 여러 책장 소속·사용자 순서, v1~v5 읽기와 첫 변환 전 앱 내부 원본 백업. 전체 문서에서 이동을 제공하지 않으며 책장 이동은 현재 소속의 ‘남겨두기’를 선택한다. 삭제 확인은 중앙 창, 이동/더보기는 하단 시트다. 앱 등록 삭제와 원본 PDF 삭제는 다르며 원본은 보존한다.
- 표지는 API 30+ 격리 프로세스와 앱 공유 store/큐/캐시를 사용한다. 합산 요청 10개, 메모리 8MiB/디스크 64MiB. API 23~29는 기본 그림 정책이며 해당 API의 실제 기기 검증은 미실행이다.

## 검증 근거와 제한

이번 후속 이전 PR #33의 단위 84개(실패/오류/건너뜀 0), strict debug/계측 빌드·Lint 0/0·ktlint 및 unsigned release/release Lint 성공은 [PR #33 리뷰 보완](docs/history/2026-10-24-08-bookshelf-review-fixes.md)에 기록돼 있다. 최종 테스트 API 분기 수정의 API 33 관련 2개는 각 실패 0이며 최종 CI 두 건도 성공했다([최종 이력](docs/history/2026-10-24-09-cover-test-api-guards.md)).

이는 현재 기본 96개 전체를 한 APK에서 새로 실행한 결과가 아니다. P2/P3 성능·장시간 결과도 각 당시 자료·앱에 한정한다. 다른 기기/API·실제 OOM·임의 PDF 안전성·모든 문서의 무지연을 보장하지 않는다. 2026-10-29 문서/캐시 정리에서는 앱 빌드·기기 검사·서명 빌드를 반복하지 않았다. 이번 앱 후속의 실제 검사 범위는 상단과 새 이력을 따른다.

## 개인용 배포와 보존

정식 ID는 `dev.example.ebookviewer`, 개발용 debug/profile은 `dev.example.ebookviewer.p0`다. 로컬 0.1.2/code4 APK의 서명·정식 ID/버전·이전 APK와 동일 인증서를 확인했고 설치와 키 백업은 사용자 확인 완료다([근거](docs/history/2026-10-24-10-main-sync-and-release-closeout.md)). 직접 기기/백업 파일 검증과 구분한다. 다음 빌드는 [개인용 안내](docs/personal-release.md)를 따른다.

사용자 `docs/design.md`와 `gradle.properties`(로컬 0.1.2/code4)는 수정·커밋 대상에서 제외한다. 기존 stash와 로컬 백업, 서명 키/배포 APK, SDK/JDK, 검사 보고서·원본 로그도 보존한다. 이번 작업 시작 시 상태와 파일 해시는 ignored `local-artifacts/project-maintenance-20261029`에 보관했다. 과거 인수인계의 중복 진행 문단은 줄이고 상세 근거는 기존 docs/history에 남겼다.

이번 정리 검증: 캐시 파일 2,622개/136.13MiB 삭제, 문서 127개·로컬 링크 317개 누락 0·공백 검사 통과, 비문서 170개·사용자 파일 2개·보호 산출물 85개·stash 7개 불변 확인. 앱 검사는 미실행이다.

## PR #36 CI 우선 수정

사용자가 실패 CI 확인을 우선 요청해 앱 전용 복사본/파일 오류 팝업 작업은 잠시 보류한다. 삭제 정책은 원본 보존·앱 복사본과 기록 정리로 사용자 확정했다. CI 두 건42342148133/42342176243은 release Lint의 미사용 last_read_document 문자열 때문에 실패했다. 이전 로컬 검사에서 release Lint를 빠뜨렸다. 예전 문자열을 제거하고 목록의 최근 문서 헤더 부재 검사도 현재 recent_documents를 참조하도록 수정했다. CI 동일 명령의 strict debug/계측/release 빌드·단위86개·두 Lint0/0·ktlint와 앱 ID/버전 검사가 로컬에서 성공했다. 수정 push 뒤 원격 결과는 아래 이력에 별도 기록한다.

4bc2748을 같은 PR #36 브랜치에 일반 push했다. 수정 head의 원격 CI42343003333/42342998353 두 건 모두 SUCCESS 확인했다. CodeRabbit 실제 완료 리뷰5810306224와 미해결 인라인0도 확인했다. 주석 비율 권고는 계약의 이유/제약을 설명하는 주석 원칙과 달라 일괄 주석 추가는 채택하지 않는다. 작은 CI 수정의 수동 리뷰 중복 요청/상태만의 push는 하지 않았다. 사용자 병합은 별도이며 파일 오류 팝업·앱 전용 복사본 요청을 재개한다.

## 다음 행동

PR #36은 CI/실제 리뷰·사용자 병합까지 완료했다. 복사본/복구 창의 관련 최종 검증·사용자 수용과 새 PR 결과를 확인한다. 검색·선택/복사 UI·EPUB·OCR·동기화, 코드 LICENSE·공개/스토어 배포, 다른 API/기기 확대는 별도 보류다. TalkBack 전용 추가 개발/수동 검증은 사용자 결정으로 완료 조건에서 제외하며 기존 구현·과거 검증은 보존한다.

새 변경은 관련 위험에 맞는 검사만 실행하고 CodeRabbit은 검토할 변경이 모였을 때 요청한다. PR 병합은 사용자다. 이번 정리의 게시 여부·검증 결과는 이번 이력과 최종 응답으로 구분하며 과거의 ‘게시 대기’를 현재 작업으로 재개하지 않는다.
