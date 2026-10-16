# P1 첫 구현 — 최근/읽던 문서와 이어 읽기

2026-10-16~17 (Asia/Seoul). 사용자 “Sol High로 진행하면 좋은 범위로 개발 진행” 요청으로 P1-1의 목록/위치 저장을 첫 구현 단위로 선택했다. 현재 main 체크아웃과 기존 P1 계획 변경을 보존했다. 시작 HEAD `08011b3`, 이번 변경은 로컬 미커밋·미푸시다. worktree/reset/clean/강제 push 없음.

## 구현

- 플랫폼 Activity/View/PdfRenderer와 기존 런타임 구성을 유지한다. 새 저장 라이브러리 없이 Application 소유 단일 I/O 큐 + AtomicFile + 버전 1 코덱을 사용한다([ADR 0002](../adr/0002-local-reading-records.md)).
- 최근 PDF와 읽던 Ebook 목록이 URI 기준 단일 기록을 공유하며 마지막 열기 순서로 표시된다. 문서명·페이지 수·페이지 내부 정규화 중심 좌표·줌을 저장하고 목록에서 이어 연다. 서로 다른 URI의 동일 원본을 자동 해시 판별하지는 않는다.
- SAF 결과의 읽기/영구 권한을 처리하고 임시 권한이면 표시한다. 열기 실패 안내에서 파일 다시 선택을 제공한다. 재연결은 새 파일이 열린 후 기록을 바꾸며, 원래 파일은 수정/삭제하지 않는다. 목록 항목을 길게 누르면 기록만 삭제한다.
- 이동 중 1초 간격으로 저장을 예약하고 문서 전환/목록/백그라운드/인스턴스 저장 시 현재 상태를 제출한다. 저장·파일 정보 조회·권한 요청은 UI 스레드 밖에서 실행한다. 닫기/목록 이동은 문서 작업을 무효화하고 worker 순서로 renderer를 닫는다.
- 회전 Bundle과 영구 기록은 같은 페이지 내부 좌표를 사용한다. 저장 기록의 복원은 정확한 전체 페이지 크기 확보 후 수행하며 사용자 줌을 너비 맞춤으로 초기화하지 않는다.
- 손상된 기록/알 수 없는 버전을 빈 목록으로 덮어쓰지 않는다. 실패한 기존 PDF 열기는 이전 위치를 지우지 않는다. 갑작스러운 종료 직전의 아직 제출/완료되지 않은 이동과 손상 기록의 복구 UI는 보장하지 않는다.

## 문서/API 확인

Context7 서버 검색 → resolve(Android Developers) → query(SAF 영구 권한/표시 이름) 실행. 반환 자료는 주로 AndroidX FileProvider여서 직접적인 SAF 근거가 부족했다. 새 AndroidX 의존성을 채택하지 않았다. 공식 [SAF 문서](https://developer.android.com/training/data-storage/shared/documents-files)와 [AtomicFile API](https://developer.android.com/reference/android/util/AtomicFile)를 확인했다. 반환 flags로 읽기 권한을 확보하고 이동/삭제 후 재선택이 필요할 수 있으며 AtomicFile 자체가 작업 직렬화를 제공하지 않는 점을 구현에 반영했다. compileSdk 35/minSdk 23 빌드이며 현재 API 33만 기기 검증 대상이다.

## 실제 명령과 발견한 문제

PowerShell, `C:\Users\cotjs\workspace\ebook_viewer`, 먼저 `. ./scripts/android-env.ps1`.

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain
./gradlew.bat ktlintFormat --dependency-verification strict --console=plain
./scripts/device-smoke.ps1
```

기본 sandbox의 사용자 Gradle 캐시 lock 접근이 거부되어 승인된 확장 실행으로 검증했다. 첫 ktlintCheck의 새 코드 스타일 오류를 명시적 ktlintFormat으로 정리했다. 첫 Lint는 상태 문자열 결합(SetTextI18n) 1건으로 실패했고 리소스 포맷으로 수정했다. baseline/검사 비활성화/검증 우회 없음.

사용자 기기가 처음 보이지 않아 연결을 요청했고 “연결했어” 이후 API 33 authorized 상태를 식별자 출력 없이 확인했다. 첫 device-smoke 호출은 adb shell 실패로 중단돼 통과 처리하지 않았다. 설치된 APK를 직접 계측한 다음 실행은 기존 11개 + 신규 저장 2개 통과, 신규 UI 2개 실패(총 15개 중 2개 실패)였다. 원인은 새 문서를 전체 크기 조사 완료 전 닫거나 전환하면 기록을 저장하지 않던 조건이다. 첫 표시부터 유효 위치와 전체 페이지 수를 저장하도록 수정했다. 실패 로그도 ignored local-artifacts에 유지한다.

## 최종 검증 상태

- strict debug 앱/계측 APK 빌드, 단위 9개, Lint 오류/경고 0, ktlintCheck 통과.
- API 33 전체 15개 계측: 수정 후 `P0 tests=15, failures=0` 통과. 기존 11개, 원자 저장/손상 기록 2개, A/B 목록 재열기/파일 삭제 오류 2개다. 역사적 runner 이름/출력의 P0 접두사는 그대로 사용한다.
- 별도 프로세스 복원: seed 계측 → `adb shell am force-stop dev.example.ebookviewer.p0` → verify 계측 통과. 실제 다른 PID임을 assert했고 각 단계 `P0 tests=1, failures=0`. 개인 자료 대신 자체 합성 PDF와 기대 좌표만 앱 cache에 두고 검증 후 해당 테스트 기록/자료만 삭제했다. 강제 종료 직전의 미완료 저장을 보장하는 테스트는 아니다.
- 최종 앱 시작 `Status: ok`, COLD TotalTime 270ms. 이는 Activity 시작 시간이며 PDF 첫 렌더 시간/성능 합격 근거가 아니다. 최종 debug APK SHA-256 `8b5e33f342e896ff66f5ae292393c333d4ae3d830e516beabda42bd1d0a95f1c`.
- `git diff --check`와 Markdown 상대 링크 검사 통과. 원격 CI/새 profile 성능 측정은 실행하지 않았다.

추가 명령(프로젝트 SDK adb 사용, 기기 식별자 생략):

```powershell
adb shell am instrument -w dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation
adb shell am instrument -w -e libraryRestart seed dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation
adb shell am force-stop dev.example.ebookviewer.p0
adb shell am instrument -w -e libraryRestart verify dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation
```

원본 로그는 `local-artifacts/p1-build-checks.log`, `p1-device-tests.log`, `p1-device-before-save-fix.log`, `p1-device-final.log`, `p1-restart-seed.log`, `p1-restart-verify.log`에 보존하며 Git에 넣지 않는다. 앱 PDF/URI/개인 제목을 로그로 출력하는 코드는 추가하지 않았다. lockfile/verification-metadata/런타임 버전 변경 없음.

## 남은 작업

P1 전체 완료 아님. 전체 화면/중앙 탭 설정/방향 고정이 다음 구현 단위다. 이후 외부 배경색, 간격/크기 모드, 페이지 이동/북마크를 진행한다. provider별 영구 권한/재부팅, 비탐색 provider의 용량 상한·취소 가능한 임시 복사, 실제 개인 PDF와 손입력 수용은 미검증/미구현으로 남긴다. 파일 오류 안내는 현재 원인을 구분하지 않는 안전한 공통 안내다. 다른 API/기기·P1 원격 CI도 미실행이다. 사용자 정정에 따라 별도 성능 최적화/10분 벤치마크는 반복하지 않았다.
