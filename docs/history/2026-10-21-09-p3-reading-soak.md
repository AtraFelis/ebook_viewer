# 2026-10-21 — P3 20분 읽기 안정성 점검

사용자가 시간 안내 뒤 진행을 승인했다. 현재 체크아웃 `codex/p3-reading-soak`는 PR #25 head3c62e34 기반이며 사용자 `docs/design.md` 아이콘86줄 변경은 보존/커밋 제외한다. 앱 코드/런타임 의존성/캐시/저장 형식은 변경하지 않는다. 실행 전 [계획과 관측 예산](../plan/p3-reading-soak.md)을 고정했다.

전용 opt-in 계측과 제한 시간/진행 보고/종료를 가진 `scripts/reading-soak.ps1`를 추가했다. 기본 기기 검사91개에는 포함하지 않는다. 최초 ktlint 줄바꿈/표현식 규칙 실패를 수동 수정했다. strict `:app:assembleDebugAndroidTest :app:lintDebug ktlintCheck` 최종 BUILD SUCCESSFUL(5초). 단위53개와 앞선 기기26개는 이전 앱 결과이며 이번에 반복하지 않았다.

준비 단계에서는 실제20분을 아직 실행하지 않아 통과로 보고하지 않았다. 아래는 이후 실제 실행 결과다.

실행 앱 SHA-256 9da3aa5e6a96472ec2e26c6aca7536a60402913022ba676b0fa073e93f8826f8와 새 계측2947c5d2e2c3b400e6678fb56553bcfac9cceafa768f0d33a71a0375e99c6a5a의 설치본 일치를 확인했다. font_scale1.1을 변경하지 않았다. 실행별 보고와 20분 반복을 시작했으며 아직 최종 통과 전이다. 호스트 helper에 계측 해시 대조도 추가했고 PowerShell 구문 검사를 통과했다.

## 실제 실행과 수치

`./scripts/reading-soak.ps1 -Mode install-test` Success 후 `-Mode run`을 한 번 실행했다. `P0 tests=1, failures=0`, 실행 UUID 일치/phase=passed/elapsedSeconds=1203/cycles=40/documentSwitches=10/backgroundReturns=20/recreations=5/samples=20을 확인했다. 실제 20분을 완료한 뒤 종료했고 deadline에 의한 불완료를 통과로 세지 않았다. 스크롤·핀치·잠금 토글, 문서 재열기/홈 복귀/실제 Activity 재생성의 페이지·좌표·줌·배치 비교와 최종 저장 파일 재읽기를 통과했다.

- 동일 백그라운드 단계 PSS 중앙값: 초기4~8분179,530KiB(175.3MiB) → 후반16~20분166,270KiB(162.4MiB), 차이−13,260KiB(−12.95MiB). 사전 증가 예산44,882KiB 이내다. 전체20표본 범위139,905~204,946KiB였다.
- 열린 descriptor 중앙값109→109, 20표본 모두109. 사전 증가 예산16개 이내다.
- 참고용 Java heap3,686,672~5,471,024bytes/native heap720,192~90,047,440bytes. 강제 GC 없이 debug/계측/할당자 변동이 포함된다. 이 값만으로 독립적인 누수 부재 판정을 하지 않는다.
- 관측 타일 캐시 최대67,064,504bytes로64MiB(67,108,864bytes)보다 작았다. 백그라운드20표본의 캐시는 모두0이었다. 관측 시점 사이의 정확한 연속 최대치를 측정한 결과로 확대하지 않는다.

`-Mode collect` 후 target/test pidof가 비어 있고 cache의 soak-fixture 패턴 파일0·원래 font_scale1.1을 확인했다. 합성 기록 삭제는 계측 finally의 library IO 완료로 확인했다. `-Mode stop`으로 앱/계측 force-stop·ADB kill-server를 다시 마친 뒤 PC adb프로세스0을 확인했다. 제한 시간 및 정리 helper의 종료 경로는 첫 명령 실패에도 후속 force-stop/kill을 시도하도록 finally를 사용하며 PowerShell 구문 검사를 통과했다. 반복 런처/예약 작업은 만들지 않았다.

원시 지표/테스트 출력은 ignored local-artifacts/reading-soak-{result.txt,report.json,summary.json}에만 남긴다. 앱 소스는 변경하지 않았고 `:app:testDebugUnitTest`/전체91개/별도 화질·속도 검사를 이번에 반복하지 않았다. 이 합성 벡터100페이지×2/debug/API33의20분 조건에서는 계속 증가하는 PSS/descriptor나 상태 손실을 관측하지 않았다. 복잡한 스캔 자료·모든 PDF·하루 사용·실제 시스템 메모리 압박/OOM/강제 프로세스 종료·다른 기기/API의 검증이 아니다.

## 기준 브랜치와 다음 절차

준비 중 PR #25 실제 CodeRabbit5600863334/12파일/inline 지적0·CI두 건 SUCCESS를 확인했다. 낮은 우선순위 미검증 reflection 제안은 현재 사적 큐 경합 검사/런타임 훅 확대의 비용을 근거로 보류하고 이유/제약 계약과 다른 docstring 비율 권고만을 위한 주석은 추가하지 않았다. 이후 사용자 병합main754ed4d(2026-10-21 23:08 KST) 확인·git fetch origin·기존3c62e34와 main의 전체 트리 diff 없음 확인 후 현재 후속 브랜치를 `git merge --ff-only origin/main`으로 연결했다. 사용자 디자인86줄 변경은 보존한다. 이번 검사/결과는 별도 PR로 게시하고 CI와 실제 리뷰/사용자 병합을 구분해 기록한다.
