# Windows 개발 환경과 P0 실행

2026-10-16 (Asia/Seoul). 사용자 승인 구성으로 로컬 빌드·검사를 완료했다. API 33 실기기 설치·기본 렌더 테스트·앱 실행을 확인했다. 현재 기기에서 합성 자료 성능을 측정했으며 최종 결과는 history에 기록한다. 다른 API와 원격 CI는 미검증이다.

## 설치와 검사

PowerShell 7에서 현재 체크아웃을 사용한다. JDK/SDK/다운로드는 Git 제외 `local-artifacts`에 저장한다. 시스템 Java나 전역 환경 변수는 바꾸지 않는다.

```powershell
Set-Location 'C:\Users\cotjs\workspace\ebook_viewer'
./scripts/bootstrap-windows.ps1 -AcceptAndroidSdkLicenses
. ./scripts/android-env.ps1
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict
```

`-AcceptAndroidSdkLicenses`는 SDK 라이선스 수락 옵션이다. 조건을 직접 읽으려면 옵션 없이 설치 후 `sdkmanager --licenses`를 대화형으로 실행한다. 설치 스크립트는 SHA-256 불일치 시 추출하지 않는다. 기존 `local.properties`를 덮어쓰지 않으므로 다른 SDK가 지정돼 있다면 내용을 확인한다. PowerShell 실행 정책을 우회하거나 시스템 정책을 바꾸지 않는다.

고정 도구: Temurin 17.0.20.1+1, Gradle 8.9, AGP 8.7.2, Kotlin 2.1.20, Platform 35 revision 2, Build Tools 34.0.0, command-line tools 12.0, platform-tools 37.0.1. 다운로드 URL/해시는 `scripts/windows-toolchain.lock.json`에 있다. Gradle 자체의 내장 Kotlin 1.9.23 출력은 앱 KGP 2.1.20과 다른 항목이다.

Android Studio는 필수 빌드 도구가 아니며 이번에 설치하지 않았다. 나중에 설치하면 Gradle JDK와 SDK를 위 로컬 도구 경로로 지정한다. 새 PowerShell 세션마다 android-env.ps1을 dot-source한다.

## 기기 실행

```powershell
adb devices -l
./gradlew.bat :app:installDebug --dependency-verification strict
adb shell am start -n dev.example.ebookviewer.p0/dev.example.ebookviewer.MainActivity
```

한 번에 설치·기기 테스트·실행을 확인하려면 `./scripts/device-smoke.ps1`을 실행한다. 기기는 USB 디버깅 및 PC 연결 승인이 필요하다. 여러 기기 연결 시 대상 serial을 로컬에서 지정하고 공개 이력에 복사하지 않는다. 연결된 SM-N981N(API 33, emulator flag 0)에서 앱/테스트 APK 설치와 am start -W 실행 성공을 확인했다. 식별자는 기록하지 않는다.

첫 화면의 “합성 100페이지”는 600×800 / 800×600 point를 교차 배치한 비공개 정보 없는 문서를 worker에서 생성한다. “PDF 열기”는 SAF를 사용한다. 합성 파일은 FD를 연 뒤 임시 이름을 삭제한다. 실제 측정 자료의 고정 SHA/기기 trace는 다음 기기 검증 시 별도 저장한다.

검증 순서: 첫 표시 → 핀치와 두 축 이동 → 페이지 경계 왕복 → 플링 중 반전 → 회전 → 빠른 문서 교체 → 종료 → 암호화/손상/provider 오류. 이후 `testing/strategy.md`의 대표 실기기 3회 측정으로 진행한다. APK 빌드 통과는 이 검증의 대체물이 아니다.

## 현재 구현 범위와 제한

