# 문서 목차

2026-10-29 기준. 현재 상태는 [SESSION](../SESSION.md), 완료 판정은 [CHECKLIST](../CHECKLIST.md), 개발 제약은 [AGENTS](../AGENTS.md)가 기준이다. P0~합의한 P3와 후속 책장 개편/개인용 준비는 완료했고 PR #34까지 병합됐다. 상세 제한은 [최신 정리](history/2026-10-29-01-documentation-and-cleanup.md)를 참고한다.

## 시작과 운영

- [제품 기능과 범위](product/requirements.md)
- [로드맵·완료/보류](plan/roadmap.md)
- [개발 환경](development.md) / [Windows 설치·검사](windows-development.md)
- [개인용 정식 APK 업데이트](personal-release.md) / [고지·배포 준비](release-preparation.md)
- [프로젝트 파일 정리](maintenance.md)
- [개발 규칙](rule/development.md) / [Git·CodeRabbit 규칙](rule/git.md)
- [검증 전략](testing/strategy.md) / [P2 통합 절차](testing/p2-integration.md) / [선명화 측정](testing/sharpness-probe.md)

## 구조와 결정

- [연속 스크롤·좌표·자원 수명](architecture/continuous-viewer.md)
- [도구·렌더러 구성](adr/0001-p0-toolchain-and-renderer.md)
- [현재 페이지 본문 읽기 격리](adr/0015-bounded-current-page-text-and-accessibility.md)
- [가로 이동 잠금](adr/0016-horizontal-pan-lock-and-reading-records-v3.md)
- [정식 앱 식별자·서명](adr/0017-personal-release-identity-and-signing.md)
- [여백·원본 앵커](adr/0018-document-crop-and-reading-records-v4.md)
- [PDF 색반전·책장](adr/0019-pdf-colors-and-bookshelves.md)
- [기록 v6·다중 소속·표지](adr/0020-bookshelf-library-redesign.md)

ADR은 결정 당시의 맥락을 보존한다. 후속 ADR이 이전 결정을 확장한 경우 현재 데이터 계약은 AGENTS와 최신 ADR을 따른다. 계획별 구현 근거는 [로드맵](plan/roadmap.md)에서 찾는다.

## 디자인과 과거 기록

- [디자인 인계](design.md)는 사용자 작업 파일이다. 구현의 현재 동작과 사용자 후속 결정을 함께 확인한다.
- [개발 이력 목차](history/README.md)에 실제 명령·검증 결과·미실행·제한을 기록한다.
- [초기 클라우드→Windows 전환](local-handoff.md)은 2026-10-16 당시 기록이며 현재 초기화 상태가 아니다.

일반 안내에 진행 로그를 계속 덧붙이지 않는다. 현재 요약은 SESSION에 한 번 기록하고, 실제 과거 과정은 history에 보존한다. plan의 당시 검사 기준이나 제안을 현재 미완료 작업으로 재개하지 않는다.
