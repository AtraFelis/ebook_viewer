# 2026-10-19 — P2 자동 페이지 외부 배경색

## 기준과 보존

사용자의 계속 진행 요청으로 자동 배경색을 구현한다. `git fetch origin` 후 PR #13 사용자 병합(2026-10-19 KST)과 origin/main 3043b7d를 확인했다. 이전 코드 수정 43c7c76의 두 CI 및 마지막 문서 head e8c48b5의 PR CI 39138224551도 성공했다. 기존 Figma 미커밋 SESSION/CHECKLIST 블록은 ignored patch+이름 붙인 stash로 보존한 뒤 main을 `git pull --ff-only origin main`으로 동기화하고 현재 체크아웃에 `codex/p2-auto-background`를 만들었다. 동일 사용자 블록을 재적용하고 stash는 복구용으로 남겼다. 두 Figma 이력 파일의 SHA-256은 이전과 동일하다. 사용자 변경은 이번 PR에서 제외한다.

## 변경

[ADR 0013](../adr/0013-automatic-page-background.md): 자동 모드를 선택/저장하고 기존 수동 RGB는 대체색으로 유지한다. 기존 `.background` v1을 수동으로 읽고 v2 모드를 기록하며, 기존 설치를 자동으로 강제 전환하지 않는다. 손상/알 수 없는 버전·모드의 기존 파일을 덮어쓰지 않는 저장 정책을 유지한다.

기존 캐시의 전체 페이지 미리보기만 읽기 전용으로 참조한다. 별도 낮은 우선순위 worker가 가장자리 128점을 분석하며 실행 1개+최신 대기 1개/색 결과 16개로 제한한다. 픽셀을 수정/recycle하거나 새 PDF 렌더/파일 IO를 요청하지 않는다. 비트맵 캐시 64MiB와 별도로 최대 2MiB의 이미 게시된 미리보기 수명이 잠깐 연장될 수 있다. 24dp 경계 완충/120ms 후보 유지 뒤 전환하며 표본 부족/복잡한 가장자리는 지정 대체색을 쓴다. reset/모드 변경/trim/비열람/detach에서 취소·세대 무효화·executor 종료한다. PDF 픽셀·줌/앵커는 유지한다.

