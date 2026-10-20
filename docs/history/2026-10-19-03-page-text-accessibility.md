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

후속 사용자 냉각 완료 답변 뒤 재시도도 thermal 2 준비 guard로 측정 전에 종료됐다. 추가 대기 후 thermal 1/SKIN 37.7°C로 내려왔지만 Dozing/keyguard showing=true라 잠금 해제를 다시 요청했다. 이 동안 앱과 테스트는 중지 상태이고 플링 표본은 아직 없다. 측정 준비 guard 실패를 성능 실패 표본이나 앱 충돌로 세지 않는다.

## 플링 기준 완료와 재검토 실제 접수

사용자 재잠금 해제 후 baseline fling은 KST 15:11:16~15:20:54, **9회/180표본**, thermal 1→0·충전 status 2·font 1.1로 정상 종료했다. 각 자료×방향 n=30이며 vector의 1초 초과는 첫 방향 5/30·역방향 3/30, gray/RGB는 양 방향 모두 0/30이다. timeout은 모든 그룹 0이다. 최종과의 비교는 아직 완료하지 않았다. final 설치 직후 메모리 guard로 시작 전 중단됐다가 2,533,616KiB·thermal 0·Awake/잠금 해제를 확인하고 재설치 없이 final fling을 시작했다.

31ebdef의 [push CI 39378567969](https://github.com/AtraFelis/ebook_viewer/actions/runs/39378567969)와 [PR CI 39378572513](https://github.com/AtraFelis/ebook_viewer/actions/runs/39378572513)는 모두 성공했다. 실제 직전 리뷰부터 한 시간 뒤 새 head에서 [수동 요청 6284729400](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6284729400)을 한 번 보냈고 [6284730602](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6284730602)의 `Review triggered` 실제 접수를 확인했다. 최근 리뷰 요약에는 아직 이전 f8d9265 구간이 남아 있어 최신 코드 검토 완료로 세지 않는다. 중복 요청하지 않는다. 체크리스트의 과거 ‘기준선 미실행’/자동 배경색 미완료/기능 회귀 미완료 중복 항목은 실제 완료 근거에 맞게 정정하되 최종 성능·개인 수용/병합 항목은 미체크로 유지한다.

후속 확인에서 CodeRabbit 6284730602가 `Review finished`로 바뀌었고, 6271088510의 recent review 구간도 **f8d9265→31ebdef**로 갱신됐다(KST 15:24:43). 12개 파일 선택 중 유사 문서 6개를 건너뛰었으며 실제 앱 MainActivity/PdfCanvasView·본문 테스트/계측 deadline·통합 절차/host guard는 검토 범위에 포함됐다. `No actionable comments`/inline 0건으로 최신 접근성/계측 수정의 실제 외부 검토 완료를 확인했다. 이후 문서 기록 변경을 해당 코드 검토와 구분하고 불필요한 같은 head 요청은 하지 않는다.

final fling도 9회/180표본·timeout 0으로 완료했다. 같은 조건(자료/공통 test SHA·화면/배율/주사율/밝기·충전/font, 전후 thermal≤1) 비교에서 자료×방향 6그룹 모두 1초 초과 비율이 증가하지 않았다. vector는 첫 방향 **5/30→0/30**, 역방향 **3/30→0/30**이고 gray/RGB는 양쪽·양 방향 **0/30→0/30**이다. 최종 pooled p95는 플링 중 이미 준비된 타일의 마지막 viewport draw가 많아 약 0.4~0.8ms이며, 물리 화면 표시/모든 플링의 무지연이나 일반 스크롤 개선율로 해석하지 않는다. 각 방향 n=30/3회 조건, null 미제거와 모든 그룹 cache≤64MiB를 확인했다. 원자료 baseline/final-fling.json 및 summary.json에 보존하고 다음 최대 배율 조건을 시작했다.

최대 배율 비교는 기준/최종 각각 9회/180표본(자료×방향 n=30), 조건 일치/전후 thermal≤1·cache≤64MiB로 완료했다. 모든 그룹에서 양쪽 1초 초과/timeout **0**으로 사전 비증가 조건을 충족한다. p95(ms)는 vector 첫 방향 **830.571→264.669**, 역방향 **797.118→266.149**; gray **63.770→49.283**, **49.801→41.470**; RGB **149.931→83.040**, **197.714→95.324**다. 내부 32px/PDF point 배율이며 너비 맞춤 대비 32배/임의 PDF의 물리 화면 지연으로 표현하지 않는다. 이후 baseline normal --complex의 단일 측정을 시작했다.

첫 complex 준비는 기준 APK `adb install --no-streaming -r -t`의 host 90초 제한으로 실패했다. JSON/계측 raw 보고서는 없고 설치 hash 재조회는 기존 final 5e968f60…였다. 기기는 승인/awake·여유 2,531,364KiB, 본 앱 PID는 없었다. 설치 실패의 내부 원인은 미확정이며 앱 렌더 충돌/성능 표본으로 세지 않는다. 동일 준비를 한 번 재시도하고 제한을 늘리거나 체크섬·기준을 우회하지 않는다.

두 번째 설치도 PC 응답은 90초 timeout이었으나 후속 APK SHA는 기준 06423be6…로 확인됐다. 재설치 없이 실행한 첫 complex 계측은 약 8초에 `Process crashed`로 끝났다. Exit-info의 해당 PID 10761/KST 15:43:56 항목은 **USER REQUESTED/FORCE STOP, description=`stop dev.example.ebookviewer.p0 due to installPackageLI`**였다. package lastUpdateTime도 15:43:56으로 일치했다. PC 설치 timeout 후 남은 기기 설치가 뒤늦게 교체하며 계측을 종료한 근거이며 이 사건을 native PDF 렌더 충돌로 세지 않는다. 실제 baseline 설치/갱신 시각 안정화를 확인하고 같은 APK로 계측만 재개한다. 실패 raw는 `baseline-normal-complex-install-replaced.txt`로 보존한다. 설치 횟수를 추가하거나 제한을 우회하지 않는다.

동일 마지막 설치 시각(15:43:56)과 실제 계측 PID 12217을 확인해 complex를 설치 없이 재개했다. 다음 실행부터 host 설치 응답 대기를 **90→300초**로 늘리고 설치/측정 시작을 즉시 출력하도록 했다. 실제 90초 뒤에도 package replacement가 진행된 이력에 대한 호스트 도구 보완이며, 기기 watchdog·계측 timeout·메모리/발열·합격선 변경은 없다. 설치 timeout 뒤 반복 설치를 큐에 넣지 않고 실제 APK/lastUpdate 안정화를 확인하는 절차를 통합 문서에 추가했다. Python AST 구문 검사를 통과했고 다음 final 설치에서 실제 완료 응답/계측 시작을 확인한다.

complex 최종도 3회/180표본으로 완료해 기준과 자료 SHA·test SHA·화면/배율/주사율·창 밝기/font/충전 일치와 양쪽 thermal 전후≤1을 확인했다. 방향별 n=90의 pooled p95(ms)는 **265.812→149.363**, **232.648→132.496**이며 500ms 목표 이내다. 양쪽 1초 초과/timeout 0, cache≤64MiB였다. 따라서 normal 6그룹·fling 6그룹·zoom32 6그룹·complex 2그룹의 **20그룹 모두 사전 수치/조건 목표를 충족**했다. raw JSON/요약은 ignored p2-integration에 보존한다. 새 300초 설치 대기·단계 출력 도구에서도 실제 final 설치 완료 응답→준비 guard→계측 시작/완료를 확인했다. 다음은 실제 MainActivity 프레임 baseline/final 각 30초×3회와 final 600초×1회이며 아직 미완료다.

## 프레임 계측의 구형 배경 설정 호환 보완

첫 baseline frames는 `Initial background settings load failed`, runs=[]/실패 1로 종료됐다. 기준 앱에도 P1 수동용 `setBackgroundSettings`가 있으므로 setter 존재만으로 자동 모드를 판별한 계측 오류였다. 기존 v2 데이터는 구형 기준에서 읽기 실패/수동 기본 대체를 유지해야 하며 임의로 지우거나 변환하지 않는다. 실제 `BackgroundSettings.getAutomatic` 지원 여부를 확인하고, 지원 시 기존 로드 성공/설정 setter assertion과 자동 설정 적용을 유지한다. 미지원 기준에서는 자동 false를 assertion하고 새 생성자를 호출하지 않는 수동 대체 경로를 사용한다. 보고서에도 supportsAutomaticBackground를 기록한다. 앱 본체·SharpnessProbe/합격선은 바꾸지 않았다.

`:app:assembleProfileAndroidTest :app:lintDebug ktlintCheck -Pp0TestBuildType=profile --dependency-verification strict --console=plain`은 성공했다. 원래 공통 계측 834c353f…를 전체 SHA명 APK로 보존하고, 새 프레임용 공통 계측 **c128cef795dd616f85c4d29c253d991396231336d0efc46d9f46ee876201d2f8**을 동결했다(frame-compat-apks.json). baseline/final 앱 hash는 06423be6…/5e968f60… 그대로다. 이미 완료한 선명도 비교는 양쪽 공통 834c…로 유효하며, 프레임/long은 양쪽 새 c128…로 검증한다. 이번 변경은 프레임 호환 경로만이라 기존 기능 59개/선명도 4조건을 반복하지 않는다. 실패 raw는 baseline-frames-background-compat-failed.txt로 보존하고 수정 baseline frames를 재개했다.

수정 공통 c128…로 baseline frames **30초×3회 정상 완료**, supportsAutomaticBackground=false/autoBackground=false를 실제 보고서에서 확인했다. 전후 thermal 0·font 1.1·충전 status 2, p95 5.571~5.989ms/p99 6.364~6.749ms·droppedReports 0·cache≤64MiB다. 빠른 제스처 직후 고해상도 타일/페이지 커버가 미준비인 표본은 원자료에 보존하며 완전 무공백/무지연으로 보고하지 않는다. 같은 c128…를 유지한 최종 앱 자동 배경색 true 프레임을 시작했다.

프레임 최종도 30초×3회 정상 완료했다. 양쪽 공통 c128…/동일 fixture SHA·주사율/밝기 0.5/font 1.1·충전 status 2·thermal 전후 0을 대조했다. 기준 supportsAutomaticBackground=false/autoBackground=false, 최종 true/true가 실제 보고서에 확인돼 이전 수동 fallback/최종 자동 켜짐 차이를 명시한다. p95(ms) 기준 **5.571~5.989**→최종 **6.148~6.287**, p99 **6.364~6.749**→**7.062~7.804**로 일부 비용은 증가했으나 16.7/33.3ms 목표 이내다. 양쪽 droppedReports 0·cache≤64MiB다. FD는 기준 141→135/144→138/144→138, 최종 140→135/143→138/143→138로 관측 구간 종료 수의 누적 증가가 없다.

제스처 직후 페이지 커버 미준비는 기준 **25/325**, 최종 **22/320** 표본이고 고해상도 타일 미준비도 원자료에 남긴다. 모두 매 제스처 직후 renderState 표본이며 물리 화면의 공백 프레임 수/완전 무공백을 뜻하지 않는다. PSS 시작/종료 차이는 캐시 가열을 포함하므로 이 30초 표본만으로 누수 부재/순간 peak 상한을 주장하지 않는다. 최종 실제 Activity 600초×1회를 재설치 없이 시작했고 결과는 별도 기록한다. 계측/host 수정 d915642를 같은 PR 브랜치로 일반 push했다.

## 최종 10분 결과·debug 복원·개인 수용 대기

최종 profile 5e968f60…/공통 c128…/자동 배경색 true의 실제 Activity는 **600,341ms·35,540프레임·2,129제스처**를 완료했다(KST 15:57:56~16:08:15, 준비 포함). thermal 전후 0·충전 status 2·font 1.1·창 밝기 0.5/60Hz였다. p95 **6.540959ms**, p99 **8.583033ms**, 50ms 초과 0/주사율 예산 초과 5, droppedReports 0·최대 cache **67,107,576byte≤64MiB**, FD **140→137**이다. 해당 연속 실행이 앱 충돌/전경 상실로 중단되지 않았음을 확인했다. 메모리와 빠른 이동 중 미준비 표본은 아래 한계와 함께 보고한다.

PSS 시작 **205,767KiB**, 종료 **281,302KiB**, 30초 간격 관측 최고 **292,908KiB**였다(약 201→275MiB/관측 최고286MiB). 시작보다 약74MiB 증가했고 마지막 다섯 표본도 268,307~292,908KiB 범위였다. 중간 감소(181초의 205,891KiB 등)도 있어 단조 증가/누수를 확정하지 않지만 캐시 가열과 native/그래픽 메모리를 분리 측정한 것은 아니다. 10분 범위의 관측값이며 긴 세션 누수 부재/순간 peak 상한이나 실제 OOM 검증으로 확대하지 않는다. 고해상도 타일 미준비 **2048/2129**, 페이지 커버 미준비 **393/2129**의 제스처 직후 renderState 표본도 보존한다. 이는 물리 공백 프레임 수가 아니며 정지 후 선명화 20그룹 비교와 구분한다. 이전 P0는 화면/조건이 달라 직접 개선율로 비교하지 않는다.

계측 호환/host 설치 대기 보완 d915642의 [push CI 39383339710](https://github.com/AtraFelis/ebook_viewer/actions/runs/39383339710)·[PR CI 39383343532](https://github.com/AtraFelis/ebook_viewer/actions/runs/39383343532)는 모두 성공했다. 실제 CodeRabbit 검토는 앱 수정과 이전 계측을 포함한 31ebdef까지 완료됐고 이 이후 프레임 호환/설치 대기 코드는 아직 외부 검토 미완료다. 시간당 1회 포함 리뷰 한도를 확인한 상태라 반복 요청하지 않는다. 건너뜀 성공을 해당 변경 리뷰 완료로 세지 않는다.

기기 검사 종료 뒤 `local-artifacts/restore-final-debug.py`로 데이터 보존 재설치/설치 APK SHA를 확인했다. 앱은 기존 최종 기능 검증과 같은 **87b75a3bba2a0f344de5883962ee0f5c6d9f119f339d7399c832dd923134959f**, 최신 debug 계측은 **c0d3665bc74ccae96762dcc7154515a95cde7275e28d30ff34e0ebd1c58f29b0**다. debug 계측도 strict assembleDebugAndroidTest 성공이며 이전 기본 59개 실행의 d6bd685f… APK는 해시명으로 보존했다. 새 계측은 프레임 호환만 달라 59개를 재실행/새 APK에서 통과했다고 주장하지 않는다. 앱 runtime은 동일하다.

글꼴 **1.1**, 앱/계측 PID 부재·PR OPEN을 확인했다. 앱은 자동으로 열지 않는다. 개인 파일을 받지 않고 본문/TalkBack 확인을 할 수 있게 기존 공개 합성 `text-outline.pdf` 1,445byte/SHA **985aa318330c9633d90327f9df4d47743c8e145e4935dd8a6e50226bda9af60b**을 기기 Download/P2-accessibility-sample-985aa318.pdf로 복사/해시 대조했다. 같은 이름의 다른 파일은 덮어쓰지 않는 절차다. 사용자에게 공개 샘플 본문 표시/닫기·TalkBack 음성/초점/큰 글꼴·평소 PDF 읽기/확대/이어 읽기 확인을 요청했고 현재 답변 대기다. 이후 기기는 추가 조작하지 않는다. 최종 개인 수용·사용자 병합·완료 기록 전이므로 **P2 전체 완료는 아직 아니다.**

## 사용자 최종 기능 수용

사용자는 공개 샘플의 현재 페이지 본문 표시/닫기, TalkBack 음성·초점/큰 글꼴, 평소 PDF 읽기·확대·이어 읽기의 묶음 확인 요청에 **“모두 정상이에요”**라고 답했다. 현재 debug 앱 runtime의 개인 기기 자기보고 수용이며 자동 100/150/200%×가로/세로 6조합 검사와 구분한다. 개인 파일명/본문을 수집하지 않았고 이후 기기는 조작하지 않았다. 기능 구현·자동 통합 검증·현재 기기 사용 수용은 완료됐고, 최종 원격 상태/후속 계측 외부 검토와 사용자 PR 병합·완료 기록은 별도로 남는다.

## 최종 CI·후속 계측 리뷰 완료

사용자 수용 기록 b8b4100의 [PR CI 39385510230](https://github.com/AtraFelis/ebook_viewer/actions/runs/39385510230)는 실제 성공했다. 이전 계측 수정 d915642의 push/PR CI와 함께 현재 app/test/host 소스 검사를 확인한다.

직전 검토 접수에서 한 시간이 지난 뒤 최신 b8b4100에 [수동 요청 6285582815](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6285582815)을 한 번 등록했다. [응답 6285584356](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6285584356)은 Review triggered→Review finished(KST 16:23:32)였고, [최근 리뷰 정보](https://github.com/AtraFelis/ebook_viewer/pull/17#issuecomment-6271088510)의 실제 구간은 **31ebdef→b8b4100**, 7개 선택/유사 문서 5개 건너뜀으로 확인됐다. 프레임 계측 DebugFrameProbe와 host run-p2-integration-probe.py가 실제 검토 범위였으며 **No actionable comments/inline 0건**이다. 최신 계측/설치 대기 보완의 외부 검토 미완료를 이번 결과로 해소한다. 이후 변경은 이 사실을 기록한 Markdown뿐이고 앱/계측/host 소스는 그대로다. 사용자 Figma 변경을 제외해 최종 결과만 기록한다.

현재 기능·자동 통합 검사·개인 기기 사용자 수용과 실제 코드 리뷰까지 완료했다. PR #17은 사용자 병합 대상이며 병합 SHA/원격 상태와 최종 완료 문서를 확인하기 전 P2 전체 완료로 선언하지 않는다. 실제 OOM/다른 API·기기/긴 세션 및 원본 DPI/압축별 원인 확정 미검증 한계는 유지한다. 추가 기기 테스트/자동 실행은 없다.
