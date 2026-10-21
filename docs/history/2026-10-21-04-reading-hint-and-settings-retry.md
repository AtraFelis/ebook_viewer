# 첫 읽기 안내·설정 저장 실패 초안과 재시도

2026-10-21 (Asia/Seoul). 사용자 “둘다 진행해줘”의 구현/검증 기록.

## 기반과 변경

- 현재 체크아웃에서 사용자 변경/원격/브랜치를 확인하고 `git fetch origin` 후 PR #21 MERGED와 최종 CI39565308778/39565304316 SUCCESS를 확인했다. merge519dfb6에서 `codex/design-hint-settings-retry`를 만들었다. 별도 worktree/reset/clean/강제 push 없음. 사용자 `docs/design.md`의 아이콘 시안 변경은 수정/커밋하지 않는다.
- Figma MCP는 사용자 지시대로 사용하지 않았다. 기존 디자인 인계와 앱 공통 스타일을 사용한다. Kotlin/Activity/View/Canvas/PdfRenderer/AtomicFile, 저장 형식과 의존성을 유지한다.
- `ReadingHintState`는 Application 소유 메모리 상태다. 최초 성공 문서 열기에서 한 번 중앙 탭 안내를 표시한다. 첫 탭/스크롤/핀치의 입력을 통과시키며 해제하고, 중앙 첫 탭은 동시에 도구를 연다. Activity 재생성이나 다른 문서 열기는 안내 횟수를 초기화하지 않는다. 새 프로세스의 세션은 다시 안내할 수 있다. 실패한 열기/홈 조작은 성공 열기 횟수를 소비하지 않는다.
- 배경색/표시 설정/문서별 배치 폼은 저장 결과를 기다린다. 실패 시 기존 실제 설정과 입력값을 유지하며 오류·‘다시 적용’을 표시한다. 저장 중 입력/중복 적용을 막고 취소/뒤로는 허용한다. 취소는 이미 요청한 쓰기를 철회하지 않으며 늦은 결과가 창을 다시 열지 않는다. 성공하면 상위 설정으로 돌아가 실제 값을 반영한다.
- 초안은 Bundle로 색상 원문/자동, 테마/밝기/시스템 밝기/화면 유지, 페이지 모드/간격 원문/오류를 Activity 재생성 뒤 복원한다. 새 영속 초안 형식은 만들지 않는다. 페이지 배치는 실제 문서의 라이브러리 저장 성공 뒤 적용하고 합성 데모만 세션 전용으로 적용한다.
- 배치 저장과 회전이 겹칠 때 old Activity의 수명주기 저장이 이전 배치를 뒤에 덮지 않도록 pending write 동안 읽기 저장을 보류한다. 복원 viewport에는 줌/앵커를 유지하되 이전 배치 키를 제외해 직렬 저장 큐의 실제 결과를 따른다. 큐 barrier로 이 경합을 재현하는 검사를 추가했다.

## PC 검사

PowerShell에서 `. ./scripts/android-env.ps1` 후:

```powershell
./gradlew.bat --dependency-verification strict :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck
```

- 최종 **BUILD SUCCESSFUL 10초**, 단위 **51개/실패0/오류0/건너뜀0**, Lint 오류0/경고0/정보6, ktlint 통과. 단위3개는 첫 성공/세션 반복·사전 홈 조작·새 세션을 검증한다.
- 처음 줄바꿈/정렬 스타일 오류는 수동으로 고쳤다. `ktlintFormat`은 실행하지 않았다. 기본 샌드박스의 Gradle cache lock 접근 거부 후 승인된 캐시 접근으로 같은 strict 명령을 실행했다. 의존성 검증 우회 없음.
- app SHA-256 `3588b85876cf44a6458cf2b8a86ef45a824dd3d5faacc6a659269c9943b3068e`, 최종 계측 `bbbba9840d82c5e8045a762e13e956b08bbea2fcb3ab5ada1fa62f5a74f1a691`.

