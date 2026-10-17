# 사용자 지정 PR 리뷰 절차와 PR #5 대응

2026-10-17 (Asia/Seoul).

## 사용자 지시

PR을 넣을 때마다 CodeRabbit 수동 리뷰를 요청한다. 이후 리뷰가 있으면 코드를 확인해 타당한 지적을 수정·검증하고 다시 push한다. 판단을 봇에 위임하지 않으며 기존 의존성 승인/사용자 변경 보존 계약을 유지한다. 이 절차를 AGENTS와 docs/rule/git.md에 저장했다. PR 병합은 사용자가 한다.

## PR #5 확인

- 이전 CodeRabbit 댓글은 별 10개 미만으로 자동 리뷰를 건너뛰었다고 안내했다. 성공 체크는 리뷰 완료가 아니었으며 실제 reviews/인라인 지적은 없었다.
- 공식 명령 `@coderabbitai review`를 [댓글](https://github.com/AtraFelis/ebook_viewer/pull/5#issuecomment-6230080100)로 한 번 등록했다. 봇의 접수 응답과 base `24ef93b`~head `751dbc0`의 23개 파일 검토 진행을 확인했다. Autofix/자동 코드 변경은 활성화하지 않았다.
- head `751dbc0`의 [Windows CI 39005653250](https://github.com/AtraFelis/ebook_viewer/actions/runs/39005653250)는 성공했다. CodeRabbit 실제 리뷰 결과는 별도로 대기 중이다. `gh pr checks --watch --interval 20`으로 기다리며 중복 실행 요청은 보내지 않는다.
- `git fetch origin` 후 현재 브랜치와 원격 head 차이는 0/0, PR은 MERGEABLE이었다. 사용자 변경/외부 커밋을 덮어쓰지 않았다.

## 검증과 다음 행동

현재 변경은 규칙 문서뿐이므로 앱 테스트를 불필요하게 반복하지 않는다. 실제 리뷰가 도착하면 미해결 지적의 타당성·위치를 확인하고, 채택/보류 근거와 수정 커밋·해당 변경의 검사 결과를 이 문서에 기록한다. 검증하지 않은 내용이나 진행 중 리뷰를 통과로 표현하지 않는다.
