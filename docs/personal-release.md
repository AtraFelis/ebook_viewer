# 개인용 정식판 만들기와 업데이트

2026-10-22. 사용자는 정식판의 읽기 기록/북마크/설정을 새로 시작하고 코드 공개 라이선스는 보류하기로 했다. 현재 단계는 빌드/로컬 서명 절차 준비다. 실제 개인용 키·설치·기기 수용/공개 배포는 아직 완료하지 않았다. [계획](plan/personal-release.md)·[ADR0017](adr/0017-personal-release-identity-and-signing.md).

## 기존 앱과 정식판

개발용 debug/profile은 기존 `dev.example.ebookviewer.p0`를 유지한다. 정식판은 `dev.example.ebookviewer`, 표시 이름 `Ebook Viewer`로 병행 설치한다. 기존 앱을 삭제하거나 그 앱의 기록/권한을 복사하지 않는다. 정식판에서 PDF를 처음 열 때 직접 다시 선택하면 새 앱에 SAF 접근 권한이 부여된다.

버전은 [gradle.properties](../gradle.properties)의 `ebook.versionName=0.1.0`, `ebook.versionCode=2`다. debug는 `-dev`, profile은 `-profile`이 붙는다. 업데이트용 APK를 내보낼 때마다 versionCode를 증가시키고 필요하면 표시 버전도 올린다. 기존 설치보다 낮은 버전을 우회 설치하거나 같은 번호의 다른 APK를 덮어쓰지 않는다.

## 최초 개인용 키 생성과 APK 만들기

이 단계만 사용자가 Windows 로컬 터미널에서 직접 진행한다. 비밀번호를 채팅으로 보내거나 명령 인자에 평문으로 쓰지 않는다. PowerShell 7에서 다음을 실행한다.

```powershell
Set-Location 'C:\Users\cotjs\workspace\ebook_viewer'
./scripts/build-personal-release.ps1 -CreateKey
```

12자 이상의 새 비밀번호를 마스킹된 입력창에 두 번 입력한다. 키는 `%USERPROFILE%\.android\ebook-viewer-release.p12`에 생성된다. PKCS12/RSA3072/SHA256withRSA, alias `ebook-viewer`, 유효기간10000일이며 기존 파일은 덮어쓰지 않는다. 저장소 안의 키 경로도 거부한다. 다른 위치를 쓰려면 `-KeystorePath '저장소 밖의 절대 경로.p12'`를 함께 지정한다.

스크립트는 strict release 빌드/Lint·서명·applicationId/버전/debuggable 검사를 통과한 APK만 다음 위치로 복사하고 SHA-256을 표시한다.

```text
C:\Users\cotjs\workspace\ebook_viewer\local-artifacts\releases\ebook-viewer-0.1.0-vc2.apk
```

키 파일과 비밀번호는 서로 다른 안전한 곳에 백업한다. 키를 잃으면 같은 정식 앱의 후속 업데이트를 서명할 수 없다. `.p12`/비밀번호/로컬 빌드 결과는 Git에 올리지 않는다. 스크립트는 설치·앱 삭제·GitHub Release/스토어 업로드를 하지 않는다.

비밀번호는 일시적인 프로세스 환경변수로 전달하고 종료 시 원래 환경으로 복구한다. Gradle 비밀값 인자/설정 파일은 쓰지 않으며 no-daemon/no-configuration-cache/Kotlin in-process로 빌드한다. 메모리의 모든 사본을 지운다고 보장하는 기능은 아니므로 이 서명 세션에서 환경 전체 출력·디버그 로그·build scan을 사용하지 않는다.

## 다음 버전 만들기

`gradle.properties`에서 versionCode를 증가시킨 후 **같은 키 파일과 비밀번호**로 실행한다. 두 번째부터 `-CreateKey`를 붙이지 않는다.

```powershell
./scripts/build-personal-release.ps1
```

다른 키 경로를 사용했다면 같은 `-KeystorePath`를 다시 지정한다. 키/비밀번호/alias 확인 실패 시 빌드를 시작하지 않는다. 해당 버전의 출력 파일이 이미 있고 내용이 다르면 교체하지 않는다. APK와 키의 백업은 별도이며 APK를 보관하는 것만으로 서명 키를 복원할 수는 없다.

## 설치와 후속 검증

실제 개인용 키로 만든 APK가 준비된 뒤 휴대폰을 연결한다. 기존 개발용 앱은 그대로 둔다. PowerShell에서 설치한 다음 직접 평소 PDF의 열기·확대/스크롤·설정·이어 읽기를 확인한다.

```powershell
. ./scripts/android-env.ps1
adb install --no-streaming -r ./local-artifacts/releases/ebook-viewer-0.1.0-vc2.apk
adb shell am start -W -n dev.example.ebookviewer/dev.example.ebookviewer.MainActivity
```

다음 업데이트는 증가한 버전의 APK 경로로 바꾼다. 서명 불일치/다운그레이드/설치 오류가 나면 중지하고 진단하며 앱 삭제·데이터 초기화·서명 우회로 해결하지 않는다. 확인을 마치면 필요에 따라 아래처럼 새 앱과 ADB만 중지한다.

```powershell
adb shell am force-stop dev.example.ebookviewer
adb kill-server
```

## 개발/CI 산출물과 구분

일반 `:app:assembleRelease`는 `app-release-unsigned.apk`를 만든다. CI는 이 unsigned APK와 개발용 APK의 서로 다른 식별자/버전/debuggable을 검사하며 키 없이 실행한다. unsigned APK는 설치/배포용 완성본이 아니다.

`./scripts/test-release-signing.ps1`은 OS 임시 폴더에서 일회용 키를 만들어 서명 성공·실패/기존 파일 보호/환경 복구를 검사한다. 결과는 `local-artifacts/release-signing-verification/`에만 보존하고 키를 삭제하며 마지막에는 기본 출력을 unsigned로 복원한다. **검증용 APK를 개인용 정식판으로 설치하거나 배포하지 않는다.** 실제 개인용 키/비밀번호를 이 검증에 사용하지 않는다. 실행 전 debug/unsigned 빌드를 갖춰야 한다.

Context7의 Android build 문서는 사용할 수 있었으나 AGP8.7.2별 인덱스는 없었다. [AGP8.7 SigningConfig API](https://developer.android.com/reference/tools/gradle-api/8.7/com/android/build/api/dsl/ApkSigningConfig)·[공식 서명 안내](https://developer.android.com/studio/publish/app-signing)·[버전 규칙](https://developer.android.com/studio/publish/versioning)·[JDK17 keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)과 실제 고정 도구 빌드로 교차 확인했다.

프로젝트 코드 라이선스는 보류 상태다. MIT 등 수정·재배포 허용 라이선스를 임의 적용하지 않았다. 제3자 라이선스 고지는 [기존 고지](release-preparation.md)에 따라 APK에 계속 포함한다. 개인용 키 빌드 준비와 소스 재사용 허가/공개 배포 승인은 각각 별개다.
