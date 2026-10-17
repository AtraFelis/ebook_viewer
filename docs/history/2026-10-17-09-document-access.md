# P1 파일 접근·오류 복구

2026-10-17 (Asia/Seoul). 작업 경로 `C:\Users\cotjs\workspace\ebook_viewer`.

## 목적과 기준

사용자가 PR #6 CI 진행 중 다음 P1 작업을 요청했다. 깨끗한 `codex/p1-page-layout`의 `a45d2d9`에서 현재 체크아웃에 `codex/p1-document-access`를 만들었다. fetch 후 이전 브랜치와 원격 차이는 0/0이었다. PR #6 최종 head의 CI 39007984185 성공, 사용자 병합과 origin/main `b41b0c9`를 확인했다. 실제 CodeRabbit 리뷰는 이전 무료 한도 미실행과 구분한다. 새 의존성/강제 push/reset/clean/worktree 생성 없음.

[ADR 0007](../adr/0007-document-access-and-failure-recovery.md)에 맞춰 원인별 오류·목록 복귀·재시도/재선택, 취소 가능한 provider 접근, 비탐색 파일의 256MiB 상한/idle timeout/익명 임시 복사를 구현했다. 읽기 기록 v2와 별도 북마크/설정 형식은 바꾸지 않았다. 개인정보/원본 PDF를 로그·Git·외부 서비스에 넣지 않는다.

## 조사와 실제 검사

