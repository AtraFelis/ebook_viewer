# ADR 0017 — 개인용 정식판 식별자와 로컬 서명

2026-10-22. 사용자는 정식판을 새 기록/설정으로 시작하고 소스 코드 라이선스는 보류하기로 했다. 기존 개발용 앱/데이터를 삭제하지 않기 위해 정식판을 병행 설치한다.

## 결정

namespace dev.example.ebookviewer는 유지하고 release applicationId를 같은 값으로 사용한다. debug/profile에는 .p0 suffix를 붙여 기존 dev.example.ebookviewer.p0와 계측/provider 식별자를 유지한다. 사용자 도메인을 새로 만들지 않는다. 정식판 이름은 Ebook Viewer이고 기존 개발용 이름은 유지한다. 기본 versionName0.1.0/versionCode2, debug -dev/profile -profile이다. 새 정식 APK마다 code를 증가시키며 다운그레이드 우회/기존 앱 제거는 하지 않는다.

명시적 -PreleaseSigning=true와 EBOOK_RELEASE_STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD가 모두 있을 때만 release signingConfig를 연결한다. 기본 release는 검증용 unsigned다. 서명 요청의 빈 값/없는 파일은 실패하며 debug signingConfig로 대체하지 않는다. 비밀값은 로컬 SecureString 입력 후 해당 프로세스의 환경변수로만 전달하고 finally에서 원래 환경을 복원한다. Gradle에는 비밀값 인자/파일을 넘기지 않으며 서명 빌드는 no-daemon/no-configuration-cache/in-process Kotlin을 사용한다.

PKCS12/RSA3072/SHA256withRSA·10000일의 키 생성 절차를 제공한다. 키 경로는 저장소 밖이며 기존 파일은 덮어쓰지 않는다. 사용자가 키/비밀번호를 직접 정하고 오프라인 백업한다. 이 작업에서 실제 개인용 키를 자동 생성하거나 비밀번호를 채팅으로 받지 않는다. 검증용 임시 키/산출물은 개인 배포물과 분리하고 실제 개인용 release 완료로 세지 않는다.

업데이트는 동일 release ID/키와 증가한 versionCode의 APK를 직접 설치한다. 최초 정식판에서는 PDF를 SAF로 다시 선택해 권한을 부여한다. 개발용 기록/권한/설정을 자동 복사하지 않으며 기존 앱은 남아 있다. 프로젝트 LICENSE는 보류하고 제3자 고지는 계속 포함한다. 이 결정은 공개 배포/스토어 업로드/법률 적합성 보장이 아니다.
