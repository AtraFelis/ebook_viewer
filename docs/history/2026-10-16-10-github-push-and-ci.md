# P0 main push와 GitHub CI

2026-10-16 (Asia/Seoul).

사용자는 준비된 P0 앱·Windows 도구/빌드 설정·문서 52개 변경을 확인한 뒤 “push 해”로 명시 승인했다. 새 런타임 의존성/릴리스 승인을 추가로 의미하지 않는다.

`git fetch origin` 후 현재 main과 origin/main의 ahead/behind는 0/0, HEAD는 `0aa65d3`. 추가 unstaged/untracked 변경 없음. staged 공백 검사 통과. 기존 체크아웃과 변경을 보존했으며 reset/clean/강제 push 없음.

실행:

- `git commit -m "feat(p0): bootstrap Windows PDF viewer and device validation"`: 성공, `0863969ace0b00f2bd01aebf0539654dd3d64e8e`.
- `git push origin main`: 성공, `0aa65d3..0863969 main -> main`.
- `gh run list --repo AtraFelis/ebook_viewer --commit 0863969ace0b00f2bd01aebf0539654dd3d64e8e`: 실행 `38885324826` 확인.

[Android checks 실행](https://github.com/AtraFelis/ebook_viewer/actions/runs/38885324826)은 **success**로 완료했다. 대상 headSha가 `0863969ace0b00f2bd01aebf0539654dd3d64e8e`와 일치함을 API 결과로 확인했다. check job 3분35초, Gradle 검사 2분59초.

- 새 windows-latest runner의 checkout 및 고정 JDK/SDK 설치·체크섬 검증·SDK 설치 목록/라이선스 처리 성공.
- Wrapper JAR SHA-256 검증 성공.
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain`: 실제 실행, BUILD SUCCESSFUL.
- 원격 CI에는 기기를 연결하지 않았으므로 기기11개/성능 결과는 기존 로컬 실기기 기록을 따른다. 새 CI의 CLI 도구 복원 검증을 실제 새 PC의 드라이버/Android Studio/기기 연결 복원으로 확대하지 않는다.
- GitHub의 checkout@v4 Node 20 선언에 대해 Node 24로 실행을 대체했다는 권고 annotation이 있었다. 실제 단계는 성공했으며 숨기거나 검사를 완화하지 않았다. 후속 CI 유지보수 시 action 버전 갱신을 검토한다.

원본 상태/전체 로그는 Git 제외 `local-artifacts/bootstrap/github-ci-result.json`, `github-ci-full.log`, `github-ci-watch.log`에 보존했다. 코드 검증 후 결과 문서만 추가 커밋한다. Markdown만 변경한 커밋은 기존 paths-ignore 정책에 따라 앱 CI를 다시 실행하지 않으며, 위 source commit의 실제 성공 결과를 근거로 삼는다.

판정: 사용자가 정한 현재 개인용 API 33·합성 자료 범위의 P0 기술 검증과 원격 CI 완료. P1 기능 보완, 빠른 이동의 미준비 영역, 손입력/개인 PDF 수용 확인과 다른 API/기기는 후속이다. 새 런타임 의존성/릴리스 승인은 별도 규칙을 유지한다.
