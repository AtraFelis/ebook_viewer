# 책장 개편 구현 — 2026-10-23

후속 사용자 요구·현재 상태는 [10월24일 책장 카드 피드백](2026-10-24-01-bookshelf-card-feedback.md)을 우선한다. 아래는 앞선 메인 통합/검증 이력이다.

## 요청·보존·브라우저 대조

사용자가 기존 Figma 시안 기반 개발을 승인했고, MCP 한도에 걸리면 브라우저/Playwright로 확인하며 계속하도록 지시했다. AGENTS→SESSION→CHECKLIST→책장 계획·Git/개발 규칙을 읽었다. origin fetch 뒤 HEAD36c9b80과 origin/main a6a202f의 파일 차이가 없음을 확인하고 현재 체크아웃을 codex/bookshelf-library-redesign으로 분리했다. 기존 미커밋 문서·사용자 docs/design.md/gradle.properties·stash는 유지한다. 강제 조작/새 worktree/릴리스/정식 키 사용은 없다.

Figma get_design_context는 Starter 호출 한도로 실패했다. 이전에 작성하고 읽은 편집 노드·스타일·전체 screenshot을 보유한 상태에서 브라우저로109:907 보드를 열고111:1217 홈→111:1260 격자까지 직접 이동·화면을 확인했다. 합성 표지를 실제 PDF 자산으로 가져오지 않는다. 기존 시스템 글꼴·ReaderStyle/공통 폼을 유지한다.

## 구현

- 기록 v6의 다중 소속/중복 방지·관계만 제거·책장 수동 순서. v1~v5 호환, 오래된 위치 저장은 현재 소속/순서와 병합. 최초 v6 쓰기 전 기존 기록 바이트를 앱 private reading-records.bin.before-v6에 원자 보존한다.
- 별도 library-preferences.bin v1: 격자/목록, 문서 최근/이름/기존 수동 순, 책장 사용자 지정/이름순. 앱 소유 직렬 IO/AtomicFile, 쓰기 실패 초안·재시도.
- 책장 홈·책장 안·선택 모드와 재사용 GridView/BaseAdapter. 가시 표지 모음·간결한 도구/이어 읽기, 다중 책장 추가와 현재 책장에서만 빼기·서재 제거를 분리한다. 선택/범위/스크롤 복원·긴 제목 두 줄·작은 가로/큰 글꼴의 도구 스크롤을 적용한다.
- 별도 API30+ 요청별 BookCoverService 격리 프로세스. 첫 페이지384×512 이하·동시1·6/7초 제한·RSS/메모리 가드. FD만 전달하고 원본 경로/URI는 서비스에 넘기지 않는다. 보이는 URI만 최대32개 스케줄, IO 취소·수명/완료 중복 방지·실패 대체·메모리8MiB/디스크64MiB 캐시. API23~29는 기본 표지로 동작한다. [ADR0020](../adr/0020-bookshelf-library-redesign.md)에 정책과 한계를 기록했다.

## 중간 검사·진단

초기 컴파일의 nullable 임시 파일 접근을 수정했다. 신규 v6 왕복 테스트의 기대값이 기존 codec의 openedAt 정규화와 달랐으며, 정규화된 순서/모든 소속·위치를 비교하도록 바로잡았다. 이후 단위81개/실패0을 확인했다. 그리기 중 RectF 생성·미사용 문자열·직접 문자열 연결·선택창 상수에 대한 Lint 지적을 수정했고, PC strict debug/계측 빌드·단위81개·Lint 오류/경고0·ktlint가 통과했다. 이후 추가한 검사의 최종 결과는 아래에 갱신한다.

초기 격자 검사는 root1080×2400·grid1080×1491·데이터1개·표시 셀0으로 실패했다. 잠금 false/interactive true를 확인하고 기본 Android 격자와 새 격자를 같은 Activity에서 비교했다. 상태 매 갱신 복원을 제거/열 너비를 명시한 것만으로 해결되지 않았다. 레이아웃 콜백 안의 열 수 재지정을 제거하고 플랫폼 AUTO_FIT으로 전환한 후 실제 셀 표시·문서 선택2개/열기0·상태 복원이 통과했다. 비교용 기본 격자 단계는 진단 후 제거했으며, 실패 메시지에는 비공개 내용 대신 치수/부착/셀 개수만 남긴다.

