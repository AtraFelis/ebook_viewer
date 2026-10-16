# P0 현재 기기 검증과 원격 CI 상태

2026-10-16 (Asia/Seoul). 사용자 결정에 따라 개인용 SM-N981N/API 33만 이번 판정 대상이다. API 23·35와 다른 기기는 후속 미검증으로 유지한다. Compose 셸 최종 선택과 새 런타임 의존성은 승인 범위에 추가하지 않았다.

## 최종 소스 검사

작업 위치 `C:\Users\cotjs\workspace\ebook_viewer`. `. ./scripts/android-env.ps1` 후 실행했다.

- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict`: 통과. 단위 6개, Lint 오류/경고 0.
- profile 빌드/계측 APK와 lintProfile/ktlint strict: 통과. profile은 debuggable=false, 로컬 debug 서명이며 출시물이 아니다.
- `./scripts/device-smoke.ps1 -Profile`: 최종 복원 보완을 포함한 기기 **11개/실패0**, Activity 실행 성공. 이미지 PDF, 500페이지 메타데이터, 취소 FD 해제, 실제 worker 타일, 손상 파일, Activity 재생성, 핀치·20페이지 왕복, 방향 변경, 점진 목록/줌/좌표, 미리보기 픽셀 포함. 주입 제스처/요청 방향 변경이며 손가락 입력·물리 센서·프로세스 강제 종료 검증과 구분한다.
- `./scripts/device-smoke.ps1 -Profile -Probe`: 동일 합성 PDF, 30초×3회 통과. 첫 고해상도 타일 486/818/869ms; p95 5.922/5.611/5.746ms; 캐시 64MiB 이내. 상세 수치와 한계는 [첫 표시 이력](2026-10-16-08-first-page-priority.md).
- `./scripts/device-smoke.ps1 -Profile -Probe -Seconds 600 -Runs 1`: 최종 600,062ms 1회 완료, 실패0/OOM 없음. 22,267 frames, p95 6.692ms/p99 7.322ms, 50ms 초과1, refresh 예산 초과10, 보고 누락0. 첫 layout 502ms / 고해상도 타일880ms / 전체 가시 타일2,915ms. 최대 캐시67,107,792 bytes(64MiB 이내).
- 10분 PSS 시작87,910→종료159,765KiB; 30초 표본 최고302,958KiB(약296MiB). 후반 표본128,845~226,527KiB로 변동하며 지속적인 단조 증가는 관찰하지 않았다. FD106→135는 초기 그래픽스 할당 등을 포함하므로 누수 없음으로 확정하지 않는다. 자원 해제는 문서 교체 FD 회귀와 별도 판정한다.
- 10분 제스처 종료 표본: 고해상도 미완성907/2,456, 미리보기까지 미준비426/2,456. 빠른 이동의 일부 미준비 영역은 남아 있다. 이 비율을 공백 면적·시간 비율로 해석하지 않는다. 장시간3회/모든 PDF 안정성 검증을 완료한 것은 아니다.
- 중간 시스템 dumpsys gfxinfo 별도 표본: 18,521 frames, Janky frames9(0.05%), p95/p99 각6ms. 프로세스 시작/준비 구간을 포함하므로 FrameMetrics의 정확한 10분 구간과 동일하다고 합치지 않는다. 원본 출력은 Git 제외 파일에 보존한다.

최종 profile APK SHA-256 `202a9494161557daf974f7fd8d3c29fa46fdd42ad445246f72c323a875ec9884`. 합성 자료 SHA-256 `ec89d9379bf880bd8e5023d63223ef23e41a9e896c3bd65d08f2eb67e6bf0963`. Git 제외 `local-artifacts/p0-final-probe-apk.json`, `p0-final-probe-30s.json`, `p0-final-probe-10min.json`에 원본 기록. 기기 식별자·개인 PDF·본문은 기록하지 않는다.

## Git 보존과 승인 검토

`git fetch origin` 후 HEAD와 origin/main은 `0aa65d3`이며 원격 추가 커밋/divergence 없음. 기존 체크아웃 main 사용, reset/clean/강제 push 없음. SDK/APK/PDF/키는 제외하고 소스·도구 잠금·문서 51개 파일을 스테이징했다. gradlew 실행 비트 지정, staged diff 공백 검사 통과.

GitHub Actions API는 enabled=true를 반환했다. 워크플로는 Windows 도구 설치와 strict 빌드/단위/Lint/ktlint를 수행하며 Markdown만 변경된 경우 앱 검사를 생략한다. 실제 원격 CI는 아직 미실행이다.

`git commit ...`와 `git push origin main` 실행 요청은 **자동 승인 검토에서 CreateProcess 이전에 거절**됐다. 검토 이유는 기본 브랜치 main에 앱·도구·문서를 포함한 대규모 변경을 직접 push하는 범위에 대한 명시적 사용자 승인 근거가 부족하다는 것이다. 커밋과 push는 실행되지 않았고 HEAD는 그대로다. 다른 브랜치/우회 명령으로 이 거절을 우회하지 않는다. 전체 변경을 검토할 수 있게 유지하고 해당 main push 범위를 승인받은 뒤 원격 CI를 실행한다.

최종 검증 기록까지 갱신한 커밋 대상은 52개 파일이다. APK SHA가 실제 측정 빌드와 동일함을 재확인했다. 실행 스크립트 구문·문서 상대 링크·staged 공백 검사를 확인하고 승인 요청 시점의 변경으로 준비한다. SDK/빌드/PDF/서명 키는 포함하지 않는다.

이후 사용자가 해당 범위를 확인하고 “push 해”로 명시 승인했다. main/원격 재확인 결과 ahead/behind 0/0, 추가 unstaged/untracked 변경 없음. 승인된 변경의 일반 push와 실제 원격 CI를 진행한다. 위 거절 기록은 과거 실행 시점의 상태다.

## 남은 제한

판정: 현재 기기·합성 자료의 로컬 P0 기술 검증 완료. 첫 타일 준비/프레임/캐시의 잠정 수치 기준을 충족했고 10분 실행을 완주했다. P0 전체의 원격 CI 확인은 아직 남아 있으며 새 환경 복원 완료도 선언하지 않는다. 빠른 이동의 완전한 공백 제거/사용자 손입력 품질은 별도 보완·수용 검증이다.

비탐색 provider 임시 복사, 세분화한 오류/재시도, API 33 암호화 문서 읽기, 북마크/최근 문서/설정·접근성은 미구현이다. 회전 복원은 정확한 전체 크기를 먼저 읽으므로 새 문서 첫 표시보다 오래 걸릴 수 있다. 합성 문서 측정을 개인의 모든 PDF 성능으로 일반화하지 않는다. 새 PC 완전 복원과 원격 CI는 로컬 같은 환경 bootstrap 재실행과 구분한다.
