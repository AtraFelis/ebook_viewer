# 책장 개편 계획 작성 — 2026-10-23

## 사용자 요청과 결정

본문 UI 개선 뒤 책장 계획을 세워 달라는 요청이다. 이번에는 앱 구현을 시작하지 않고 현재 기능·참고 화면과 사용자의 세 가지 응답을 반영했다.

- 표지 격자가 기본이고 목록 전환을 제공한다.
- 같은 문서는 여러 책장에 소속할 수 있다.
- 문서는 이름순·최근 읽은 순 정렬을 제공한다.
- 책장은 사용자 지정 순서 변경·이름순 정렬을 제공한다.

문서별 읽기 위치/북마크는 소속과 별개로 공유하고 파일을 복제하지 않는 방향이다. 책장에서만 빼기·서재 전체에서 제거·원본 파일 삭제를 구분한다. 화면 배치/기본값·표지 예산·PR 분할은 제안으로 표시하고 기능 선택을 구현/디자인 최종 수용으로 확대하지 않았다.

## 확인한 근거와 산출물

AGENTS→SESSION→CHECKLIST→기존 책장/본문 계획을 읽고 Git 상태를 확인했다. LibraryCatalog의 단일 membership, 기존 order/Bookshelf 목록, ReadingRecord v5, ReaderHomeView의 전체 LinearLayout 행 생성, LibraryManagement의 직렬 큐/기존 창을 확인했다. 표지 캐시는 현재 없다. docs/design.md는 읽기만 했으며 별도 디자인 작업의 소유·사용자 변경을 보존했다.

원격 `gh pr view 32 --json state,mergedAt,mergeCommit,baseRefName,headRefName`에서 PR #32가 main/a6a202fb260787b001f01f08e1277632d7823828로 사용자 병합됨을 확인했다. 로컬은 기존 feat/pdf-inversion-and-bookshelves/36c9b80 그대로이며 이번 계획에서 fetch/pull/checkout/새 브랜치/worktree/commit/push는 하지 않았다.

[상세 계획](../plan/bookshelf-library-redesign.md)에 화면3개·다중 소속/삭제 의미·정렬/보기 보존·v6 제안/이전 기록 변환·표지 안전/캐시 예산·3단계 PR 경계·완료 기준을 정리했다. 표지384×512px/메모리8MiB/디스크64MiB/동시성1은 초기 제안이며 측정값이 아니다. 출력 비트맵 축소만으로 원본 이미지 디코딩 메모리까지 제한된다고 주장하지 않으며 격리/중단 경로는 표지 착수 전에 구체화한다. 새 의존성 승인은 여전히 별도다.

계획 설명용 단일 Canvas 파일은 저장소 밖 전용 경로에 저장했다: `C:/Users/cotjs/.cursor/projects/c-Users-cotjs-workspace-ebook-viewer/canvases/bookshelf-redesign-plan.canvas.tsx`. 가상 문서4개·종이 그림만 포함한다. 책장 진입/격자·목록/이름·최근 정렬/책장 순서/다중 소속 추가·현재 책장에서만 빼기 흐름의 클릭 모형이며, 실제 PDF를 열거나 앱 데이터를 바꾸지 않는다. import는 cursor/canvas만 사용하고 네트워크/외부 표지는 없다. 공개 SDK 선언과 대조하고 번들 Babel의 babelParse로 TSX 구문을 파싱했다(상위 구문7개). 별도 TypeScript 전체 타입 검사·호스트 렌더/실기기 시안 수용을 실행했다고 보고하지 않는다.

## 검증과 미실행

계획/인수인계 문서의 상대 링크232개·공백 검사 통과와 앱/scripts 무변경·사용자 디자인/버전의 기존 SHA-256 보존을 확인했다. Canvas의 단일 SDK import/외부 네트워크 없음·스타일 제약도 정적으로 확인했다. Codex 측면 열기는 queued로 접수됐고 실제 렌더 완료로 세지 않는다. 문서/설계 시안만의 작업이므로 Android 빌드/단위/계측/ADB/CI·CodeRabbit을 반복하지 않는다. 기존73개/기기14개·후속2개 성공은 이전 본문 UI의 결과이며 새 책장 기능의 검증이 아니다.

