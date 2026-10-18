# 검색 격리 진단 — 합성 자료 전용

제품 검색이 아니다. 현재 읽기 앱의 검색 UI는 철회 상태이며 이 도구의 결과만으로 다시 활성화하지 않는다. [ADR 0011](../adr/0011-isolated-search-diagnostics.md)의 예산/한계를 먼저 읽는다.

## PC 검사

Python 3.10 이상 표준 라이브러리로 생성/호스트 검사를 실행한다. Android 앱 런타임 의존성이나 pip 설치가 필요하지 않다.

```powershell
. ./scripts/android-env.ps1
python ./scripts/generate-search-probe-fixtures.py
python ./scripts/test-search-probe-runner.py
./gradlew.bat :searchprobe:assembleDebug :searchprobe:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain
```

생성 위치는 ignored `local-artifacts/search-probe-fixtures`다. `text-1000.pdf`(2,000건), `images-430.pdf`/`images-1000.pdf`(0건), `dense-100.pdf`(20,000건), SHA-256/페이지 수/크기를 가진 manifest를 생성한다. 현재 고정 질의는 두 ASCII 문자 `ne`이며 사용자 검색어가 아니다. 이미지 자료는 페이지마다 고유한 32×32 RGB 스트림이다. 430페이지 전체의 원본 픽셀 합계는 약 1.26MiB로 고해상도 스캔과 동등하지 않다. 바이너리 PDF는 Git에 넣지 않는다.

CI도 같은 생성기/호스트 테스트와 진단 APK 빌드·Lint를 실행한다. 실제 Android 격리/메모리 측정은 CI에 포함하지 않는다.

## 기기 검사

진단 APK 설치는 `-t`를 요구하며 읽기 APK를 설치/삭제/force-stop하지 않는다. Python 실행 경로가 다르면 `-Python`에 그 경로를 준다. 승인한 도구 조합과 다른 Wrapper는 이 스크립트가 실행 전에 거부하며, 체크섬이나 strict 검증을 우회하지 않는다.

```powershell
./scripts/probe-search-isolation.ps1 -Python python -Suite guards
./scripts/probe-search-isolation.ps1 -Python python -Suite search -Fixture images-430.pdf -Mode single-keep
```

검색 요청마다 먼저 9개 guard를 확인한다. 기존 7개에 메인 looper 대기 중 감시 종료와 부모 unbind 후 2초 내 Binder death/재연결 검사를 더했다. worker 대기·부모 unbind 검사는 서비스 메인 looper가 정상일 때이며 VM 전체 정지/native hang 강제 회수를 증명하지 않는다. 네이티브 비교 모드는 `single-keep`, `dual-keep`, `single-reopen`, `dual-reopen`이고 reopen은 16페이지마다 텍스트 renderer를 닫고 다시 연다. single/dual은 별도 진단 프로세스 안의 renderer 수이며 실제 읽기 화면/캐시 성능 실험이 아니다. 예산 초과/시스템 메모리 부족이 관찰되면 비교를 중단하고 원인을 먼저 살핀다. 통과를 위해 임계값을 바꾸지 않는다.

설치 전에도 동일한 메모리 gate를 적용하고 설치 후 실행 전에 다시 확인한다. APK 설치 직후의 메모리 부족 거부도 실패/건너뜀으로 기록한다. 같은 APK가 이미 설치돼 있고 메모리 기준이 나중에 회복됐다면 재설치 없이 runner를 호출할 수 있다. `--serial`은 연결된 기기 선택용이며 결과/로그에 노출하지 않는다. runner에도 같은 guard·예산·종료 검사가 있어 PDF 검사만 단독 우회할 수 없다.

결과는 ignored `local-artifacts/search-isolation/*.json`에 집계 값만 저장한다. outcome `OK`와 `RESIDENT_BUDGET`/`SYSTEM_MEMORY`/`TIME_BUDGET`을 구분한다. 보호 중단을 검색 성공으로 세지 않는다. 페이지 수는 마지막 전송 표본이며 실제 중단 페이지와 다를 수 있다. `readerWasRunning=false`이면 PID 상태 동일을 실행 중인 읽기 앱의 생존으로 보고하지 않는다.

2026-10-18 수정 APK 0ba73ec7에서 기기 guard 9개와 작은 이미지 430페이지 single-keep/결과 0건을 통과했다. [실제 APK/시간/메모리와 한계](../history/2026-10-18-10-search-probe-termination.md)를 따른다. 이전 APK/다른 모드·고해상도 스캔/제품 검색의 통과가 아니다.

실험 후 남은 진단 APK가 필요 없으면 정확한 진단 패키지만 제거한다.

```powershell
adb shell am force-stop dev.example.ebookviewer.searchprobe
adb uninstall dev.example.ebookviewer.searchprobe
```

개인 PDF/본문/URI/비밀번호를 입력·복사·로그/외부 서비스로 전송하지 않는다. raw logcat/시스템 전체 프로세스 목록을 결과 파일에 저장하지 않는다.
