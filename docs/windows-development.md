# Windows 개발 환경과 실행

갱신: 2026-10-31. 현재 도구 계약은 [AGENTS](../AGENTS.md), 기능/검증 상태는 [SESSION](../SESSION.md), 정식 APK는 [개인용 빌드](personal-release.md)를 따른다. 과거 P0/초기 P1 구현 제한을 현재 상태로 적용하지 않는다.

## 최초 설치

PowerShell 7에서 현재 체크아웃을 사용한다. 시스템 Java/전역 환경 변수는 바꾸지 않고 SDK/JDK·다운로드는 Git 제외 local-artifacts에 둔다.

```powershell
Set-Location 'C:\Users\cotjs\workspace\ebook_viewer'
./scripts/bootstrap-windows.ps1 -AcceptAndroidSdkLicenses
```

이 옵션은 SDK 라이선스 수락을 뜻한다. 조건을 직접 읽으려면 옵션 없이 설치 후 sdkmanager --licenses를 대화형으로 실행한다. 다운로드 SHA-256 불일치 시 추출하지 않고 기존 local.properties를 덮어쓰지 않는다. 기존 SDK 경로와 스크립트 도구 경로가 맞는지 확인한다. 실행 정책/서명/체크섬 검증은 우회하지 않는다.

고정 도구는 Temurin 17.0.20.1+1, Gradle 8.9, AGP 8.7.2, Kotlin 2.1.20, Platform 35 rev2, Build Tools 34.0.0이다. 다운로드 URL/해시는 [도구 잠금 파일](../scripts/windows-toolchain.lock.json)에 있다. Gradle 내장 Kotlin 출력은 앱 Kotlin 플러그인 버전과 구분한다.

Android Studio는 필수가 아니며 이 프로젝트 초기화 때 설치하지 않았다. 사용한다면 Gradle JDK/SDK를 프로젝트 설치 경로에 맞춘다. 새 PC의 드라이버·Studio·기기 연결 복원은 별도 확인 대상이다.

## 평소 빌드와 검사

이미 도구가 있으면 새 PowerShell 세션마다 환경만 읽는다.

```powershell
Set-Location 'C:\Users\cotjs\workspace\ebook_viewer'
. ./scripts/android-env.ps1
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict
```

ktlintCheck는 읽기 전용이고 ktlintFormat은 명시적으로 요청할 때만 실행한다. 의존성 lockfile/verification-metadata를 정상 검증하며 일반 검사에서 다시 생성하거나 검증을 끄지 않는다. 변경 승인 후에만 공식 출처를 대조해 잠금/해시를 갱신한다.

debug APK는 app/build/outputs/apk/debug/app-debug.apk, 계측 APK는 app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk다. debug는 개발용이고 정식 서명 APK가 아니다. 보고서는 app/build/reports와 app/build/test-results에 있다. [캐시 정리](maintenance.md) 뒤 첫 빌드는 중간 산출물을 다시 생성한다.

## 필요한 기기 검사

PC 빌드·문서 정리에는 휴대폰이 필요하지 않다. 기능 검사 때만 USB 연결·디버깅 승인·잠금 해제를 확인한다. 한 기기를 대상으로 하며 식별자는 공개 이력에 남기지 않는다.

```powershell
. ./scripts/android-env.ps1
adb devices
$deviceSmokeError = $null
$deviceCleanupError = $null
try {
    ./scripts/device-smoke.ps1
} catch {
    $deviceSmokeError = $_
} finally {
    try {
        & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File ./scripts/reading-soak.ps1 -Mode stop
        if ($LASTEXITCODE -ne 0) {
            throw "Device cleanup failed with exit code $LASTEXITCODE."
        }
    } catch {
        $deviceCleanupError = $_
    }
}
if ($null -ne $deviceSmokeError) {
    if ($null -ne $deviceCleanupError) {
        Write-Warning 'Cleanup also failed; preserving the device-smoke error.'
    }
    throw $deviceSmokeError
}
if ($null -ne $deviceCleanupError) {
    throw $deviceCleanupError
}
```

device-smoke는 빌드/설치·검사 후 앱을 실행하므로 성공/실패에 관계없이 finally에서 앱/계측과 ADB 정리를 시도한다. reading-soak의 stop 경로는 exit를 사용하므로 별도 PowerShell 7 프로세스에서 호출하고 종료 코드도 확인한다. 검사·정리 오류를 따로 보존해 둘 다 실패하면 검사 오류를 다시 던지고 정리 실패는 경고로 남긴다. 검사 성공 후 정리만 실패해도 오류로 보고한다. 현재 기본 기대 수는 96개이며 [검증 전략](testing/strategy.md)의 관련 검사 선택과 [배포 준비](release-preparation.md)의 실제 명령을 따른다. 최종 APK에서 전체 96개가 통과했다는 뜻은 아니다. 문서·표시만의 변경에서 기본 전체/장시간 검사를 반복하지 않는다.

프로파일 측정은 device-smoke.ps1 -Profile -Probe를 명시적으로 선택한다. 30초·3회가 기본이고 -Seconds 600 -Runs 1은 10분 측정이다. profile은 로컬 debug 키를 쓰는 비디버그 계측용이며 정식판이 아니다. 성능의 조건/정의는 [기존 P0 이력](history/2026-10-16-07-gestures-and-probe.md)과 [후속 선명화 측정](testing/sharpness-probe.md)을 따른다. 과거 결과를 다른 API/기기·새 빌드의 통과로 바꾸지 않는다.

## 현재 기능과 배포

원본/너비 통일·간격·여백, 이어 읽기·책장 v6, 다섯 방향·작은 도구·번호/북마크·색반전/배경·본문 읽기는 [제품 문서](product/requirements.md)에 정리했다. 파일 접근은 SAF와 비탐색 파일의 취소 가능한 256MiB 상한 임시 복사를 사용한다. 현재 개인 API 33 기기 외 검증, 실제 OOM·모든 PDF의 무지연은 완료하지 않았다.

개인용 정식판은 별도 ID와 사용자 키를 사용한다. 기존 키를 다시 만들거나 기존 앱을 삭제하지 않고 새 versionCode로 업데이트한다. 고지·서명·출력 보호와 공개 배포 보류는 [개인용 안내](personal-release.md), [배포 준비](release-preparation.md)에 따른다.