새 계획과 이번 상태 기록은 로컬 미게시다. 다음은 계획/시안 검토 후 구현 지시를 받으면 기존 변경을 보존한 채 병합된 main에서 전용 개발 브랜치를 준비하고, 데이터 전환/다중 소속 기반과 ADR부터 진행하는 것이다. 실제 코드/저장 형식 전환·새 캐시/서비스·배포·사용자 병합은 아직 하지 않았다.

## 후속 — 실행 안내 정정과 Figma 시안 우선 작성

사용자가 클릭 시안을 보는 방법을 물었다. 이전 Canvas 소스 링크는 렌더 접수가 queued였을 뿐 실제 실행 화면 제공이 아니었음을 정정했다. 별도 thread visualization 경로에 가상 문서 HTML 모형을 만들고 로컬 Chrome headless로 진입/격자·목록/정렬/소속 추가·현재 책장에서만 제거/다른 소속 보존/순서 변경을 검사했다. 초기 브라우저 바이너리 경로 실패와 저장 상태 반향으로 선택이 초기화되는 문제를 수정한 뒤 통과했다. 이 모형의 검증은 Android 앱 검증이 아니다. 이후 사용자 요청이 Figma MCP 우선 디자인으로 바뀌어 Figma 결과를 우선 제공한다.

Figma MCP 세션 aa1db9ea-b9f5-46b2-ad4a-81c78d965efb에서 기존 파일 wFQ8qOUx3YfMy8u0E3t7fM의 페이지/인스턴스/67개 변수/6개 텍스트 스타일과 Noto Sans KR 폰트를 확인했다. Code Connect 파일은 없고 기존 EV 컴포넌트를 우선 사용했다. 라이브러리 확인 뒤 bookshelf/book cover 검색 결과가 비어, EV/BookTile·EV/BookshelfTile 제안 컴포넌트2개만 추가했다. 기존 디자인 페이지/노드와 docs/design.md는 덮어쓰지 않았다.

[검토 보드109:907](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=109-907), 프로토타입 최상위 화면 home111:1217 / grid111:1260 / list111:1312 / select111:1346 / add111:1398 / order111:1423, 구성 자산111:1195다. 같은 6개 화면의 편집 가능한 검토 복사본을 보드에 배치했다. 대표 연결28개와 시작점을 설정했다. 모든 정렬·순서·체크 조합/실제 PDF 열기/데이터 저장을 구현한 것은 아니다. 미연결 세부 버튼과 설정/생성 등의 후속 화면은 남아 있다.

중첩 프레임 목적지 거절, 자동 시작점과 수동 시작점 중복으로 생성 호출2회가 실패했다. 각 실패 뒤 읽기 전용 검사에서 생성 화면/자산이 남지 않고 기존 보드만 있음을 확인한 후, 최상위 프로토타입 프레임과 시작점 중복 제거로 복구했다. 최종 보드는1192×1870이며 검토/프로토타입 합계472개 노드(TEXT250/FRAME188/INSTANCE34), 잘못된 폰트0/이미지 채움0, 각 화면은360×800 안에 상단/본문/이어 읽기를 배치했다. 전체 구성 screenshot을 육안 검토했다.

선택 문서에 체크를 추가하고 소속 선택 행을 담백하게 만드는 최종 시각 보완 쓰기는 Starter MCP tool-call limit으로 거절돼 실행되지 않았다. 이미 저장된 시안/28개 연결은 유지된다. 호출 한도 해제 후 보완과 사용자 수용은 미결이며 제한을 숨기고 디자인 완료로 보고하지 않는다. 별도 thread visualization 경로의 figma-bookshelf-state.json/results.json에 ID/응답과 한계를 보존했다. 앱/의존성/Gradle/기기/ADB·git 변경/commit/push/CI/CodeRabbit은 실행하지 않았다. 사용자 디자인/버전 SHA-256은 기존 값과 동일하다. 계획/상태 기록은 로컬 미게시다.
