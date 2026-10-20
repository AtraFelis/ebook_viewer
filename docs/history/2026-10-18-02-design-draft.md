# PDF 뷰어 UI/UX 디자인 초안

2026-10-18 (Asia/Seoul). 앱 구현 완료 보고가 아닌 디자인 산출물과 검증 기록이다.

## 사용자 결정과 범위

긴 PDF를 확대하고 연속 스크롤하며 읽는 사용을 우선한다. 사용자가 차분하고 절제된 도구형 추천안과 플로우 진행을 승인했다. 이어 읽기를 홈의 주요 행동으로 두고, 중앙 탭으로 도구를 표시하며, 중립색에 제한된 파랑을 사용한다. 읽기에 집중하고 조작 위치를 예측할 수 있게 하기 위한 선택이다.

[Figma 파일](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM)은 송채선 팀의 `Ebook Viewer — UI UX Design`이다. 실제 개인 PDF·제목·URI를 전송하지 않고 합성 제목과 본문으로 제작했다. 네이티브 Activity/View/Canvas 계약을 유지하며 새 런타임 의존성이나 Compose 채택을 결정하지 않았다.

## Foundations와 컴포넌트

[Foundations](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=4-2), [컴포넌트](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=4-101). 페이지 `0:1`에 변수 39개, 텍스트 스타일 6개, 컴포넌트 세트 8개와 변형 25개를 구성했다. Light 모드 초안이며 다크 모드 지원을 의미하지 않는다.

| 규칙 | 값과 의도 |
| --- | --- |
| 바탕/표면 | #F5F6F8 / #FFFFFF, 설정과 문서 카드를 구분 |
| 본문/보조/경계 | #20252D / #626C79 / #D9DEE5 |
| 주요 행동 | #285BC5, 누름 #20499E, 옅은 바탕 #EAF0FC |
| 오류 | #B3261E, 바탕 #FFF0EE |
| 읽기 외부 배경 | 기존 기본값 #444444 유지. PDF 내부 색 변경과 구분 |
| 간격 | 4/8/12/16/24/32, 화면 가로 여백 24 |
| 모서리/터치 | 8/12/16, 터치 영역 최소 48을 설계 기준으로 사용 |
| 타이포그래피 | Display 28/40, Title 22/32, Heading 18/28, Body 16/26, Label 14/22, Caption 12/20 |

서체는 Figma 예시용 Noto Sans KR Regular/Medium/Bold다. 앱에 별도 폰트를 추가한 것이 아니며 구현은 시스템 서체와 sp 배율을 고려해 다시 검증해야 한다. Figma 숫자를 PDF point나 화면 px로 그대로 사용하지 않는다.

| 컴포넌트 | 변형 |
| --- | --- |
| Button | Primary, Pressed, Secondary, Disabled, Loading, Danger |
| Field | Default, Focused, Error, Disabled |
| SettingRow | Default, Disabled, Selected |
| DocumentRow | Default, Unavailable |
| Choice | Off, On |
| Notice | Info, Error, Success |
| ToolAction | Default, Selected, Disabled |
| Toggle | Off, On |

## 사용자 플로우와 화면

첫 실행 → 빈 홈 → PDF 열기 → Android SAF → 로딩 → 첫 읽기 안내 → 전체 화면 읽기. 이후 실행은 이어 읽기 중심 홈 → 마지막 내부 위치로 복귀한다. 읽기 중앙 탭 → 도구 표시 → 페이지 이동/북마크/설정 → 같은 읽기 위치로 돌아온다. 설정은 공통 읽기 환경과 문서별 설정을 구분한다.

세로 화면은 360×800, 가로 예시는 800×360이다. 정적 검토용 크기이며 모든 기기의 반응형 배치가 검증된 것은 아니다.

