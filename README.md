# Android Ebook Viewer

PDF를 하나의 긴 문서처럼 읽는 Android 전자책 뷰어입니다. 핵심은 페이지 경계에서 끊기지 않는 연속 스크롤과 확대 상태 유지입니다.

P0 타일 렌더링 프로토타입과 검증된 Gradle Wrapper가 있습니다. Windows 로컬 debug 빌드·단위 테스트·Lint·ktlint는 통과했으며, API 33 기기 실행·기기 테스트 11개도 통과했습니다. 현재 API 33 개인용·합성 자료의 로컬 기술 검증을 마쳤으며 새 Windows 환경의 원격 CI도 통과했습니다. 다른 API는 미검증입니다. [Windows 설치와 실행](docs/windows-development.md)을 참고하세요.

최근/읽던 PDF 목록에서 마지막 페이지 내부 위치·줌·가로 위치를 복원하며 항목을 길게 누르면 원본을 유지하고 기록만 삭제합니다([이어 읽기 결과](docs/history/2026-10-17-01-p1-reading-library.md)). 읽는 중에는 전체 화면을 유지하고 중앙 탭으로 설정/목록 도구를 엽니다. 설정에서 가로·세로 고정/자동을 선택할 수 있습니다. API 33 계측 19개를 통과했습니다([후속 결과](docs/history/2026-10-17-03-immersive-reading.md)). 간격/크기 모드·파일/오류 UX 등은 다음 범위이며 P1 전체 완료는 아닙니다.

확대 상한은 이전보다 4배 높였으며 과도한 축소를 제한합니다. 확대 상태의 회전/이어 읽기를 포함해 현재 API 33 계측 20개·단위 12개를 통과했습니다([배율 조정 결과](docs/history/2026-10-17-04-zoom-range.md)).

설정 → 페이지 외부 배경색에서 흰색/검정/회색/종이색 또는 임의 #RRGGBB를 선택할 수 있습니다. 읽기 위치·줌·PDF 픽셀을 유지하며 앱 재실행에도 색상을 보존합니다. 단위 15개·API 33 계측 23개 통과([결과](docs/history/2026-10-17-05-custom-background.md)). 자동 배경색과 [스크롤 선명도 복구 지연 개선](docs/plan/p2-sharpness-recovery.md)은 P2로 이관했습니다.

중앙 탭 도구에서 페이지 이동과 북마크를 사용할 수 있습니다. 북마크 목록은 누르면 이동, 길게 누르면 삭제합니다. 페이지 번호는 설정의 ‘읽는 동안 페이지 번호 표시’로 켜고 끄며 선택을 재실행에도 유지합니다(기본 숨김). 이동해도 현재 줌과 유효한 가로 위치를 유지합니다. 최종 단위 19개·API 33 계측 27개를 통과했습니다. [페이지 이동/번호 설정 결과](docs/history/2026-10-17-06-navigation-bookmarks.md).

## 시작하기

1. [AGENTS.md](AGENTS.md): 기술·개발 계약
2. [SESSION.md](SESSION.md): 현재 상태와 다음 작업
3. [CHECKLIST.md](CHECKLIST.md): 완료 기준
4. [제품 기획](docs/product/requirements.md)
5. [문서 목차](docs/README.md)

평소에는 클라우드에서 작업합니다. 사용자가 PC 작업 시작을 알릴 때만 로컬 변경을 확인하고 pull합니다. Windows 경로는 `C:\Users\cotjs\workspace\ebook_viewer`입니다. 클라우드에서 이 경로에 직접 접근할 수 있다고 가정하지 않습니다.
