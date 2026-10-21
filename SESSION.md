# SESSION.md — 세션 인수인계

최종 갱신: 2026-10-21 (Asia/Seoul)

## 현재 진행 — 방향 세부 설정 유지, PC 검증 완료/기기 보류

사용자 요청으로 방향을 선택해도 세부 창을 닫지 않고, 회전/재생성 뒤에도 같은 방향 창과 저장된 선택을 복원한다. 닫기/뒤로가기만 상위 설정으로 돌아간다. 저장 중 재선택은 잠시 막고 실패하면 마지막 저장 항목을 다시 표시한다. 기존 표시 설정 등 다른 세부 창의 상위 복귀 동작과 PDF 위치/줌 복원 경로는 유지했다.

같은 `codex/design-page-slider`/Draft PR #21에서 수정했다. 기존 af0e64e의 원격 CI39441645592/39441637281는 모두 SUCCESS이며, 실제 CodeRabbit 요약6293480957의 base f90233d→af0e64e/17개 파일/수정 요구0와 응답6293540887의 Review finished를 확인했다. 리뷰 API 목록은 비어 있지만 실제 요약/완료 응답이 있어 자동 skip/성공 체크와 구분한다. 이번 방향 수정 head의 CI/리뷰는 별도다.

최종 strict 앱/계측 빌드·단위48개/실패0/오류0/건너뜀0·Lint 오류/경고0(정보6)·ktlint를32초에 통과했다. 방향 창 유지/재생성/닫기·뒤로 검사1개 추가와 기존 회전·전체 화면 검사 기대값 보완은 **컴파일만 완료/실행 안 함**이다(settings4개/전체 기대70개). 사용자 지시대로 ADB/설치/기기 검사는 시작하지 않았다. 사용자 `docs/design.md` 변경을 보존하며 이번 커밋에서 제외한다. [실행 이력](docs/history/2026-10-21-01-orientation-settings-stay-open.md)을 따른다.

## 이전 진행 — 페이지 슬라이더, PC 검증 완료/기기 보류

최신 게시: 계측 정리 보완 af0e64e를 같은 Draft PR #21에 일반 push했다. 한도 안내 시간 경과 후 수동 요청6293538210에 봇6293540887이 **Review triggered**로 응답해 실제 접수를 확인했다. 아직 완료 리뷰는 없으며 새 CI39441645592/39441637281도 확인 당시 IN_PROGRESS다. 진행 중 head는 유지하고 이 접수 기록은 로컬에 모은다. 기기 연결/검증·ADB 시작은 사용자 지시대로 보류한다.

