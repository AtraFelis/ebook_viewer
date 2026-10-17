# 문서 목차

| 문서 | 역할 |
| --- | --- |
| [AGENTS](../AGENTS.md) | 프로젝트 계약 |
| [SESSION](../SESSION.md) | 인수인계 |
| [CHECKLIST](../CHECKLIST.md) | 완료 판정 |
| [기획](product/requirements.md) | UX와 범위 |
| [구조](architecture/continuous-viewer.md) | 좌표·제스처·렌더링 |
| [로컬 전환](local-handoff.md) | Windows 동기화·점검·채팅 프롬프트 |
| [개발 환경](development.md) | 현재 상태·초기화 절차 |
| [Windows 실행](windows-development.md) | 고정 도구 설치·빌드·기기 검증 절차 |
| [P0 ADR](adr/0001-p0-toolchain-and-renderer.md) | 승인 구성, 조사 근거와 미결 |
| [개발 계획](plan/roadmap.md) | 단계와 완료 조건 |
| [검증](testing/strategy.md) | 기능·성능 기준 |
| [개발 규칙](rule/development.md) | 구현 원칙 |
| [Git 규칙](rule/git.md) | 커밋·동기화 |
| [이력](history/README.md) | 실제 결과 |

계획은 plan, 현재 동작 계약은 product/architecture, 실제 결과는 history에 기록합니다.

현재 기기의 [최종 P0 로컬 검증과 원격 CI 상태](history/2026-10-16-09-p0-local-validation.md)를 먼저 확인합니다.

후속 [main push·원격 CI 성공 결과와 P1 인수인계](history/2026-10-16-10-github-push-and-ci.md)까지 확인합니다.

현재 P1의 [로컬 읽기 기록 ADR](adr/0002-local-reading-records.md), [목록·이어 읽기 구현/검증](history/2026-10-17-01-p1-reading-library.md), [PR #1/CI 이력](history/2026-10-17-02-p1-library-pr.md)을 확인합니다. 기능별 PR로 검토하며 P1 전체 완료와 구분합니다.

PR #1 병합 이후 [몰입형 읽기/방향 ADR](adr/0003-immersive-reader-and-orientation.md)과 [후속 구현·검증](history/2026-10-17-03-immersive-reading.md)을 이어서 확인합니다.

사용자 확대/축소 피드백 반영과 PR #2 최종 CI 결과는 [배율 범위 변경 이력](history/2026-10-17-04-zoom-range.md)을 확인합니다.

[외부 배경색 ADR](adr/0004-custom-reader-background.md)과 [P1 수동 색상 검증](history/2026-10-17-05-custom-background.md)을 확인합니다. 자동 색 추출은 P2로 이관했고, [스크롤 선명도 복구 지연의 P2 계획](plan/p2-sharpness-recovery.md)을 별도로 관리합니다.

[페이지 이동·북마크·번호 표시 ADR](adr/0005-navigation-bookmarks-and-page-numbers.md)과 [API 33 검증 이력](history/2026-10-17-06-navigation-bookmarks.md)을 이어서 확인합니다. 페이지 번호는 사용자가 표시/숨김을 선택하고 보존합니다.

사용자 지정 [PR 수동 리뷰·수정 절차](rule/git.md)와 [PR #5 실제 대응 이력](history/2026-10-17-07-pr-review-workflow.md)을 확인합니다. 봇의 리뷰 건너뜀 성공과 실제 리뷰 완료를 구분합니다.

후속 [페이지 크기·간격/기록 v2 ADR](adr/0006-page-layout-and-reading-records-v2.md)과 [로컬·API 33 검증 결과](history/2026-10-17-08-page-layout.md)를 확인합니다.
