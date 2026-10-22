# 빌드·설치·라이선스와 배포 준비

2026-10-22 (Asia/Seoul). 개인용 API33 기기의 현재 구성을 점검한 문서다. 이번에는 debug APK의 고지·빌드·설치 절차를 정리하며 release 빌드/키 생성/실제 배포는 실행하지 않는다. 검증 결과와 APK별 범위는 [P3 최종 이력](history/2026-10-22-01-p3-final-verification.md)을 따른다.

## 현재 산출물과 다시 빌드하기

PowerShell 7에서 실제 체크아웃을 사용한다. JDK17/Gradle8.9/AGP8.7.2/Kotlin2.1.20, compile/target35/min23, Build Tools34.0.0을 유지한다. 최초 도구 설치는 [Windows 개발](windows-development.md)의 라이선스 수락/검증 절차를 따른다. 이미 설치된 도구를 매번 다시 설치하지 않는다.

```powershell
Set-Location 'C:\Users\cotjs\workspace\ebook_viewer'
. ./scripts/android-env.ps1
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict
Get-FileHash ./app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
```

앱은 `app/build/outputs/apk/debug/app-debug.apk`, 계측 APK는 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`다. 기기/사용자에게 제공하는 앱 APK와 개발 검사 전용 APK를 구분한다. 현재 applicationId는 `dev.example.ebookviewer.p0`, versionCode1/versionName `0.1.0-p0`이며 이번 점검에서 변경하지 않는다. 테스트·Lint의 up-to-date 재사용과 실제 새 실행을 이력에서 구분한다.

## 개인 기기 설치와 업데이트

USB 디버깅 승인·화면 잠금 해제 후 한 기기만 연결한다. 식별자를 공유 이력에 복사하지 않는다.

```powershell
. ./scripts/android-env.ps1
adb install --no-streaming -r ./app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n dev.example.ebookviewer.p0/dev.example.ebookviewer.MainActivity
```

동일 applicationId/서명의 `-r` 업데이트를 사용한다. 서명 불일치·버전 다운그레이드 오류가 나면 원인을 확인하고 앱 삭제·데이터 초기화·우회 플래그로 해결하지 않는다. 기록은 앱 내부에 저장되므로 삭제/데이터 초기화 시 사라지며, 현재 백업도 비활성화돼 있다. 원본 PDF 삭제와 읽기 기록 삭제는 별개다. 다른 PC의 자동 생성 debug 키는 기존 키와 다를 수 있으므로 새 환경으로 옮길 때 서명 일치를 먼저 확인한다. 서명 키를 Git/로그에 복사하지 않는다.

기본 통합 검사는 `./scripts/device-smoke.ps1`이며 현재91개다. 이 명령은 마지막에 앱을 한 번 실행하므로 검사 종료 후 `./scripts/reading-soak.ps1 -Mode stop`으로 앱/계측과 ADB를 중지한다. 중지 명령은 연결 기기 한 개를 전제로 한다. 명시적 전용 `readingSoak`20분은 일반 검사에 포함하지 않는다. 새 전체 기능/성능 검사가 필요하지 않은 문서 변경에서 기기 검사를 반복하지 않는다.

전체91개의 계측 제한은900초다. 최초720초 전체 중단 뒤 필요한 검사만 분할 보완했으며 그 실제 결과를 단일전체 성공으로 표기하지 않는다. 오류에 필요한 개별 재현은 `-e methodClass gestures|settings|chrome -e method 실제@Test함수명`을 사용할 수 있다. 기존 `-e suite access -e method 실제@Test함수명`도 유지한다. 허용 클래스/실제 @Test 외 입력과 probe/restart 혼용은 거부한다. 예를 들어 `adb shell am instrument -w -e methodClass chrome -e method centeredPageIndicatorKeepsViewportAndPassesScrollingThroughItsOwnSurface -e deadlineSeconds 90 dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation`로 번호 검사 하나를 실행한다. 실행 전에 현재 계측 APK를 `adb install --no-streaming -r -t ./app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`로 설치한다. 불완료/실패는 원문 로그와 함께 보존하고 별도 성공 실행으로 덮지 않는다.

## 실제 의존성과 고지

`./gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --dependency-verification strict`로 해석한 런타임은 다음 두 개다. 이 명령은 release APK를 만들지 않는다.

- `org.jetbrains.kotlin:kotlin-stdlib:2.1.20`: 캐시 POM의 Apache-2.0 선언과 [v2.1.20 라이선스 원문](https://github.com/JetBrains/kotlin/blob/v2.1.20/license/LICENSE.txt), [표준 라이브러리 소스](https://github.com/JetBrains/kotlin/tree/v2.1.20/libraries/stdlib)를 대조했다. JetBrains/Kotlin 기여자 저작권 표기는 소스에서 확인했다. [Kotlin 배포 NOTICE](https://github.com/JetBrains/kotlin/blob/v2.1.20/license/NOTICE.txt)는 compiler distribution이라는 원래 문구를 그대로 보존하며 stdlib만 사용하는 앱의 역할과 구분했다.
- `org.jetbrains:annotations:13.0`: [공식 Maven Central POM/버전 소스](https://repo.maven.apache.org/maven2/org/jetbrains/annotations/13.0/)에서 Apache-2.0과 `Copyright 2000-2012 JetBrains s.r.o.`를 확인했다. 현재 upstream master를13.0의 유일한 근거로 쓰지 않는다.

APK 고지는 [THIRD_PARTY_NOTICES.txt](../app/src/main/assets/THIRD_PARTY_NOTICES.txt), 공통 [Apache-2.0 원문](../app/src/main/assets/licenses/Apache-2.0.txt), [Kotlin 원문 NOTICE](../app/src/main/assets/licenses/Kotlin-NOTICE.txt)다. 원문 파일은 공식 v2.1.20 경로에서 변경 없이 보존한다. 패키징 후 ZIP 내부의 `assets/` 대응 엔트리와 저장소 원문 바이트를 대조한다. 새 의존성을 추가하면 runtime graph/실제 APK/소스 고지를 다시 점검한다. 이번에는 Kotlin/Gradle/저장 라이브러리를 새로 도입하거나 변경하지 않았다.

JUnit4.13.2(EPL-1.0)와 Hamcrest1.3(New BSD/3-clause)는 단위/기기 검사 전용이다. JUnit JAR의 LICENSE-junit.txt/POM, Hamcrest parent POM/[1.3 원문](https://github.com/hamcrest/JavaHamcrest/blob/hamcrest-java-1.3/LICENSE.txt)을 확인했다. ktlint1.5.0(MIT)은 빌드 검사 CLI다. Gradle8.9/AGP8.7.2/Kotlin compiler2.1.20/JDK/SDK는 앱 런타임과 구분하며 각 도구 배포의 고지를 따른다. 도구 전체의 전이 의존성이나 계측 APK를 배포하는 경우의 고지 전체를 이번 앱 APK 점검만으로 완료했다고 선언하지 않는다. PdfRenderer/PreV는 기기 플랫폼에서 제공되며 별도 PDF 엔진 JAR/native 라이브러리를 APK에 넣지 않는다.

소스/기본 APK 점검에서 외부 폰트·이미지 자산이나 독립 native `.so`는 없었다. Android 시스템 글꼴과 저장소 내 View/Canvas·vector XML을 사용한다. 아이콘 최종안의 출처/라이선스는 선택 시 별도로 확인한다. 이 문서는 구성요소/고지의 기술적 확인이며 프로젝트 전체의 법률 판단을 대신하지 않는다.

## 공개 배포 전에 남은 결정

- 저장소 자체의 LICENSE가 아직 없다. 제3자 Apache-2.0 고지는 앱 자체를 Apache-2.0으로 허가하는 선언이 아니다. 공개 라이선스 선택은 사용자 결정 후 별도로 반영한다.
- 2026-10-22 사용자가 첨부한 아이콘을 최종 선택했고 일반/adaptive/단색 테마 런처 리소스로 적용했다. 원본 [PNG](design-assets/selected-launcher-icon.png)와 대응 [SVG](design-assets/selected-launcher-icon.svg)를 보존하며 APK에는 vector만 넣는다. 출처는 사용자가 선택한 별도 디자인 작업의 자체 SVG 시안이다. 프로젝트 공개 라이선스 결정은 별도이며 선택/적용을 모든 법적 권리 검증으로 확대하지 않는다. [검증 범위](history/2026-10-22-03-launcher-icon.md).
- applicationId/제품 버전 정책과 release 서명·키 보관/업데이트 방식을 정해야 한다. 현재 debug APK는 개발용, profile APK는 비디버그 계측용이지만 debug 키 서명이며 출시물이 아니다. release는 아직 서명 설정이 없다.
- release용 버전·서명·최적화 설정을 결정한 다음 해당 **실제 release 산출물**의 설치/업데이트·기능·서명/고지/개인 데이터 제외를 다시 확인한다. debug 통과를 미생성 release의 검증으로 바꾸지 않는다.
- 외부 배포/스토어 등록·개인정보 안내·대상 API/기기 확장은 현재 개인용 범위와 별도로 결정한다. 실제 게시/릴리스는 사용자 승인 전 진행하지 않는다.

PDF·추출 본문·키·개인 파일·local.properties·SDK/JDK 다운로드·빌드/측정 결과는 Git에서 제외한다. 앱 APK에는 고지 텍스트만 포함하고 합성 성능 자료는 androidTest source set/ignored fixtures에 한정한다. release 준비 완료와 실제 release 승인/배포를 구분한다.
