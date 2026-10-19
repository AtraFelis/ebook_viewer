# P2 최종 통합 — 재현과 판정

실제 통과/실패·APK·미결은 [이력](../history/2026-10-19-03-page-text-accessibility.md)이 기준이다. 이 문서는 실행 절차이며 완료 증거가 아니다. 개인 API 33 한 대 범위로, 개인 PDF/본문을 수집하지 않고 공개 합성 자료만 자동 처리한다. 선택/복사 UI와 검색/목차/비밀번호 PDF 읽기·Figma 전면 적용은 현재 필수에서 제외한다.

## 기능과 본문 안전성

PowerShell에서 `. ./scripts/android-env.ps1` 후 strict `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck`를 실행한다. 최종 기본 suite는 `scripts/device-smoke.ps1`의 실제 기대 개수/결과를 확인하며 별도 `-e suite text`, `-e suite font`, `-e suite sharpnessPixels`, `-e libraryRestart seed`→force-stop→`verify`는 실행별로 집계한다.

본문은 현재/사용자가 지정한 페이지 하나, 고유 격리 인스턴스, 6초 서비스·7초 클라이언트 deadline, 100ms RSS/시스템 메모리 관측, 16,000 UTF-16 결과 상한을 확인한다. [ADR 0015](../adr/0015-bounded-current-page-text-and-accessibility.md)의 샘플링/순간 peak 한계와 취소 수명을 유지한다. 1KiB로 예산을 낮춘 격리 프로세스 종료 검사는 실제 대량 할당/OOM 재현이 아니다. 작은 이미지 430페이지 합성 문서도 사용자 고해상도 문서의 재현이 아니다.

글꼴 suite는 100/150/200%×가로/세로를 실제 기기 configuration에서 확인하고 원래 앱 방향/시스템 배율을 finally로 복원한다. master smoke도 시스템 배율 복원 fallback을 가진다. 버튼/입력의 전체 bounds가 스크롤로 노출되는지 확인하며, 실사용 TalkBack 초점/음성·개인 가독성 수용을 대체하지 않는다. 접근성 이벤트 검사 전에 UiAutomation 연결/앱 AccessibilityManager 활성화를 기다린다. 플랫폼 이벤트 timeout은 다른 앱 화면의 텍스트를 포함할 수 있어 메시지를 그대로 기록하지 않는다.

## 성능 준비

기존 승인 도구와 호스트 Python/Pillow/pypdf로 `scripts/generate-sharpness-fixtures.py`, `scripts/generate-p2-complex-fixture.py`를 실행한다. Android 런타임 의존성 추가가 아니다. profile 앱/계측은 `:app:assembleProfile :app:assembleProfileAndroidTest -Pp0TestBuildType=profile --dependency-verification strict`로 준비한다.

이전 최적화 전 측정용 APK와 최종 profile 앱 및 하나의 공통 profile 계측을 ignored `local-artifacts/p2-integration/{baseline-app,final-app,common-test}.apk`에 동결한다. 기존 APK/보고서를 덮어쓰지 않고 세대별 백업과 SHA를 남긴다. `scripts/run-p2-integration-probe.py`는 설치 APK SHA와 비디버그 보고 필드를 확인한다. 시작 전 승인 기기 한 대/잠금 해제/여유 2GiB 이상/thermal NONE 또는 LIGHT를 요구하며 조건 미달을 통과로 세지 않는다. 2GiB는 측정 준비 조건이고 앱 본문 기능의 512MiB 이상 여유 검사와 별도다.

비교 창은 밝기 0.5·화면 유지로 고정하고 전역 밝기/시간 제한은 변경하지 않는다. 메타데이터 갱신 완료 후 측정하며 도중 전경/가시성을 잃으면 실패한다. font scale·화면 크기/주사율·입력 SHA·열/충전 상태를 기록하고 다르면 같은 조건의 합격 비교로 판단하지 않는다.

## 실행과 수용

각 label `baseline`, `final`을 번갈아 `--mode normal`, `--mode fling`, `--mode zoom32`, `--mode normal --complex`, `--mode frames`로 실행한다. 마지막으로 최종 앱에 `--label final --mode long`을 실행한다. 로그/JSON은 ignored p2-integration의 label/mode별 파일이며 재실행은 먼저 기존 보고서를 보존한다.

- normal: 벡터/단일 공유 이미지 그레이·RGB, 방향별 30표본×3회. 기존 보통 스크롤의 p95≤500ms 또는 느린 기준선 대비 30% 감소를 유지한다. 기존 기준선이 이미 ≤500ms이면 ≤500ms를 유지한다.
- fling/zoom32: 방향별 10표본×3회, 즉 그룹별 30개 이상. 32는 화면 px/PDF point의 내부 최대 배율이며 너비 맞춤 대비 32배라는 뜻이 아니다. 플링 종료를 기다려 마지막 viewport 변화→현재 배율 95% 선명화 onDraw를 기록한다. 1초 초과/미완료 비율이 기준보다 증가하지 않아야 한다.
- complex normal: 1440×2240의 서로 다른 RGB JPEG 12개를 총 48페이지에 배치한 4MiB 이하 공개 자료다. 사용자 문서나 모든 ICC/마스크/JPX/복합 PDF의 대조가 아니다.
- frames: 실제 MainActivity에서 공개 PDF 열기·핀치/플링을 30초×3회. 기준 앱은 자동 배경색이 없는 수동 대체 경로, 최종 앱은 자동 켜짐으로 차이를 명시한다. 최종 p95≤16.7ms/p99≤33.3ms, 보고 누락/캐시·자원 회귀를 확인한다.
- long: 최종 실제 Activity에서 600초×1회. 표본/FD/메모리·캐시 관측과 전경 유지·충돌을 확인한다. 64MiB는 비트맵 캐시 상한이며 총 프로세스 PSS/native 순간 peak 상한이 아니다.

선명도 probe는 Activity 안의 View/worker를 직접 연결한 컴포넌트 계측이며 물리 화면 표시 시각이 아니다. 프레임/장시간 probe는 실제 MainActivity 문서 열기 경로다. 두 결과를 구분하고 기본 기능/화질/프로세스 복원·개인 사용 확인과 함께 판정한다. 개인 파일 제출 없이 테스트 문서에서 실제 TalkBack/페이지 선택/닫기·글꼴 가독성, 평소 PDF 읽기/이어 읽기 체감을 확인받는다. 최종 CI/리뷰의 실제 상태와 사용자 PR 병합·완료 기록을 마친 뒤에만 P2를 종료한다.
