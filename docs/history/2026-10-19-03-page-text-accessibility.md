# 2026-10-19 — 페이지 본문 안전성·접근성·P2 통합 검증

## 범위와 기준

사용자가 선택/복사 UI는 뒤로 미루고 앞서 제시한 1(텍스트 처리 안전성), 3(접근성), 4(최종 통합 검증)를 요청했다. AGENTS/계획/로드맵의 현재 범위와 종료 기준에 반영한다. 본문 읽기와 native 안전 검증은 유지하고 검색을 재활성화하지 않는다. 새 런타임 의존성/도구 버전 변경은 없다.

PR #16 사용자 병합 main 45651e3와 최종 head 51861a3 CI 성공/review 0건을 조회했다. 첫 브랜치 준비 명령은 자동 승인 검토 사용량 한도로 실행되지 않았다. 표시된 복구 시각 뒤 재시도해 fetch→사용자 Figma tracked 블록 백업/stash→main ff-only→현재 체크아웃의 codex/p2-accessibility-safety→stash 재적용을 통과했다. 복구용 stash/디자인 두 미추적 이력은 유지한다. reset/clean/강제 push/새 worktree는 하지 않았다.

승인 기기 한 대/Awake/잠금 해제, MemAvailable 2,366,080KiB를 확인했다. 개인 PDF/기기 식별자는 수집하지 않았다. 이전 사용자 PDF를 자동으로 다시 열어 native 처리하지 않는다.

## 구현·조사와 실제 검사

[ADR 0015](../adr/0015-bounded-current-page-text-and-accessibility.md)의 한 페이지·격리 서비스·시간/RSS/시스템 여유 메모리 감시·본문 결과 상한·취소/프로세스 종료를 구현 중이다. 본문 읽기/Canvas 접근성 actions·PageUp/Down·스크롤 가능한 홈/도구/페이지 설정을 추가한다. 계측은 공개 합성 자료만 사용한다.

Context7 resolve Android Developers/query `/websites/developer_android_reference`를 사용했다. SDK 고정 자료가 아니고 일부 생성 서명이 실제 SDK와 달라 공식 Page/Service/AccessibilityNodeInfo 및 설치 SDK 컴파일로 교차 확인한다. 첫 두 strict 검사에서 새 코드의 ktlint 줄바꿈/주석 간격 오류를 확인해 직접 수정했다. ktlintFormat은 실행하지 않았다. 해당 실패를 빌드/기기 통과로 기록하지 않는다.

현재 검증/수정 중이며 최종 통합 성능·10분 읽기·프로세스 복원·실제 TalkBack/큰 글꼴 사용자 확인·CI/PR는 아직 통과로 보고하지 않는다. 후속 실제 결과를 이 파일에 추가한다.

## 본문/접근성 기기 검사와 글꼴 계측 수정

strict debug 앱/계측 빌드·단위 **44개/실패·오류 0**·Lint **0 errors/0 warnings**·ktlint를 통과했다. 초기 줄바꿈 외에 SDK 확장 API에 숫자 31을 사용한 Lint WrongConstant와 계측 PdfDocument의 Closeable 가정을 수정했다. SDK 상수 VERSION_CODES.S와 명시적 finally close로 고쳤으며 검사를 제거하지 않았다.

앱의 서로 다른 UID/요청한 둘째 페이지 내용·첫째 페이지 부재/빈 이미지 430페이지의 마지막 한 페이지/손상 PDF와 잘못된 페이지·새 요청 복구/취소 뒤 늦은 FD 해제/클라이언트 deadline을 포함한 첫 본문 suite **6개/실패 0**을 확인했다. 이후 서비스의 bind부터 시작하는 독립 main-loop deadline과 관측 실패 시 중단, 요청자가 RSS 예산을 낮출 수만 있는 정책을 보강했다. 과도한 메모리를 실제 할당하지 않고 1KiB 예산으로 실제 격리 프로세스 Binder 사망·다음 새 요청 복구를 검증했다. 무요청 서비스 deadline도 실제 Binder 사망으로 확인해 후속 본문 suite **8개/실패 0**이었다. 6개와 8개는 별도 실행이며 하나의 14개로 보고하지 않는다. 실제 native OOM/사용자 고해상도 PDF 재현은 아니다.

