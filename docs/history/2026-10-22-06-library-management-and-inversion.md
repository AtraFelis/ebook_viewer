# PDF 색반전·여러 파일 추가·책장 관리

## 요청과 기준

사용자 요청: PDF 페이지 색반전(외부 배경 보존/읽기 화면 빠른 접근), SAF 여러 파일 추가, 문서 순서·다중 선택 삭제·사용자 책장. 시작 체크아웃 work/d850b15는 변경이 없었고 GitHub main/b6df772를 ff-only로 가져왔다. 초기 수정은 work에서 시작했고 사용자 브랜치 확인 직후 모든 변경을 보존해 `feat/pdf-inversion-and-bookshelves`로 분리했다. worktree는 만들지 않았다.

## 변경

- PDF paint RGB 필터/토글·별도 v1 설정. 원본 타일/외부 자동/수동 배경색·줌/캐시 유지.
- SAF ClipData/data 병합·URI 중복 제거·한 번의 일괄 등록. 선택 책장에 신규 등록, 기존 문서 기록/배치 보존. 추가/기존/실패/임시 권한 수 안내. 재연결은 한 파일 선택 유지.
- 책장 추가/이름 변경/삭제, 전체/미분류/책장 필터. 관리 모드 체크박스·전체 선택/해제·일괄 책장 이동/등록 삭제·위/아래 순서 이동. 원본 PDF 보존. 관리 선택/필터 Activity 상태 복원.
- v5 catalog에 문서·책장·소속·순서를 같은 AtomicFile로 저장. v1~v4 읽기/원본 앵커·layout 보존, 위치 자동 저장 시 소속/순서 병합. 미지원/손상 파일 덮어쓰기 방지 유지. ADR0019/계획/기획/계약·인수인계 갱신.
- 새 런타임 의존성 없음. Linux 빌드용 기존 aapt2 동일 버전 classifier 체크섬만 검증 메타데이터에 추가.

## 도구와 실제 실행

클라우드의 기존 Java는 JRE21이었다. 저장소 밖 `/workspace/android-tools`에 기존 승인 버전 Temurin17.0.20.1+1 Linux, Gradle8.9, SDK35_r02/Build Tools34.0.0을 준비했다. JDK/Gradle/SDK ZIP SHA-256은 공식 배포/기존 잠금 값으로 대조했다. Linux Build Tools/platform-tools는 공식 SDK repository XML의 SHA-1과 대조했다. SDK 라이선스 자동 수락이나 검증 우회는 하지 않았다.

기존 verification-metadata는 Windows aapt2만 포함해 첫 Linux strict 빌드는 해당 classifier 검증에서 실패했다. Google Maven의 동일 버전 Linux JAR 공식 SHA-1을 받은 뒤 실물과 대조하고 SHA-256 `59dc25b21fcb8f367c3ba10708779e87f3a4be3822ef1a138e8b0e362b623970`을 추가했다. lockfile/런타임 버전 변경 없음.

Wrapper JAR SHA-256은 `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17` 일치. 첫 Wrapper 실행은 JVM 프록시 설정 누락으로 UnknownHostException이 발생했다. JAVA_OPTS에 프록시·시스템 CA trustStore를 전달해 정상 다운로드/배포 체크섬 검증했다. 플랫폼 네트워크 정책은 우회하지 않았다.

최종 검증 명령(작업 디렉터리 `/workspace/ebook_viewer`):

```bash
JAVA_HOME=/workspace/android-tools/jdk-17.0.20.1+1 \
ANDROID_HOME=/workspace/android-tools/sdk \
GRADLE_USER_HOME=/workspace/android-tools/gradle-home \
JAVA_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
bash ./gradlew ktlintFormat :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:lintRelease \
  ktlintCheck --dependency-verification strict --console=plain \
  -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 \
  -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 \
  -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts
```