격리 표지는 합성 PDF에서 실제 생성·384×512 상한·manifest isolated/non-exported·손상 PDF 대체와 요청 해제를 통과했다. 대형 이미지/실제 OOM/다른 API 성능 검증으로 확대하지 않는다. 원래 전체96개 suite는 반복하지 않으며, 필요한 개별 검사를 추가/등록했다.

기기의 읽기 기록을 PC로 복사하려던 첫 명령은 자동 승인 검토가 명시적인 민감정보 egress 승인 부족으로 거절했다. 해당 복사는 실행하지 않고 APK 갱신/합성 검사만 별도 실행했다. 이 거절을 우회해 개인 기록을 추출하지 않았다. 이후 추가한 백업은 원본 기기의 앱 private 영역 내부에만 남는 이전 형식 보존이다.

코드 스타일 정리는 새로 수정한 Kotlin 파일에 한정해 기존 고정 ktlint CLI1.5.0의 format을 명시 실행하고 잔여 항목은 직접 수정했다. 저장소 전체 ktlintFormat은 실행하지 않았고 사용자 파일/변경하지 않은 앱 파일에 일괄 포맷을 적용하지 않았다. 첫 Java 시작 오류(-1073741502) 뒤 max-workers=2로 같은 검사를 진행했다.

## 사용자 범위 정정·메인 복원 — 중간 안

사용자는 “이전 메인화면은 그대로 유지하되, 책장으로 들어가는 버튼”을 원했다고 정정했다. 메인까지 책장 중심으로 교체한 구현은 범위를 넓힌 것으로 인정하고 되돌렸다. 기존 HEAD의 ReaderHomeView 배치/동작을 ClassicReaderHomeView로 보존하고 제목 옆 작은 ‘책장’ 버튼만 추가했다. 별도 BookshelfLibraryView에 새 격자/목록/책장 관리를 두고 ReaderHomeView가 두 목적지를 연결한다. 기존 메인의 ‘이동’/관리 동작도 보존한다. 격자의 재사용 성능 범위를 기존 메인의 모든 행까지 확대하지 않는다.

목적지·선택/범위 상태를 저장하고 돌아올 때 메인 ScrollView 위치를 유지한다. 양쪽에 보이는 오류/재시도/다시 연결은 실제 동일 컨트롤을 옮겨 비동기 실패 안내와 클릭 처리가 숨겨지지 않도록 했다. 이전 메인에 필요한 문자열11개를 HEAD에서 복원했다. 새로운 회귀 검사는 실제 메인 제목/환영 카드·진입·복귀 스크롤/목적지 복원·오류/재시도를 합성 카탈로그로 확인한다. 기존 두 문서 계측은 복원된 메인의 행을 실제로 눌러 이어 읽는다.

## 분리 화면 중간 안의 실행 결과와 한계

