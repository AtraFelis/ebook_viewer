# 2026-10-22 — 개인용 정식판 빌드·서명 준비

## 승인과 보존

사용자가 배포 준비를 승인한 뒤 정식판은 새 기록/북마크/설정으로 시작하고 프로젝트 코드 라이선스는 보류하기로 했다. MIT의 수정·재배포/판매 허가와 앱 사용을 구분해 설명했으며 LICENSE를 추가하지 않았다. AGENTS→SESSION→CHECKLIST→배포 문서/Git 규칙을 읽고 git status/branch/remote·fetch를 확인했다. main973f784와 일치하는 현재 체크아웃에서 codex/release-preparation을 만들었다. 기존 상태 문서 변경과 사용자 docs/design.md128줄/SHA-256 28ef0cdc82d5c8fddb5bb6f1a9693e6b09299892224983522e5ae0fa9e759681 변경을 보존하고 후자는 커밋 제외한다. 기존 앱/파일/기록은 읽거나 제거하지 않았다.

## 구성과 자료 확인

release ID dev.example.ebookviewer는 기존 namespace와 같은 값이며 사용자 도메인을 새로 만들지 않았다. debug/profile .p0 suffix로 기존 ID/계측·provider 호환성을 유지하고 별도 정식판에 새 SAF 권한/기록을 사용한다. gradle.properties에 versionName0.1.0/code2를 모았고 debug -dev/profile -profile을 붙였다. release 표시 이름만 Ebook Viewer다. 새 런타임 의존성/저장 형식/렌더러 변경은 없다.

