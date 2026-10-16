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