게시: 구현39a7548을 일반 push해 [Draft PR #21](https://github.com/AtraFelis/ebook_viewer/pull/21)을 생성/채팅 첨부했다. 수동 리뷰 요청6293482467에 봇6293485014는 **Review rate limited/미실행**으로 응답했고 요약은 다음 포함 리뷰1분 후라고 안내했다. 성공 체크는 실제 리뷰가 아니며 원격 CI39441280919/39441271184는 당시 IN_PROGRESS였다. 이후 새 기기 검사의 종료 시 저장 큐와 합성 기록 삭제 순서만 보완하고 strict 계측 빌드/Lint/ktlint를5초에 통과했다. 앱 런타임/단위 결과는 그대로이고 기기 검증은 여전히 보류한다. 한도 안내 시간이 지난 후 최신 검증 head에 접수 여부를 한 번 재확인하며 진행 중 중복 요청이나 상태 문서 push로 head 변경을 하지 않는다.

PR #20 수정 head aadc1a6의 원격 CI39434928729/39434920521 SUCCESS와 사용자 병합(origin/main f90233d)을 확인했다. 현재 체크아웃에서 원격 fetch 후 `codex/design-page-slider`를 origin/main 기반으로 만들고, 앞선 TalkBack 제외 문서와 사용자 `docs/design.md` 변경을 보존했다. 사용자 디자인 문서는 이번 작업/커밋 대상에서 제외한다.

48dp 터치/4dp 트랙/18dp 손잡이의 슬라이더와 현재/목표 번호 구분을 구현했다. 드래그는 미리보기만 갱신하며 놓을 때 기존 이동을 한 번 호출하고 도구를 유지한다. 같은 페이지/취소는 이동 요청이 없다. 뒤로가기·두 번째 손가락·Window 포커스 상실·비활성화·재생성·문서 교체에서 목표를 버리고, 페이지 준비 중에는 실제 위치를 표시하며 중복 이동을 막는다. 기존 줌/가로 위치/바이트 캐시/렌더러/저장 형식을 재사용하고 TalkBack 전용 기능은 추가하지 않았다.

최종 strict 앱/계측 빌드·단위48개/실패0/오류0/건너뜀0·Lint 오류/경고0(정보6)·ktlint를 9초에 통과했다. 새 기기 검사5개는 **컴파일만 완료/실행 안 함**이며 전체69개/성능/시각/개인 수용도 미실행이다. 처음 연결 확인은 승인 기기0대였고 ADB를 종료했다. 사용자 “기기 연결 없이 할 수 있는 작업부터 먼저 끝내줘. 나중에 연결할게”에 따라 설치·ADB 재시작·기기 검사는 보류한다. 다음은 연결 후 관련 slider/immersive/font 검사와 합성 화면·사용자 수용이며, 첫 안내/저장 실패 초안은 그 다음 단계다. [실행 이력](docs/history/2026-10-20-07-page-slider.md)을 따른다.

## 최신 범위 변경 — TalkBack 제외

사용자 “TalkBack은 필요없어”에 따라 이후 디자인 개발·완료 조건에서 TalkBack 전용 기능과 음성/초점/제스처 검증을 제외했다. 다음 페이지 슬라이더에는 전용 TalkBack 조절을 추가하지 않는다. 기존 구현·과거 P2 수용 이력은 보존하고 큰 글꼴/일반 터치/본문 읽기는 유지한다. 앱 코드·테스트 변경이나 기기 검사는 하지 않았다. 사용자 변경 `docs/design.md`는 수정하지 않았다. [변경 이력](docs/history/2026-10-20-06-talkback-scope.md)을 따른다.

## 이전 단계 — PR #20 리뷰 수정

head7715e31의 원격 CI39432525983/39432517916 모두 SUCCESS, 실제 CodeRabbit 리뷰5585939549의19개 파일 검토/지적1개 완료를 확인했다. 초기 번호 표시 로드 callback이 저장 중인 Switch를 이전 값으로 덮을 수 있다는 지적4325698309는 실제 코드/저장 큐와 대조해 채택했다. 컨트롤이 활성일 때만 로드 값을 binding하도록 최소 수정했고, 실제 저장 큐를 barrier로 대기시켜 늦은 초기 값·저장 완료·재열기 일치를 검증하는 회귀를 추가했다. strict 앱/계측 빌드·단위44개/실패0/건너뜀0·Lint 오류/경고0·ktlint 및 API33 관련settings3개/실패0을 통과했다. 설치 APK `bf596a0e7204cc27405e6cbda9e50af9c4be03d4cd3e5b60423ff07bac124164` 일치·글꼴1.1·원래 번호 표시 복원·앱/계측/ADB 종료(프로세스0)를 확인했다. 사용자 수용 “세 가지 모두 정상”은 유지하며 새 전체/성능 검사는 반복하지 않는다. 별도로 추가된 사용자/디자인 세션의 `docs/design.md` 변경은 보존하고 이번 커밋에서 제외한다. 리뷰 잔여0과 작은 수정 범위를 고려해 추가 수동 요청은 하지 않는다. 새 수정 head의 원격 CI는 이전 성공과 구분하며 병합은 사용자가 한다.

### 앞선 게시·수용 기록

최신 게시/수용: 설정 피드백 수정7715e31을 같은 PR #20에 일반 push하고 최종 제목/설명을 읽기 도구+설정 왕복으로 갱신했다. 수정 앱의 상위 복귀·전환·방향 글자3건에 사용자는 **“세 가지 모두 정상”**이라고 재수용했다. 새 CI39432525983/39432517916는 시작됐고, 이전 한도 안내 시간 경과 후 검증된 새 head에 [CodeRabbit6292251554](https://github.com/AtraFelis/ebook_viewer/pull/20#issuecomment-6292251554)을 한 번 요청했다. 실제 응답6292253699의 **Review triggered/PENDING**을 확인했다. 결과 확인 중 head는 유지하며, 게시/수용 상태4개 문서는 로컬에 모은다. 앱/계측·ADB를 다시 시작하지 않는다.

사용자 실사용 피드백3건(세부 설정 닫기/적용 시 상위 설정 복귀, 전환 중 PDF/홈 노출, 방향 글자 하단 잘림)을 같은 PR #20 브랜치에서 수정했다. 상위 설정 Window를 닫지 않고 유지해 비동기 로드/세부 폼 전환을 덮고, 세부 폼 종료는 부모로 돌아가도록 바꿨다. 방향/테마 재생성에도 상위 설정과 저장된 번호 표시 값을 복원한다. 기기 시스템 리소스에서 확인한 고정 높이를 WRAP_CONTENT로 바꾸고 font padding을 포함했다. strict 앱/계측 빌드·단위44개/실패0/건너뜀0·Lint 오류/경고0·ktlint 및 최종 API33 **14개/실패0**(settings2/font1/display4/immersive7)을 통과했다. font100/150/200%×가로/세로의 방향 글자 영역, 부모 Window 유지/Activity 노출 없음, 적용/뒤로·회전/테마 후 상위 복귀와 PDF 픽셀/위치/줌 보존을 포함한다. 설치 app SHA-256 `6f32ef0370febbbbe8c43889e6ff33b69d2a51e0c915678107d21f712cd273aa` 일치·글꼴1.1·앱/계측/ADB 종료(프로세스0)를 확인했다. [수정 이력](docs/history/2026-10-20-05-settings-navigation-feedback.md)을 따른다. 수정 후 사용자 재확인은 대기 중이며 이전 피드백을 수용 완료로 세지 않는다.

게시: 구현/검증 기록1214f64를 일반 push해 [Draft PR #20](https://github.com/AtraFelis/ebook_viewer/pull/20)을 등록/첨부했다. 원격 CI39428703554/39428695825는 시작됐고, 자동 CodeRabbit는 Draft 기본 제외로 skip됐다. [수동 리뷰 요청6291715178](https://github.com/AtraFelis/ebook_viewer/pull/20#issuecomment-6291715178)을 최신 head에 한 번 등록했다. 실제 접수/리뷰·CI 결과는 별도 확인하며, 진행 중 head를 바꾸지 않도록 이 게시 상태 기록은 로컬에 모은다. 사용자 직접 수용도 대기 중이다.

실제 봇 응답6291717359는 **Action not completed / Review rate limited**였다. 요약6291707279는 다음 포함 리뷰8분 후라고 안내했으며 실제 리뷰는 실행되지 않았다. 성공 체크를 완료로 세지 않고 즉시 중복 요청/요금·설정 변경은 하지 않는다. CI는 당시 두 실행 모두 IN_PROGRESS다. 다음에는 사용자 수용/CI와 한도 복원 여부를 확인해 실제 리뷰를 진행한다. 앱/계측·ADB를 추가로 시작하지 않는다.

후속 작업(읽기 도구): PR #19 수정63fa450의 원격 CI39424169568/39424163063 성공, 봇 답변4325028852의 수정 확인과 두 스레드 해결, 사용자 병합(main46eaa41)을 확인했다. 변경 없는 현재 체크아웃에서 main을 ff-only 동기화하고 `codex/design-reader-tools`로 진행한다. 브라우저의 읽기 도구30:190을 다시 시각 확인했다. 상단 문서 제목/48dp 벡터 뒤로가기·하단 페이지 수/이동/북마크/설정/기존 본문 읽기로 나누고 ‘읽기로 돌아가기’ 버튼을 제거했다. 투명한 가운데는 PDF 터치를 통과시키며, 열린 상태의 바깥 단일 탭과 도구 여백 단일 탭으로 닫는다. 스크롤/핀치/플링 중지 탭은 기존 억제 정책을 유지한다. 페이지 슬라이더/첫 안내/실패 초안은 이번 범위 밖이다. [현재 실행 이력](docs/history/2026-10-20-04-design-reader-tools.md)을 따른다.

현재 strict 앱/계측 빌드·단위44개/실패0/건너뜀0·Lint 오류/경고0·ktlint와 API33 관련 **12개/실패0**(immersive7/font1/navigation4)을 통과했다. 최초 추가 스크롤 검사의24px 입력/핀치 전 비교를 보정한 계측 APK에서 재확인했으며 앱 APK는 그대로였다. 합성 화면의 다크 도구 배치를 시각 확인했고, font 검사는100/150/200%×가로/세로/본문 조작을 포함한다. 설치 app SHA-256 `8fd46e16ba11647393bb8c878af791eef095d18430268f3e7798e9cb63f67da4` 일치, font_scale1.1과 앱/계측·ADB 종료(프로세스0)를 확인했다. 전체61개/성능 검사는 반복하지 않았다. 새 UI의 사용자 수용·TalkBack 음성·최소폭320dp/긴 제목 조합은 아직 별도이며 Draft 게시 후 확인한다. 새 렌더/저장 의존성은 없다.

## 이전 단계 — 홈·입력/설정(PR #19)

최신 리뷰: head `7a0d05c`의 실제 CodeRabbit 리뷰5584970664가 완료됐다(19개 파일 선택·유사 파일4개 건너뜀·지적1개). [페이지 입력의 중복 제목 지적](https://github.com/AtraFelis/ebook_viewer/pull/19#discussion_r4324914603)을 채택해 본문의 굵은 ‘페이지 이동’ 라벨만 제거했다. 공통 제목/본문 읽기의 동적 ‘페이지 선택’ 제목·범위 안내·입력/이동 로직은 유지한다. strict 앱·계측 빌드/Lint/ktlint를 34초에 통과했다. 표시 한 줄 삭제여서 단위/기기/성능 검사와 ADB 시작은 반복하지 않았다. 작은 수정이고 포함 리뷰 잔여도0이라 추가 수동 호출은 하지 않는다. 새 수정 head의 원격 CI는 별도 확인 대상이다.

게시 상태: head `7a0d05c`를 push한 뒤 PR #19의 **isDraft=false / OPEN**을 확인했다. [Android CI 39421706642](https://github.com/AtraFelis/ebook_viewer/actions/runs/39421706642)는 **SUCCESS**다. 자동 CodeRabbit는 별10개 미만 기준으로 건너뛰었고, 구현·검증·사용자 수용을 마친 head에만 [수동 요청6290753082](https://github.com/AtraFelis/ebook_viewer/pull/19#issuecomment-6290753082)을 한 번 등록했다. 봇 응답6290755075의 Review triggered/PENDING 뒤 실제 완료 결과를 위에 반영했다. 진행 중에는 이 기록을 로컬에 유지했으며 리뷰 완료 후 수정과 함께 게시한다. 병합은 사용자가 한다.

후속 게시: 자동 승인 검토의 사용량 한도로 실행되지 않았던 커밋을 사용자 재개 후 정상 수행했다. 구현 `b17412d`를 같은 PR #19 브랜치에 일반 push했고 [답변4324848346](https://github.com/AtraFelis/ebook_viewer/pull/19#discussion_r4324848346)에 수정/검증을 남긴 뒤 기존 primary 색상 스레드의 resolved=true를 확인했다. PR 제목·설명도 홈·설정·입력까지로 갱신했다. 최종 게시 기록을 모아 push한 후 정식 PR로 전환하며, 새 UI 소스의 원격 CI/실제 후속 리뷰는 이전 head의 성공과 구분한다. 추가 수동 리뷰 호출/병합은 하지 않았다.

사용자가 Figma 디자인 적용을 선택했고 `docs/design.md`를 읽고 계획 후 구현하도록 지시했다. Figma MCP는 한도 때문에 사용하지 않는다. Chrome에서 홈/읽기/도구 시안을 직접 확인했고 [적용 계획](docs/plan/design-implementation.md)에 5단계와 단계별 수용 기준을 기록했다. 사용자 디자인 커밋 `ea91122`를 보존한 `codex/design-foundation`에서 origin/main `8d0731b`를 일반 merge했다. PR #18은 사용자 병합 완료이며 **P2는 종료**했다. 아래 P2 진행 문구는 이전 단계의 기록이다.

이번 구현은 light/dark 공통 토큰·홈 카드/기록 관리와 전체 화면 입력·설정 폼이다. 방향·배경색·테마/밝기·문서 배치·페이지 이동·북마크·본문 창에 공통 UI와 스크롤/적응형 하단 조작을 적용했다. SAF/기록 삭제 확인/실제 저장 결과·전체 기록·합성 문서 진입을 유지한다. `docs/design.md`와 디자인 worktree는 수정하지 않는다. 엔진/캐시/저장 형식/런타임 의존성 변경은 없다. 읽기 도구/슬라이더/첫 안내/실패 초안 복구는 다음 단계다.

현재 PC strict 앱/계측 빌드·단위44개/실패0·Lint 오류/경고0·ktlint를 통과했고, 사용자 USB 재연결 후 같은 APK의 관련 기기 **17개/실패0**(display4/font1/navigation4/layout3/immersive5)을 통과했다. font 검사는 100/150/200%×가로/세로와 본문 창을 포함한다. 설치 APK SHA-256 `f51341ea8910a6187c5d882103b8f5032c3c70ae8bede2f071d977bc29eadc2f` 일치와 종료 후 글꼴1.1을 확인했고 앱/계측·ADB를 모두 중지했다(ADB 프로세스0). 사용자는 실제 홈/이어 읽기·새 UI·키보드/오류/적용 버튼 가림과 편의성 확인에 **“모두 정상이고 화면도 괜찮아요”**라고 수용했다. 추가 성능/전체59개 반복은 하지 않았다. 새 TalkBack 음성/최소폭320dp/전체 시안 일치는 아직 별도다.

사용자는 CodeRabbit의 Draft 호출 금지 지시를 정정해 **Draft에서도 필요하면 호출할 수 있으나 구현·검증이 어느 정도 끝난 시점에만 요청하고 잦은 호출을 피하도록** 했다. 최종 규칙을 AGENTS/Git 규칙/계획에 반영했다. 실제 리뷰5582891368은 head2234180의20개 파일/지적1건이었다. 기본 테마 primary 텍스트의 비활성 상태 색상 지적을 타당하다고 판단해 전용 light/dark 선택자로 고쳤고 secondary/Alert 테마는 확대 수정하지 않았다. 새 호출 없이 수정·UI 변경을 함께 검증했다. [구현·리뷰·실제 검사](docs/history/2026-10-20-03-design-settings-and-review.md)를 따른다.

초기 홈 단계에서는 strict 앱/계측 빌드·단위44개·Lint/ktlint만 완료했고 기기 수용은 미실행이었다. USB 분리 요청으로 ADB를 종료한 뒤 후속 사용자 재연결 승인 전까지 시작하지 않았다. 이후 실제 검증·현재 수용은 위 결과로 갱신한다. [초기 명령·미결](docs/history/2026-10-20-02-design-foundation.md)을 보존한다.

구현 `6541ee5`를 일반 push해 [Draft PR #19](https://github.com/AtraFelis/ebook_viewer/pull/19)를 등록/채팅 첨부했다. push/PR Android CI는 실제 실행 중이다. [CodeRabbit 수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/19#issuecomment-6286630224) 한 번에 봇이 [Review triggered](https://github.com/AtraFelis/ebook_viewer/pull/19#issuecomment-6286632250)로 응답했다. 최초 자동 Draft 건너뜀과 수동 접수를 구분하며 실제 리뷰 완료는 아직 확인하지 않았다. 후속 읽기 설정 `30:219`/표시 설정 `31:371` 시안도 브라우저에서 시각 확인했다. 기기 검증 전 Draft를 유지하며 병합하지 않는다.

### PR #19 앞선 접수 기록 — 2026-10-20

head `2234180`의 [Android CI 39392685917](https://github.com/AtraFelis/ebook_viewer/actions/runs/39392685917)은 성공했다. 실제 코드 리뷰는 아직 없다. 앞선 수동 응답 `6286632250`은 최종적으로 **Action not completed / Head commit changed**로 바뀌었다. 구현 뒤 문서 push가 기존 요청의 head를 바꿨으므로 그 요청의 접수 이력을 리뷰 완료로 세지 않는다. 최신 head에 [재요청 6287191310](https://github.com/AtraFelis/ebook_viewer/pull/19#issuecomment-6287191310)을 한 번 등록했고 봇 응답6287193160의 Review triggered를 확인했다. 리뷰 중 head를 다시 바꾸지 않도록 이번 상태 문서는 로컬에 먼저 기록하고 결과 확인 후 모아 게시한다. 다음 구현은 입력·설정 화면, 홈 실제 기기 수용은 별도로 남아 있다.

## 이전 진행 — P2 완료 문서

2026-10-20 사용자 최종 수용 “모두 정상이에요”와 PR #17 사용자 병합을 확인했다. main은 **68ced37**이며 최종 앱/test/host CI·실제 CodeRabbit 리뷰까지 완료했다. 사용자 Figma 변경을 백업/해시 대조·복구용 stash 보존 후 현재 체크아웃에서 main을 ff-only 동기화하고 `codex/p2-completion`으로 완료 문서를 정리한다. [최종 판정·검증·한계](docs/history/2026-10-20-01-p2-completion.md)를 따른다. 기능 개발/검사/개인 수용과 기능 PR 병합은 완료했고 [완료 문서 PR #18](https://github.com/AtraFelis/ebook_viewer/pull/18)을 등록/채팅 첨부했다. 문서 링크162개/누락0·공백/앱 소스 불변을 확인했다. 새 Android CI는 Markdown 전용 제외로 미실행이며 수동 CodeRabbit는 포함 리뷰 한도로 실제 문서 검토 미실행이다. 앱 코드는 PR #17에서 실제 리뷰/CI를 완료했다. 문서 PR 병합 확인 후 P2 종료를 확정하며 이번 변경은 Markdown뿐이고 기기 조작/검사 반복은 없다.

2026-10-19 사용자 결정: 선택/복사 UI를 후속으로 미루고 페이지 단위 텍스트 안전성·TalkBack/큰 글꼴·최종 통합 검증을 진행한다. 검색 보류/목차·비밀번호 제외와 Figma 별도 단계는 유지한다. PR #16 사용자 병합 main 45651e3와 최종 head CI 성공을 확인했다. 실제 CodeRabbit 리뷰는 없었다. 사용자 Figma 변경을 백업/stash 후 현재 체크아웃에서 main ff-only 동기화·`codex/p2-accessibility-safety` 생성·재적용했다. 이전 자동 승인 사용량 한도로 실행되지 않은 Git 명령은 복구 시각 이후 성공했으며 우회하지 않았다.

본문은 요청한 한 페이지만 제한 권한 별도 프로세스에서 처리하도록 구현했다. 토큰 중단 뒤 사용자 삭제 앱을 재설치해 실행한 전체 59개는 접근성 페이지 이벤트 1개 실패/439.3초 종료였다. 진단에서 세로 1080×2308·zoom 1.35의 내부 인덱스 1→2 요청이 화면 중앙/이벤트 3으로 건너뛰는 원인을 확인했다. 접근성 이동 중앙 정렬·경계의 임시 논리 페이지를 노드/이벤트/키보드/본문에 일관 적용하고 터치·일반 이동/reset에서 해제했다. 줌/일반 스크롤·기록/북마크 형식은 유지한다. 수정 본문 10개/실패 0 뒤 최신 전체 **59개/실패 0·453.3초**와 별도 화질 1개·프로세스 복원 seed/verify 각 1개를 통과했다. 최초 다른 페이지 이동 실패 원인은 미확정으로 보존한다.

기기 검사는 한 invocation씩 종료를 확인하며 다음 작업을 자동 연결하지 않는다. PC 래퍼 timeout/finally와 계측 자체 일반 720초/선명도 1200초/프레임 요청 시간+여유의 deadline을 적용했다. 실제 1초 제한에서 약 2.1초에 incomplete로 종료하고 앱 PID 부재·글꼴 1.1을 확인했다. 현재 소스 strict debug/profile 빌드·단위 44개·Lint/ktlint는 통과했다. 이전 앱 삭제 전 private 기록/설정 복구를 주장하지 않는다.

본문 창의 페이지 선택은 짧은 문서를 모두 표시해도 PDF 줌/위치 변경 없이 다른 페이지를 읽는다. native 서비스의 6초·192MiB RSS 관측·여유 메모리·16,000자 제한은 유지하며 순간 peak/OOM hard limit 보장은 아니다. 실제 결과/미결은 [실행 이력](docs/history/2026-10-19-03-page-text-accessibility.md)에 기록한다. 선택 UI·검색·새 런타임 의존성/도구 버전 변경은 없다.

최종 선명도 비교는 **normal/플링/최대 배율/복잡 RGB 자료의 20그룹 모두 사전 목표 충족**이다. 동일 frozen baseline/final APK와 공통 계측·동일 자료/화면/배율/주사율/창 밝기/font/충전 조건, thermal 전후 0~1·cache≤64MiB에서 확인했다. normal 기준/최종 각 540표본, 플링·최대 배율·복잡 자료는 각 조건/앱 180표본이며 timeout은 모두 0이다. 실제 MainActivity 프레임 기준/최종 각 30초×3회와 최종 600.341초도 완료했다. 최종 frame p95 최대6.287ms/p99 최대7.804ms, long 6.541/8.583ms·cache≤64MiB·보고 누락0·long FD140→137이다. PSS는 시작201→종료275MiB/관측 최고286MiB로 증가와 변동이 있어 장기 누수 부재를 보장하지 않는다. 사용자는 공개 본문/TalkBack/큰 글꼴과 평소 PDF 읽기·확대·이어 읽기에 “모두 정상이에요”라고 최종 수용했다. 기능 PR #17 사용자 병합은 완료했으며 완료 문서 반영을 마무리한다. 준비 중 ADB 연결/메모리/잠금·설치 timeout 실패를 성능 표본과 구분해 이력에 보존한다. 설치 timeout 뒤 남은 package 교체가 계측을 종료한 원인은 exit-info로 확인했고, PC 설치 대기만 90→300초/단계 출력으로 보완했다. 기기/성능 deadline이나 합격선은 그대로다. P2 전체 완료는 아직 아니다.

[PR #17](https://github.com/AtraFelis/ebook_viewer/pull/17)은 사용자 병합 완료(main 68ced37)이며 앱 첨부도 성공했다. c7ea3ed 및 계측 보완 f8d9265의 push/PR CI는 모두 성공했고, CodeRabbit 실제 31개 파일/후속 코드 1개 파일 검토도 각각 수정 요구 없이 완료됐다. 별도 docstring 80% 권고는 저장소 이유/제약 주석 규칙의 필수 기준이 아니어서 일괄 생성하지 않는다. 후속 접근성/계측 deadline 수정과 Awake 준비 검사를 포함한 31ebdef의 push/PR CI도 모두 성공했다. CodeRabbit 수동 재요청은 한 차례 한도 거부 뒤, 새 head의 실제 f8d9265→31ebdef 검토가 완료돼 수정 지적 0건을 확인했다(12개 선택/유사 문서 6개 건너뜀). 후속 계측/host 수정 d915642의 push/PR CI도 성공했다. 사용자 수용 기록 b8b4100의 PR CI도 성공했고, CodeRabbit 실제 **31ebdef→b8b4100** 후속 검토가 완료됐다(7개 선택/유사 문서5개 건너뜀, 실제 프레임 계측/host 코드 2개 검토·수정 지적0). 이전 한도/미검토 이력과 구분하며 이후 수정은 결과 기록 Markdown뿐이다. 최종 debug 앱87b75a3b…/최신 계측c0d3665b…를 복원하고 글꼴1.1·앱/계측 중지를 확인했다. Download/P2-accessibility-sample-985aa318.pdf 공개 샘플을 포함한 실제 본문/TalkBack/큰 글꼴 및 개인 PDF 읽기/확대/이어 읽기 사용자 수용도 완료했다(“모두 정상이에요”). 이후 기기 조작/검사 예약은 없고 기능 PR 병합은 확인했고 별도 완료 문서 PR을 준비한다.

이전 PR CI 대기 조회/취소/재실행의 상태 불일치 이력은 보존한다. 이후 attempt 1의 원래 push/PR 실행이 성공으로 확인돼 재실행은 필요하지 않다. 대기 표시의 내부 원인은 미확정이며 성공을 취소·재실행 효과로 주장하지 않는다. 후속 로컬 최종 기능/성능과 개인 접근성 수용도 위 결과로 완료했다.

## 읽기 화면 설정 — P2

사용자 요청으로 무응답이던 PR #15를 닫고 같은 브랜치/동일 head 74d2ceb로 **[PR #16](https://github.com/AtraFelis/ebook_viewer/pull/16)**을 정식 PR로 다시 등록했다. #15의 댓글/진단 이력과 브랜치를 보존하고 #16을 채팅에 연결했다. [CodeRabbit 수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/16#issuecomment-6263981481)을 한 번 등록했으며 생성 직후 봇 접수/리뷰는 없고 CI는 진행 중이다. 새 PR 생성 성공을 리뷰 문제 해결로 판단하지 않는다. 아래 #15 기록은 이전 시도 이력이다. 현재 리뷰/사용자 병합 대상은 #16이며 앱 소스·기기 설치는 변경하지 않았다.

PR #14 사용자 병합 d35ff36/마지막 문서 head 181ea11의 [PR CI 성공](https://github.com/AtraFelis/ebook_viewer/actions/runs/39203788443)을 확인했다. 현재 체크아웃에서 사용자 Figma 변경을 백업·stash/해시 대조·재적용한 뒤 main을 ff-only 동기화하고 `codex/p2-reading-settings`를 만들었다. 복구용 stash와 미추적 디자인 이력을 보존한다.

[ADR 0014](docs/adr/0014-reader-display-settings.md)의 시스템/밝게/어둡게 테마, 열람 중 시스템/수동 1~100% 밝기·화면 유지, 별도 AtomicFile v1 저장을 구현했다. 기존 View/PdfRenderer/캐시·새 의존성 없는 스택을 유지한다. 테마 재생성에서 기존 viewport를 복원하고 PDF/외부 배경색은 그대로다. 읽기 설정 메뉴는 스크롤 가능하며 방향 5개 선택은 별도 메뉴로 분리했다.

최신 앱/계측 strict 빌드·단위 41개/실패 0·Lint 오류/경고 0·ktlint를 통과했다. 새 기기 4개 최초 통과 뒤 전체 48개는 방향 메뉴 2개 실패였다. 이전 dismiss 콜백/소유권 전환을 앱에서 고치고 Force Dark 보호도 추가해 최신 app 41093bcf/계측 cadababf로 방향/전체 화면 5개 및 후속 **전체 48개/실패 0·앱 실행**을 통과했다. 테스트를 제거/완화하지 않았다. 실제 Window 플래그 검증에는 테스트 도구의 강제 화면 유지를 제외한다. 사용자는 테마/읽기 밝기·목록에서 시스템 밝기 복귀 확인 요청에 **“잘돼.”**라고 수용했다. 개인 파일/본문을 받지 않았고 이후 기기를 추가 조작하지 않는다.

구현 f3b1816을 일반 push해 [PR #15](https://github.com/AtraFelis/ebook_viewer/pull/15)을 Draft로 등록했다. [push CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39227054763)·[PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39227060932)는 모두 성공했다. 전체 기기/사용자 수용 후 Draft를 해제했고 문서 head 02f2ed7의 [PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39228296780)도 성공했다. 최초 요청과 정식 PR/새 head의 [CodeRabbit 재요청](https://github.com/AtraFelis/ebook_viewer/pull/15#issuecomment-6263763069) 모두 봇 응답이 없다. 사용자 로그인 후 대시보드에서 저장소 Installed·상속 조직 자동 리뷰 켜짐/Draft 리뷰 꺼짐을 확인했다. #15는 Review Stack이 없으며 화면의 시작 버튼을 한 번 눌러도 접수/오류 사유를 확인하지 못했다. 내부 원인은 미확정이며 설정 변경/반복 요청은 하지 않는다. 코드 head/실제 검사와 문서 후속을 구분하며 리뷰 결과·사용자 병합은 남아 있다. [실제 명령/미결](docs/history/2026-10-19-02-reader-display-settings.md)을 따른다. 선택·본문 읽기/접근성 및 최종 P2 통합도 남아 있다.

## 자동 배경색 — P2

main 3043b7d 기반 `codex/p2-auto-background`의 구현 head 1415185를 일반 push하고 [PR #14](https://github.com/AtraFelis/ebook_viewer/pull/14)을 등록/채팅에 연결했다. Figma 사용자 블록과 두 이력은 백업/해시 대조 뒤 그대로 보존하고 PR에서 제외했다. 새 런타임 의존성/SDK·CI 변경 없이 [ADR 0013](docs/adr/0013-automatic-page-background.md)의 자동/수동 선택·v1 수동 호환/v2 저장, 별도 bounded 미리보기 분석, 24dp 완충/120ms 안정화·대체색을 구현했다.

strict 앱/계측 빌드·단위 **37개/실패 0**·Lint 오류/경고 0·ktlint, API 33 기본 **44개/실패 0**·앱 실행을 확인했다. 이후 보강한 실제 두 색 페이지 경계 왕복 테스트의 고정 px 가정을 density/zoom 기준으로 고쳐 최신 strict 계측 빌드/Lint/ktlint 및 배경 suite **6개/실패 0**을 재확인했다. 기본 44개와 보강 6개는 같은 app f7ad8a2f/다른 계측 APK의 별도 실행이다. 최종 debug 설치/실행도 확인했다. 사용자는 profile 설치본에서 **“이 정도면 괜찮은 것 같아. 수동으로 돌려도 지정 색으로 돌아와.”**라고 수용했다. 이후 앱 runtime은 동일하며 수용 질문을 반복하지 않는다.

기존 profile app 41850cd1/계측 c86c23c8의 보고서는 보존하되 초기 설정 로드가 측정 모드를 덮어쓸 수 있어 모드별 비교 합격 근거로 확정하지 않는다. 계측의 직렬 설정 큐/메인 콜백 완료를 기다리고 실패/시간 초과 시 실패 처리하도록 수정했다. strict profile/debug 계측 빌드·Lint·ktlint 통과 뒤 동일 비디버그 profile app 163007c7/계측 05492bfa로 수동·자동 각각 30초×3회 재측정했다. p95 최대 6.27→7.11ms/p99 최대 7.64→8.47ms로 기존 목표 이내·cache≤64MiB·보고 누락 0·관측 FD 구간 증가 없음이다. 일부 비용은 증가했으며 임의 PDF/native 순간 peak·새 선명화 분포나 최종 P2 통합 성능 보장은 아니다. [조건/실제 명령·결과·도구 차단/복구](docs/history/2026-10-19-01-automatic-background.md)를 따른다.

구현 head의 [push CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39199720880)·[PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39199726286)는 모두 성공했다. CodeRabbit [수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/14#issuecomment-6259682228)/[실제 완료](https://github.com/AtraFelis/ebook_viewer/pull/14#issuecomment-6259683425) 후 20개 파일 리뷰를 확인했다. 두 지적(계측 모드 경합, 오래된 PR 상태 기록)을 검증·수정한 45afa40을 일반 push하고 두 스레드를 해결 처리했다. 수정 head의 [push CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39202987397)·[PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39202990303)도 모두 성공했다. [재검토 요청](https://github.com/AtraFelis/ebook_viewer/pull/14#issuecomment-6260135828)은 [Review rate limited](https://github.com/AtraFelis/ebook_viewer/pull/14#issuecomment-6260136985)로 실제 미실행이며 성공 체크를 재리뷰 완료로 세거나 중복 요청하지 않는다. 후속 변경은 결과를 기록한 문서뿐이다. debug 앱/최신 계측 데이터 보존 재설치·앱 실행도 확인했다. 사용자만 병합하며 P2 전체 완료는 아니다. 다음은 읽기 설정(테마/밝기/화면 유지), 이후 선택·복사/접근성과 최종 통합 검증이다.


## 선명도 개선 — main 기반 독립 브랜치

`origin/main` 5d74c68에서 현재 체크아웃의 `codex/p2-sharpness-recovery`를 만들었다. Figma 문서 변경은 별도 백업/stash 후 동일 블록/이력 해시를 확인해 그대로 재적용했다. 복구용 stash는 보존했다. 검색 소스/진단 모듈·Gradle/CI 변경은 이 브랜치에 가져오지 않고 최신 범위 계획만 반영했다. 검색·목차·비밀번호 PDF 읽기는 P2 필수에서 제외하고 선택/복사·접근성 등은 유지한다. 검색 추가 개발/진단은 보류한다.

View의 실제 onDraw 가시 면적 95% 선명화와 전달된 타일의 큐/페이지 open-render-close/전달 지연을 계측한다. 계측은 기본 꺼짐이며 미리보기·다른 배율 fallback·페이지 외부/간격은 선명 면적에 포함하지 않는다. 비디버그 합성 벡터/그레이/RGB의 기준선을 수집한 뒤 최대 4개 타일 배치 안의 같은 페이지 재사용을 비교한다. 새 런타임 의존성 없음. [구조/한계](docs/adr/0012-bounded-page-reuse-and-sharpness.md), [실행/미결](docs/history/2026-10-18-13-sharpness-baseline-and-page-reuse.md).

기존 PR #12의 실제 CodeRabbit 3건은 기존 브랜치에서 최소 수정/호스트 19개 검사 후 f94ffc4로 push했다. 후속 수동 요청 6250577597은 한도로 실제 미실행(6250580688)이며 자동 재요청하지 않는다. 검색 보류를 새 검색 구현으로 바꾸지 않는다. 아래 ‘P2 전체 포함/목차 미결’은 이전 범위 기록이며 최신 계획이 우선한다.

동일 profile/합성 자료 SHA·가로 2400×1080/60Hz에서 전후 각 540표본(조건별 30×3회)을 수집했다. 첫 방향 선명화 p95는 벡터 1714→649ms, 그레이 132→64ms, RGB 464→166ms로 보통 스크롤 목표를 충족했고 timeout 0/64MiB cache를 유지했다. 같은 계측의 프레임 30초×3회도 전후 p95 약 6.0~6.7ms/p99 최대 8.4ms로 기존 목표 이내였다. 메모리는 일부 조건에서 증가했고 실제 peak/다른 자료의 안전성 보장으로 보고하지 않는다. 고배율/빠른 플링의 선명도 분포·복잡한 원본 대조·P2 최종 통합 수용은 아직 남아 있다.

최종 debug app c945bfe1에서 기존 API 33 기본 41개/실패 0·앱 실행과 별도 화질 대조 1개/실패 0을 확인했다. 미리보기 이후 3/8/32 배율의 벡터/그레이/RGB 타일을 매번 새 페이지를 여는 기준과 비교해 픽셀이 일치했다. 기본 41개와 추가 대조는 같은 app/다른 계측 APK이며 하나의 42개 실행으로 보고하지 않는다. 사용자는 개인 PDF에서 **“더 빨라졌고 화질도 정상”**이라고 확인했다. 합성 자동 검증과 이 자기보고를 구분하며 이번 첫 개선으로 P2 전체/선명도 전체를 완료 처리하지 않는다.

첫 개선을 [PR #13](https://github.com/AtraFelis/ebook_viewer/pull/13)에 등록했다. 구현 head 892c44b의 [push CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39136769982)와 [PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39136773066)는 모두 성공했다. CodeRabbit [수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/13#issuecomment-6251196279)은 [실제 접수](https://github.com/AtraFelis/ebook_viewer/pull/13#issuecomment-6251197303) 후 29개 파일 리뷰를 완료했다. 타당한 2건(계측 복원 실패 시 원래 오류/보고 유실, 완료된 선명도 기준선의 오래된 다음 단계 문구)을 43c7c76에서 수정/push하고 두 스레드를 해결 처리했다. 후속 strict 계측 빌드/Lint/ktlint와 PC 실패 경로 4건은 통과했으며 기기 측정을 재실행한 것은 아니다. 수정 head의 [재요청](https://github.com/AtraFelis/ebook_viewer/pull/13#issuecomment-6251306446)은 최초 접수 응답 이후 [Review rate limited](https://github.com/AtraFelis/ebook_viewer/pull/13#issuecomment-6251309603)로 바뀌어 실제 재리뷰 미실행이다. 성공 체크를 재리뷰 완료로 세지 않으며 중복 요청하지 않는다. 수정 head CI 결과는 아래를 따르며 PR 병합은 사용자가 한다.

리뷰 수정 head 43c7c76의 [push CI 39137832581](https://github.com/AtraFelis/ebook_viewer/actions/runs/39137832581)와 [PR CI 39137835631](https://github.com/AtraFelis/ebook_viewer/actions/runs/39137835631)도 모두 성공했다. 이후 변경은 이 결과/재리뷰 한도를 기록한 문서뿐이며 실제 앱/계측 검사 head와 구분한다. 다음은 남은 선명도 시나리오/최종 통합과 자동 배경색 등 P2 필수 기능이다.

## P2 착수 실행 — 플랫폼 검증

착수 검증을 [PR #11](https://github.com/AtraFelis/ebook_viewer/pull/11)로 등록했다. 구현 head `a387ad4`의 [Windows CI 39099974986](https://github.com/AtraFelis/ebook_viewer/actions/runs/39099974986)와 push CI 39099946435가 성공했다. CodeRabbit [수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/11#issuecomment-6246012788)/실제 접수 후 16개 파일 리뷰를 완료했고 로드맵의 미실행 범위 문구 1건을 타당해 수정했다. 코드 지적은 없었고 후속 문서 변경의 체크와 실행한 앱/도구 소스 CI를 구분한다. 기존 디자인 수정은 미커밋으로 보존하며 PR 병합은 사용자가 한다.

현재 체크아웃의 `codex/p2-readiness`에서 기존 디자인/계획 미커밋 변경을 보존했다. 연결된 API 33 기기의 **S/T 확장 23**과 기본/PreV 공개 API를 자체 shell probe로 확인했다. 합성 자료에서 PreV 텍스트 추출·검색/결과 없음·선택·렌더, AES-128/256 암호 없음/오류 거부와 올바른 암호 검색/렌더를 통과했다. 앱 설치/개인 문서/기록은 건드리지 않았으며 앱 UI 기능 완료와 구분한다.

그레이/RGB 등 합성 이미지 4종×2 renderer×3회=24개 격리 표본을 실행했다. 기본 renderer 첫 타일 중앙값은 그레이 q85 13.03ms, 같은 픽셀 RGB q85 37.15ms, RGB q50 35.77ms다. 앱 큐·실제 draw·프레임·PSS가 없는 측정이므로 사용자 PDF 병목이나 스크롤 개선/합격으로 보고하지 않는다. [ADR 0009](docs/adr/0009-p2-platform-pdf-capabilities.md), [실행·한계](docs/history/2026-10-18-05-p2-platform-readiness.md), [재현 절차](docs/testing/p2-platform-probes.md).

새 런타임 라이브러리/SDK·Kotlin/AGP/Wrapper 변경은 없다. 확장 플랫폼의 앱 어댑터/계측을 우선 진행하는 방향이며 문서 전체 목차의 보조 경로와 실제 앱 선명화 기준선/목표는 여전히 미결이다. 외부 라이브러리 후보는 POM/module/manifest·SHA-256·API를 정적 조사했을 뿐 채택/앱 실행하지 않았다. P2-0 전체 완료/P2 완료가 아니다.

## P2 착수 계획 — 2026-10-18

최신 분석 화면: 느렸던 두 번째 이미지는 픽셀 수가 약간 적지만 용량은 약 1.9배이며 첫 이미지의 그레이와 달리 RGB다. DPI도 두 번째가 더 높아 기존 저DPI 가설을 확정하지 않는다. [픽셀/용량·색상/배치 크기·캐시와 순서의 비교 계획](docs/plan/p2-sharpness-recovery.md)을 보완했다. 분석 화면만 확인했고 원본 PDF/기기 시간을 분석하거나 디코딩 병목을 실증한 것은 아니다. [추가 이력](docs/history/2026-10-18-04-image-render-hypothesis.md).

추가 사용자 관찰: ‘DPI가 낮을수록 선명도 복구가 오래 걸리는 것 같다’. 실제 이미지 해상도/배치 크기·압축과 시간을 측정하지 않은 가설이며 [선명도 계획](docs/plan/p2-sharpness-recovery.md)에 낮은/높은 유효 PPI·메타데이터만 변경한 대조군·벡터의 비교를 추가했다. 원본 화질 한계와 현재 배율 타일 표시 지연을 구분하며 앱 코드/기기 측정은 변경·실행하지 않았다.

사용자가 기존 로드맵의 P2 전체(선명도·자동 배경색·검색·선택/복사·목차·암호화·테마/밝기/화면 유지·TalkBack/큰 글꼴)를 포함하고 Figma 디자인 적용은 별도 단계로 분리했다. [착수 전 준비/기능별 수용/정확한 종료 판정](docs/plan/p2-scope-and-completion.md)을 작성했다. 세부 수용 기준/성능 수치는 제안이며 엔진 선정·새 의존성 구성 승인은 아직 하지 않았다.

fetch 후 main `0aea06e`와 origin/main 차이 0, PR #10 사용자 병합과 PR #9/#10 실제 리뷰 제출 없음 확인. 기존 디자인 변경(SESSION/CHECKLIST 및 history 01/02)을 보존했다. 이번에는 문서만 갱신하며 앱 구현/빌드/설치/성능 측정은 하지 않았다. [실제 확인·미결 이력](docs/history/2026-10-18-03-p2-planning.md).

착수 계획 당시에는 API 33/SDK 확장·엔진 지원 경로, 구체적인 구성안/승인·ADR, 합성 샘플과 기준 측정/합격선을 준비하는 단계였다. 당시 Context7/공식 API·SDK 35 서명 대조는 API 33 실행 증거가 아니었고 승인 기기 0대여서 확장/실행은 미확인이었다. 이후 실제 연결/실행은 위 ‘P2 착수 실행’ 결과를 따른다. 새 엔진/라이브러리는 도입하지 않았다. 기능 전체·최종 통합 검증·개인 기기 수용·사용자 PR 병합·완료 기록이 끝나야 P2를 종료하며 종료일은 준비 단계 뒤 근거와 함께 제시한다.

## UI/UX 디자인 초안 — 2026-10-18

사용자 요청으로 송채선 팀에 [Ebook Viewer — UI UX Design](https://www.figma.com/design/wFQ8qOUx3YfMy8u0E3t7fM)을 생성했다. 차분한 도구형 톤과 이어 읽기 중심 추천안/플로우를 사용자가 승인한 뒤, 변수 39개·텍스트 스타일 6개·컴포넌트 8종/25변형과 21개 화면·상태 초안을 작성했다. 마지막 버튼 예시 문구와 설정 토글/첫 안내/로딩 화면을 보정·시각 확인했다. [화면 명세와 검증 이력](docs/history/2026-10-18-02-design-draft.md).

편집 가능한 정적 시안이며 사용자 최종 디자인 수용, 클릭 연결 프로토타입, Ready for dev 표시는 미완료다. 앱 코드/의존성과 P2 구현은 변경하지 않았다. 다음은 사용자 시안 검토 결과를 반영한 뒤 구현 범위를 정하는 것이다. [준비 당시 근거](docs/history/2026-10-18-01-design-preparation.md)는 과거 상태로 보존한다.

## 현재 상태

2026-10-17, 개인용 SM-N981N(API 33) 대상의 **P1 핵심 MVP를 완료**했다. 사용자 병합한 [PR #9](https://github.com/AtraFelis/ebook_viewer/pull/9)의 최종 head `cefc265`와 origin/main 병합 `6400b85`를 확인했다. 최종 head의 [Windows CI 39029124087](https://github.com/AtraFelis/ebook_viewer/actions/runs/39029124087)는 성공했다. 현재 체크아웃의 깨끗한 상태에서 main 기반 `codex/p1-completion`을 만들고 완료 판정·후속 범위만 정리한다. [완료 근거·명령·제한](docs/history/2026-10-17-12-p1-completion.md).

완료 기능은 SAF 열기/오류 복구, 최근·읽던 PDF 목록과 내부 앵커/줌 이어 읽기, 전체 화면/중앙 탭 설정, 자동 및 가로·세로·두 역방향 고정, 수동 외부 배경색, 번호 표시 선택/페이지 이동/북마크, 문서별 크기/간격, 수명·캐시·자원 회귀 보완이다. 새 런타임 의존성 없음.

최종 앱의 strict 빌드·단위 22개·Lint 오류/경고 0·ktlint, API 33 기본 계측 41개/실패 0·앱 실행을 통과했다. 두 역방향은 실제 display rotation 180도·줌/앵커·재실행 보존을 자동 계측했다. 별도 v2 프로세스 복원은 수명 보완 앱의 seed 1개 → force-stop → 다른 PID verify 1개 통과이며 기본 suite와 별도 집계한다. 이번 문서 마무리에서 앱/기기 검사를 재실행한 것으로 보고하지 않는다.

사용자는 개인 PDF 열기·목록→이어 읽기가 “정상”, 수명 보완 앱의 홈 복귀·확대/스크롤·중앙 탭 설정이 “다 잘 돼”라고 확인했다. 새 역방향의 사용자 직접 확인과 자동 계측을 구분한다. 다른 API/기기·제조사 provider 전반·재부팅·실제 시스템 메모리 고갈/OOM은 후속 미검증이며 이번 개인용 P1 완료 범위를 확대하지 않는다.

CodeRabbit PR #8의 실제 리뷰 지적 1건(문서 상태 표현)을 수정/해결했다. PR #9는 [수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/9#issuecomment-6233338631) 후 [한도 응답](https://github.com/AtraFelis/ebook_viewer/pull/9#issuecomment-6233341731)으로 실제 리뷰 미실행이다. 리뷰 제출/인라인 지적이 없는 상태를 확인했으며 성공 체크를 리뷰 완료로 취급하지 않는다. PR #6도 당시 한도로 미실행이어서 모든 P1 PR의 외부 리뷰 완료를 주장하지 않는다.

완료 문서를 [PR #10](https://github.com/AtraFelis/ebook_viewer/pull/10)으로 등록했다. Markdown 7개만 변경했으며 Android checks는 paths-ignore로 새 실행이 없다. [CodeRabbit 수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/10#issuecomment-6233510967)을 한 번 등록했고 [실제 응답](https://github.com/AtraFelis/ebook_viewer/pull/10#issuecomment-6233511959)은 Review rate limited였다. 20:47 KST 당시 26분 후 복구 안내이며 실제 리뷰는 미실행이다. 요청/응답 기록만 같은 PR에 후속 push하고 중복 요청하지 않는다. PR 병합은 사용자가 한다.

P1 완료 당시 다음 범위는 P2 선명도 복구·자동 배경색이었다. 이후 [선명도 1차 개선](docs/plan/p2-sharpness-recovery.md)의 보통 스크롤 기준선·목표/개선 검증·개인 PDF 수용을 완료했으며 현재 상태는 맨 위 기록을 따른다. 빠른 플링·고배율·복잡한 자료와 최종 통합 검증, 자동 배경색과 다른 P2 필수 기능은 남아 있다. P1에 새 성능 목표나 다른 기기 수용 조건을 추가하지 않는다.

## 이전 작업 기록

아래는 각 작업 당시 상태를 보존한 기록이다. 현재 완료/대기 판정은 위 상태와 최신 history를 따른다.

`codex/p1-reverse-orientation`에서 요청한 가로/세로 역방향 고정을 기존 이름 기반 설정 v1에 추가했다. strict 빌드·단위 22개·Lint 오류/경고 0·ktlint와 기존 포함 immersive 계측 5개를 통과했다. 실제 display rotation 180도·앵커/줌/전체 화면·재실행 보존 및 다섯 모드 디스크 왕복을 확인했다. 전체 41개는 같은 APK에서 진행 중이며 [역방향 이력](docs/history/2026-10-17-11-reverse-orientation.md)에 실제 결과를 구분한다. PR #8 head db616d5의 Windows CI 39027868781는 성공했고 한도 복구 후 실제 리뷰를 완료했고 상태 문서 지적 1건을 수정했다. 현재 브랜치는 해당 head부터 분리됐으며 후속 PR 기준은 PR #8 병합 상태를 확인한다.

PR #8 head `bc26364`의 [Windows CI 39012958849](https://github.com/AtraFelis/ebook_viewer/actions/runs/39012958849)는 성공했다. 이후 자동 승인 검토 사용량 한도로 Git 기록/브랜치 생성이 실행되지 않았지만 로컬 문서는 보존됐다. 20:12 KST 재개 시 승인 도구가 다시 동작함을 확인했다. 개인 수용/한도 기록을 PR #8에 push한 뒤 별도 역방향 설정 브랜치를 만든다. 두 번째 수동 요청으로 head db616d5의 16개 파일 실제 리뷰를 완료했다. 코드 동작 지적은 없고 이 문서의 과거 리뷰 상태 표현 1건이 지적되어 현재 사실로 수정했다. 원격 CI 39027868781도 성공했다. 이 결과만 기록한 후속 문서 head checks는 별도 확인한다.

사용자가 수명 보완 APK에서 개인 PDF의 홈 복귀·확대/스크롤·중앙 탭 설정(방향/배경색/번호 표시)이 “다 잘 돼”라고 확인했다. 기존 P1 개인 사용 수용을 기록하며 자동 계측과 구분한다. 추가 요청한 가로 역방향/세로 역방향 고정은 다음 작은 변경으로 구현/검증한다. PR #8 수동 리뷰는 [요청](https://github.com/AtraFelis/ebook_viewer/pull/8#issuecomment-6231097690) 후 한도 제한으로 실제 미실행이다. 봇이 16:16 KST 당시 9분 후 복구를 안내했으며 성공 체크를 실제 리뷰로 보고하지 않는다. 최신 CI는 PR checks로 구분한다.

수명/캐시 보완 구현 `8270cde`를 main 대상 [PR #8](https://github.com/AtraFelis/ebook_viewer/pull/8)로 등록했다. 새 head의 원격 CI/CodeRabbit은 [최신 checks](https://github.com/AtraFelis/ebook_viewer/pull/8/checks)와 실제 봇 응답으로 확인한다. 이 등록 기록 push 후 수동 리뷰를 한 번 요청한다. 앱 설치/계측 검증은 아래와 같으며 병합은 사용자가 한다.

사용자 병합된 PR #7 최종 head a282718의 Windows CI 39010146312 성공과 origin/main `29d0763`를 확인했다. 깨끗한 현재 체크아웃의 `codex/p1-reader-lifecycle`에서 정지 중 타일 렌더/캐시 재충전 차단, 같은 앵커의 복귀, 취소 전 동일 키 타일 배제, 파괴 시 캐시 해제를 구현했다. strict 빌드·단위 22개·Lint 오류/경고 0·ktlint 및 신규 API 33 계측 4개/최종 전체 40개·앱 실행 통과. 기록 v2 크기/간격을 포함하는 별도 seed→force-stop→다른 PID verify도 각 1개/실패 0, 이후 앱 실행을 통과했다. [ADR 0008](docs/adr/0008-reader-lifecycle-and-cache-cancellation.md), [명령·범위·결과](docs/history/2026-10-17-10-reader-lifecycle.md). 새 런타임 의존성 없음. 실제 메모리 고갈/OOM·다른 API·새 APK의 개인 수용을 자동 계측으로 대체하지 않는다. 새 APK의 개인 홈 복귀/확대·스크롤/중앙 탭 설정 수용을 요청했다.

PR #7 head `32bd52b`의 [Windows CI 39009827260](https://github.com/AtraFelis/ebook_viewer/actions/runs/39009827260)가 성공했다. CodeRabbit도 같은 head의 19개 파일 실제 리뷰를 완료했고 actionable 지적은 없었다([결과](https://github.com/AtraFelis/ebook_viewer/pull/7#issuecomment-6230655962)). 함수 설명문 80% 비율의 일반 권고는 계약의 이유/제약 중심 주석과 별개이며 구체적 누락 계약/오류를 지적하지 않아 기계적으로 추가하지 않았다. 결과 기록만 담는 후속 문서 head의 CI는 [최신 checks](https://github.com/AtraFelis/ebook_viewer/pull/7/checks)로 구분한다. 앱/테스트 코드는 그대로이며 불필요한 재설치/중복 리뷰 요청은 하지 않는다. 다음 P1 범위는 백그라운드 복귀·메모리 압박·문서 교체 시 상태/자원 회귀와 남은 개인 기기 수용이다.

파일 접근·오류 복구 구현 `c238fda`에 origin/main `b41b0c9`를 일반 merge한 `c206098`을 [PR #7](https://github.com/AtraFelis/ebook_viewer/pull/7)로 등록했다. 병합 전후 검증된 앱/스크립트 차이는 없다. 아래 로컬/기기 결과와 원격 검사를 구분하며 [최신 CI/리뷰](https://github.com/AtraFelis/ebook_viewer/pull/7/checks)는 게시 후 확인한다. 이 문서 갱신을 push한 뒤 CodeRabbit 수동 리뷰를 한 번 요청한다. 병합은 사용자가 한다.

사용자가 최종 접근 기능 APK에서 개인 PDF 열기·몇 페이지 이동·목록→이어 읽기가 “정상”이라고 확인했다. 내용/파일명은 받지 않았다. 자동 계측과 사용자 수용 자기보고를 구분하며 전체 설정/물리 회전/재부팅/다른 API 수용 완료로 확대하지 않는다.

`codex/p1-document-access`에서 원인별 오류·목록 복귀·재시도/재선택과 취소 가능한 provider 접근을 구현했다. 비탐색 파일은 256MiB 상한/30초 무응답 기준으로 익명 임시 inode에 복사하고, 탐색 가능한 파일은 그대로 연다. provider 조회/열기는 PDF worker에 두고 기록 큐를 막지 않는다. strict 빌드·단위 22개·Lint 오류/경고 0·ktlint 통과. 첫 신규 6개 중 1개 실패는 테스트 monitor의 Intent category/MIME 누락으로 진단했고 수정 후 provider 단독 1개는 통과했다. 최종 전체 API 33 계측 36개/실패 0·앱 설치/실행을 통과했다. [ADR 0007](docs/adr/0007-document-access-and-failure-recovery.md), [명령·실패 수정·결과](docs/history/2026-10-17-09-document-access.md). 새 런타임 의존성 없음. 실제 DocumentsUI/제조사 provider 전반·재부팅·암호화 파일은 별도 미검증이며, 개인 PDF 기본 열기/이어 읽기는 위 사용자 확인 범위다.

PR #6 최종 head `a45d2d9`의 Windows CI 39007984185가 성공했고 사용자가 병합했다. fetch 후 origin/main은 `b41b0c9`다. CodeRabbit 실제 리뷰는 당시 무료 한도 소진으로 수행되지 않았다. 다음 기능 PR도 생성 시 수동 리뷰를 요청하고 현재 head/응답을 확인한다.

구현 소스가 같은 `b35a962`의 [Windows CI 39007698593](https://github.com/AtraFelis/ebook_viewer/actions/runs/39007698593)는 성공했다. 이 결과와 최종 상태 문서만 갱신한 후속 head의 [실시간 checks](https://github.com/AtraFelis/ebook_viewer/pull/6/checks)를 구분한다. CodeRabbit 실제 리뷰는 한도 소진으로 미실행이며, 다음 일은 한도 복구 후 리뷰 확인과 P1 파일 접근/오류 UX·개인 PDF 수용이다.

PR #6에 [수동 리뷰 댓글](https://github.com/AtraFelis/ebook_viewer/pull/6#issuecomment-6230354554)을 한 번 등록했고 접수 응답을 확인했다. 그러나 CodeRabbit이 무료 OSS 리뷰 한도 소진과 당시 29분 후 재사용 가능을 안내해 **실제 리뷰는 미실행**이다. 성공 상태를 리뷰 완료로 보고하지 않으며 중복 재요청하지 않았다. CI는 별도로 진행하며 최신 결과는 [PR checks](https://github.com/AtraFelis/ebook_viewer/pull/6/checks)/본문을 확인한다. 한도 복구 후 후속 작업에서 현재 head/접수 이력을 다시 확인하고 수동 리뷰를 진행한다.

페이지 크기·간격 구현 `8be274b`에 origin/main `1aece35`를 일반 merge한 `7cd490f`를 push하고 [PR #6](https://github.com/AtraFelis/ebook_viewer/pull/6)을 등록했다. merge 전후 검증한 앱/스크립트 차이는 없었다. 로컬/기기 결과는 아래와 같으며 [원격 checks](https://github.com/AtraFelis/ebook_viewer/pull/6/checks)는 게시 후 별도 확인한다. PR마다 수동 CodeRabbit 요청 규칙을 적용하고 접수/실제 리뷰를 구분한다. 병합은 사용자가 한다.

PR #5는 CodeRabbit이 head `751dbc0`의 23개 파일 실제 검토를 완료해 지적이 없었고 CI도 통과했다. 사용자가 병합한 origin/main `1aece35`와 부모 head 포함을 확인했다. 다음 브랜치 `codex/p1-page-layout`에서 문서별 원본/너비 통일·간격 설정과 v1→v2 읽기 기록 호환을 구현했다. strict 빌드·단위 22개·Lint 오류/경고 0·ktlint 및 신규 API 33 계측 3개·최종 전체 30개·앱 실행을 통과했다. 첫 전체 2개 실패는 저장된 가로 고정과 테스트의 직접 회전/고정 스와이프 횟수 충돌을 보완한 뒤 재검증했다. PR #6을 등록했고 앱/계측 소스가 같은 b35a962의 원격 CI 39007698593도 성공했다. 최신 문서 head checks는 별도 확인한다. [ADR 0006](docs/adr/0006-page-layout-and-reading-records-v2.md), [실패 수정·명령·결과](docs/history/2026-10-17-08-page-layout.md). 개인 PDF/실제 provider와 P1 전체 완료는 별도다.

사용자 지시로 모든 PR 생성 시 CodeRabbit 수동 요청, 후속 리뷰의 타당성 판단·수정/검증 후 같은 브랜치 push를 AGENTS/[Git 규칙](docs/rule/git.md)에 저장했다. PR #5는 자동 리뷰 제외를 확인해 수동 명령을 한 번 등록했고 실제 검토 완료까지 확인했다. [절차/실제 대응 이력](docs/history/2026-10-17-07-pr-review-workflow.md).

`codex/p1-navigation-bookmarks`에서 페이지 이동·북마크 및 사용자 지정 번호 표시/숨김을 구현/검증했다. 번호는 기본 숨김이며 설정을 켜도 Canvas 크기/배율은 유지한다. 페이지/북마크 이동은 현재 줌·유효 가로 위치를 보존한다. strict 빌드·단위 19개·Lint·ktlint 및 신규 API 33 계측 4개 통과, 저장 경로 보완 후 최종 전체 27개 회귀·앱 설치/실행까지 통과했다. 구현 `8861ae7`의 [Windows CI 38935046856](https://github.com/AtraFelis/ebook_viewer/actions/runs/38935046856)는 성공했다. 중단돼 있던 PR 등록을 마무리해 [PR #5](https://github.com/AtraFelis/ebook_viewer/pull/5)를 만들었다. [최종 head checks](https://github.com/AtraFelis/ebook_viewer/pull/5/checks)는 별도 확인하며 병합은 사용자 몫이다. [ADR 0005](docs/adr/0005-navigation-bookmarks-and-page-numbers.md), [명령·결과](docs/history/2026-10-17-06-navigation-bookmarks.md). 새 런타임 의존성 없음.

`codex/p1-background-color`에서 P1 수동 외부 배경색을 구현/검증했다. 기본/임의 RGB 색상, 미리보기·입력 검증·취소, 별도 AtomicFile 설정 저장을 추가했다([ADR 0004](docs/adr/0004-custom-reader-background.md)). strict 빌드·단위 15개·Lint·ktlint 통과, API 33 전체 23개 회귀·앱 설치/실행까지 통과했다. 구현 `e6753bf`를 main 대상 [PR #4](https://github.com/AtraFelis/ebook_viewer/pull/4)로 등록했다. 최종 head `be0de9d`의 [CI 38932215957](https://github.com/AtraFelis/ebook_viewer/actions/runs/38932215957)가 통과했고 사용자가 병합했다. fetch/조상 검사로 origin/main `24ef93b`를 확인했다. 자동 색 추출과 스크롤 선명도 복구 지연은 사용자 범위 조정으로 P2에 남긴다. [이번 결과](docs/history/2026-10-17-05-custom-background.md), [선명도 P2 계획](docs/plan/p2-sharpness-recovery.md).

사용자의 확대 한도/과도한 축소 피드백을 `codex/reader-zoom-range`에서 구현/기기 검증을 완료했다(PR #2 문서 head `3d7b6d5`부터 분리). 최대 배율 8→32, 짧은 변 너비 맞춤의 75% 핀치 축소 하한이며 회전/기존 기록의 작은 배율은 강제 재맞춤하지 않는다. strict 빌드·단위 12개·Lint·ktlint 통과, API 33 전체 20개 회귀/앱 설치·실행까지 통과했다. 첫 중앙 반복 탭/뒤로가기 실패를 이중 탭 간섭 제거로 보완한 뒤 전체를 재검증했다. 구현 `cb2cc6c`를 main 대상 [PR #3](https://github.com/AtraFelis/ebook_viewer/pull/3)으로 등록했다. 최종 head `5c3550b`의 [CI 38929386952](https://github.com/AtraFelis/ebook_viewer/actions/runs/38929386952) 통과와 사용자 병합을 확인했다. fetch 결과 origin/main은 `af90f29`다. [변경·검증 이력](docs/history/2026-10-17-04-zoom-range.md). 새 런타임 의존성 없음.

후속 브랜치 `codex/p1-immersive-reading`에서 전체 화면·중앙 탭 메뉴·방향 설정을 구현했다. 새 API 33 UI/설정 테스트 4개와 기존 포함 전체 19개 회귀, strict 빌드·단위 9개·Lint·ktlint 통과. 기기에 새 debug APK 설치/실행 완료. 새 런타임 의존성 없음. [ADR 0003](docs/adr/0003-immersive-reader-and-orientation.md), [진행/검증 이력](docs/history/2026-10-17-03-immersive-reading.md). 구현 `ff1ea45`를 [PR #2](https://github.com/AtraFelis/ebook_viewer/pull/2)로 등록했다. 최종 문서 head `3d7b6d5`의 [Windows CI 38927859821](https://github.com/AtraFelis/ebook_viewer/actions/runs/38927859821)까지 성공했다. 사용자가 PR #2를 병합했고 fetch로 origin/main `70829a7` 및 부모 head 포함을 확인했다.

P1 첫 범위인 최근/읽던 문서 목록과 문서별 이어 읽기를 구현했고 당시 API 33 debug 계측 15개, 별도 프로세스 복원 seed/force-stop/verify를 통과했다. 구현 `9fdb405`를 `codex/p1-reading-library`에 push하고 [PR #1](https://github.com/AtraFelis/ebook_viewer/pull/1)을 만들었다. [구현 검증](docs/history/2026-10-17-01-p1-reading-library.md), [PR/CI 이력](docs/history/2026-10-17-02-p1-library-pr.md).

현재 개인용 SM-N981N(API 33)·합성 자료 범위의 P0 기술 검증과 원격 CI를 완료했다. 사용자는 이 기기에서 동작하면 충분하다고 결정했다. API 23·35/다른 기기는 이번 완료 조건에서 제외하며 minSdk 23 선언을 기기 검증 완료로 표현하지 않는다. 사용자가 P0 변경의 main push를 “push 해”로 명시 승인했고 실제 원격 CI도 통과했다.

전체 화면 기반 브랜치 `codex/p1-immersive-reading`은 PR #1의 `df3ef15`에서 현재 체크아웃을 사용해 생성했다. PR #1은 사용자 직접 병합으로 origin/main `a67b0a8`에 포함됐다. P0 구현 커밋 `0863969`를 origin/main에 일반 push했고 GitHub Actions 실행 38885324826이 새 Windows runner에서 도구 설치/Wrapper 검증/앱·테스트 APK 빌드/단위/Lint/ktlint strict를 통과했다. reset/clean/강제 push 없음. 작업 경로는 `C:\Users\cotjs\workspace\ebook_viewer`.

- 승인 구성: Kotlin/stdlib 2.1.20, AGP 8.7.2, Gradle 8.9, Temurin 17.0.20.1+1, compile/target 35/min 23, Platform 35 rev2, Build Tools 34.0.0. Android Studio는 미설치, CLI 사용. 시스템 Java는 바꾸지 않고 프로젝트 설치본을 사용한다.
- 플랫폼 Activity/SAF + PdfRenderer + View/Canvas. 첫 4페이지를 먼저 제공하고 나머지 크기를 single worker에서 점진 조사한다. 사용자 줌/좌표 유지. 회전 복원은 정확한 전체 크기를 먼저 확보하므로 새 문서 첫 표시보다 오래 걸릴 수 있다.
- 가시 페이지 미리보기, 512px 타일, 배율 단계/이전 타일이 같은 64MiB 캐시를 공유한다. 진행 중 native render는 즉시 중단할 수 없으며 문서/요청 검사와 worker 순서로 수명을 관리한다.
- 최종 debug/profile 빌드, 단위 6개, Lint 오류/경고 0, ktlint strict 통과. API 33 비디버그 계측 11개 통과. 제스처는 MotionEvent 주입, 방향 변경은 requestedOrientation이며 물리 센서/손입력 검증과 구분한다.
- 최종 비디버그 30초 3회: 첫 고해상도 타일 486/818/869ms, 프레임 p95 5.6~5.9ms, 캐시 64MiB 이내.
- 같은 최종 APK로 10분 1회 완주: p95 6.692ms/p99 7.322ms, OOM 없음, 최대 캐시64MiB 이내. PSS 최고 표본302,958KiB/종료159,765KiB. 빠른 이동의 미준비 페이지 표본426/2,456과 FD106→135는 한계로 기록하며 완전한 누수/공백 제거를 선언하지 않는다.

## 승인과 다음 행동

런타임은 승인된 stdlib 2.1.20과 전이 annotations 13.0뿐이다. JUnit/ktlint는 검사 전용. P1은 플랫폼 AtomicFile을 사용하며 읽기 기록은 v1 호환/v2 쓰기([ADR 0006](docs/adr/0006-page-layout-and-reading-records-v2.md)), 방향/배경색/번호와 북마크는 별도 v1이다. Compose 최종 선택, 새 저장/PDF 라이브러리는 미선정이며 새 런타임 의존성 승인 규칙을 유지한다.

1. P0 현재 범위 완료: 구현 `0863969` 및 결과 문서 `08011b3`를 main에 push했고 CI 38885324826 성공. P1은 사용자 승인으로 기능별 PR 방식을 사용한다. 첫 [PR #1](https://github.com/AtraFelis/ebook_viewer/pull/1)은 구현/문서 최종 head `df3ef15`의 [Windows CI 38923209449](https://github.com/AtraFelis/ebook_viewer/actions/runs/38923209449)까지 통과했고 사용자가 `a67b0a8`로 병합했다. 전체 화면/방향 기능은 PR #2로 분리했다.
2. 사용자 P1 요구사항을 [로드맵](docs/plan/roadmap.md)에 통합했고, 목록/이어 읽기, 전체 화면·중앙 탭 도구·방향 설정, 수동 외부 배경색까지 구현했다. 읽기 도구에는 설정/목록/읽기 복귀와 페이지 이동/북마크를 제공하며 페이지 번호 표시는 사용자 선택이다. 페이지 이동/북마크 검증과 PR 등록을 완료했다. 페이지 간격/원본 크기·너비 통일 모드는 로컬 및 API 33 기기 검증을 완료했다. 파일 접근/오류 UX와 합성 provider 검증까지 구현/검증했다. 다음은 실제 파일 선택 UI/개인 PDF 수용과 수명·메모리 압박 회귀다.
3. 2026-10-17 사용자 결정: 스크롤 선명도 복구 지연은 [P2 전용 계획](docs/plan/p2-sharpness-recovery.md)에 문서화하고 개선은 P2에서 한다. 자동 배경색도 P2로 이관하며 P1은 수동 색상/설정 보존으로 완성한다. 현재 성능은 충분하므로 별도 렌더링/스크롤 최적화 및 빠른 이동의 미준비 영역 개선은 P1 필수에서 제외한다. 기존 성능과 줌/앵커·캐시/수명 계약은 유지하고 새 기능에 따른 회귀는 검증한다. 손입력·물리 회전·개인 PDF로 기능 수용을 확인한다.
4. 새 Windows CI의 CLI 도구 복원은 검증했지만 실제 새 PC의 드라이버/Android Studio/기기 연결 복원과 다른 기기/API는 미검증이다. API 33 암호화 읽기·검색 등은 P2 범위다. 새 저장/UI 라이브러리를 채택하지 않았으며 필요 시 별도 구성 승인 후 도입한다.
5. 실제 SAF provider 영구 권한·재부팅·권한 소실 재연결과 개인 PDF 손입력/물리 회전 센서 수용은 남아 있다. 비탐색 provider 제한 복사는 합성 provider로 검증했고, 실제 제조사 파일 선택 UI/개인 PDF 수용은 후속이다. 방향 설정의 별도 강제 프로세스 종료/기기 재부팅은 미검증이다. 갑작스러운 종료 직전 미제출/미완료 이동은 유실될 수 있으며 손상된 기록/설정의 복구 UI는 아직 없다. 이번 통과는 API 33 합성 자료/앱 내부 파일 URI와 주입 입력 기준이다.

[push·CI 결과](docs/history/2026-10-16-10-github-push-and-ci.md), [최종 검증·명령·원본 위치](docs/history/2026-10-16-09-p0-local-validation.md), [첫 표시 개선](docs/history/2026-10-16-08-first-page-priority.md), [Windows 실행](docs/windows-development.md), [ADR](docs/adr/0001-p0-toolchain-and-renderer.md).