- Context7 resolve/query 실행: 첫 검색의 Firebase SDK는 플랫폼 근거로 채택하지 않았다. Android Developers Reference를 resolve한 뒤 조회했지만 필요한 내용이 부족했다. 공식 PdfRenderer/ContentResolver/Os API와 설치 API 35 `android.jar`를 `javap`으로 대조했다. 세부 링크/API 최소 수준은 ADR 0007에 기록했다. 자료 조회를 빌드/기기 검증으로 취급하지 않았다.
- `. ./scripts/android-env.ps1` 후 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain` 실행. 첫 스타일 오류는 수동 수정했다. 이전 통합 load_error 문자열의 미사용 Lint 오류는 실제 리소스를 제거해 해결했다. UsableSpace 권고는 추측성 사전 검사 대신 상한·실제 생성/쓰기 오류 처리로 단순화했다. `ktlintFormat`/Lint 억제/의존성 변경 없음.
- 최종 위 명령은 빌드·단위 **22개/실패 0**·Lint **오류 0/경고 0**·ktlint 통과했다. raw 로그 `local-artifacts/document-access-build.log`는 Git에 넣지 않는다. 계측 코드 보완 후 좁은 범위의 `:app:assembleDebugAndroidTest ktlintCheck --dependency-verification strict`도 실행했다.
- 첫 전용 기기 실행은 **6개/실패 1**이었다. source seekability/빈 파일·pipe 복사/정리·상한/무응답/reliable pipe 오류/저장 실패·취소 후 최신 문서·오류 후 재시도 중 5개 테스트가 통과했다. 초기 실행의 앱 APK는 사전 여유 공간 검사 제거 전이며 이후 최종 APK로 재설치했다. 첫 로그는 `local-artifacts/document-access-first.log`로 보존한다.
- 실패한 provider 테스트를 `-e suite access -e method crossUidPipeProviderPersistsGrantAndRevocationPreservesRecord`로 분리해 진단했다. `pickerHits=0, home=false, reason=null`로 파일 선택 Intent를 monitor가 가로채지 못한 것을 확인했다. 앱의 OPENABLE category와 application/pdf MIME에 맞춰 테스트 필터를 수정했다. protected Activity 콜백을 직접 호출하거나 앱 API를 테스트용으로 노출하지 않는다. 원래 준비 완료 조건/timeout은 유지했다. 진단 로그 `local-artifacts/document-provider-diagnostic.log`, 수정 후 결과는 별도로 기록한다.

## 다음 행동

필터 보완 후 provider 단독 계측은 **1개/실패 0**으로 통과했다. 스트리밍 content URI 열기, persistable read grant 획득, Activity 재실행 후 앵커 복원, 권한 회수의 ACCESS 오류/기록 보존, 재권한 후 재시도/캐시 상한을 확인했다. raw 로그는 `local-artifacts/document-provider-fixed.log`. 설치 패키지 메타데이터로 target/test provider가 실제로 다른 UID임도 확인했다. 이는 실제 DocumentsUI 선택 조작을 뜻하지 않는다.

최종 strict 전체 로컬 검사를 다시 통과한 뒤 같은 앱 APK에서 `adb shell am instrument -w dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation`을 실행했다. 신규 6개를 포함한 **36개/실패 0**과 이후 `adb shell am start -W -n dev.example.ebookviewer.p0/dev.example.ebookviewer.MainActivity`의 **Status: ok**를 확인했다. 신규 메서드를 기본 전체 suite에서 제외하지 않았다. 최종 로그는 `local-artifacts/document-access-device-full.log`. 앱 debug APK SHA-256은 `0ecfa077b75f39b9e921f0b96ba3bd9ec565aadd94b94d4a2967e086123b7d9a`다.

실제 디스크를 가득 채우거나 256MiB 전송 성능을 측정하지 않았다. storage fixture는 임시 파일 생성 실패, idle fixture는 짧은 시간 제한을 검증했다. 실제 파일 선택 UI/제조사 provider·개인 PDF/재부팅/다른 API·암호화 PDF는 별도 미검증이며 P1 전체 완료는 아직 아니다. 계측 종료 후 사용자에게 개인 PDF 열기·목록에서 이어 읽기의 정상 여부만 요청했고 내용/파일명은 요청하지 않았다. 다음은 main 반영/별도 PR·수동 CodeRabbit/원격 CI 확인, 실제 기능 수용과 수명/메모리 압박 회귀다.

사용자는 이후 최종 APK의 개인 PDF 열기·몇 페이지 이동·목록→이어 읽기에 “정상이에요”라고 확인했다. 이는 사용자 수용 자기보고이며 문서 내용/파일명을 받거나 측정하지 않았다. 실제 개인 파일의 기본 선택/이어 읽기 확인으로 위 미검증 범위를 일부 보완하지만, 모든 설정·물리 제스처/센서·재부팅·다른 provider/API 수용은 아직 아니다.

## PR 등록

구현/검증/사용자 확인을 `c238fda`로 커밋했다. fetch 후 origin/main `b41b0c9`를 일반 merge한 `c206098`에서 `git diff --exit-code c238fda HEAD -- app scripts gradle`가 차이 없이 통과했다. 브랜치를 일반 push하고 [PR #7](https://github.com/AtraFelis/ebook_viewer/pull/7)을 main 대상으로 등록/대화에 연결했다. 사용자 변경 덮어쓰기/reset/clean/강제 push 없음. 게시 직후 원격 CI는 진행 중이며, 이 문서 갱신을 push한 뒤 최종 head에 CodeRabbit 수동 요청을 한 번 등록하고 실제 응답을 확인한다. 동적 결과는 [PR checks](https://github.com/AtraFelis/ebook_viewer/pull/7/checks)와 본문에서 확인하며 로컬 통과를 원격 통과/실제 리뷰로 대체하지 않는다.

## 원격 CI·실제 리뷰 결과

등록 기록을 `32bd52b`로 push한 뒤 [수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/7#issuecomment-6230658371)을 한 번 등록했다. 봇의 [접수/완료 응답](https://github.com/AtraFelis/ebook_viewer/pull/7#issuecomment-6230662455)을 확인했다. 이전 PR #6 한도와 달리 이번에는 실제 실행되어 [19개 파일 검토 결과](https://github.com/AtraFelis/ebook_viewer/pull/7#issuecomment-6230655962)가 **No actionable comments**로 완료됐다. covered/source commit은 `32bd52b47340511c5d86663b116d5999157b5d51`이며 인라인 수정 지적/리뷰 스레드는 없다. `skipped` 상태를 완료 근거로 쓰지 않았다.

Docstring Coverage 0%/80% 경고는 봇의 일반 설명문 비율 기준이다. 48개 함수/8개 파일에 일괄 설명을 추가하라는 권고에는 구체적 잘못된 동작이나 빠진 소유권 계약이 없고, 저장소는 이유·제약을 설명하는 주석을 요구한다. 실제 FD 소유권·익명 복사·provider 취소의 제약은 소스/ADR에 기술되어 있어 비율만 맞추는 주석은 추가하지 않았다. Autofix/문서 생성 체크박스는 활성화하지 않았다. 타당한 코드 수정 지적이 없어 코드 변경/재설치는 없다. 리뷰 사용량은 이번 실행 후 0회로 표시됐으며 결과 기록만 바뀌는 head에 중복 요청하지 않는다.

같은 head의 [Windows CI 39009827260](https://github.com/AtraFelis/ebook_viewer/actions/runs/39009827260)는 **completed/success**다. 도구 설치, Wrapper/strict 앱·계측 빌드·단위·Lint·ktlint를 원격에서 통과했다. 원격 기기 테스트를 실행한 것은 아니며 위 API 33 로컬 계측과 구분한다. 조회 당시 PR은 OPEN/MERGEABLE/CLEAN이고 병합은 하지 않았다. 이 결과 기록을 담는 후속 문서 head의 CI는 최신 checks와 구분한다. 다음은 P1 백그라운드 복귀·메모리 압박·문서 교체 관련 상태/자원 회귀와 남은 개인 기기 수용이다. P2 선명도 복구/자동 배경색은 계속 보류한다.
