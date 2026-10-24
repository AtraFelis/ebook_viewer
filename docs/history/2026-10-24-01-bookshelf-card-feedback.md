# 책장 카드 피드백 반영 — 2026-10-24

후속 상태: 사용자가 새 표시 이미지/전체 문서 요구와 개발 전 방향 검토를 요청했다. 현재는 [다음 계획 이력](2026-10-24-02-library-interaction-planning.md)·[조작 UI 계획](../plan/library-interaction-refinement.md)을 우선하며 아래의 버튼 배경/인라인 관리 성공은 그 새 요구의 완료가 아니다.

## 요청과 보존

사용자는 기존 메인 상단을 유지한 상태에서 미분류를 별도 책장 카드로 표시하되 비어 있으면 숨기고, 책장 추가는 편집 안에서만 보이도록 요청했다. 본문 읽기 이외의 책장 버튼에는 배경색을 넣고 첨부 사진의 표지 묶음/이름/더보기 배치를 참고하도록 했다. 10월23일 시작한 보완 중 중단된 작업을 24일 이어 마무리했다.

현재 체크아웃 codex/bookshelf-library-redesign/main 기반 a6a202f를 유지했다. 사용자 docs/design.md·gradle.properties의 기존 SHA-256과 stash/개인 키·정식 앱을 보존하며 새 의존성·저장 형식 변경은 없다. 이전 구현/중간 실패는 [앞선 이력](2026-10-23-05-bookshelf-redesign-implementation.md), 최종 범위는 [책장 계획](../plan/bookshelf-library-redesign.md)·[ADR0020](../adr/0020-bookshelf-library-redesign.md)을 따른다.

## 변경

- 미분류는 소속 없는 문서가 있을 때 첫 번째에 계산하는 가상 책장이다. 빈 가상 카드는 제거하며 실제 사용자가 만든 빈 책장은 유지한다. 가상 책장을 저장/이름 변경/삭제/수동 순서 대상으로 만들지 않는다. 카드 안에는 해당 미분류 문서만 표시한다.
- 책장 추가는 편집에서만 표시한다. 이름순에서도 편집/추가는 가능하며 사용자 지정 정렬일 때만 실제 책장의 위/아래 이동을 제공한다. 가상 카드가 추가돼도 실제 책장의 이동 경계가 밀리지 않게 계산한다. 빈 화면의 안내에도 편집에서 추가한다고 명시했다.
- 책장·문서 관리 동작은 ReaderStyle의 기존 옅은 배경색을 사용한다. 본문 도구·보기 설정·ReaderStyle 전역 구현은 변경하지 않았다. 카드 자체에는 표지/텍스트가 중심이 되도록 누를 때만 반응을 유지하고 더보기는 배경이 있는48dp 버튼으로 제공한다.
- 참고 이미지처럼 정사각 대표 영역에 최대3개 표지를 첫 장은 크게, 나머지는 오른쪽 위/아래로 묶는다. 아래 이름/문서 수와 세 점 더보기를 표시한다. 실제 읽은 시각만 ‘최근 읽음’으로 구분하며 존재하지 않는 책장 수정 시각을 만들지 않는다. 큰 글꼴/좁은 폭은 도구를 나눠 배치한다.
- 합성 화면 검토에서 더보기가 없는 미분류의 제목 줄이 다른 책장보다 높게 보임을 발견했다. 책장 제목 줄의 최소 높이를 맞췄으며 본문 문서 제목에는 이 높이를 강제하지 않는다.

## 실제 검증

PowerShell의 실제 로컬 저장소에서 android-env.ps1을 적용했다.

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug ktlintCheck --max-workers=2 --dependency-verification strict --console=plain` 성공13초. 제목 정렬 마지막 보완 뒤 같은 검사47초 성공. debug Lint XML 오류0/경고0이다. 로그는 무시 경로 local-artifacts/bookshelf-card-feedback-final-build.log 및 bookshelf-card-alignment-build.log다.
- API33 관련4개를 분할 실행해 각 `P0 tests=1, failures=0`을 확인했다: uncategorizedCardAndEditingFollowTheHomePresentationRules, originalHeaderAndEmbeddedBookshelvesRestoreTogether, gridSelectionRestoresAndShelfHomeUsesRecycledCells, largeFontsAndLandscapeKeepDocumentGridUsable.
- 새 검사는 미분류 내용/카드 숨김, 빈 카탈로그의 생성 접근, 편집 진입/종료 시 추가 표시, 더보기 콜백과 가상 카드의 더보기 숨김, 버튼의 지속 배경이 실제로 그려짐을 합성 자료로 확인한다. 기존 검사는 메인 상단/책장 복귀·스크롤/재생성, 선택2개/열기0, 문서 격자의 큰 글꼴/가로6조합을 확인한다. 이6조합을 메인 전체 화면 수용/실제 회전6회로 확대하지 않는다.
- 토큰 중단 뒤 기존 실행 결과를 이어 읽어 위4개가 이미 완료됐음을 확인하고 반복하지 않았다. 마지막 정렬 보완 때 기기 미연결로 설치 시도가 실패한 뒤, 사용자가 다시 연결/잠금 해제를 알려 주어 개발용/계측 APK만 -r 갱신했다. 최종 APK에서 새 검사1개 재실행도 실패0이다. 로그 local-artifacts/bookshelf-card-alignment-device.log를 확인했다. 마지막 한 APK에서4개 전체를 다시 실행한 것은 아니다.
- 합성 카탈로그만으로 만든 테스트 View를 PNG로 그려 무시 경로의 public-bookshelf-card-feedback.png를 검토했다. 사용자의 PDF/개인 기록·표지나 실제 기기 화면을 추출하지 않았다. 이 이미지는 마지막 제목 높이 보완 전 결과다.
- 최종 앱/계측 force-stop·ADB 종료를 완료했다. 단위81개는 앞선 저장/정렬 구현 단계의 실패0 결과이며 이번 UI만의 보완에서 반복하지 않았다. 기본96개 전체·장시간/성능·OOM·다른 API·최신 unsigned release/정식 서명 검사는 미실행이다.

## 남은 일

사용자에게 최종 카드/편집/배경/참고 배치 확인을 요청했다. 화면 수용·추가 조정과 관련 변경의 commit/push/PR·원격 CI/CodeRabbit 실제 리뷰는 별도이며 아직 게시하지 않았다. 병합/정식판 배포는 하지 않는다. PR 설명은 실제 실행 범위와 미실행을 구분해 준비한다.
