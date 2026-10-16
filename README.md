# Android Ebook Viewer

PDF를 하나의 긴 문서처럼 읽는 Android 전자책 뷰어입니다. 핵심은 페이지 경계에서 끊기지 않는 연속 스크롤과 확대 상태 유지입니다.

P0 타일 렌더링 프로토타입과 검증된 Gradle Wrapper가 있습니다. Windows 로컬 debug 빌드·단위 테스트·Lint·ktlint는 통과했으며, API 33 기기 실행·기기 테스트 11개도 통과했습니다. 현재 API 33 개인용·합성 자료의 로컬 기술 검증을 마쳤으며 새 Windows 환경의 원격 CI도 통과했습니다. 다른 API는 미검증입니다. [Windows 설치와 실행](docs/windows-development.md)을 참고하세요.

최근/읽던 PDF 목록에서 마지막 페이지 내부 위치·줌·가로 위치를 복원하며 항목을 길게 누르면 원본을 유지하고 기록만 삭제합니다([이어 읽기 결과](docs/history/2026-10-17-01-p1-reading-library.md)). 읽는 중에는 전체 화면을 유지하고 중앙 탭으로 설정/목록 도구를 엽니다. 설정에서 가로·세로 고정/자동을 선택할 수 있습니다. API 33 계측 19개를 통과했습니다([후속 결과](docs/history/2026-10-17-03-immersive-reading.md)). 배경색·페이지 이동/북마크 등은 다음 범위이며 P1 전체 완료는 아닙니다.

## 시작하기

1. [AGENTS.md](AGENTS.md): 기술·개발 계약
2. [SESSION.md](SESSION.md): 현재 상태와 다음 작업
3. [CHECKLIST.md](CHECKLIST.md): 완료 기준
4. [제품 기획](docs/product/requirements.md)
5. [문서 목차](docs/README.md)

평소에는 클라우드에서 작업합니다. 사용자가 PC 작업 시작을 알릴 때만 로컬 변경을 확인하고 pull합니다. Windows 경로는 `C:\Users\cotjs\workspace\ebook_viewer`입니다. 클라우드에서 이 경로에 직접 접근할 수 있다고 가정하지 않습니다.
