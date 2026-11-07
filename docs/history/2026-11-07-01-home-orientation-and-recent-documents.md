# 메인 방향·최근 읽은 문서·합성 메뉴 정리

2026-11-07. AGENTS → SESSION → CHECKLIST/로드맵과 방향/기록 ADR·개발 규칙을 읽었다. 사용자 요청의 범위·수용 기준은 [계획](../plan/home-orientation-and-recent-documents.md)을 따른다.

PR #35의 사용자 병합 `d5f7fd3af954974bc1b80888232424a1ea7941c4`을 GitHub에서 확인하고 fetch했다. 기존 HEAD `a0053c4`와 새 main의 파일 차이는 없었다. 변경5개를 ignored `local-artifacts/home-follow-up-20261107`에 바이트 사본/해시로 보존하고 같은 체크아웃에서 `codex/home-orientation-recent-documents`를 origin/main 기준으로 만들었다. 원래 디자인/버전·기존 stash는 그대로 유지하며 reset/clean·강제 push·worktree 생성은 없다.

조사에서 방향 설정 로드/저장 결과가 읽기 여부와 관계없이 requestedOrientation에 적용되고, 메인 이탈 시 해제되지 않음을 확인했다. 기존 메인은 position이 있는 마지막 문서 한 개를 표시한다. 합성 자료는 이미 내부 openSample로 직접 여는 계측 경로가 있어 사용자 메뉴를 제거해도 테스트 생성 경로를 유지할 수 있다. 새 의존성/저장 형식/원본 PDF 변경 없이 구현한다.

초기 adb 가용성 조회는 종료1로 연결 상태를 확인하지 못했다. 이 결과를 기기 미연결 확정이나 테스트 실패로 확대하지 않는다. PC 구현/검증부터 진행하며 기기 상태와 실제 검사는 뒤에서 기록한다.

## 구현과 PC 검증

MainActivity는 방향 선택을 Bundle/필드에 유지하고 현재 읽기 여부로 실제 requestedOrientation을 계산한다. 메인 설정에서 저장해도 메인 회전을 강제하지 않으며 본문 진입/이탈과 비동기 설정 로드에서 같은 정책을 적용한다. 최근 목록은 카탈로그 position 필터/최신5개와 기존 열기·삭제 확인 경로를 사용한다. 합성 메뉴와 전달 콜백은 제거했고 openSample/자료 생성·복원은 계측에 유지했다. 기록 v6/방향 v1과 원본·캐시/의존성은 유지했다.

