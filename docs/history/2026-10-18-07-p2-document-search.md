# P2 문서 검색 — 플랫폼 텍스트 어댑터와 앱 UI

2026-10-18 (Asia/Seoul). 작업 위치 `C:\Users\cotjs\workspace\ebook_viewer`.

## 기준과 보존

`git fetch origin` 후 PR #11의 사용자 병합 main `5d74c682d72abb38e802048c7742c89fbcf39b81`에서 `codex/p2-platform-text`를 만들었다. PR #11 최종 문서 head `ccfb535d37cbccf39fe4d5e27388e84c782fcc15`의 Windows CI 39100314131 성공도 확인했다. 기존 Figma의 SESSION/CHECKLIST 부분과 history 01/02는 미커밋 상태로 보존하며 이번 커밋에서 제외한다. 이전 범위 조정 이력 06은 이번 P2 변경에 포함한다.

연결 기기는 API 33/S 확장 23으로 앞선 probe와 같다. 승인된 기기 1대, 잠금 해제 상태를 식별자 출력 없이 확인했다. 새 런타임 의존성/개발 도구 변경·개인 PDF 분석/업로드는 없다. 기본 픽셀 렌더러/타일 배율/캐시/해상도를 유지해 선명도 최적화와 독립적으로 검색 기능을 진행했다.

## 변경

온디맨드 `PdfRendererPreV` 검색 어댑터, 페이지별 worker 양보/세대 취소, 진행/결과 수·이전/다음·읽기 복귀 UI, 결과 위치로 이동/강조를 구현했다. 최대 질의 256 UTF-16 단위, 결과 500건, 일치당 사각형 32개다. 사용자 문서 본문·질의·검색 인덱스를 저장/기록하지 않는다. 기존 읽기 기록 스키마와 사용자 데이터는 유지한다. 설계·정책·native 메모리/취소 한계는 [ADR 0010](../adr/0010-on-demand-platform-pdf-search.md).

## 실행과 결과

PowerShell에서 `. ./scripts/android-env.ps1` 후 실행했다.

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e suite search dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation
adb shell am instrument -w dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation
```

strict 앱/계측 빌드·단위 22개·Lint 오류/경고 0·ktlintCheck 통과. 초기 컴파일의 `INPUT_METHOD_SERVICE` 수신자 누락과 테스트 PdfDocument의 Closeable 가정, ktlint 형식 오류는 각각 Context 상수·명시적 finally close·수동 형식 수정 후 재검사했다. ktlintFormat은 실행하지 않았다. 기존 deprecated API 경고/버전 정보 권고와 Lint 오류/경고를 구분한다.

검색 계측은 최초 5개/실패 0, 회전 텍스트/이미지 전용 페이지와 영문 대소문자를 추가한 6개/실패 0. 첫 APK(972,509 bytes)의 SHA-256은 `2e23986b3bd78c53a230a1fa003e872456fb7f729dedd2ba1fefa084dd4bf459`이며 전체 47개/실패 0을 통과했다. 이후 새 검색창을 열면 이전 선택 강조와 보류 이동을 지우도록 보완하고 재열기 UI 회귀를 추가했다. 보완 APK(1,078,743 bytes)의 SHA-256은 `0c4fb4994c067dd2dcd56d994b3bbbeaf8a92f950870385853d3ba68ccab7260`이며 strict 앱/계측 빌드·단위 22개·Lint/ktlint를 다시 통과했다. 전체 회귀 종료 후 보완 APK의 검색 6개/실패 0을 재통과했다. 전체 47개는 앞선 APK, 재열기 보완을 포함한 6개는 보완 APK 결과로 구분하며 마지막 APK에서 전체 47개를 다시 실행했다고 보고하지 않는다. 앱 업데이트 설치는 기존 데이터를 보존했다. 합성 검색 UI 테스트는 테스트 URI의 기록만 제거했다.

로컬 증거는 ignored `local-artifacts/p2-platform-text-build.log`, `p2-platform-text-final-build.log`, `p2-search-device.log`, `p2-search-device-six.log`, `p2-search-full-device.log`, `p2-search-device-final.log`에 둔다. 출력은 합성 테스트 상태/예외만 포함하고 개인 PDF/URI/식별자는 기록하지 않는다.

추가 Context7의 SdkExtensions API 질의는 해당 자료를 찾지 못했다. [공식 SdkExtensions](https://developer.android.com/reference/android/os/ext/SdkExtensions#getExtensionVersion(int))의 도입 API/유효 extension 인자를 대조했다. 기존 설치 SDK 35 서명/실제 API 33 확장 검증과 구분하며 하위 API 기기의 검색 fallback을 실행한 것으로 세지 않는다.

## 미결과 다음 행동

줄바꿈/Unicode 경계·PDF `/Rotate` 메타데이터, 개인 PDF 검색 수용·최종 앱 성능/PSS·큰 글꼴/TalkBack은 미검증이다. 검색 어댑터를 검색 전체 종료 또는 P2 완료로 보고하지 않는다. 기존 렌더러가 그대로인 이번 기능 통합 뒤에도 선명도 최적화 전의 앱 draw 기준선/목표 고정은 필수다. 다음은 검색 경계 보완과 선택/복사, 선명도 계측·자동 배경색·테마/밝기/화면 유지·접근성이다. 목차·비밀번호 읽기는 사용자 결정으로 제외한다.

PR 등록/원격 CI/CodeRabbit 실제 결과는 확인 후 추가한다. 사용자가 PR을 병합한다.
