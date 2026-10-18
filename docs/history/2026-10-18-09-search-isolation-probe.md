# P2 검색 — 별도 APK/격리 프로세스 진단

2026-10-18 (Asia/Seoul). 로컬 작업 경로 `C:\Users\cotjs\workspace\ebook_viewer`.

## 기준과 사용자 정보

`codex/p2-platform-text`의 f2d331b에서 기존 Figma 변경(SESSION/CHECKLIST 및 history 01/02)을 보존하고 진행했다. fetch 후 PR #12 OPEN/Draft와 철회 1bb8d89의 Windows push CI 39102375613 성공을 확인했다. 검색은 계속 철회 상태다.

사용자가 장애 문서를 **약 430페이지·스캔 이미지 위주·글자 선택/복사 불가·약 두 글자 검색**이라고 설명했다. 파일/본문/실제 검색어는 받지 않았고 개인 PDF를 재실행하지 않았다. 사용자 설명은 실제 PDF 객체/텍스트 레이어 분석과 구분한다. 이전 종료 사유 LOW_MEMORY를 구체적인 원인 확정으로 바꾸지 않는다.

## 구현과 검증

별도 `:searchprobe` APK에 비공개 isolatedProcess 서비스, Binder FD 전달, 메모리/시간 감시와 자기 종료, 부모의 종료/재연결 확인, 외부 호스트 deadline/진단 패키지 한정 force-stop을 구현했다. 내장 합성 자료만 허용하고 읽기 앱 의존성/권한·UI/데이터 경로를 추가하지 않았다. Java와 Android 공개 API만 사용하며 새 런타임 라이브러리/lockfile/verification-metadata 변경은 없다. 진단 min 30은 읽기 앱 min 23과 별개다.

호스트 안전 테스트 최종 **12개 통과**. 테스트는 실제 ADB 없이 기한 초과 정리 대상을 검증하며, 기기 메모리가 기준보다 낮거나 guard 결과가 잘못되거나 읽기 앱 PID 상태가 바뀌면 PDF 실행을 막는다. 부모가 처음부터 실행 중이 아니었을 때도 별도 보고한다.

strict 읽기/진단 앱 빌드·기존 단위 22개·두 모듈 Lint 오류/경고 0·ktlint 통과. ktlintFormat은 실행하지 않았다. 처음의 Gradle DSL receiver 오류, Java SDK 상수/Lint backup/icon 오류를 수정한 뒤 통과했다. 진단 APK의 release variant는 만들지 않는다. 실제 종료 guard 7개는 초기 진단 APK에서 통과했다. 실제 시스템 OOM을 유발하지 않고 최대 4MiB 할당으로 감시 종료를 검증했다.

## 초기 탐색 표본 — 질의 6자, 각 1회

사용자 후속 정보 반영 전 `needle` 고정 질의로 작고 단순한 1,000페이지 텍스트 합성 자료를 비교했다. single-keep은 1,000페이지/2,000건 완료, 부모 case 경과 2,554ms, RSS 표본 최고 107,004KiB였다. dual-keep도 1,000페이지 완료, 2,290ms, 109,028KiB였다. 각각의 마지막 PSS 표본은 46,570/46,679KiB다.

single-reopen은 마지막 전달 표본 945페이지 뒤 **RESIDENT_BUDGET 보호 중단**했다. 시작 RSS 75,256KiB, 50ms 감시의 최고 관측 108,104KiB로 증가 예산 32MiB를 넘었다. 마지막 정규 표본의 PSS는 46,952KiB다. 실제 중단 페이지/진짜 순간 peak는 모른다. 임계값을 바꾸지 않고 dual-reopen 비교는 실행하지 않았다. 부모 FD 수는 세 case 모두 85→85였고 각 요청 전후 읽기 앱 PID 상태는 같았다.

이 초기 결과는 단일 표본·고정 순서이고 온도/OS cache를 통제하지 않았다. p95/성능 합격/누수 확정으로 보고하지 않는다. 자주 다시 여는 방식이 메모리를 줄인다는 증거도 아니다. 초기 보고서에는 읽기 앱 실행 여부를 별도로 저장하지 않아, PID 상태 동일을 실행 중 앱의 생존으로 확대하지 않는다. 이후 runner는 `readerWasRunning`을 명시한다.

## 최종 합성 자료와 실행 제한