첫 글꼴 suite 1개는 샘플의 `Visible tiles not ready`, 안정화만 추가한 두 번째는 configuration settle 실패였다. 계측에서 요청한 방향이 Activity 재생성 때 앱의 저장 방향 설정으로 다시 적용되는 경로를 확인하고, 원래 방향을 snapshot한 뒤 조합별 저장 방향을 적용/Activity 안정화 후 검사하도록 수정했다. 조건/assertion을 완화하지 않았다. 후속 **글꼴 suite 1개/실패 0**은 100/150/200%×가로/세로의 6조합에서 홈/도구/표시·페이지 배치·배경 설정의 실제 전체 control bounds가 스크롤로 접근 가능한지 확인했다. 테스트 finally에서 원래 앱 방향과 시스템 배율을 복원했고 PC 외부 finally에서도 원래 배율 **1.1**을 재확인했다. 실제 TalkBack 및 큰 글꼴의 개인 가독성 수용과 구분한다.

현재 debug app SHA-256은 `84fbe376559e1b9097e5a73d073a19470f85a3a3e085a3323ebec108ff0a8db1`이다. 기본 57개(기존 48+본문 8+글꼴 1) 회귀를 시작했다. 이번 전체 회귀의 계측 SHA는 `2dcd1b2694d445924359ccec9af5a3ed7fa0e993324b9123f9fdb838a8db18d1`이며 결과는 후속으로 확인한다. 이후 추가한 성능 전용 probe/합성 complex 자산의 profile 계측 APK는 별도이며 기본 suite 결과와 SHA를 혼동하지 않는다.

최종 성능 계측은 초기 보통 스크롤 조건을 유지하고 추가 플링/32배율(방향별 10표본×3회), 서로 다른 1440×2240 RGB JPEG 12개/총 48페이지·2,721,378byte의 공개 합성 문서도 사용한다. 이 자료는 임의 문서 복잡도·사용자 문서의 재현이 아니다. 기존 baseline APK를 보존하고 최종 profile app `dbd3ab71265740c3a27117990b1f35b23611d2c6b1b0a0d1017f0a1e6f7d7cd0`, 공통 계측 `5b9c312aaa9a512480f601a1748a2d62dbd3b67cd74aa235c17a394d2e022e83`을 ignored p2-integration에 동결했다. 실제 실행/비교/통과는 아직 미확인이다. 프레임의 frozen P1 경로는 자동 배경색이 없어 수동 대체색을 사용하며 새 v2 설정 원본을 보존한다. 최종 앱은 자동 배경색 켜짐을 별도 확인한다.

## 2026-10-20 후속 — 전체 회귀와 짧은 문서 본문 접근

첫 전체 57개는 기존 DisplayDeviceTest의 keep-screen-on 체크박스가 스크롤 바깥에 있어 1개 실패/56개 통과였다. 기존 click/slider helper에서 ACTION_SHOW_ON_SCREEN으로 먼저 스크롤하고 기존 저장/취소/값 assertion을 그대로 확인하게 수정했다. 표시 suite 4개/실패 0, 한글 포함 본문 8개/실패 0, 화질 대조 1개/실패 0, 별도 실제 프로세스 복원 seed 1개→force-stop→verify 1개 통과를 확인했다. Canvas 페이지 action에 TYPE_VIEW_SCROLLED/fromIndex/toIndex/itemCount를 추가한 후 전체 57개/실패 0·앱 실행도 통과했다.

짧은 문서를 크게 축소해 전부 표시하면 center 기반 viewport는 마지막 페이지에 머물 수 있어, 본문 창에서 읽을 페이지 번호를 별도로 지정하도록 했다. 해당 UI는 PDF 줌/위치를 변경하지 않으며 선택/복사 UI를 재개하지 않는다. 이전 Dialog dismiss가 새 요청을 취소하지 못하도록 소유권을 먼저 비우고 콜백도 owner를 확인한다. API 30 이상 본문 경로에서 Context.bindIsolatedService(API 29)의 고유 instanceName으로 요청을 분리했다. Context7/공식 Context 문서와 설치 SDK/기기 실행으로 대조했으며 공유 인스턴스 플래그/새 권한·라이브러리는 없다.

