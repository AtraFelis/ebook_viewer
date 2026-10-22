# 2026-10-22 — P3 최종 통합 기능 검사와 배포 준비

2026-10-21 사용자 “ㄱㄱ”로 기본91개 검사와 라이선스·빌드/설치 절차 점검을 승인했다. [계획](../plan/p3-final-verification-and-release-preparation.md)에 따라 기존 체크아웃에서 진행한다. PR #26 사용자 병합main bda1e6f를 fetch·기존 head d80de81과 전체 트리 동일 확인 후 ff-only 연결하고 `codex/p3-final-verification`을 만들었다. 원래 상태 기록과 사용자 `docs/design.md`를 보존했다. 디자인 문서는 최초86줄에서 작업 중 외부 추가로120줄이 됐으며 이번 작업에서 수정/커밋하지 않는다.

## 실행 전 상태와 PC 검사

현재 API33 기기의 승인/잠금 해제와 설치 앱 SHA-256 `9da3aa5e6a96472ec2e26c6aca7536a60402913022ba676b0fa073e93f8826f8` 일치를 확인했다. `. ./scripts/android-env.ps1` 후 strict `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck` BUILD SUCCESSFUL(4초). 단위 XML53개/실패0는 변경 없는 테스트의 Gradle up-to-date 재사용이며 새53개 실행으로 보고하지 않는다. Lint 오류/경고0·정보6도 구분한다.

기본91개는 `scripts/device-smoke.ps1`를 한 번 호출하는 ignored local host wrapper로 시작했다. 계측 기본720초/PC900초 제한, 원래 font_scale 보존/복원, 성공/실패 뒤 앱/계측·ADB 종료를 구성했다. 원래 앱/계측 APK를 local-artifacts/p3-final-tested-{app,androidTest}.apk에 보존했다. 전용20분/성능/화질 검사는 실행하지 않았다. 이 단계에는 결과를 미확정으로 남겼고 아래 실제 중단/보완 결과와 구분했다.

## 라이선스와 APK 구성 점검

strict `:app:dependencies --configuration releaseRuntimeClasspath`로 kotlin-stdlib2.1.20→annotations13.0 두 개만 해석함을 확인했다. 캐시 POM, 공식 Kotlin v2.1.20 라이선스/NOTICE/stdlib 소스, 공식 Maven Central annotations13.0 소스의 Copyright2000-2012/Apache2.0을 대조했다. Kotlin 컴파일러 배포 NOTICE는 원래 compiler distribution 문구와 앱의 stdlib 역할을 구분해 원문을 보존했다. JUnit4.13.2 EPL1.0과 Hamcrest1.3 New BSD는 JAR/POM/parent POM/버전 원문으로 확인한 테스트 전용이고 ktlint1.5.0 MIT 등 빌드 도구는 앱 런타임과 구분했다. annotations GitHub13.0 태그 경로는404였으므로 최신 master로 버전을 추정하지 않고 공식 버전 소스를 사용했다.

