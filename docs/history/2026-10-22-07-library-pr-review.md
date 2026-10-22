# PR #32 게시와 CodeRabbit 리뷰 대응

## 게시

사용자가 PR 게시·CodeRabbit 실제 리뷰 확인 및 타당한 지적 수정을 요청했다. `feat/pdf-inversion-and-bookshelves`에서 구현 `620cfd01549170f35511f7a692e408f2401d76bf`를 커밋·일반 push했다. 원격 브랜치 SHA 일치와 작업 트리 무변경을 확인한 뒤 [PR #32](https://github.com/AtraFelis/ebook_viewer/pull/32)를 생성했다(OPEN/ready, base main/b6df772). 병합/자동 병합은 실행하지 않는다.

로컬 gh GraphQL/REST는 환경 정책으로 Forbidden이어서 연결된 GitHub 도구로 PR 생성/리뷰 조회를 진행했다. Codex 채팅 첨부 도구는 호출했으나 응답이 오지 않아 해당 대기 셀을 종료했다. PR 생성 성공과 채팅 첨부 성공은 구분한다.

## 리뷰 요청

자동 리뷰는 별 수 정책으로 건너뛰었으며 처음 CodeRabbit success 상태를 실제 리뷰로 세지 않았다. 수동 요청6347641116(`@coderabbitai review`) 한 번에 봇6347643658 Review triggered를 확인했다. 요약6347595567이 b6df772→620cfd0의29개 파일 처리 중으로 바뀌었다. 중복 요청이나 리뷰 중 문서만의 push로 head를 바꾸지 않았다. Autofix/과금 설정을 활성화하지 않았다.

첫 PR CI39827236504는 Windows 도구 설치를 통과하고 Verify Wrapper and run checks 단계 실행 중으로 확인했다. 이후 실제 완료 결과와 코드 리뷰 판단/수정·검증은 아래에 기록한다. 기존 로컬69개/양 Lint/빌드 통과와 원격 CI를 구분한다. 기기 연결 없음/새 계측3개 미실행 한계는 유지한다.

- 최초 head620cfd0의 PR CI39827236504는 completed/success로 확인했다. Windows 도구 설치·Wrapper/앱/검사 작업 통과이며 실제 기기 검사 결과가 아니다.

## 실제 완료 리뷰와 판단

CodeRabbit review `PRR_kwDOVLKYJM8AAAABTuegTg`는620cfd0/29개 파일에 대해 완료했다. 수동 응답도 Review finished로 바뀌었다. 인라인4351865818/스레드PRRT_kwDOVLKYJM6vUWax의 수정 지적은1개다.

- 타당/수정: ReaderHomeView의 선택 패널은 기본 VISIBLE이고 첫 비동기 render 전에 노출된다. 목록 로드가 실패하면 숨기는 render 자체가 호출되지 않는다. 초기 visibility=GONE으로 지정했다. 기존 관리 계측에 render 전 패널 숨김 확인을 추가했다(빌드만 확인, 기기 실행 전).
- 일반 docstring coverage3.26%/권고80%는 코드 오류 지적이나 저장소 필수 기준이 아니다. 계약의 이유/제약 중심 주석을 유지하며 의미 없는 설명을 일괄 생성하거나 봇 설정을 바꾸지 않았다.
- 실제 리뷰에 명시된 included allowance는 시간당1개/잔여0이었다. 수정 후 유료/중복 재요청을 하지 않으며 이번 완료 리뷰는620cfd0에 대한 것이다.

## 독립 점검 보완

별도로 LibraryManagement의 책장 저장 완료 콜백이 무조건 close()를 호출해, 저장 도중 취소 후 새 대화상자를 연 경우 새 창을 닫을 수 있음을 코드 경로로 확인했다. 현재 dialog 객체 소유권을 확인하고 snapshot 요청의 세대를 비교해 닫힘/교체/Activity pause 이후 응답이 예전 창을 다시 열지 않게 보완했다. 실제 기기 재현 성공으로 주장하지 않는다.

## 수정 검증

초기 구현 이력과 동일한 JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME·프록시/CA 설정으로 저장소 Wrapper를 실행했다. `ktlintFormat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:lintRelease ktlintCheck --dependency-verification strict --console=plain` 최종49초/BUILD SUCCESSFUL.

단위69개 실제 실행/실패·오류·건너뜀0, debug/release Lint issue0, ktlint 통과. 기존 기기 계측3개의 개수는 그대로다. 새 패널 단언/대화상자 수명 보완의 실제 기기 검사와 성능 측정은 연결 기기가 없어 미실행이다. 런타임 의존성/저장 형식/원본 파일 처리 범위를 바꾸지 않았다.

같은 PR 브랜치에 일반 push 후 수정 근거를 리뷰 스레드에 답하고 해결 처리한다. 수정 head의 실제 CI/원격 일치는 후속 결과에 구분해 기록한다.