새 본문 10개 최초 실행은 접근성 이벤트 1개 실패였다. 독립 suite 시작 시 UiAutomation 연결 전에 action이 먼저 실행될 수 있어 연결/AccessibilityManager 활성화를 먼저 기다리도록 계측을 고쳤고, timeout의 타 앱 window 텍스트가 trace에 포함되지 않도록 일반 실패로 바꿨다. 기대하는 정확한 TYPE_VIEW_SCROLLED/페이지 인덱스 필터는 유지했다. 이어서 본문 **10개/실패 0**(빠른 교체에서 이전 FD/결과 폐기, 짧은 2페이지 문서의 첫 페이지 본문 접근과 줌/위치 불변 포함), 본문 창까지 포함한 글꼴 **1개/실패 0**(6조합)을 확인했고 원래 배율 1.1을 복원했다.

최종 debug app **35c52b4b40124d038cb19ddcc90fed167a38db92d23ce134d23daa96412bb881**/계측 **84455f8ba364ed119f1ebf9649fe5cfe9d7052890507b24c6951f5b091c7cbfd**의 strict 빌드·단위 44개/Lint/ktlint를 통과한 뒤 기본 **59개**와 최종 별도 화질·프로세스 복원을 실행 중이다. 이전 57개/별도 검사를 최종 59개 통과로 합산하지 않는다. 앱은 pause 중 새 본문 요청도 시작하지 않는다. 상세 재현/통합 측정 판정은 [최종 통합 절차](../testing/p2-integration.md)를 따른다.

## 2026-10-20 최종 회귀 실패와 범위 제한 재검사

최종 `./scripts/device-smoke.ps1`은 **59개/실패 1개**로 끝났다(로컬 ignored `accessibility-final-smoke-59.log`, 약 8분). `NavigationDeviceTest.pageJumpAndBookmarksKeepCurrentZoomAndSurviveActivityRelaunch`의 `Did not navigate to selected page`였다. 앞선 실패로 연결된 별도 최종 화질·프로세스 복원 명령은 실행되지 않았다.

앱/계측 소스를 변경하거나 재설치하지 않고 `. ./scripts/android-env.ps1` 후 `adb shell am instrument -w -e suite navigation dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation`만 실행했다. **4개/실패 0**이었다(ignored `accessibility-navigation-recheck.log`). 재현되지 않은 원인을 테스트 타이밍이나 앱 오류로 단정하지 않으며, 전체 통과/해결로 바꾸지 않는다. 사용자의 소요 시간 질문에 구현 보완과 재검증이 반복된 점을 설명했고, 추가 기능을 늘리지 않고 해당 간헐 실패 진단과 기존 필수 검증에 집중한다.