ktlintFormat을 명시적으로 실행했다. 중간 스타일 검사 실패는 작성 중 새 계측 파일 포맷을 포함하지 않은 시점이었다. Lint는 관리 UI에서 제거된 문자열2개를 찾아 삭제했다. 최종 정리 중 SuspiciousIndentation 3개 보고가 있어 빈 목록 조건을 명시적 블록으로 보완하고 변경을 고정해 다시 검사했다. 중간 실패를 최종 통과로 숨기지 않는다.

단위 69개(기존59+추가10)의 실패/오류0을 XML로 확인했다. 기존 바이트 손상 테스트는 실제 v4 포맷 fixture를 독립 생성해 v5 부가 메타데이터 때문에 원래 검증 대상을 잃지 않도록 수정했다.

## 기기 검증과 한계

`adb devices -l`: 연결된 기기 없음. 조회 후 `adb kill-server`로 종료했다. `/dev/kvm`도 없으며 기존 Windows 실제 기기에 접근하지 않았다. 새 계측3개는 작성/빌드하며 실행 통과로 세지 않는다. 기본 runner 선언을 세어 기존91+신규3=94 기대값으로 host 스크립트를 맞췄다. 전체94개 실행·큰 글꼴/가로 UI·실제 SAF provider·재실행·성능·사용자 수용은 미실행이다. 기존 P3 결과는 당시 APK의 이력이다.

로컬에서 새 계측을 개별 실행할 때는 `methodClass=libraryFeatures`와 아래 method를 사용한다(기존 P0Instrumentation):

- `inversionChangesOnlyPagePixelsWithoutRerenderOrViewportReset`
- `clipDataBatchDeduplicatesAndCatalogReloadKeepsOrganization`
- `managementSelectsMultipleWithoutOpeningAndRestoresSelection`

등록은 PDF 유효성 전체 검사나 파일 이동이 아니다. 문서를 실제 열 때 기존 손상/암호화/권한 오류 처리를 사용한다. v5 기록을 쓴 뒤 구버전 앱으로 내려가는 데이터 호환은 제공하지 않는다.

PR/원격 CI/CodeRabbit/기기 배포는 이번 로컬 구현 검증에 포함하지 않았다. 변경은 전용 브랜치에서 보존하며 원격 main을 수정하지 않았다. 최종 명령과 문서 검사 결과는 아래에 기록한다.

## 최종 결과

- Wrapper 최종 명령: BUILD SUCCESSFUL, 57초. debug/계측/unsigned release 빌드, 양 Lint, ktlint 통과.
- 단위 XML: {'tests': 69, 'failures': 0, 'errors': 0, 'skipped': 0}. 이번 최종 명령에서 testDebugUnitTest 실제 실행.
- debug Lint: 오류/경고/정보 0.
- release Lint: 오류/경고/정보 0.
- app/build/outputs/apk/debug/app-debug.apk: SHA-256 `cf894fd2ab4fc494d3efbc1c09a69bc58cd4053ef734f9ca42e08ad07ae2a0ed`. 설치/배포는 미실행.
- app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk: SHA-256 `e009d7c7e36da8d11b04710e31a528e16d0c39be0ee418ca9d14c2afbcba983b`. 설치/배포는 미실행.
- app/build/outputs/apk/release/app-release-unsigned.apk: SHA-256 `6c05686ac5bdd9829dd7e05dae6089adac488c57ffca521391c2a984a5d220be`. 설치/배포는 미실행.
- Markdown 상대 링크397개/누락0·후행 공백0, git diff --check 통과.
- Git: feat/pdf-inversion-and-bookshelves 로컬 미커밋·미푸시. 원격 main 변경 없음.

## 후속 사용자 요청

사용자가 PR 게시·CodeRabbit 실제 리뷰 확인과 타당한 지적 수정을 요청했다. 위 미커밋/미푸시/리뷰 미실행은 초기 구현 종료 시점이며, 후속 결과는 별도 게시/리뷰 이력에 기록한다.