실행: `. ./scripts/android-env.ps1` 뒤 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain`.

- 최초 실행은 전역 Gradle Wrapper lock 쓰기 권한으로 실패해 승인된 실행으로 재시도했다. 첫 구현의 긴 줄/새 계측 코드 서식 오류로 ktlint가 실패했고 해당 줄을 수동 보완했다. ktlintFormat/검사 비활성화는 실행하지 않았다. 실패 로그 initial-checks.log/second-checks.log는 ignored 산출물에 보존했다.
- `--continue` 재검증에서 단위86개가 실제 실행돼 실패/오류/건너뜀0이었다. 최신 final-checks.log는 strict debug/계측·Lint/ktlint 10초 성공이며 단위는 같은 소스 결과 up-to-date 재사용이다. Lint XML은 Error/Warning0, Information6이다. 이전 deprecation 컴파일 안내와 실제 Lint 오류를 구분한다.
- 관련 방향 검사만 선택하려고 계측 methodClass에 home/immersive를 추가했다. 이 보완과 설정 검사 아이콘 수정 후 각각 strict 계측/Lint/ktlint 7초 성공했다. 실제 앱/실행 스크립트에 테스트 전용 진입 메뉴를 추가하지 않았다.

## 기기 결과 — API 33 관련9개 분할 실행

사용자가 연결했다고 확인한 뒤 승인된 API33 기기 한 개에서 개발용 앱을 -r 업데이트하고 계측 APK를 설치했다. 정식 앱/사용자 데이터 초기화는 하지 않았다. 실행은 ignored `local-artifacts/home-follow-up-20261107/run-related.ps1 -Install`과 이후 `-Only` 선택으로 제한했으며 개별120초/host150초 제한을 사용했다. adb 출력 수집은 원격 식별자를 공개하지 않았다. 초기 서버 시작/출력 수집 때 승인 기기0개로 시작하지 못한 시도가 있었고 이후 직접 조회에서 승인1개를 확인했다. 출력을 먼저 수집한 뒤 필터하는 절차로 보완해 실행했다.

앱 소스가 같은 상태에서 다음 고유9개가 각각 P0 tests=1, failures=0을 보고했다.

1. home: 본문 가로 고정→메인 해제→메인 재생성→최근 항목 재열기의 고정/읽기 앵커·줌 복원.
2. home: 메인에서 본문 방향을 바꿔 저장해도 메인 정책/열린 설정창 유지.
3. home: 최근5개/미열람 제외/제목·열기/삭제 콜백과 100/150/200%×좁은/넓은 폭 6조합 측정.
4. home: 전체 문서/책장 더보기의 합성 메뉴 부재·내부 합성 문서 직접 열기 유지.
5. immersive: 방향 선택·앵커/전체 화면·재실행 선택 보존과 메인 기기 정책.
6. immersive: 세로/가로 역방향 실제 display rotation 180도·앵커/줌·저장 선택 복원.
7. settings: 방향 세부창 유지·명시적 닫기와 재생성 복원.
8. gestures: 실제 방향 드롭다운 선택 후 회전·줌/문서 앵커 보존.
9. font: 큰 글꼴3단계×본문 가로/세로의 도구/설정 접근과 메인의 기기 정책.

최초 settings 검사7은 메인의 예전 글자 Button을 찾다가 NoSuchElementException으로 실패했다. 앱 동작 실패로 확정하지 않고 실제 톱니바퀴 ImageButton과 본문 글자 Button을 모두 탐색하도록 검사 helper를 수정했다. 실패 원문은 orientationChoice-first-failure.log에 보존했고 마지막3개(수정7/미실행8/추가9)만 재실행해 성공했다. 먼저 성공한6개는 반복하지 않았다. 따라서 최신 계측 APK에서 전체9개나 기본96개를 한 번에 다시 실행한 결과가 아니다. main 앱 소스/기능은 이 검사 helper 보완으로 변경하지 않았다.

종료 finally에서 앱/계측 force-stop, 원래 font_scale 복원과 adb kill-server를 실행했다. 모든 검사/정리는 완료됐으며 사용자에게 개발용 앱의 방향·최근 목록·메뉴를 직접 확인하도록 요청했다. 사용자 수용은 응답 뒤 별도로 기록한다. 개인 문서의 파일명/본문·기기 화면을 추출하거나 공개하지 않았다.

## 남은 범위

실제 센서/제조사 회전 정책의 개인 체감 수용, 이번 사용자 확인과 PR/원격 CI·실제 CodeRabbit 결과는 각각 구분한다. 전체96개·장시간/성능/OOM·다른 API/기기·정식 서명/설치·원본별 안전성을 새로 완료하지 않았다. 사용자 디자인/로컬 버전·stash 보존과 문서 링크/공백은 게시 전에 최종 확인한다.

## 게시 전 최종 확인

문서 파일131개/상대 로컬 링크340개를 검사해 누락0, `git diff --check -- . ':(exclude)docs/design.md' ':(exclude)gradle.properties'` 종료0을 확인했다. 사용자 디자인/로컬 버전2개는 시작 해시와 동일하고 기존 stash7개도 동일하다. 사용자 변경과 원래 미게시 리뷰 완료 기록을 보존했으며 사용자 두 파일은 커밋에서 제외한다. UI 콜백의 onSample은 main 소스에서 사라졌고 sample_pdf 참조는 내부 합성 본문 제목에만 남아 있다.

기기9개 검증 완료 뒤 사용자 확인 요청을 보냈으며 아직 답변을 받기 전에는 개인 체감 수용으로 표시하지 않는다. 관련 구현/검증을 묶어 PR을 준비하고 리뷰 접수/CI 상태는 뒤에서 실제 조회 결과로 기록한다. 키/정식 APK 생성·공개 배포는 이번 범위가 아니다.