구현 c7ea3ed를 일반 push하고 [PR #17](https://github.com/AtraFelis/ebook_viewer/pull/17)을 등록했다. [수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6271078778)에 [실제 접수](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6271097847) 및 리뷰 진행 응답이 왔다. 조회 시 reviews는 비어 있었고 push/PR CI 39279202637/39279404607은 queued였다. 완료 리뷰/CI 성공으로 보고하지 않는다. 앱 첨부 도구 호출은 응답 없이 대기해 성공 여부를 확정하지 못했다. 최종 성능 비교·10분 연속 검사·실제 TalkBack/큰 글꼴 사용자 확인·사용자 병합은 미완료다.

## PR #17 CI 대기 진단·재실행 시도

사용자 질문에 실제 job을 조회했다. `windows-latest`, runner_id/name은 null, steps는 비어 있고 push/PR 두 실행은 queued였다. Actions는 enabled/all이며 공개 개인 저장소다. 계정 소유 19개 저장소의 실행 수만 조회한 결과 in_progress 0, queued 2, 조회 실패 0이었다. 조회 시 GitHub Status Actions는 operational/incident 없음이었다. 이 정보로 공급 부족이나 내부 장애 원인을 확정하지 않는다.

동일 head의 멈춘 CI를 정상 취소 후 PR CI 한 건만 재실행하려 했으나, `gh run cancel 39279202637`와 `gh run cancel 39279404607`은 모두 `Cannot cancel a workflow run that is completed`로 거부됐다. 이어 `gh run rerun 39279404607`은 `This workflow is already running`으로 거부됐다. 재조회도 attempt 1/queued/steps 없음이었다. 정상 취소·재실행은 성공하지 않았고 새 실행도 없다. 상태 응답의 불일치 자체만 확인했으며 GitHub 내부 원인은 미확정이다. 반복 요청/강제 취소/우회 커밋/워크플로 변경은 하지 않는다. 로컬 검증을 계속하고 원격 CI 결과 확인 전 사용자 병합은 보류한다.

## 후속 확인 — 원래 CI 성공·CodeRabbit 실제 완료·입력 반영 대기

재조회에서 c7ea3ed의 [push CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39279202637)·[PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39279404607)가 모두 성공했다. 원래 attempt 1이며 취소/재실행 성공이 아니다. [CodeRabbit 완료 응답](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6271088510)에도 실제 31개 파일 검토·`No actionable comments` 및 [Review finished](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6271097847)를 확인했다. API의 봇 login은 `coderabbitai[bot]`이므로 표시 이름 `coderabbitai`로만 필터링한 초기 조회의 누락을 바로잡았다. 별도 docstring coverage 80% 권고는 이유·제약 중심 주석이라는 계약의 필수 기준이 아니며, 설명을 반복하는 주석 일괄 생성/봇 Autofix를 채택하지 않는다.

간헐 페이지 이동 검사에는 ACTION_SET_TEXT 요청 뒤 실제 입력 값이 보일 때까지 기다리는 조건과 합성 페이지/줌/centerY/화면 크기 실패 진단을 추가했다. 기존 페이지 69·줌/북마크 복원 assertion은 유지하며 제품 동작은 바꾸지 않는다. 최초 검사에서 계측 줄바꿈 ktlint 오류가 있어 수동 수정한 후 strict 계측 빌드/Lint/ktlint를 통과했다. 동일 앱에 글꼴 suite 1개(6조합)→navigation suite 4개를 순서대로 실행해 각각 실패 0이었다. 최초 실패의 원인은 여전히 미확정이며 입력 대기를 증명된 원인 수정으로 표현하지 않는다. 현재 계측으로 최종 전체 59개와 통과 후 별도 화질/복원·성능 비교를 진행한다.

최종 성능 app `db330d6c71b1203a9277f76dffe89144b3d102207eaac5932a00c3a3ce7064ca`, 공통 profile 계측 `bbec085f92b28afb299b88a9bece02a386a524a563df9a25611ff4dcf4d2ae10`을 strict 빌드해 동결했다. 이전 final/common APK는 해시명 파일로 보존했고 기준 `06423be61af1ea5f52fccd2831fa2921f4ce8a45a5690230a5ea59cc03e0665c`는 변경하지 않았다. 이는 현재 소스의 측정 준비이며 실제 성능 결과/통과는 별도 확인한다.

## 2026-10-20 오후 재개 — 토큰 중단·삭제된 앱·검사 수명

사용자가 토큰 소진 중단과 반복 앱 실행 때문에 앱을 삭제했다고 알렸다. 이전 PC 실행 세션은 존재하지 않고 예전 전체 로그에도 최종 59개 결과가 없었다. p2-integration에는 동결 APK/준비 자료만 있고 실제 측정 JSON은 없었다. 이전 전체 검사는 중단/미완료, 후속 화질·복원·성능은 미실행으로 유지한다.

14:11 KST 점검에서 본 앱은 없고 테스트 패키지만 남아 있었다. 이전 host 테스트 프로세스나 본 앱 PID는 확인되지 않았다. 설치 manifest에는 부팅 receiver·주기 실행 작업·화면을 여는 service가 없다. 테스트가 Activity를 반복 실행하는 구조는 확인했으나 중단 시점의 실제 반복 원인은 확정하지 않는다. API 33 한 대 승인·잠금 해제·시스템 글꼴 1.1·thermal 0, 초기 여유 메모리 약 1.9GiB였다. 사용자에게 앱 정리를 요청했고 완료 답변을 받았다. 성능 시작 전 기존 2GiB 조건을 다시 확인한다.

같은 debug 앱 `35c52b4b40124d038cb19ddcc90fed167a38db92d23ce134d23daa96412bb881`와 f8d9265 계측 `1999e68943d2aae3ac6835b0cd4824149b9fa07fa6545428e14f8fdeffbc4fa7`을 재설치했다. 삭제 전 앱 private 기록/설정 복구를 주장하지 않는다. 공개 합성 자료만 사용하는 기본 59개를 한 번만 실행하며, 로컬 host 래퍼는 실시간 결과 저장·720초 제한·finally의 앱/테스트 force-stop 및 시스템 글꼴 복원 fallback을 갖는다. 다음 작업은 자동 연결하지 않는다. 아직 실행 중인 결과를 통과로 세지 않는다.

f8d9265의 [push CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39285297203)·[PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39285300340)는 모두 성공했다. 해당 head의 CodeRabbit 자동 리뷰는 별 10개 미만 정책으로 건너뛰었으며 성공 체크를 새 리뷰 완료로 세지 않는다. [수동 재검토](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6283920655)를 한 번 요청했다. PR #17 앱 첨부도 이번에는 성공했다.

host 연결 소실에도 기기 계측을 끝내도록 P0Instrumentation에 독립 scheduled executor deadline과 완료 한 번 보장을 추가한다. 일반 검사는 720초, 선명도는 1200초, 프레임/장시간은 요청 초×회수+120초이며 `deadlineSeconds`는 이 값을 줄이는 것만 허용한다. 초과 시 RESULT_CANCELED/실패 1/incomplete로 finish하고 성공 요약을 내지 않는다. Context7 `/websites/developer_android_reference`와 [공식 Instrumentation.finish](https://developer.android.com/reference/android/app/Instrumentation#finish(int,%20android.os.Bundle))에서 계측 종료가 대상 프로세스를 종료함을 확인했다. 앱 런타임/자동 시작 동작 변경은 아니다. 첫 빌드는 줄바꿈 ktlint 실패, 두 번째는 profile 선택으로 debug 계측 task가 사라지는 구성 오류였고, 타입별 별도 Gradle 호출로 다시 검사한다. 기기에서 실제 짧은 deadline 종료를 후속 검증한다.

## 재개 후 실제 실패와 원인 확정·수정

재개한 기본 suite는 `59개/실패 1개`, 439.3초에 종료했다. 페이지 이동·북마크는 통과했으며 실패는 PageTextDeviceTest의 접근성 페이지 이벤트였다. host finally에서 앱/테스트를 멈췄고 다음 작업은 실행하지 않았다. 실패 진단을 추가한 본문 suite 10개도 같은 항목 1개가 실패했다. 공개 합성 수치가 **before=1, after=3, zoom=1.35, size=1080×2308, observedPages=[3]**이었다(페이지 값은 0-based 인덱스). 이벤트 미수신/연결 지연이 아니라, 인덱스 2(표시 3쪽)로 top 정렬 이동한 뒤 화면 중앙이 인덱스 3(표시 4쪽)에 놓여 기대 번호와 달라진 것이 확인됐다. 최초 다른 navigation 실패의 원인까지 이 결과로 확정하지 않는다.

접근성/키보드 이동을 중앙 기준으로 바꾸고, clamp/전체 문서가 보이는 작은 배율에서는 요청 논리 페이지를 노드·이벤트·앞/뒤 이동·본문에 함께 사용한다. 터치/일반 이동/reset은 이를 비운다. 일반 스크롤/줌·읽기 기록/북마크 형식·렌더 엔진/캐시와 새 의존성은 바꾸지 않는다. 기존 작은 2페이지 검사에 논리 앞뒤 이동/경계 거부/도구 버튼과 접근성 action 각각의 올바른 Alpha·Beta 본문/줌 불변을 추가하고, 기존 이벤트·페이지·줌 assertion도 유지했다.

watchdog debug/profile 계측 strict 빌드/Lint/ktlint는 타입별 호출로 통과했다. `-e suite navigation -e deadlineSeconds 1`을 실행했을 때 약 2.1초에 `Instrumentation deadline exceeded; incomplete`로 끝나고 앱 PID 부재/글꼴 1.1을 확인했다. 최초 host parser는 am의 stream 출력에서 숨겨지는 Bundle bool을 기대해 실패했고, 실제 incomplete 메시지·15초 미만 종료·성공 요약 부재를 확인하도록 바로잡아 재검증했다. 앱 timeout이 성공으로 위장된 것은 아니다.

접근성 수정의 첫 strict 실행은 주석 앞 빈 줄 ktlint 오류였고 수동 수정 뒤 debug 앱/계측 빌드·단위 **44개/실패 0**·Lint/ktlint를 통과했다. 이어 본문 전용 **10개/실패 0**, 27.5초 종료·앱/테스트 force-stop을 확인했다. 해당 debug APK 쌍은 ignored `accessibility-page-cursor-apks.json`으로 연결하며 전체 59개는 현재 별도 확인 중이다.

수정된 최종 profile app **5e968f60ab7a7dd536ef6c524f66445b6f367927b71a6e8252f595bb5df5cbfc**/공통 계측 **834c353ffa9e983474fa09fb3b0d133fbaff7906432bd2962a7b58089e16688a**을 strict 빌드해 동결했다. 이전 세대는 SHA명으로 보존했고 baseline `06423be6…`은 그대로다. 실제 성능 수집/통과는 아직 아니다. f8d9265의 [CodeRabbit 재검토 완료](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6283922091)는 계측 변경 5개 파일(문서 4개 유사 변경 건너뜀)에서 수정 요구 없음이었다. 이 후속 watchdog/접근성 수정에 대한 검토와 혼동하지 않는다.

## 최종 기능 검증 — 통과와 성능 준비 조건

watchdog a67f3f2·접근성 수정 0a95883을 같은 PR 브랜치에 일반 push했다. 사용자 Figma SESSION/CHECKLIST의 6줄씩과 미추적 이력 2개는 제외·해시 대조 보존했다. 0a95883의 [push CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39375277426)·[PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39375281644)는 모두 성공했다. 새 head CodeRabbit 자동 건너뜀 성공 체크를 실제 재검토로 세지 않는다.

최종 debug app **87b75a3bba2a0f344de5883962ee0f5c6d9f119f339d7399c832dd923134959f**/계측 **d6bd685f6d853a33db738c876d2fc5c25c2f5795590cd00651a3bf9dfec112a5**에서 다음을 각각 확인했다.

- 기본 **59개/실패 0**, 453.3초 종료(ignored `accessibility-page-cursor-basic-59.log`). 이전 실패 접근성 이벤트·navigation도 이번 실행에서 통과했다.
- 별도 `suite sharpnessPixels` **1개/실패 0**, 4.6초(ignored `accessibility-final-pixels-page-cursor.log`). 벡터/그레이/RGB의 확대 픽셀 대조다.
- 별도 `libraryRestart seed` **1개/실패 0**, 7초→host 종료→`verify` **1개/실패 0**, 10초(ignored `accessibility-final-restart-page-cursor-{seed,verify}.log`). 서로 다른 PID/읽기 위치·줌·페이지 배치 복원이다.
- 최종 단위 XML 합산 **44개/실패·오류 0**, Lint XML **0 errors/0 warnings**, ktlint 통과. 각 별도 실행을 하나의 62개 기본 suite로 표현하지 않는다.

모든 invocation 후 앱/테스트를 멈췄고 전역 글꼴 **1.1**을 확인했다. 여유 메모리는 **2,526,968KiB**로 준비 조건 2GiB 이상이지만 **thermal 3**으로 기준 0~1을 벗어났다. 이 상태에서 성능을 시작하거나 합격선을 낮추지 않고 앱 중지 상태로 냉각을 기다린다. 최종 정상/플링/32배율/복잡 자료 비교·프레임·10분 실제 Activity·TalkBack/가독성 개인 수용·사용자 병합은 미완료다.

냉각 중 재조회는 thermal 2였다. `scripts/run-p2-integration-probe.py --label baseline --mode normal`의 준비 guard가 `Thermal status unavailable or above LIGHT; workload not started`로 종료했다. guard는 APK 설치/표본 수집보다 먼저 실행되므로 기준 앱 설치·측정 JSON은 아직 없다. 사용자에게 화면을 끄고 3~5분 쉬게 한 뒤 잠금 해제를 요청했다. 그동안 앱은 중지 상태이며 반복 테스트는 예약하지 않았다. 변경 Markdown의 로컬 링크 153개도 누락 없이 확인했다. 로드맵은 완료한 본문/자동 접근성 검사와 남은 개인 수용·성능·사용자 병합을 구분해 갱신했다.

최신 문서 head 867c53b에서 [CodeRabbit 재요청](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6284300894)을 한 번 했으나 [Review rate limited](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6284302680)로 실제 미실행이었다. 자동 건너뜀/한도 성공 체크를 후속 접근성 수정 검토 완료로 세지 않고 반복 요청하지 않는다.

사용자 냉각/재개 답변 뒤 baseline normal 준비 조건은 통과했지만 첫 실제 probe는 `Sharpness probe left the foreground`로 실패했다. runs=[]/실패 1이었고 Dozing·keyguard showing=true·thermal 1을 확인했다. APK 설치/준비 중 자동 잠금으로 화면 전경을 잃었으며 성공 표본/성능 통과가 아니다. 원래 로그는 ignored `baseline-normal-foreground-failed-instrumentation.txt`로 보존했다. host guard에 잠금 상태뿐 아니라 `mWakefulness=Awake` 확인도 추가해 화면이 꺼진 준비 상태를 거부한다. 사용자에게 잠금 해제를 요청하고 완료 답변 직후 이미 설치된 같은 APK로 재시도했다. 이후 실제 수집 결과는 별도 기록한다.

## 2026-10-20 성능 수집 재개 — 기준 normal 완료

사용자 잠금 해제 답변 뒤 같은 baseline/common APK의 normal 재시도는 KST 14:48:46~14:55:38에 완료했다. `--label baseline --mode normal`의 9회/540표본(자료×방향별 90), timeout 0, thermal 전후 1·충전 status 2·font 1.1이었다. pooled p95는 vector 첫 방향 1,331.400ms/역방향 1,430.140ms, gray 149.166/132.827ms, RGB 516.135/432.595ms다. 이전 실패 보고서는 보존하며 이 표본과 합산하지 않는다.

final normal 첫 준비는 APK 설치 뒤 메모리가 2GiB 미만이어서 guard로 중단됐다. 측정 표본은 없다. 후속 읽기에서 2,206,248KiB/thermal 1·Awake/잠금 해제를 확인하고 재설치 없이 같은 명령을 재개했다. 시스템 화면 timeout은 300,000ms이고 device-admin 강제 상한은 없었다. 전역 timeout/밝기/잠금 설정은 바꾸지 않았다. 최종 측정 완료와 비교는 후속 기록한다.

PR 최신 head 867c53b의 실제 [PR CI 39376473079](https://github.com/AtraFelis/ebook_viewer/actions/runs/39376473079)는 도구 설치·Verify Wrapper and run checks가 실행돼 성공했다. 문서 전용이라는 이유로 이 실행을 미실행으로 단정하지 않는다. CodeRabbit 성공 표시는 기존 rate limited/자동 건너뜀과 구분하며 최신 접근성 코드의 실제 외부 재검토는 미완료다.

final normal도 9회/540표본·timeout 0으로 완료했다. 동일 test SHA/font/자료 hash·viewport·배율·주사율·창 밝기·충전 상태를 대조했고 양쪽 thermal 전후 0~1이었다. 기준→최종 pooled p95(ms)는 vector 첫 방향 **1331.400→811.342**, 역방향 **1430.140→726.838**; gray **149.166→99.506**, **132.827→82.516**; RGB **516.135→249.090**, **432.595→216.119**로 6그룹 모두 사전 목표를 충족했다. 각 방향 n=90이며 null 표본 제거 없이 집계했고 cache≤64MiB였다. 이 결과는 normal 컴포넌트 계측만의 통과이며 나머지 플링/최대 배율/복잡 자료·실제 Activity 프레임/10분·개인 수용 완료로 확대하지 않는다. 원자료 `baseline-normal.json`, `final-normal.json`, 계산 `summary.json`은 ignored local-artifacts/p2-integration에 보존한다.

이후 `--label baseline --mode fling`은 ADB operation failed로 중단됐고 실패 처리의 force-stop도 같은 연결 오류로 실패했다. JSON/플링 원자료는 생성되지 않았다. 재조회에서는 같은 기기가 승인 상태로 복구돼 앱/계측을 각각 force-stop하고 본 앱 PID 부재를 확인했다. 연결 실패의 내부 원인은 미확정이다. thermal 2로 상승한 상태여서 재시도하지 않고 냉각을 기다린다. normal 완료 표본/판정은 변경하지 않는다.