Context7 첫 Android SDK 검색은 무관한 Firebase 등의 결과여서 채택하지 않았다. Android Developers 검색으로 `/websites/developer_android_reference`를 선택해 Bitmap 픽셀·recycle 자료를 조회하고 [공식 Bitmap API](https://developer.android.com/reference/android/graphics/Bitmap)와 설치 SDK 35 `javap`의 getPixel/getPixels/isRecycled/recycle 서명을 대조했다. 버전 고정 문서/다른 API 기기 실행을 보장하는 자료가 아니다. 현재 소프트웨어 ARGB_8888 읽기 전용 bitmap 소유권을 사용하며 새 런타임 의존성/스택/SDK 변경은 없다.

## 실제 검사

`. ./scripts/android-env.ps1` 뒤 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain` 통과. 단위 **37개/실패 0**(기존 29 + v1/잘못된 모드 2 + 추출/대표 페이지 6), Lint 오류/경고 0, ktlint 통과. 첫 수동 코드의 줄바꿈 규칙 실패와 합성 테스트의 허용 간격 초과(100pt, 계약 최대 72pt)를 수정해 전체 명령을 재실행했다. ktlintFormat/검사 억제/스택 업그레이드는 하지 않았다.

기기 시작 전 승인 1대·API 33·잠금 해제와 MemAvailable 2,580,784KiB를 확인했다. `./scripts/device-smoke.ps1`의 기본 suite는 기존 41개를 유지하고 자동 배경색/PDF 픽셀·요청 불변, 분석 종료/늦은 결과, 자동/수동 UI·재실행 3개를 더해 **44개를 기대**한다. 결과/성능/사용자 수용·원격 CI는 아래 후속 실제 결과로 갱신하며 이 기대값을 통과 결과로 취급하지 않는다.

이후 실제 `./scripts/device-smoke.ps1`은 업데이트 설치와 API 33 **44개/실패 0**, 앱 실행을 확인했다. 새 테스트는 대표 미리보기의 종이색을 외부에만 반영/PDF 중앙 검정 픽셀 유지·줌/앵커/캐시/렌더 요청 불변과 reset 뒤 색 미노출, analyzer의 main 결과/취소 후 늦은 callback 무효화·executor 종료, 자동 선택/재실행/수동 복귀의 기존 RGB 보존을 확인했다. 테스트 전후 기존 사용자 배경 설정을 repository로 복원했다. 개인 PDF의 색 어울림이나 최종 P2 접근성 수용을 대신하지 않는다.

기능 검사 app SHA-256 `f7ad8a2f4b382f077f10e81c7722af8b5076456e78c32b01466e8be93187d84f`, 기본 44개 계측 APK `66beb0f6686ed01a243c7f14f64f4281d39293cc109970ee55c31c3c3a37134e`이다. 이후 기존 frame probe에 강제 수동/자동 선택을 명시하는 opt-in 코드만 보완했으며 기본 44개 경로/앱 runtime은 바꾸지 않았다. 최신 계측 빌드/Lint와 같은 profile APK의 전후 성능 검사 결과는 구분한다.

## 같은 profile APK의 수동/자동 대조

`./scripts/device-smoke.ps1 -Profile -Probe -Seconds 30 -Runs 3`으로 수동 모드부터 측정했다. 기존 사용자 설정과 무관하게 View에만 동일 기본색/수동을 적용하고 저장 파일을 변경하지 않았다. 자체 합성 URI의 이전 읽기 기록은 기존 probe의 직렬 repository로 복원한다. 첫 실행 전 앱 자체만 force-stop하고 MemAvailable 2,209,640KiB/thermal status 1을 확인했다.

수동 3회 p95 6.204214/6.058649/6.524550ms, p99 7.359100/7.312765/7.333108ms, cache 최대 67,028,992bytes로 기존 목표(16.7/33.3ms, 64MiB) 안이었다. FD 구간 전후는 140→135/143→138/143→138로 증가하지 않았다. 이어서 같은 설치 APK/같은 문서 bytes로 `am instrument -w -e probe true -e seconds 30 -e runs 3 -e autoBackground true .../P0Instrumentation`을 실행했다. 자동 시작 전 MemAvailable 2,523,560KiB/thermal status 1을 확인했다. 자동 결과와 최종 판정은 아래 후속 결과를 따른다.

자동 측정 중 별도 호스트 경과 시간/파일 tail 조회 한 건은 자동 승인 검토 서비스의 `Selected model is at capacity`로 실행되지 않았다. 위험 판정이 아니라 검토 처리 실패였으며 승인 검사를 우회하지 않았다. 이미 승인돼 실행 중인 계측은 계속됐고 그 결과를 별도 확인한다.

자동 3회도 실제 **1개 계측 테스트/실패 0**으로 끝났다. p95 6.259772/6.520094/6.810907ms, p99 15.603182/7.718032/8.654917ms, cache 최대 **67,108,864bytes**였다. FD 구간 전후는 수동과 같은 140→135/143→138/143→138이며 두 모드의 droppedReports 모두 0이다. 양쪽 동일 fixture SHA `ec89d9379bf880bd8e5023d63223ef23e41a9e896c3bd65d08f2eb67e6bf0963`, debuggable=false/60.000004Hz/30초×3회였다. 시작 thermal status는 모두 1이지만 CPU/배경 실행/충전을 완전 통제한 실험은 아니다. 자동 첫 p99가 증가했으며 원인을 단일 샘플링 함수로 확정하거나 오버헤드 0으로 보고하지 않는다.

수동 PSS 전후(KiB): 180,702→249,487 / 248,365→283,041 / 249,733→253,532. 자동: 178,492→272,128 / 240,647→283,658 / 238,293→189,236. 실제 순간 peak/네이티브 hard cap 측정이 아니다. 이번 합성 warm 플링/핀치 프레임·64MiB 캐시 목표는 충족했지만 새 선명도 95% 지연 분포나 색이 다른 복잡한 이미지 문서/최종 P2 통합의 성능 합격을 대신하지 않는다.

공통 profile app SHA-256 `41850cd1b023161c360dbe9473acf55119301f5359d6f9b43f00704c125404b1`, 공통 계측 APK `c86c23c8d061f8f60bb0d0443d1b046887a5ec1fcb1a23fa9e24549cb96bdda8`을 파일 해시로 대조했다. 자동은 수동 뒤 같은 설치본을 재설치하지 않고 실행했다. 원시 자체 합성 보고서는 ignored `local-artifacts/auto-background-frames-{manual,automatic}.json`/동명 log에 보존했다. 사용자 PDF/본문·기기 식별자는 수집하지 않았다.

## 잔여 작업과 도구 차단

기본 44개 뒤 같은 View의 두 색 페이지에서 경계 완충 구간 안 유지→구간 밖 전환→역방향 유지/복귀를 검사하도록 기존 자동 배경색 테스트 1개를 보강했다. `:app:assembleDebugAndroidTest ktlintCheck --dependency-verification strict`는 통과했다. 이 보강을 포함하는 배경 suite 6개는 아직 기기에서 실행하지 않았다. 먼저 성공한 44개 결과와 혼동하지 않는다.

성능 종료 뒤 최종 debug 앱/최신 계측을 업데이트 설치하고 `-e suite background` 6개를 재실행하는 명령은 자동 승인 검토 서비스가 **Selected model is at capacity / action was not executed**로 거절해 시작되지 않았다. 이후 PC의 최신 `:app:assembleDebugAndroidTest :app:lintDebug ktlintCheck`도 같은 서비스 처리 실패로 시작되지 않았다. 앞선 정상 Lint/단위/기기/성능 통과는 유효하나 이 두 후속 검사를 통과 처리하지 않는다. 위험하다는 판단으로 차단된 것은 아니고 승인 검토 처리 자체의 실패이며, 다른 실행 경로나 승인 검증 해제로 우회하지 않았다.

현재 설치본은 위 profile app이며 debug로 바뀌지 않았다. 사용자가 직접 앱을 열어 자동 모드의 어울림과 수동 복귀를 확인하도록 질문했다. 사용자 응답 전 추가 기기 작업도 진행하지 않는다. 커밋/push/새 PR/CodeRabbit 요청·새 원격 CI는 아직 하지 않았다. 후속은 검토 서비스 복구→사용자 수용 확인→추가 기기 검사/최신 Lint→정확한 결과 기록/일반 커밋·push/PR→수동 CodeRabbit 및 CI 확인이다. PC에서 준비한 코드·문서와 ignored PR 본문은 보존하며 기존 Figma 변경을 포함하지 않는다.

## 사용자 수용 및 응답 후 재시도

사용자는 현재 설치된 profile 앱의 개인 PDF에서 **“이 정도면 괜찮은 것 같아. 수동으로 돌려도 지정 색으로 돌아와.”**라고 확인했다. 자동 배경색의 주관적 어울림과 수동 지정색 복귀 수용을 완료로 기록한다. 파일명/본문/이미지는 받지 않았고 합성 자동 검증/임의 문서의 보장이나 전체 P2 수용과 구분한다.

응답 후 현재 계약→SESSION→CHECKLIST→ADR 0013과 Git 상태를 다시 확인해 기존 코드/Figma 변경이 보존된 것을 확인했다. `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain`을 정상 승인 경로로 재시도했다. 자동 승인 검토 서비스는 다시 `Selected model is at capacity / action was not executed`를 반환해 명령을 시작하지 않았다. 실행 옵션을 바꾸거나 다른 경로로 승인 검사를 우회하지 않았다. 최신 Lint/보강 기기 검사가 남아 있어 커밋·push·PR 게시는 계속 보류한다. 재개 시 사용자 수용 질문은 반복하지 않고, 검토 서비스 복구 뒤 남은 검사/게시 절차만 진행한다.

## 권한 변경 후 검증 완료·원격 단계 대기

사용자가 권한을 변경하고 계속 진행하도록 요청했다. 같은 승인 요청 경로에서 최신 `:app:assembleDebugAndroidTest :app:lintDebug ktlintCheck --dependency-verification strict`가 정상 실행/통과했다. 데이터 보존 `adb install -r`로 debug 앱/계측 업데이트 후 배경 suite 6개를 처음 실행한 결과는 **실패 1개**였다. `automaticPreviewChangesOnlyOutsideAndResetDropsDocumentColors`에서 경계 전환 대기 assertion이 실패했다. 테스트가 정방향 0.05×800=40px 이동을 고정했고, 실제 `wm density`는 450dpi여서 24dp 완충 구간은 zoom=1에서 67.5px였다. 앱의 dp→문서 좌표 변환을 유지하고 테스트의 구간 안/밖 거리를 density/실제 zoom에 맞춰 계산하도록 고쳤다. 검사를 제거하거나 앱의 안정화 기준을 완화하지 않았다.

수정 뒤 같은 strict 계측 빌드·Lint 오류/경고 0·ktlint를 통과했다. 새 계측만 `install -r` 후 `am instrument -w -e suite background .../P0Instrumentation`을 재실행해 **6개/실패 0**을 확인했다. 두 색 페이지의 경계 안 유지/밖 전환과 역방향 복귀가 실제 View에서 통과했다. 최종 app SHA-256은 기존 기본 44개와 동일한 `f7ad8a2f4b382f077f10e81c7722af8b5076456e78c32b01466e8be93187d84f`, 새 계측은 `031a2f82b14d36d6f5ae93aa4c6753a2fccd686dbdecfb29dad47b728bf6d835`다. 기본 44개 당시 계측 66beb0f6/성능 계측 c86c23c8과 구분하며 하나의 50개 실행으로 합치지 않는다. 앱 실행 Status ok와 최종 debug 설치를 확인했다. 앱 runtime은 바뀌지 않아 이미 완료한 개인 수용/단위/기본 기기·profile 비교를 반복하지 않았다.

원격 단계 준비를 위해 `git fetch origin`/`gh pr list --head codex/p2-auto-background`를 요청했으나, 이 요청은 다시 `auto_review`로 전달돼 동일 모델 용량 오류로 **명령 자체가 미실행**이었다. 현재 실행 환경의 `auto_review`와 사용자에게 보이는 현재 채팅 권한 메뉴의 선택 항목을 대조하도록 질문했다. 원격 조회/게시를 다른 도구로 우회하거나 자동 검토를 해제하지 않았다. 따라서 최신 원격 조회·커밋·push·PR·CodeRabbit·새 CI는 아직 수행하지 않았다. 앱 검증/수용은 완료됐으므로 권한 경로 정상화 후 Git/PR 단계부터 재개한다. Figma 두 이력/SESSION·CHECKLIST 블록을 제외한 staging 복사본과 정확한 검증을 포함한 PR 본문만 로컬에 준비한다.

사용자는 중간에 자동 모드로 바꿨다가 다시 승인 요청으로 되돌렸다고 설명했다. 반영된 권한에서 같은 `git fetch origin`/`gh pr list`가 정상 실행됐고 origin/main 3043b7d/동일 브랜치 기존 PR 없음, Gradle·SDK·CI 설정 차이 없음을 확인했다. 사용자가 정식 권한 메뉴에서 선택한 정상 승인 요청 경로를 사용했으며 샌드박스/서명/검사 우회나 무제한 권한 설정 변경은 하지 않았다. 검증을 반복하지 않고 준비된 변경만 일반 커밋·push/PR 단계로 진행한다.
