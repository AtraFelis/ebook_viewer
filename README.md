# Android Ebook Viewer

PDF를 하나의 긴 문서처럼 읽는 Android 전자책 뷰어입니다. 핵심은 페이지 경계에서 끊기지 않는 연속 스크롤과 확대 상태 유지입니다.

P0 타일 렌더링 프로토타입과 검증된 Gradle Wrapper가 있습니다. Windows 로컬 debug 빌드·단위 테스트·Lint·ktlint는 통과했으며, API 33 기기 실행·기기 테스트 11개도 통과했습니다. 현재 API 33 개인용·합성 자료의 로컬 기술 검증을 마쳤으며 원격 CI는 main push 승인 후 실행할 예정입니다. 다른 API는 미검증입니다. [Windows 설치와 실행](docs/windows-development.md)을 참고하세요.

## 시작하기

1. [AGENTS.md](AGENTS.md): 기술·개발 계약
2. [SESSION.md](SESSION.md): 현재 상태와 다음 작업
3. [CHECKLIST.md](CHECKLIST.md): 완료 기준
4. [제품 기획](docs/product/requirements.md)
5. [문서 목차](docs/README.md)

평소에는 클라우드에서 작업합니다. 사용자가 PC 작업 시작을 알릴 때만 로컬 변경을 확인하고 pull합니다. Windows 경로는 `C:\Users\cotjs\workspace\ebook_viewer`입니다. 클라우드에서 이 경로에 직접 접근할 수 있다고 가정하지 않습니다.