- 메인 복원 뒤 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --max-workers=2 --dependency-verification strict --console=plain`:17초 성공. XML 합계 단위81개/실패0/오류0/건너뜀0, debug Lint 오류0/경고0. 로그는 무시 경로 local-artifacts/bookshelf-main-preservation-build.log다.
- API33 최신 APK에서 개별3개 모두 `P0 tests=1, failures=0`: originalMainRemainsDefaultAndBookshelvesReturnWithoutLosingScroll, gridSelectionRestoresAndShelfHomeUsesRecycledCells, LibraryDeviceTest.twoDocumentsRestoreIndependentlyFromListAfterActivityRelaunch. 메인 복귀와 재시도·격자 선택/상태·줌을 포함한 문서별 재실행 이어 읽기를 확인했다.
- 앞선 APK에서 isolatedCoverProducesBoundedPixelsAndInvalidPdfFallsBack, legacyMigrationBacksUpPrivatelyAndPositionWritesKeepBothShelves, largeFontsAndLandscapeKeepDocumentGridUsable도 각각1개/실패0이다. 글꼴100/150/200%×가로/세로는 합성 네이티브 뷰6조합이며 전체 Activity 회전6회로 확대하지 않는다. 메인 복원 뒤 이3개는 반복하지 않았다.
- unsigned release assemble/release Lint 성공은 메인 복원 이전 상태다. 정식 서명/배포·개인 키 사용은 없었다. 기본96개 전체·장시간/성능·대형 이미지의 실제 OOM·다른 API/기기는 미실행이다.
- 개발용 .p0 앱/계측 APK만 `adb install -r` 갱신했다. 마지막 앱/계측 force-stop·ADB 종료를 완료했다. 사용자 docs/design.md·gradle.properties의 원래 SHA-256과 기존 stash/정식 앱·데이터를 보존했다. 개인 기록/실제 표지·PDF는 PC/Git/외부로 추출하지 않았다.

## 현재 미결

아래 최종 구체화를 우선한다. 사용자에게 개발용 앱의 메인 상단 보존·아래 책장 격자/문서 진입·복귀 확인을 요청했으며 응답 대기다. 최종 수용·commit/push/PR·원격 CI/CodeRabbit은 미실행이다. 가져오기 중 회전 시 완료 안내 손실은 기존 비차단 후속으로 남으며 새 다중 소속 저장 성공과 별개다. 개인정보/키·원본 PDF·실제 표지는 Git/외부 서비스에 보내지 않는다. 정식 APK 빌드/설치·병합은 하지 않았다.

## 최종 구체화 — ‘등록한 문서’를 책장 격자로

사용자는 중간 안 확인 요청에 “‘등록한 문서’ 부분에 ‘책장’이 들어가서 여기에서 그리드뷰”라고 답했다. 제목 옆 별도 진입 버튼을 제거하고 기존 상단의 제목/설정·이어 읽기 카드·PDF 열기를 ReaderMainHeader로 보존했다. 그 아래 영역에 ‘책장’ 제목·정렬/생성 도구·표지 격자를 표시한다. ListView 헤더와 재사용 카드 행으로 화면 전체를 한 번에 스크롤하며, 카드→문서 GridView/목록→뒤로가기에서 원래 책장 행/오프셋을 복원한다. 별도 중첩 스크롤 격자/ClassicReaderHomeView는 사용하지 않는다.

오류/재시도 컨트롤은 현재 목적지의 도구 영역을 이동해 실제 콜백과 표시를 유지한다. 메인으로 돌아오면 이전 책장 필터를 해제해 이후 메인에서 등록하는 PDF가 이전 책장에 자동 추가되지 않도록 보완했다. 메인에서 전체 문서·미분류로도 진입할 수 있으며 최근 개별 문서는 전체 문서의 최근 읽은 순과 기존 상단 이어 읽기로 접근한다.

- strict debug/계측 빌드·Lint·ktlint 최종 성공13초, 필터 해제 보완 뒤11초. 로그 local-artifacts/bookshelf-embedded-main-build.log 및 bookshelf-embedded-main-final-build.log. 첫 계측 import 누락/순서와 제거된 기존 문서 목록용 미사용 문자열5개의 Lint 오류는 수정 후 성공했다. 기준/테스트를 무력화하지 않았다.
- API33 관련4개 분할 성공/각1개·실패0: originalHeaderAndEmbeddedBookshelvesRestoreTogether, gridSelectionRestoresAndShelfHomeUsesRecycledCells, largeFontsAndLandscapeKeepDocumentGridUsable, LibraryDeviceTest.twoDocumentsRestoreIndependentlyFromListAfterActivityRelaunch. 메인 검사는 합성 책장20개로 상단 보존·재사용 행·카드 진입·재시도·스크롤/재생성 복원을 확인한다. 큰 글꼴6조합은 문서 격자이며 내장 메인6조합 전체 수용으로 확대하지 않는다.
- 필터 해제 최종 보완 뒤 메인 검사1개를 다시 실행해 `P0 tests=1, failures=0`을 확인했다. 최신 한 APK에서4개 전체를 다시 실행한 것은 아니다. 로그 local-artifacts/bookshelf-embedded-main-final-device.log다.
- 단위81개는 앞선 저장/정렬 및 메인 복원 단계에서 실패/오류/건너뜀0이었다. 최종 UI만의 변경에서 반복하지 않았다. 격리 표지/손상 대체·v5 private 원본 보존/병합의 성공은 이전 APK 결과이며 원래 전체96개/장시간·성능·OOM/다른 API/최신 unsigned release는 반복하지 않았다.
- 개발용 앱/계측만 -r 갱신했고 마지막 앱/계측 force-stop·ADB 종료를 완료했다. 사용자 디자인/버전 파일의 원래 해시와 stash를 보존하고 키/정식 앱/개인 기록 추출·원격 게시를 하지 않았다. 사용자 최종 배치 수용 후 필요한 보완과 게시/실제 CI·리뷰를 진행한다.
