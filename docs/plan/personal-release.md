# 개인용 정식 APK 준비

> 2026-10-29 상태: 합의한 구현/검증과 사용자 수용·병합은 완료했다. 현재 완료 범위·보류/제외는 [로드맵](roadmap.md), 실제 검증 한계는 [최신 정리](../history/2026-10-29-01-documentation-and-cleanup.md)를 따른다. 아래는 당시 계획/진행 기록이며 ‘다음·대기·미게시’ 문구나 포괄 검사 조건을 현재 미완료 필수 작업으로 재개하지 않는다. 모든 기기/API·전체 검사·공개 배포 완료를 뜻하지 않는다.


2026-10-24 완료 상태: 로컬0.1.2/code4 APK의 서명·정식 ID/버전과 이전 버전의 동일 인증서를 확인했다. 사용자가 현재 정식판 설치와 키 백업을 완료했다고 확인했다. 로컬 main은 PR #33 병합69931e9로 동기화했고 사용자 디자인/버전을 보존했다. [실제 근거와 제한](../history/2026-10-24-10-main-sync-and-release-closeout.md)을 따른다. 개인용 준비는 완료이며 코드 LICENSE/스토어 공개 배포는 별도 보류다. 아래는 준비 당시의 승인 범위/절차를 보존한다.

2026-10-22 사용자 승인: 배포 준비를 진행하고 정식판은 기록/설정을 새로 시작한다. 코드 라이선스는 앱 사용 허가와 구분한 뒤 보류하기로 했다. 현재 체크아웃 main973f784 기반 codex/release-preparation을 사용하고 사용자 docs/design.md 및 기존 상태 기록을 보존한다.

## 구성과 종료 기준

- release applicationId는 기존 namespace와 같은 dev.example.ebookviewer로 정하고, debug/profile은 .p0 suffix로 기존 dev.example.ebookviewer.p0를 유지한다. 기존 개발용 앱 삭제/교체/데이터 읽기·이전은 하지 않는다. 정식판은0.1.0/versionCode2부터 시작하며 이후 APK마다 code를 증가시킨다. debug/profile은 -dev/-profile 표기로 구분한다.
- release는 사용자 전용 PKCS12 키를 명시할 때만 서명한다. 키·비밀번호를 저장소/명령 인자/공개 로그에 쓰지 않는다. 전용 스크립트는 로컬 SecureString 입력을 받고 저장소 밖 키 파일을 생성/읽는다. 기존 키 덮어쓰기 금지, 비밀번호 누락 시 signed 요청 실패, debug 키 fallback 금지다. 서명용 빌드는 no-daemon/no-configuration-cache/in-process Kotlin으로 제한한다.
- 실제 개인용 키의 비밀번호는 사용자가 로컬 터미널에서 직접 입력한다. 그 전에 signed 경로는 임시 검증용 키로만 확인하고 이 APK는 설치/배포하지 않는다. unsigned release 빌드는 CI에서도 점검하며 개인용 서명 APK 생성/설치/공개 게시 완료로 보고하지 않는다.
- strict debug/계측/단위/Lint/ktlint와 unsigned release/Lint, 실제 APK applicationId·버전·debuggable·고지·아이콘, signed의 올바른 키/잘못된 비밀번호/누락된 설정/기존 키 보호를 확인한다. 스크립트 검증은 의미 있는 서명 실패/데이터 보존 경로에 한정한다. 전체 기기 검사/20분/성능 측정·스토어 업로드는 하지 않는다.
- 라이선스 파일을 임의로 추가하지 않는다. 신규 런타임 의존성/키 Git 저장/서명 검사 우회/기존 앱 제거는 없다. 계획·ADR·SESSION·CHECKLIST/history·설치/업데이트 절차를 정리해 PR로 게시한다. 실제 개인용 키 생성은 구현/검증이 끝난 로컬 절차에서 사용자가 진행하고 보관/백업 위치도 확인한다.