후속 조건을 반영해 두 문자 `ne`와 작은 이미지 430페이지 자료를 추가했다. 최종 4개 자료의 SHA-256/페이지 수/첫 페이지 기대 문자열은 별도 pypdf로 대조했다(검증에만 bundled pypdf를 사용, 생성기/앱/CI의 추가 의존성 아님). 이 자료들의 이미지는 32×32 RGB이며 고해상도 원본 재현이 아니다.

최종 진단 APK는 206,456 bytes, SHA-256 `988a901c7210123de67a1a720cf780f50b018d500b4de4616a6cabce6732ec74`다. 이 APK 설치 후 읽기 앱을 실행한 상태에서 images-430 검사를 시도했으나 MemAvailable이 고정한 호스트 기준 2,176MiB보다 낮아 **guard/PDF 작업 시작 전에 거부**됐다. 앞서도 메모리 거부가 있었고 사용자가 앱을 정리한 뒤 기준을 회복했을 때만 초기 검사를 실행했다. 최종 APK의 기기 전체 통과나 430페이지 이미지 검색 완료를 선언하지 않는다. 이후 실제 재시도 결과가 있으면 아래에 추가한다.

원시 로그/개인 자료는 저장하지 않았다. ignored 증거는 `local-artifacts/search-isolation-{build,final-build,guards,guards-run,images-430-final}.log`, `search-isolation/*.json`, `search-probe-fixtures/manifest.json`이다. 집계는 RSS/PSS·Java/native heap·FD·메모리 여유·진행/시간·격리 확인/종료 유형만 포함한다.

## 작업 중 Wrapper 변경과 복구

외부에서 Wrapper URL만 8.9→9.7.1로 변경되고 SHA-256은 8.9 값인 상태가 관찰됐다. 9.7.1 실행이 kotlin-reflect/stdlib 2.4.0 관련 strict 검증 4개를 거부했으며 verification-metadata를 수정해 통과시키지 않았다. 9.7.1과 AGP의 호환성 전체를 판정한 결과는 아니다.

변경을 덮어쓰지 않고 설치된 8.9를 직접 사용해 독립 검사를 진행했고 사용자에게 의도를 확인했다. 사용자는 의도하지 않은 변경이라고 설명하고 8.9 유지 이유를 물었다. 공식 [AGP 8.7 호환표](https://developer.android.com/build/releases/agp-8-7-0-release-notes#compatibility)의 기본/최소 Gradle 8.9·JDK 17과 검증된 계약을 설명한 뒤, 변경 파일을 ignored 위치에 보관하고 Wrapper를 기존 8.9로 복구했다. 실제 발생 원인을 Android Studio 등 특정 도구로 확정하지 않는다.

새 버전 메타데이터가 발견된 뒤 Kotlin stdlib 최신판 권고도 Lint 실패로 나타났다. 기존 AGENTS의 ‘버전 권고는 정보’ 정책과 app/lint.xml의 동일한 AGP 권고 처리에 맞춰 `GradleDependency`를 informational로 표시했다. 버전 권고는 계속 보이고 실제 Lint 오류/경고·strict/잠금·체크섬 검사는 유지한다. 앱 Kotlin/리소스/Manifest/빌드 버전과 라이브러리는 변경하지 않았다.

## 미결

마지막 재확인 MemAvailable 2,148,036KiB도 호스트 기준 2,228,224KiB보다 낮아 추가 guard/PDF 호출 없이 건너뛰었다. 이후 정확한 진단 패키지만 force-stop/uninstall해 정리했다. 정리 전 읽기 앱 프로세스가 실행 중이었고 정리 후 같은 PID였음을 확인했다. 읽기 앱 APK/데이터는 설치·삭제/초기화하지 않았다. 최종 빌드도 120 tasks, strict 성공이며 두 Lint 오류/경고 0, 앱 기존 단위 22개·호스트 12개·ktlint 통과다. 이 PC 재검사는 최종 APK 기기 검증의 대체가 아니다.

430페이지 이미지 문서의 실제 문제는 재현/해결되지 않았다. 고해상도/압축/문서 구조·짧은 질의·무결과 처리의 비용과 native/시스템 메모리를 분리해야 한다. 격리/중단 정책은 kernel hard cap가 아니며 Java sleep/외부 ADB 정리 검증이 제품 내 막힌 native 프로세스 종료를 증명하지 않는다. [ADR 0011](../adr/0011-isolated-search-diagnostics.md)의 경계를 유지하고 원인·보호·최종 사용자 수용 전에는 검색 UI를 다시 켜지 않는다. P2 완료가 아니다.
