# P2 검색 진단 — 메인 looper에 의존하던 종료 경로 수정

2026-10-18 (Asia/Seoul). 작업 경로 `C:\Users\cotjs\workspace\ebook_viewer`.

## 확인과 변경

현재 `codex/p2-platform-text`/cdff4ab에서 사용자 Figma 문서 변경을 보존했다. 앞선 구현 fd16190의 PR/push CI 39128387162/39128385242와 문서 head cdff4ab의 CI 39128581528이 모두 성공했다. CodeRabbit 요청 6250044688의 접수 답변 6250054637은 이후 **Action not completed / Head commit changed**로 바뀌었다. 실제 리뷰/인라인 지적은 0건이다. 접수 당시 기록과 현재 취소 상태를 구분한다.

코드 점검에서 별도 진단 APK의 `terminate()`가 메인 Handler에 프로세스 종료를 예약함을 확인했다. 메인 looper가 막히면 감시자가 예산 초과를 알아도 종료 예약을 처리하지 못할 수 있다. 종료를 별도 daemon 스레드로 옮기고 terminal IPC 전부터 30ms 후 자기 종료를 예약한다. 읽기 앱의 원래 LOW_MEMORY 원인을 발견한 것이 아니라 진단 안전장치의 결함을 수정한 것이다.

guard를 7→9개로 확대했다. `stall-main`은 별도 자식의 메인 looper와 worker만 Java sleep으로 대기시키고 150ms 기한의 감시 종료를 요구한다. `abandon`은 자식 worker 대기 확인 후 부모가 마지막 바인딩을 해제하고 2초 내 Binder death를 요구한다. 부모가 취소 명령/ADB 종료를 보내 완료로 만드는 검사가 아니다. 서비스 main은 이 검사에서 정상이며 `onDestroy`가 자기 종료한다. 부모는 연결 콜백만으로 종료를 판정하지 않는다. 호스트는 대기/부모 unbind 증거와 회수 기한을 확인하고, 설치 전/후에 같은 메모리 기준을 검사한다.

[공식 수명 문서](https://developer.android.com/develop/background-work/services/bound-services)와 Context7 `/websites/developer_android_guide`에서 마지막 unbind 후 서비스 파괴와 정상 unbind에 `onServiceDisconnected`가 호출되지 않음을 대조했다. 문서가 프로세스의 즉시 종료나 막힌 VM의 2초 회수를 보장한다고 해석하지 않는다.

## 실제 검증

- `python scripts/test-search-probe-runner.py`: **16개 통과**. 부모 회수/메인 대기 증거가 없는 보고서 거부와 설치 전 부족한 메모리에서 설치 금지를 포함한다. PC mock 검사이며 기기 Binder 동작의 대체가 아니다.
- `. ./scripts/android-env.ps1` 후 `./gradlew.bat :searchprobe:assembleDebug :searchprobe:lintDebug ktlintCheck --dependency-verification strict --console=plain`: **성공**, 45 tasks, 진단 Lint 오류/경고 0. 읽기 앱 소스/의존성 불변이며 기존 앱 단위/기기 41개를 이번에 다시 실행하지 않았다. ktlintFormat 미실행.
- 진단 APK 236,245 bytes, SHA-256 `0ba73ec7f0241bcf986d9e81d423c770faf839a666b74dec9d606e2831e41886`. 이전 이력의 APK와 구분한다.
- 설치 전 MemAvailable 2,254,880KiB로 고정 기준 2,228,224KiB를 만족한 경우에만 진단 APK를 설치했다. 설치 후 같은 gate에 걸려 **guard/PDF 호출 전 거부**됐다. 재설치 없이 한 번 확인한 2,123,064KiB도 미달이어서 작업을 실행하지 않았다. 이 거부를 시스템 OOM이나 보호 검사의 기기 통과로 세지 않는다.

위 거부 시점에는 기기 9개 guard/430페이지 검사를 실행하지 않았다. 이후 아래와 같이 같은 기준에서 실행했다. 개인 PDF 재검색·읽기 APK 재설치/초기화·다른 앱 강제 종료·기기 재부팅은 하지 않았다. 예산을 통과하도록 낮추거나 올리지 않았다. 검색은 계속 비활성화이며 원인/해결 및 P2 완료를 선언하지 않는다.

## 후속 기기 실행 — 같은 기준에서 통과

최종 정리 전 MemAvailable 2,248,408KiB로 호스트 기준을 회복해 설치된 같은 APK를 재설치 없이 실행했다. runner의 전체 guard/두 번째 메모리 gate를 유지했다. **기기 guard 9개 모두 통과**: ping, worker 시간 초과, main looper 대기 시간 초과, 취소, RSS 증가 예산, 자식 종료, 부모 unbind, 입력 크기 제한, 새 연결 ping이다. 부모 guard FD는 85→85였고 `stall-main`은 실제 대기 확인·TIME_BUDGET·Binder death를 관측했다(연결 포함 case 495ms). 부모 unbind는 취소 메시지 없이 **107ms** 후 Binder death를 관측했다. main looper가 정상인 이 검사를 VM 전체 정지 회수 보장으로 확대하지 않는다.

`images-430.pdf`/`single-keep`/두 ASCII 문자 `ne`에서 **430페이지/결과 0건/OK**를 확인했다. 검색 루프 경과 705ms, renderer 시작/정리·IPC/종료 대기까지 포함한 부모 case 1,562ms다. 자식 시작 RSS 76,032KiB, 표본/감시 최고 관측 101,928KiB, 마지막 PSS 38,562KiB, 감시 최소 MemAvailable 2,211,004KiB로 고정한 예산 안이었다. RSS는 실제 순간 peak가 아니며 Java/native/private 할당과 동일하지 않다.

고유 이미지 430개가 각각 32×32 RGB인 1회 합성 표본이며, 원래 고해상도 스캔 자료의 복잡도/압축·구조를 재현한 것이 아니다. 이 조건에서는 페이지 수/두 글자 질의만으로 장애를 재현하지 못했다. 여러 모드/반복/고해상도 성능 합격이나 원인 확정은 아니다.

진단 APK만 force-stop/uninstall했고 읽기 앱은 실행 중/동일 PID를 유지했다. 앱 자료/설정/개인 파일을 변경하지 않았다. ignored 집계 증거는 `local-artifacts/search-isolation/recovery-images-430.json`, `recovery-final.json`, strict 빌드 로그 `local-artifacts/search-isolation-recovery-build.log`다.

## 남은 일

고해상도/압축/객체 구조와 원래 장애의 관계, VM 전체 정지/급격한 native 할당의 한계, 제품 검색 프로세스·수명 통합과 개인 수용은 여전히 남아 있다. 대표적인 고해상도 자료를 원래 사용자 기기에 무작정 재생하지 않고 통제 가능한 실험 환경/자료를 먼저 정한다. 이번 수정의 원격 CI/실제 리뷰는 push 이후 별도로 확인한다. 모든 변경/기록 push 뒤 안정된 head에 한 번 요청하고 리뷰 중 기록만의 추가 push로 취소시키지 않는다.