| Figma 보드 | 포함 화면과 상태 | 구조와 행동 |
| --- | --- | --- |
| [핵심 흐름](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=5-30) | 홈, 읽기, 도구 표시, 읽기 설정 | 이어 읽기 카드/최근 목록, 연속 문서, 상하단 overlay, 공통·문서별 설정 |
| [시작과 복구](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=6-52) | 빈 홈, 로딩, 파일 오류, 기록 오류 | 첫 행동 PDF 열기, 진행 표시/취소, 원인별 복구, 기록 보존 |
| [이동과 문서 설정](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=6-135) | 페이지 이동, 북마크 목록, 빈 북마크, 페이지 배치 | 숫자 입력, 위치 선택/관리, 추가 안내, 원본·통일 너비와 간격 |
| [설정과 확인](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=7-82) | 방향, 배경색, 입력 오류, 기록 삭제 확인 | 5방향, 프리셋/RGB, 필드 오류, 원본 파일 보존 안내 |
| [읽기와 저장 상태](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=8-100) | 첫 안내, 홈 설정, 저장 오류, 북마크 추가 완료 | 중앙 탭 안내, 문서 설정 비활성, 저장 실패 안내, 간단한 완료 피드백 |
| [가로 읽기와 사용 흐름](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM?node-id=9-115) | 가로 읽기 및 플로우/상태 설명 | 가로에서도 줌/내부 위치 보존, 화면별 상태 적용 원칙 |

총 21개 화면·상태다. 기본/로딩/빈 상태/오류는 의미 있는 화면에 적용하며 모든 화면에 네 상태를 억지로 복제하지 않는다. 북마크 삭제 등의 세부 행동은 설명으로 명시하며 모든 전이를 별도 프레임으로 제작한 것은 아니다.

## 구현 인계 시 유지할 제약

- 기본 페이지 스냅 없음, 간격 0pt. 문서별 간격 범위 0–72pt. 회전·페이지 이동·설정·도구 노출로 사용자 줌과 내부 앵커/가로 위치를 초기화하지 않는다.
- 중앙 1/3 단일 탭은 읽기 영역 크기를 바꾸지 않는 overlay를 토글한다. 페이지 번호는 기본 숨김이다.
- 방향/외부 배경/번호는 공통, 크기/간격은 문서별이다. 문서가 없으면 문서 설정을 비활성화한다.
- 원본 삭제와 읽기 기록/북마크 삭제를 구분한다. 권한 소실·파일 오류 복구 중 기존 기록을 임의로 지우지 않는다.
- 로딩 막대는 진행 중 표현이다. 측정할 수 없는 진행률을 숫자로 만들어 표시하지 않는다.
- 저장 실패 상태, 긴 제목, 키보드/시스템 inset, TalkBack, 큰 글꼴, 가로의 짧은 높이는 실제 구현에서 검증한다.
- 검색/텍스트 추출/자동 배경/암호 지원과 P2 선명도 개선은 현재 제공 기능으로 넣지 않았다.

## 실제 검증과 제한

Figma 응답에서 변수 39개, 스타일 6개, 8세트/25변형을 확인했다. Foundations·컴포넌트·6개 화면 보드를 렌더링하여 시각 검토했고, 후속 보정에서는 Loading/Danger 예시 라벨, 설정 2종의 토글, 첫 안내 dim 처리, 로딩 막대를 확인했다. 토글 세트 `11:36`은 Foundations 페이지에 있음을 재확인했다. 검토 중 컴포넌트 높이/텍스트 넘침을 보정했다.

이전 Figma 수정 요청 한 번은 자동 승인 검토 사용량 한도로 실행되지 않았다. 사용자 재개 요청 후 연결이 복구됐고, 기존 노드를 확인한 다음 버튼 인스턴스 `4:125`/`4:129` 문구를 수정했다. 중복 화면을 생성하지 않았다.

프로토타입 연결 시도는 중첩 화면을 내비게이션 대상으로 쓸 수 없어 실패했다. 클릭 연결은 없는 정적 시안이다. Ready for dev 상태를 설정하지 않았으며 최종 시안 사용자 수용과 구현 범위 확정은 남아 있다.

이번 저장소 변경은 SESSION/CHECKLIST/history 문서뿐이다. 앱 빌드·단위·Lint·ktlint·기기 테스트는 앱 코드 변경이 없어 미실행이며 과거 P1 검증 결과로 새 디자인 구현을 통과 처리하지 않는다. 커밋/push/PR/릴리스는 실행하지 않았다. 문서 공백과 변경 범위는 `git diff --check`, `git diff --stat`, `git status --short`로 확인한다.
