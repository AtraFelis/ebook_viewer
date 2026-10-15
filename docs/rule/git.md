# Git 규칙

기본 브랜치는 main입니다. 작업 브랜치는 feat/, fix/, docs/, chore/를 권장합니다. 사용자 요청 없이 worktree를 만들지 않습니다.

커밋: `<type>(<scope>): <요약>`. scope 선택, type은 feat/fix/perf/refactor/test/docs/build/ci/chore. 한 목적을 한 커밋으로 묶습니다.

PR에는 문제·최종 변경·실제 검증·남은 제약을 기록합니다. 렌더 변경은 측정 근거를 포함합니다. 비공개 PDF/개인 정보는 제외합니다.

SDK·build·local.properties·키·개인 문서는 추적하지 않습니다. 검증된 Wrapper JAR는 의도적으로 추적 가능합니다. 사용자 변경을 보존하고 강제 push/reset/clean으로 충돌을 숨기지 않습니다.

평소 클라우드 작업, PC 작업 요청 때만 해당 PC에서 상태를 확인하고 `git pull --ff-only`를 우선 사용합니다. 커밋·push는 승인 범위에서 수행합니다. 환경 종료 전 미푸시 변경을 명시합니다.
