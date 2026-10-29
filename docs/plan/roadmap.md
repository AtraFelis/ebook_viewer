# 개발 로드맵

갱신: 2026-10-29. 개인 API 33 기기를 대상으로 합의한 개발과 개인용 설치 준비는 완료했다. 현재 상태는 [SESSION](../../SESSION.md), 증거와 제한은 각 완료 이력을 따른다. 계획의 과거 제안/검사 목표를 현재 미완료 필수 작업으로 간주하지 않는다.

## 완료한 단계

- **P0 기술 검증:** 도구/렌더러 승인·초기화·가시 타일/제한 캐시·첫 표시·기기 측정과 CI. [완료 근거](../history/2026-10-16-09-p0-local-validation.md).
- **P1 핵심 MVP:** 이어 읽기·몰입형 화면·방향·수동 배경·북마크/번호·크기/간격·파일 오류 복구/수명. [완료 판정](../history/2026-10-17-12-p1-completion.md).
- **합의한 P2:** 선명도 복구 개선·자동 배경·테마/밝기/화면 유지·현재 페이지 본문 읽기·당시 통합 검사/사용자 수용. [범위](p2-scope-and-completion.md), [선명화 계획](p2-sharpness-recovery.md), [완료 결과](../history/2026-10-20-01-p2-completion.md). 검색/복사·목차/암호는 아래 보류 범위다.
- **디자인/읽기 조작:** 홈/설정·작은 읽기 도구·페이지 슬라이더·저장 실패 재시도·번호 가독성·가로 잠금. [디자인 계획](design-implementation.md), [읽기 도구](compact-reader-tools.md), [가로 잠금](horizontal-pan-lock.md), [세부 조작](reader-chrome-polish.md).
- **P3 안정화:** 상태·수명·장시간 읽기·회전/포커스·최종 통합/고지 점검. [상태 안정화](p3-reading-stability.md), [20분 점검](p3-reading-soak.md), [회전 보완](p3-rotation-focus-stability.md), [최종 범위](p3-final-verification-and-release-preparation.md), [실제 결과](../history/2026-10-22-01-p3-final-verification.md).
- **개인용 준비:** [아이콘](launcher-icon.md) 적용과 사용자 외관 수용, [별도 정식판 서명/업데이트](personal-release.md), 사용자 설치·키 백업 확인. [완료 근거](../history/2026-10-24-10-main-sync-and-release-closeout.md).
- **개인 사용 후속:** 자동 배경색 깜빡임·방향 드롭다운·문서 공통 여백/실제 미리보기·숫자 키패드. [계획](reader-usability-follow-up.md), [검증](../history/2026-10-22-05-reader-usability.md).
- **PDF 색반전/책장:** [초기 확장](library-management-and-inversion.md)과 [책장 개편](bookshelf-library-redesign.md)·[조작 정리](library-interaction-refinement.md)를 PR #33까지 반영했다. 다중 소속·정렬·격자/목록·표지·선택 이동/중앙 삭제 확인·사용자 수용/리뷰 수정/최종 CI 완료. [최종 결과](../history/2026-10-24-09-cover-test-api-guards.md).

PR #34 완료 문서도 사용자 병합됐다. [이번 문서·파일 정리](../history/2026-10-29-01-documentation-and-cleanup.md)는 앱 기능과 별도의 유지보수다.

## 다음 작업을 시작하는 조건

다음 기능은 실제 사용 피드백을 받은 뒤 목적·화면 흐름·저장/원본 영향·수용 기준을 먼저 정한다. 새 런타임 의존성/스택 변경은 구성안 승인 후 착수한다. 필요한 검사만 선택하고 실제 결과가 모인 시점에 CodeRabbit을 요청한다. PR 병합은 사용자다.

## 보류·제외 범위

- 문서 전체 검색과 선택/복사 UI는 후속 보류. 검색 장애의 원인 미확정 진단은 Draft PR #12 이력으로 남기며 현재 main에 검색을 다시 켜지 않는다.
- 내장 목차·비밀번호 PDF 읽기는 현재 필수 제외. OCR·EPUB·주석/동기화·원본 파일 이동/삭제·태블릿/다른 기기 확대는 별도 계획이 없다.
- DPI/픽셀·압축·색공간별 지연 원인의 정량 확정은 미실행. 기존 선명화 개선이 모든 PDF의 무지연을 의미하지 않는다.
- TalkBack 전용 후속 개발/수동 검증은 제외하고 큰 글꼴·일반 터치·현재 페이지 본문 읽기는 유지한다.
- 프로젝트 코드 LICENSE와 스토어/공개 배포는 보류. 개인용 서명/설치 완료와 구분한다.

## 검증 완료의 의미

각 단계의 앱/자료/실행 범위를 history에 보존한다. 단위 84개와 최종 API 33 관련 검사/CI가 통과했지만 기본 96개 전체를 최종 APK에서 다시 실행하지는 않았다. API 23~29 표지 대체 정책의 컴파일 확인을 기기 통과로 부르지 않으며 실제 OOM/임의 PDF 안전성도 미검증이다. 기능 수용·자동 검사·외부 리뷰·개인 설치를 서로 구분한다.