측정: `./scripts/device-smoke.ps1 -Probe`(debug), `./scripts/device-smoke.ps1 -Profile -Probe`(비디버그, 기본 30초×3회). `-Seconds 600 -Runs 1`로 10분 측정 가능하다. profile은 release 설정을 복사하고 로컬 debug 키로 서명하며 profileable을 켠다. 난독화는 현재 release와 동일하게 꺼져 있다. 스토어 출시물이 아니다. 보고서는 `local-artifacts/p0-profile-probe.json`에 저장하며 인프로세스 계측 오버헤드와 firstVisibleTilesMs의 의미를 [이력](history/2026-10-16-07-gestures-and-probe.md)에서 확인한다.

- 플랫폼 Activity + View/Canvas, 기본 API PdfRenderer, single worker 수명 관리. 텍스트/암호화 API 35 확장은 SDK 서명만 확인했으며 UI에 구현하지 않았다.
- 문서 전체 공통 zoom, gap 0 원본 크기 배치, 가시 영역 512px 타일, 64MiB 바이트 LRU, 문서/요청 세대 검사. active native render는 즉시 중단할 수 없으며 close는 worker에서 순서대로 수행한다.
- 회전 시 문서 중심·zoom Bundle 복원 경로 구현. API 33 Activity 재생성에서 합성 문서와 동일 zoom 복원은 통과했다. 물리 회전/프로세스 강제 종료 복원은 미검증이다.
- 첫 4페이지를 먼저 표시하고 나머지 크기는 worker에서 2페이지씩 추가한다. 기존 페이지 위치·사용자 줌을 유지한다. 회전 복원 시에는 전체 크기를 먼저 확보해 정확한 경계에서 위치를 복원한다. 렌더 배율은 반 옥타브 단계로 올림하며 사용자 줌은 연속값이다. 가시 페이지의 긴 변 최대 512px 미리보기를 우선 표시하고 현재/이전 배율 타일과 같은 64MiB 캐시를 공유한다. 아주 빠른 이동에서는 아직 준비되지 않은 영역이 남을 수 있다.
- 캐시 64MiB는 총 메모리 상한이 아니다. worker 비트맵·UI 게시 대기·플랫폼 native 메모리는 별도 측정 대상이다.
- 비탐색 provider는 임시 복사 대신 현재 일반 오류 안내. 북마크/최근 문서/설정·완전한 접근성·오류 분류와 재시도는 P1 이전 보완 대상이다.

## 의존성 및 검사 정책

앱 runtime은 stdlib 2.1.20과 annotations 13.0(Apache-2.0). JUnit 4.13.2(EPL-1.0)/Hamcrest 1.3(BSD 계열)은 테스트 전용, ktlint 1.5.0(MIT)은 공식 shadowed CLI로 실행한다. JDK는 GPLv2+Classpath Exception, Android SDK는 배포 SDK 약관/각 구성요소 고지를 따른다. 실제 배포 전 전체 고지 정리는 별도다.

`gradle/verification-metadata.xml`은 최초 공식 HTTPS 다운로드를 신뢰 기준으로 SHA-256을 생성했다. 전체 의존성의 독립 서명 검증을 주장하지 않는다. 변경 승인 후에만 `--write-locks --write-verification-metadata sha256`로 갱신하고 diff/출처를 검토한다. 일반 검사는 strict를 유지한다.

ktlintCheck는 읽기 전용, ktlintFormat은 명시적 포맷 명령이다. Android Lint는 warningsAsErrors이며 승인한 버전을 유지하기 위한 AndroidGradlePluginVersion 권고만 informational로 남긴다. 기기 테스트는 JUnit 4와 최소 Instrumentation 브리지로 실행하며 앱 런타임 의존성은 늘리지 않았다. API 33에서 11개 기기 테스트가 통과했다. 최종 성능 수치/한계는 history/2026-10-16-09-p0-local-validation.md를 따른다. 원격 CI/새 PC 복원은 미실행이다.

산출물: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/reports/tests/testDebugUnitTest`, `app/build/reports/lint-results-debug.html`. debug APK는 출시 서명물이 아니다. apksigner는 서명 검증에 성공했으며 AGP의 META-INF app-metadata.properties 엔트리가 개별 보호되지 않는다는 경고도 기록했다.