Context7 resolve/query는 사용 가능했지만 버전별 AGP8.7.2 인덱스는 제공되지 않았다. 공식 Android build signing/build-variants 자료를 받은 뒤 [AGP8.7 ApkSigningConfig](https://developer.android.com/reference/tools/gradle-api/8.7/com/android/build/api/dsl/ApkSigningConfig), [서명](https://developer.android.com/studio/publish/app-signing), [버전](https://developer.android.com/studio/publish/versioning), [JDK17 keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)과 실제 고정8.7.2/8.9/JDK17 빌드로 교차 확인했다. 자료 예제의 평문 비밀번호는 채택하지 않았다. [MIT 원문](https://opensource.org/license/mit)은 라이선스 설명 근거이며 프로젝트 라이선스로 적용하지 않았다.

명시적 releaseSigning=true와4개 EBOOK_RELEASE 환경변수가 모두 있을 때만 PKCS12 signingConfig를 쓴다. 자격이 없거나 파일이 없으면 요청을 실패시키고 debug 키로 대체하지 않는다. 기본 release는 unsigned다. scripts/build-personal-release.ps1은 저장소 밖 기존 키/비밀번호/alias를 먼저 검사하고 성공한 signed APK의 서명/실제 ID·버전/debuggable을 확인한 뒤만 내보낸다. 기존 키/같은 버전의 다른 산출물을 덮지 않으며 키 생성의 마지막 파일 이동/출력 복사에도 no-replace를 적용한다. 비밀번호는 SecureString→프로세스 환경으로만 넘기고 finally에서 환경을 복원한다. 서명 Gradle은 no-daemon/no-configuration-cache/Kotlin in-process다. 실제 개인용 키는 자동 생성하지 않았다.

## 검증과 수정된 실패

1. `. ./scripts/android-env.ps1` 후 strict debug/계측/단위/양 Lint/ktlint·unsigned release23초 성공. 버전 단일 관리와 profile 확인을 더한 최종 `:app:assembleDebug :app:assembleDebugAndroidTest :app:assembleProfile :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:lintRelease ktlintCheck --dependency-verification strict --console=plain`은 **17초 성공**. 단위53개는 UP-TO-DATE/기존 XML 실패0이며 새 실행으로 세지 않는다. Lint debug 오류0/경고0/정보6, release0/0/정보4다.
2. scripts/check-build-identities.ps1이 실제 APK의 개발용 ID/version0.1.0-dev/code2/debuggable=true와 정식 ID/0.1.0/code2/debuggable=false를 확인했다. profile도 기존 ID/0.1.0-profile/code2/nondebuggable을 aapt2로 별도 확인했다. unsigned release의 고지 assets 바이트 일치, PDF/키/원본 SVG·PNG 미포함을 확인했다.
3. 첫 임시 키 검증은 PowerShell이 따옴표 없는 -Pkotlin.compiler.execution.strategy를 분리해 Gradle이 .compiler.execution.strategy 작업을 찾는 오류로 실패했다. 인자를 인용해 수정했다. 다음에는 서명/검증은 성공했으나 환경 복원 검사가 실패했다. 실제 null SetEnvironmentVariable 호출이 ‘없음’ 대신 빈 문자열을 남기는 것을 비밀값 없는 probe로 재현하고 원래 없던 변수는 Env:에서 제거하도록 수정했다.
4. 이후 서명 검증 단계는 통과했으나 마지막 예상된 missing-credentials 실패의 LASTEXITCODE1이 호출자에게 남았다. 종료 코드를 바로잡고 마지막에 unsigned release를 복원/검증하는 경로를 추가했다. 최종 `./scripts/test-release-signing.ps1`은 **exit0**, 내부 성공 빌드7/7/7/8초였다. 성공 재시도만으로 앞선 실패를 지우지 않으며 로그는 ignored local-artifacts/personal-release-*.txt에 분리했다.
5. 최종 검증은 OS-temp의 새 일회용 PKCS12 키 생성·재사용·서명/ID 검사, 기존 키 덮어쓰기 거부/바이트 보존, 잘못된 비밀번호의 빌드 전 거부, 저장소 안 키 경로 거부, 같은 버전의 다른 기존 APK 보존, 비밀 환경의 원래 상태 복구, 서명 요청 자격 누락의 Gradle 실패를 확인했다. 마지막에는 기본 출력을 app-release-unsigned.apk로 복원했다. 임시 키는 정확한 경로만 삭제했고 해당 임시 디렉터리0을 확인했다. 검증용 APK는 ignored verification 경로에만 남기고 설치/배포하지 않는다.

휴대폰 연결이 필요한지 묻는 사용자에게 지금 PC 검사에는 필요 없고 나중에 실제 정식판 설치 시 연결하면 된다고 답했다. ADB프로세스0을 확인했으며 앱 설치/실행/기기 검사/20분 측정은 하지 않았다. 실제 개인용 키/비밀번호를 읽거나 만들지 않았고 키·비밀번호를 Git/공개 로그/명령 인자에 넣지 않았다. 빌드 검증을 실제 개인용 서명·기기 성능/수용 완료로 확대하지 않는다.

## 남은 사용자 단계

게시: 5b90164c0e2e838a9137cf6c82d15606cbf01620 일반 push·PR #30 OPEN/base main 생성/채팅 첨부. CI39735181505/39735168394 IN_PROGRESS이며 수동 요청6334472989 한 번에 공식 봇6334474290 Review triggered·요약6334469491 실제21파일 처리 중을 확인했다. 완료 리뷰 API는 빈 배열이라 처리 중과 완료를 구분한다. 상태 기록은 로컬에 모으며 리뷰 처리 중 상태 기록만의 재push/중복 호출/자동 예약/에이전트 병합은 하지 않는다. 사용자 디자인128줄 변경만 커밋 제외했고 기존 앱·휴대폰·실제 개인 키는 사용하지 않았다.

[개인용 정식판 절차](../personal-release.md)의 build-personal-release.ps1 -CreateKey를 사용자가 로컬에서 실행하고 마스킹 비밀번호를 입력해야 한다. 실제 키의 별도 백업과 해당 키의 APK·기기 설치/기능 수용은 남아 있다. 현재 설치된 개발용 앱/기록은 그대로다. 프로젝트 코드 LICENSE/공개 GitHub Release/스토어 배포는 보류·별도이며 이번 PR 생성/CI/실제 CodeRabbit/사용자 병합도 검증 기록과 별도로 확인한다.
