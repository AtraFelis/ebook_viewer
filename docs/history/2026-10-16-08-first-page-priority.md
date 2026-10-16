# 첫 표시 우선: 페이지 조사와 읽기 분리

2026-10-16 (Asia/Seoul). 개인용 SM-N981N/API 33 대상. main `0aa65d3` 위의 로컬 변경을 보존하며 작업, 미커밋·미푸시.

## 원인과 사용자 기준

`./scripts/device-smoke.ps1 -Profile -Probe -Seconds 10 -Runs 1`로 동일 합성 PDF를 분리 측정했다. PdfRenderer 생성 40ms, 100페이지 openPage/크기 순회 및 close 9,601ms. 앱 layout 7,170ms, 첫 고해상도 타일 7,272ms, 전체 가시 타일 8,516ms. 같은 파일이라도 캐시/실행 상태에 따라 시간이 달라진다. 기존 구현은 전체 크기 순회가 끝나야 첫 layout을 게시했다.

사용자는 전체 100페이지 조사가 9.6초 걸리는 것은 읽는 속도에 비해 충분하다고 지적했다. 전체 조사 단축을 완료 기준으로 강요하지 않는다. 첫 페이지를 먼저 읽을 수 있고 이후 조사가 줌/위치를 바꾸지 않는지가 목표다. 기록: `local-artifacts/p0-open-phase-probe.json`.

## 구현

- 첫 4페이지의 정확한 크기를 먼저 제공하고 나머지는 2페이지씩 추가한다. 아직 조사하지 않은 페이지의 크기를 임의로 추정하지 않는다.
- single scheduled worker가 PDF 객체를 계속 독점한다. 렌더 작업도 최대 4타일 후 실행권을 넘겨 페이지 조사와 최신 가시 요청이 번갈아 진행된다. 진행 중 native render는 즉시 취소하지 않는다.
- 기존 페이지의 누적 y는 그대로 유지하고, 문서 최대 너비가 늘면 기존 페이지의 화면상 x를 보정한다. 목록 갱신은 사용자 줌을 다시 fit하지 않는다.
- 복원 시 전체 크기를 먼저 읽어 정확한 경계에서 좌표를 복원한다. 처음에는 중심 페이지 부근까지만 읽는 방식을 구현했으나, 크게 축소한 화면이 여러 페이지에 걸칠 수 있어 경계 clamp가 기존 위치를 옮길 수 있음을 확인하고 전체 크기 확보로 보완했다. 복원은 새 문서의 첫 표시보다 오래 걸릴 수 있다. Bundle의 중심 페이지 인덱스는 진단용 정보이며 현재 복원 좌표는 centerX/centerY/zoom이다.
- 가시 페이지별 긴 변 최대 512px 미리보기를 우선 요청하고 현재 고해상도 타일로 보완한다. 전 페이지 미리 읽기/별도 무제한 캐시를 만들지 않으며 미리보기·이전 배율·현재 타일이 같은 64MiB 캐시를 공유한다.
- 상태 문자열은 전체 페이지 수로 오해하지 않도록 ‘준비된 n페이지’로 표시한다.
- 추가 회귀: 초기 콜백 4페이지→최종 100페이지의 단조 증가 및 줌/중심 좌표 유지; 미완성 고해상도 타일 밖에서 미리보기 픽셀이 남는지 확인한다.

## 실제 검증

- 점진 조사(미리보기 추가 전): assembleProfile/assembleProfileAndroidTest, 단위6개, lintProfile/ktlint strict 통과; 기기9개 실패0.
- 미리보기 추가 후: Kotlin/계측 컴파일, 단위6개, lintProfile/ktlint strict 통과. 최종 기기11개와 동일 문서 3회 측정은 이어서 실행한다.
- 미리보기·점진 조사 회귀 11개, 실패0 통과. 이후 복원 전체 경계 보완과 함께 빌드/단위6개/lintProfile/ktlint strict 통과. 같은 최종 빌드의 기기11개를 재확인한다.
- 최종 복원 보완 후 `./scripts/device-smoke.ps1 -Profile`: **11개, 실패0**. `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict`도 통과했다. final-device.log/final-debug-checks.log에 실제 실행 기록 보존.
- 자료 SHA 및 측정 한계는 [이전 이력](2026-10-16-07-gestures-and-probe.md)을 따른다. firstTileCachedMs는 화면 제출 시각이 아니다. uncoveredPageSamples는 미리보기 또는 모든 가시 고해상도 타일이 준비되지 않은 페이지가 있는 제스처 종료 표본 수이며 공백 면적/지속 시간을 뜻하지 않는다.

점진 조사만 적용한 3회(`local-artifacts/p0-progressive-probe.json`, 미리보기 적용 전): layout 286/433/447ms, 첫 고해상도 타일 341/538/533ms, 전체 가시 타일 1,420/2,371/2,369ms. p95 5.266/5.268/5.353ms, p99 6.512/6.294/6.030ms. 전체 페이지를 기다리던 초기 지연을 해소했다. 고해상도 미완성 표본은 113/115, 112/114, 112/115로 남아 있어 가시 미리보기 경로와 별도의 커버리지 측정을 추가했다.

## 최종 빌드 3회

`local-artifacts/p0-final-probe-30s.json` 및 `p0-final-probe-apk.json`에 자료/빌드 SHA 기록. debuggable=false, 같은 합성 PDF. `./scripts/device-smoke.ps1 -Profile -Probe` 실패0.

- 1회: layout 273ms / 첫 고해상도 타일 486ms / 전체 가시 타일 1,669ms; p95 5.922ms / p99 7.196ms; 미리보기까지 미준비 9/114, 고해상도 미완성 113/114; 최대 캐시 67,069,952 bytes.
- 2회: layout 435ms / 첫 고해상도 타일 818ms / 전체 가시 타일 2,847ms; p95 5.611ms / p99 6.521ms; 미리보기까지 미준비 7/115, 고해상도 미완성 115/115; 최대 캐시 67,055,616 bytes.
- 3회: layout 473ms / 첫 고해상도 타일 869ms / 전체 가시 타일 2,901ms; p95 5.746ms / p99 6.858ms; 미리보기까지 미준비 8/115, 고해상도 미완성 112/115; 최대 캐시 67,076,096 bytes.

최종 기기 회귀 11개/실패0와 debug/profile 검사 통과. 고해상도 타일이 모두 준비되기 전에도 미리보기로 읽는 경로를 픽셀 테스트로 확인했다. 표본 비율을 실제 공백 면적·시간 비율로 바꿔 해석하지 않는다. 최종 10분 재측정 결과와 원격 CI 차단 상태는 [최종 로컬 검증](2026-10-16-09-p0-local-validation.md)을 따른다.

원격 CI는 코드·설정 변경 시 고정 Windows 도구 설치/검사를 수행한다. Markdown만 바뀐 push/PR은 불필요한 앱 검사를 생략한다. 로컬 계측/성능 검증은 그대로 별도 수행하며 CI 성공으로 실기기 성능을 대체하지 않는다.
