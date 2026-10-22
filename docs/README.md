# 문서 목차

현재 Windows의 [빌드·설치·라이선스/배포 준비](release-preparation.md)와 [P3 최종 통합 점검](history/2026-10-22-01-p3-final-verification.md)을 먼저 확인합니다. [이번 범위/종료 조건](plan/p3-final-verification-and-release-preparation.md)은 아이콘 선택·실제 release 배포와 분리합니다.

2026-10-20 P2의 기능·자동 통합 검사·사용자 수용과 PR #17 병합 완료 근거는 [P2 마무리](history/2026-10-20-01-p2-completion.md)를 확인합니다. 완료 문서 PR 반영 후 종료를 확정하며 기기/메모리·리뷰 범위의 한계를 함께 기록합니다.

현재 페이지 본문 읽기의 [격리/중단·접근성 결정](adr/0015-bounded-current-page-text-and-accessibility.md)과 [실제 검사·미결](history/2026-10-19-03-page-text-accessibility.md)을 확인합니다. 2026-10-19 사용자 결정으로 선택/복사 UI는 후속 보류하고 본문 읽기 안전성·TalkBack/큰 글꼴·최종 통합 검증은 유지합니다.

P2 읽기 화면 설정은 [ADR 0014](adr/0014-reader-display-settings.md), [실행/미결](history/2026-10-19-02-reader-display-settings.md)을 따릅니다. 새 의존성 없이 테마와 열람 중 밝기/화면 유지를 구현하며 기기 검사/개인 수용·최종 통합은 실제 이력의 상태를 따릅니다.

P2 자동 배경색은 [ADR 0013](adr/0013-automatic-page-background.md), [실행/미결](history/2026-10-19-01-automatic-background.md)을 따릅니다. 기존 수동 설정을 보존하는 선택 기능이며 선명도/자동 배경색의 최종 통합 검증과 다른 P2 필수 기능은 별도입니다.

현재 P2는 사용자 결정으로 검색을 후속 보류하고 목차·비밀번호 PDF 읽기도 필수에서 제외합니다. [최신 범위](plan/p2-scope-and-completion.md)를 따릅니다. 선명도 작업은 검색과 독립된 [가시 면적/페이지 재사용 결정](adr/0012-bounded-page-reuse-and-sharpness.md), [실행 결과](history/2026-10-18-13-sharpness-baseline-and-page-reuse.md), [재현 절차](testing/sharpness-probe.md)에서 확인합니다.

P2는 최신 범위의 기능 확장을 포함하고 Figma 전면 적용은 별도 단계로 분리합니다. [기능별 수용·종료 시점](plan/p2-scope-and-completion.md)을 확인합니다. [플랫폼 확장/합성 기능 검증](adr/0009-p2-platform-pdf-capabilities.md) 후 보통 스크롤 선명화 기준선과 페이지 재사용 1차 개선·개인 PDF 체감 수용을 완료했습니다. 빠른 플링·최대 배율·복잡 자료의 최종 선명도 20그룹과 실제 Activity 프레임/10분 검사·개인 사용 수용도 완료했습니다. 목차는 필수 범위에서 제외했습니다.

현재 개인 API 33 범위의 P1은 완료했습니다. [완료 판정·최종 CI·사용자 수용·미결 리뷰](history/2026-10-17-12-p1-completion.md), [다음 단계 로드맵](plan/roadmap.md)을 확인합니다.

가로/세로 역방향 고정 추가는 [방향 설정 ADR](adr/0003-immersive-reader-and-orientation.md), [검증 이력](history/2026-10-17-11-reverse-orientation.md)을 확인합니다.

현재 백그라운드·캐시 취소 보완은 [ADR 0008](adr/0008-reader-lifecycle-and-cache-cancellation.md), [실행 이력](history/2026-10-17-10-reader-lifecycle.md)을 확인합니다. 알림 주입과 실제 시스템 메모리 고갈 검증은 구분합니다.

현재 후속 파일 접근·오류 복구는 [ADR 0007](adr/0007-document-access-and-failure-recovery.md), [API 33 실행 이력](history/2026-10-17-09-document-access.md)을 확인합니다. 제조사 파일 선택 UI/개인 PDF 수용과 합성 provider 계측은 구분합니다.

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
