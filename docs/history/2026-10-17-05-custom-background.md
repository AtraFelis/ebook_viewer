# P1 — 사용자 지정 외부 배경색, P2 개선 항목 이관

2026-10-17 (Asia/Seoul). 작업 위치 `C:\Users\cotjs\workspace\ebook_viewer`.

## 범위와 현재 상태

사용자는 스크롤 중 흐림→선명도 복구 지연을 문서화하고 P2에서 개선하도록 지정했다. [전용 계획](../plan/p2-sharpness-recovery.md)에 코드로 확인한 구조, 미측정 원인별 시간, 개선 순서와 검증 기준을 구분했다. 자동 배경색은 P1 필수가 아니라는 후속 의견과 위임에 따라 P2로 이관했다. 최종 P1 구현에는 자동 추출 코드/추가 PDF 분석을 포함하지 않는다.

기존 체크아웃에서 `codex/p1-background-color`를 PR #3 head `5c3550b`부터 생성했다. PR #3의 [CI 38929386952](https://github.com/AtraFelis/ebook_viewer/actions/runs/38929386952)는 최종 head에서 성공했고 사용자가 병합했다. `git fetch origin` 및 조상 검사로 main `af90f29`에 부모 head가 포함됨을 확인했다. 에이전트 병합/reset/clean/강제 push 없음.

## 구현

- 읽기/비열람 설정에 외부 배경색 메뉴. 흰색/검정/회색/종이색 버튼, 임의 #RRGGBB 입력·색상 미리보기·적용/취소. 잘못된 입력은 저장하지 않는다.
- 기존 #444444 기본색과 사용자 선택을 별도 AtomicFile 버전 1에 비동기 저장. 기존 방향/읽기 기록 파일과 독립적이며 손상/알 수 없는 버전을 덮어쓰지 않는다([ADR 0004](../adr/0004-custom-reader-background.md)).
- Canvas 외부 영역과 cutout 안전 영역만 변경하고 PDF 픽셀/줌/앵커/타일 캐시/worker 렌더 순서는 유지한다. 새 런타임 의존성 없음.

## 명령과 검증

`. ./scripts/android-env.ps1` 후:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain
./scripts/device-smoke.ps1
```

strict 앱/계측 APK 빌드·단위 15개·Lint 오류/경고 0·ktlintCheck 통과. 초기 긴 줄/스타일 오류는 수동 줄바꿈으로 해결했다. 미사용 이전 방향 제목 리소스의 Lint 오류는 리소스를 제거해 해결했다. 검사 비활성화/ktlintFormat 실행 없음. PDF worker는 변경하지 않았다.

기기가 잠겨 있어 사용자에게 해제를 요청했고 “ㄱㄱ” 응답 후 잠금 해제를 직접 확인했다. API 33 전체 23개 회귀를 실행해 `P0 tests=23, failures=0`과 앱 설치/실행까지 통과했다. 신규 3개는 외부/PDF 픽셀 비교와 줌/앵커/캐시/요청 불변, 별도 파일 보존·AtomicFile 복구·알 수 없는 버전 보존, 읽기/비열람 UI·입력 검증/취소·설정 재실행 보존을 검증한다.

원본: Git 제외 `local-artifacts/background-build.log`, `background-device-full.log`. 개인 PDF/URI/식별자를 공개하지 않는다. 이번 검증에 개인 PDF 손입력·다른 API/기기·강제 프로세스 종료/재부팅과 장시간 성능 측정은 포함하지 않는다. 선명도 복구 지연 개선 및 자동 색상 수용은 P2 미실행이다.

최종 debug APK SHA-256: `c0c0920428d53320523b2156e777c38908328c14f2ac02586213788e8c03c254`.

## 다음 행동

검토 가능한 기능 PR로 분리하며 원격 CI는 실제 실행 결과를 별도 확인한다. P1 전체 완료는 아니며 다음은 간격/크기 모드·페이지 이동/북마크·오류 UX 등 남은 MVP다.
