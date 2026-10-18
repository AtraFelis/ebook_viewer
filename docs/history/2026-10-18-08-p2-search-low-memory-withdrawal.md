# P2 검색 — 개인 파일 수용 실패와 기능 철회

2026-10-18 (Asia/Seoul). 작업 위치 `C:\Users\cotjs\workspace\ebook_viewer`.

## 우선 판정

사용자가 페이지가 많은 개인 PDF를 검색하던 중 앱이 종료되고 Android 조작까지 불안정해졌다고 보고했다. 이후 휴대폰은 다시 조작할 수 있다고 확인했다. **개인 파일 수용 실패이며 문서 검색 기능은 철회한다.** 앞선 합성 검색 6개·전체 47개·strict 빌드/CI 통과가 이 실패보다 우선하지 않는다. 검색 기능을 완료/사용 가능으로 보고하지 않는다. P2 필수 기능에서는 검색을 제외하지 않고 안정성 미해결로 남긴다.

## 진단 증거와 한계

앱만 `adb shell am force-stop dev.example.ebookviewer.p0`로 중지했다. 기기 재부팅/초기화·앱 데이터 삭제·개인 PDF 재검색/업로드는 하지 않았다. 종료 이력 `adb shell dumpsys activity exit-info dev.example.ebookviewer.p0`에서 **14:50:38의 종료 사유 `LOW_MEMORY`**를 확인했다. 14:52:31의 후속 종료는 이번 복구 과정의 force-stop이므로 사용자 장애와 구분한다.

해당 LOW_MEMORY 기록의 마지막 PSS/RSS 표시는 288MB/374MB다. 종료 전 마지막 표본이며 실제 native/전체 시스템 최고 사용량으로 해석하지 않는다. 앱 중지 후 MemAvailable은 약 2.47GB였으며 이 사후 수치로 장애 당시 메모리 압박을 부정하지 않는다. 제한된 최근 crash/events 버퍼에 Java OutOfMemoryError나 native SIGSEGV/SIGABRT 표시는 없었지만, 로그 유실/표본 범위 때문에 해당 원인이 없었다고 단정하지 않는다. Android 전체가 충돌/재부팅했다고 진단하지 않으며 조작 불안정은 사용자 관찰로 기록한다.

FD 복제에 따른 두 renderer의 native 작업 집합, 페이지별 검색/문서 내부 캐시 증가, 단일 페이지의 해석 비용, 시스템의 다른 메모리 사용량이 조사 후보다. 현재 근거로 특정 함수/누수/문서 페이지 수를 단독 원인으로 확정하지 않는다. 앱 결과 500건 상한과 25ms 페이지별 양보가 native 메모리 상한이나 native 호출 중단을 보장하지 못했다는 한계가 드러났다.

원시 로그·본문·파일명/URI·기기 식별자는 파일/Git/외부 서비스에 저장하지 않았다. 진단 출력은 종료 유형/시간·메모리 필드·이벤트 종류별 건수로 제한했다. 개인 문서를 재현 자료로 요구하지 않는다.

## 복구와 게시 상태

PR #12를 즉시 Draft로 바꿨다. 실험 코드는 Git 커밋 `6258ce1e2c5d1536ce87f3f70d55cf114358014a`에 보존하며 일반 후속 커밋으로 앱/계측/기기 스크립트를 사용자 병합 main `5d74c682d72abb38e802048c7742c89fbcf39b81`과 동일하게 복원한다. 사용자 Figma의 SESSION/CHECKLIST 부분과 history 01/02는 보존한다. reset/clean/강제 push를 사용하지 않는다.

복구 명령:

```powershell
git restore --source=origin/main -- app scripts/device-smoke.ps1
. ./scripts/android-env.ps1
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
git diff --exit-code origin/main -- app scripts/device-smoke.ps1
adb shell am instrument -w -e suite lifecycle dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation
```

복구 앱/계측 strict 빌드·단위 22개·Lint 오류/경고 0·ktlint 통과, 데이터 보존 업데이트 설치·앱 실행 `Status: ok` 확인. 수명/캐시/FD 회귀 4개/실패 0을 통과했다. app/기기 스크립트가 origin/main과 동일하고 복구 APK의 dex에 실험 검색 클래스가 없음을 검사했다. 복구 APK는 948,660 bytes, SHA-256 `8a26e657b59f4d439488b6ed88c13f6ef7fc2785a0e9e5afe388c6fcc2f0e2fb`다. 현재 기본 suite는 원래의 41개이며 철회된 검색 6개를 현재 suite로 세지 않는다. 이번 복구에서 전체 41개를 재실행한 것으로 보고하지 않는다. ignored 증거는 `local-artifacts/p2-search-withdrawal-build.log`, `p2-search-withdrawal-device.log`다.

실험 소스 `6258ce1`의 Windows CI 39101925343(PR)/39101921813(push)는 성공했다. 이는 작은 합성 테스트/빌드 결과이며 실사용 안정성 통과가 아니다. CodeRabbit [수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/12#issuecomment-6246264455)은 최초 접수 응답 뒤 [Review rate limited](https://github.com/AtraFelis/ebook_viewer/pull/12#issuecomment-6246265107)로 변경됐다. 14:50 KST 당시 25분 후 복구 안내였으며 실제 리뷰 제출/인라인 지적은 없다. 재요청을 반복하지 않는다.

## 검색 재개 전 조건

1. 합성 대용량 자료로 페이지 수와 이미지/텍스트/폰트 복잡도를 분리하고, 빈 결과·반복/짧은 질의·중도 취소를 포함한 재현 계획을 고정한다. 개인 PDF를 다시 위험한 경로로 실행하지 않는다.
2. Java/native PSS/RSS·시스템 가용 메모리·페이지별 시간·FD/프로세스 종료 사유를 함께 측정한다. 한 시점의 PSS나 결과 건수 상한을 native 메모리 상한으로 간주하지 않는다.
3. native 검색이 멈추거나 메모리를 과다 사용해도 읽기 앱과 기기를 보호할 구조를 먼저 검증한다. 별도 중단 가능한 프로세스, 메모리/시간 예산, 프로세스 종료/복구와 문서/FD 수명을 비교한다. 단순 페이지 수 제한·지연 증가만으로 해결됐다고 판단하지 않는다.
4. 대용량/취소/반복 검색의 안정성·기존 읽기 회귀와 사용자 수용을 통과한 뒤에만 UI를 다시 제공한다. 새 라이브러리/스택이 필요하면 별도 구성 승인을 받는다. 현재 다른 엔진이나 새 의존성은 도입하지 않는다.

검색은 미완료, PR #12는 Draft다. 그 외 P2 기능·최종 성능/접근성 검증도 남아 있다.

## 원격 기록

철회 커밋 `1bb8d8906b1bac0690414a96952ac899faa34da8`을 일반 push했고 PR #12의 제목/설명을 장애·철회·재개 조건으로 수정했다. 원격 PR 최종 diff는 Markdown 13개뿐이며 app/기기 스크립트는 병합 main과 동일하다. 해당 철회 head의 [push CI 39102375613](https://github.com/AtraFelis/ebook_viewer/actions/runs/39102375613)은 마지막 확인 때 진행 중이다. PR의 전체 diff가 문서뿐이어서 새 pull_request CI는 관찰되지 않았다. 후속 기록 전용 head와 앱/복구 검사 소스를 구분한다. 실제 CodeRabbit 리뷰/인라인 지적은 여전히 0건이다.
