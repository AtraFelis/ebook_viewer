# Android Ebook Viewer

PDF를 하나의 긴 문서처럼 읽는 개인용 Android 뷰어입니다. 페이지 경계를 넘어도 확대 배율과 읽기 위치를 유지하며, 기본 읽기에 계정이나 외부 서비스가 필요하지 않습니다.

2026-10-29 기준: P0 기술 검증, P1 MVP, 합의한 P2·P3와 후속 책장 UI를 완료했습니다. 책장 개편 PR #33과 완료 문서 PR #34가 병합됐습니다. 개인용 정식판 설치·서명 키 백업은 사용자가 확인했습니다. [현재 상태와 제한](SESSION.md), [로드맵](docs/plan/roadmap.md)을 참고하세요.

## 제공하는 기능

- 연속 세로 스크롤, 핀치 확대·축소, 문서별 크기·간격과 원본을 보존하는 여백 자르기.
- 한 손가락 가로 이동 잠금. 잠금 중에도 두 손가락 이동과 확대·축소가 가능합니다.
- 읽기 위치·줌 이어 읽기, 북마크, 페이지 슬라이더, 선택 가능한 하단 중앙 페이지 번호.
- 전체 화면과 중앙 탭 도구, 제목 옆 방향 드롭다운, 자동·세로·가로·두 역방향 고정.
- 보기 설정의 자동/수동 외부 배경색, PDF 색반전, 읽기 밝기와 화면 켜짐 유지. 일반 설정은 별도로 제공합니다.
- SAF 다중 PDF 추가, 표지 책장 격자, 문서 격자/목록, 이름순·최근 읽은 순과 책장 사용자 순서.
- 같은 문서의 여러 책장 소속, 선택 이동·등록 삭제. 원본 PDF 파일은 변경하거나 삭제하지 않습니다.
- 지원되는 플랫폼의 현재 페이지 본문 읽기. 스캔 이미지 OCR과 문서 전체 검색은 제공하지 않습니다.

검색·선택/복사 UI는 후속 보류이며 목차·비밀번호 PDF 읽기·EPUB·동기화는 현재 필수 범위에 포함하지 않습니다. 검증 대상은 개인 API 33 기기와 합성 자료입니다. minSdk 23 선언이 다른 API의 기기 검증 완료를 뜻하지 않습니다.

## 빌드와 설치

Windows PowerShell 7, 작업 경로는 `C:\Users\cotjs\workspace\ebook_viewer`입니다. 도구 최초 설치는 [Windows 개발 환경](docs/windows-development.md)을 따릅니다.

```powershell
Set-Location 'C:\Users\cotjs\workspace\ebook_viewer'
. ./scripts/android-env.ps1
./gradlew.bat :app:assembleDebug --dependency-verification strict
```

개인용 정식 APK는 [빌드·업데이트 안내](docs/personal-release.md)를 따릅니다. 기존 서명 키를 재사용하고 새 APK마다 `versionCode`를 올립니다. 개발용 PDF Viewer와 정식판 Ebook Viewer는 별도 앱입니다. 코드 LICENSE와 스토어·공개 배포는 보류했습니다.

## 개발 문서

작업 전 [AGENTS](AGENTS.md) → [SESSION](SESSION.md) → [CHECKLIST](CHECKLIST.md) → 해당 계획을 읽습니다. [문서 목차](docs/README.md)에서 기능 계약·결정·검증 이력을 찾을 수 있습니다. 현재 상태는 SESSION, 실제 과거 실행 결과는 docs/history에 기록하며 테스트 수를 서로 다른 버전 사이에서 합산하지 않습니다.

빌드 캐시와 개인 산출물의 구분은 [프로젝트 파일 정리](docs/maintenance.md)를 참고하세요. 사용자 디자인·로컬 버전·서명 키·APK·검증 근거는 정리 대상으로 취급하지 않습니다.