기존 JAR/APK에는 독립 고지 텍스트가 없었다. 새 의존성 없이 main/assets의 THIRD_PARTY_NOTICES.txt·licenses/Apache-2.0.txt·licenses/Kotlin-NOTICE.txt를 추가했다. Apache 원문 SHA-256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`, Kotlin NOTICE `0b09a83d3ef7795c7dec65e815866597c066c53898f2852a76599b8f5552941a`를 기록했다. 법률 적합성 보장이나 프로젝트 자체 Apache 라이선스 선언은 하지 않는다.

고지 추가 뒤 strict 앱/계측·Lint·ktlint BUILD SUCCESSFUL(21초). 고지 APK SHA-256 `64b5ceb7369945f106eac6ce860214ec9b85a8323055cac3fed67ae8dc4c3d8d`, 계측 `2947c5d2e2c3b400e6678fb56553bcfac9cceafa768f0d33a71a0375e99c6a5a`. **최초 기기 기본 검사는 원래9da3aa5e 앱에서 실행**이며 새 APK에서91개를 재실행한 것으로 세지 않는다. 두 앱 APK의 META-INF 서명/고지 assets를 제외한20개 엔트리(DEX·manifest·resources 포함)가 바이트 단위로 동일하고, 새3개 assets가 저장소 원문 바이트와 일치함을 확인했다. 실행 코드/리소스·저장 형식·스택/의존성 변경은 없다.

Build Tools34.0.0 apksigner verify: Verifies, v1/v2 true·signer1. 기존 AGP app-metadata.properties 개별 보호 경고는 보존하고 서명 검증 우회/메타데이터 삭제를 하지 않았다. aapt는 applicationId dev.example.ebookviewer.p0/versionCode1/versionName0.1.0-p0/min23/target35/debuggable, uses-permission0을 확인했다. 독립 native `.so`·외부 글꼴·PDF/이미지/합성 성능 자료 assets는 없고 현재 앱 assets는 세 고지 파일뿐이다.

[배포 준비 문서](../release-preparation.md)에 현재 경로/도구·strict 빌드·동일 서명의 데이터 보존 업데이트·debug/profile/release 구분과 고지 출처를 정리했다. 자체 LICENSE 선택·아이콘 최종안·제품 버전/applicationId·release 키/서명·실제 release 산출물 검사·배포는 별도 미완료이며 이번에 임의 결정/실행하지 않는다.

## 최초 전체 실행과 실패 보존

최초720초 제한에85번째 검사를 시작한 상태로 계측이 종료됐다. 원래 출력의 성공81개·실패 스택3개·나머지7개 미완료를 구분했다. `am instrument -w`가 실패는 stream 스택으로 출력해 완료 status만 세는 초기 집계에서는3개가 단순 미확인처럼 보였지만, 원문 AssertionError 대조로 실제 실패를 확인하고 사용자에게 안내를 정정했다. 원래 p3-final-device.txt/별도 stderr는 그대로 보존했다. 글꼴1.1 복원·앱/계측/ADB 종료도 확인했다. P0 tests=91, failures=0라는 전체 완료 결과는 없으며 이를 성공으로 꾸미지 않는다.

1. Gesture 회전: centerY1056.764827→964.204406 불일치. 같은 앱에서 해당 원래 검사 단독 실행은 통과했다. 원인이 확인되지 않아 관성/앱 수정으로 단정하지 않고 원래 검사 조건도 바꾸지 않았다.
2. 설정 회전:187.175043→190.476252 불일치가 단독으로도 재현됐다. 진단 메시지의 새 viewport minimum centerY=190.476252와 실제 값이 정확히 같았다. 원래 문서 선두 fixture는 화면/컷아웃 높이가 커졌을 때 상단 밖의 center를 유지할 수 없다. page0 fixture임을 추가 확인하고 기존 줌/X/2point 오차 검사는 유지하며 Y 기대값만 유효 상단 경계로 제한했다. 앱의 constrain/회전 코드는 바꾸지 않았다.
3. 번호 정렬:1154→1108 불일치. margin 차이를2로 나눈 검사 식이 원인이었다. padding만 더한 첫 수정도1246→1292로 실패해 보존했다. [Android13 FrameLayout 원문](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-13.0.0_r1/core/java/android/widget/FrameLayout.java)의 layoutChildren은 content 중앙 계산 후 전체 leftMargin-rightMargin을 더한다. 그 식으로 수정하고 기존1px 오차/투명/터치 통과 검사를 유지했다. 앱 번호 위치/디자인을 바꾸지 않았다.

3개 허용 클래스(gestures/settings/chrome)의 실제 @Test 이름만 선택하는 methodClass/method 경로를 계측에 추가했다. 임의 클래스·보조 함수·probe/restart 혼용은 거부한다. 기존 access/method 선택도 유지했다. 첫 스타일 실패는 수동으로 고친 뒤 최종 strict :app:assembleDebugAndroidTest :app:lintDebug ktlintCheck BUILD SUCCESSFUL(7초)을 확인했다. 전체91개에 과거720초가 부족했던 실측을 근거로 기본 전체 한도만900초로 조정했다. 개별/전용 검사 한도와 watchdog은 유지하며 전체를 다시15분 실행한 결과로 세지 않는다.

## 보완 결과와 미결

보완 로그의 원래 Gesture1 성공, access 미완료3 성공/lifecycle4 성공, 최종 settings1 성공/chrome1 성공을 원래81개와 합치면 **91개 고유 class/method 각각의 최신 성공 결과**다. 소스의 기본18개 클래스 @Test91개 이름을 추출해 실제 성공 이벤트의91개 고유 이름과 대조했고 누락0/중복 합산 없음을 확인했다(local-artifacts/p3-final-coverage.json). 새 APK64b5ceb7에서 보완10개를 확인했으며 기존81개는 byte 동일 실행 코드인9da3aa5e 결과다. 전체91개를 새 APK에서 한 번에 재실행한 결과와 구분한다.

최종 기대값 수정 후 첫 재검사에는 settings의 부모 복귀 대기 실패와 chrome의 Activity 시작 포커스 실패도 각각1개 있었다. keyguard/전원·시스템 앱 오류 창을 확인했지만 잠금/오류 창은 없었다. 실패 시 Activity/parent 상태를 표시하도록 진단 메시지를 추가한 뒤 최종 개별 실행들은 통과했다. 이 두 포커스 문제와 최초 Gesture 좌표 오차는 **미재현/원인 미확정**으로 남기며 통과한 재실행만으로 근본 해결을 선언하지 않는다. 실제 실패/진단/최종 로그는 ignored local-artifacts의 p3-final-*.txt에 구분해 보존했다.

최종 앱64b5ceb7369945f106eac6ce860214ec9b85a8323055cac3fed67ae8dc4c3d8d/계측f0e5d3288188a1983dea84882af9ce9b084a820879016480a42077d03b7fe14e 설치본 해시 일치, -r 업데이트(앱 삭제/데이터 초기화 없음), am start -W Status: ok·원래font_scale1.1·앱/계측 force-stop·ADB프로세스0을 확인했다. 원문 라이선스 바이트는 .gitattributes의 해당 txt LF 규칙으로 Windows checkout에서도 보존한다. 사용자 디자인은 이후128줄로 늘었으며 이번 커밋에서 제외한다.

이번 검증/고지·문서 정리는 완료했지만 단일91개 전체 실행 성공·위 미확정 원인·실제 OOM/다른 기기/API·최종 아이콘/프로젝트 라이선스·release서명/실제배포는 완료가 아니다. 새 런타임 의존성/앱 Kotlin 실행 코드/저장 형식 변경 없이 별도 PR로 게시하고 실제 리뷰/CI·사용자 병합은 후속 확인한다.

게시: 04294fe00a60f717713f6a5867c18fa8731bd97c 일반 push·PR #27 OPEN/isDraft=false/base main 생성/채팅 첨부. 수동 요청6333176748 한 번에 봇6333178154 Review triggered·요약6333176392의 실제19파일 처리 중을 확인했다. CI39725961666/39725943014은 IN_PROGRESS이며 완료 리뷰 API는 아직 비어 있다. 동일 head 중복 호출·리뷰 처리 중 상태 기록만의 재push·앱/ADB 재시작·에이전트 병합은 하지 않는다. 게시 상태는 로컬에 모으며 실제 리뷰/타당한 수정·CI와 사용자 병합이 남아 있다.

추가 정정/실제 UI 수정: PR 최초 head04294fe의 번호 기대값을 FrameLayout 구현 식으로 맞춘 것만으로는 의미적인 안전 영역 중앙을 검증하지 못했다. 원래 안전 영역 식은 타당했고 실제 CENTER_HORIZONTAL/비대칭 전체 margin 적용이 번호를 두 배 치우치게 하는 구현 문제도 있었다. 이 판단을 정정하고 MainActivity 번호 overlay를 safe margin 안의 MATCH_PARENT 너비/BOTTOM으로 최소 변경했다. PageNumberView의 투명 바탕·중앙 glyph/외곽선/터치는 유지한다. 테스트는 기존1px 조건의 안전 영역 중앙 계산으로 되돌렸고 배치 너비/중력 조건을 새 구조에 맞춘다. 단순 구현 모방으로 실패를 숨기지 않는다. 새 strict 앱/계측·Lint·ktlint18초 통과, chrome3/font1/immersive7 관련11개를 실행 중이며 원래91개/20분은 반복하지 않는다.

추가 수정 최종 결과: 새 앱81fc92890ab75f7fdb0c36a0b5829914bf36c9090c389dd48e7b4a8c09fc2db4/계측c7d4c190e95e08b6f2eb9ab84e17c9c409be237e7d0b032ab90be41f462bfe5b에서 chrome3/font1/immersive7 합11개/실패0을 확인했다(p3-safe-center.txt/progress.json). strict 앱/계측/Lint/ktlint18초 통과, 설치본 해시 일치·-r 업데이트·앱실행Status ok·원래font_scale1.1·앱/계측/ADB프로세스0을 다시 확인했다. 서명 검증도 통과했다. 실제 MainActivity 배치를 바꾼 뒤에는 원래91개 분할 결과를 이 앱의 전체91개 재실행으로 주장하지 않고 관련11개의 실제 회귀로 구분한다. 이전64b5ceb7의 DEX20개 동일성 설명은 실제 배치 수정 전의 고지 단계 근거다.

PR 최초 head04294fe CI39725961666/39725943014 모두SUCCESS·실제 CodeRabbit5609528462/19파일/지적1개를 확인했다. 검증 전략5행의41/48개를 현재 결과처럼 쓴 지적4344763871은 타당해 과거 표시 설정 당시 결과로 바꿨다. 별도 AutoFix/CLI 설치·실행 지시는 따르지 않았다. 의미 있는 번호 실제 수정과 문서 지적을 모아 같은 PR에 일반 push하며 새 head의 CI/실제 재검토·사용자 병합을 별도 확인한다.