## 기기 검사 경과

- 연결/승인/잠금 해제된 API33 한 대에서 데이터 보존 설치(`adb install --no-streaming -r -t`)했다. 개인 PDF·파일명·본문·URI/기기 식별자는 출력하거나 캡처하지 않는다.
- 최초 guidance6개/실패1: 실제 저장 실패·재시도, 표시 초안/중복, 배치 검증/재시도, 중앙 탭·스크롤 안내는 통과했다. Activity 재생성 반복 검사에서 다음 문서의 준비 전 설정을 누르던1건이 실패했다. 준비/활성/포커스 대기를 보완해 같은 앱에서6개/실패0으로 재통과했다. 최초 결과는 ignored local-artifacts에 보존한다.
- 앞선 앱의 display4개/실패0. 호스트 배치의 LASTEXITCODE 잔존값 체크가 실제 통과 후 멈춘 것은 앱 검사 실패와 구분한다.
- 최초 layout3개/실패1: 기존 helper가 취소 후에도 도구/상위 설정이 유지되는 상태에서 다시 탭해 도구를 닫았다. 열린 도구를 다시 토글하지 않고 기존 상태를 사용하도록 검사만 보정했고, 합성 Activity 종료 후 기록을 삭제하도록 정리 순서를 보완했다. 원본 실패 결과는 보존한다.
- 저장 경합 보강 뒤 최종 앱/계측을 다시 설치했다. guidance는 새 경합1개를 포함한7개다. suite별 자체180초/호스트210초 상한이며 완료 후 앱/계측을 중지한다. 새 전체 기본 기대77개는 실행 수가 아니며 전체/성능/다른 API는 반복하지 않는다.

## 최종 관련 결과와 수용

- 같은 최종 앱/계측으로 `local-artifacts/design-device.ps1 -Mode suite -Suite guidance/layout/settings/immersive/font/display`를 각각 실행해 **7/3/4/7/1/4개, 합26개/실패0**을 확인했다. guidance는 테스트 전용 file supplier의 IOException으로 실제 저장소 실패 경로를 호출하고, 표시/배치는 제어 callback으로 실패·중복·재시도 UI를 검증한다. 실제 디스크 용량/권한을 망가뜨리지 않았다. 별도 배치 큐 barrier/Activity 재생성 검사는 라이브러리 실제 파일의 최종 배치와 복원 초안을 검증한다.
- font1개는 내부100/150/200%×가로/세로6조합의 기존 주요 조작 접근 검사다. 새 오류/안내의 모든 글꼴·해상도 조합을 시각 확인한 것으로 확대하지 않는다. 기존 줌/앵커·정/역방향·설정 상위 복귀·테마/밝기 저장/취소/재실행을 함께 확인했다.
- `local-artifacts/guidance-artifacts.ps1`: 설치 앱 해시가 위 검증 APK와 일치, 원래 font_scale **1.1** 확인. 검사에서 생성한 합성 PDF의 안내와 테스트 전용 배경색 실패 창만 가져와 문구/입력/오류/취소/‘다시 적용’의 잘림·겹침 없이 노출되는 것을 확인했다. 사용자 목록/문서 화면은 수집하지 않았다.
- `local-artifacts/design-device.ps1 -Mode stop`: 앱·계측 force-stop/ADB kill-server와 프로세스 **0** 확인. 자동 반복/예약/백그라운드 계측 없음.
- 사용자는 새 앱의 첫 안내 해제/중앙 첫 탭 도구/배경색·표시·페이지 배치 실제 적용에 **“모두 정상이에요”**로 응답했다. 이 개인 수용은 자동 실패 주입 검사와 구분한다.
- 디자인5단계 구현·관련 통합·사용자 수용 완료. 전체77개/성능/다른 API/실제 OOM/실제 저장장치 고장/모든 화면 조합은 미실행이며 원격 CI/실제 CodeRabbit·사용자 병합은 별도다.
