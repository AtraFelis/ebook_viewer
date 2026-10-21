# 화면 방향 선택 후 세부 창 유지

2026-10-21 (Asia/Seoul). 실제 기기 확인은 사용자 연결 후 진행한다.

## 요청과 변경

방향을 선택하자마자 상위 설정으로 돌아가면 회전 결과 확인이나 잘못 선택한 방향 재선택에 불필요한 단계가 생긴다는 사용자 요청이다.

- 기존 선택 listener의 즉시 dismiss를 제거했다. 저장 완료 후 방향은 적용하지만 같은 방향 창을 유지한다. 저장 중 목록을 비활성화/중복 선택 차단하고, 저장 실패 시 마지막 확인된 항목으로 체크를 복원한다.
- 방향 창이 열려 있거나 로드 중인 상태를 Activity Bundle에 기록한다. 재생성 시 기존 상위 설정 복원 뒤 방향 창을 다시 열고 저장소에서 실제 저장된 선택을 읽는다. 새 영속 형식/런타임 의존성은 없다.
- 닫기/뒤로에서만 방향 창 상태를 해제하고 상위 설정으로 돌아간다. 전체 설정 종료도 상태를 지운다. 다른 세부 창의 적용 후 상위 복귀와 기존 PDF 위치/줌 복원은 유지한다.
- 같은 `codex/design-page-slider`/Draft PR #21에 포함한다. 기존 로컬 접수 기록은 함께 보존하고 사용자/별도 디자인 세션의 `docs/design.md` 변경은 수정·커밋하지 않는다.

## PC 검증

```powershell
. ./scripts/android-env.ps1
./gradlew.bat --dependency-verification strict :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck
git diff --check
```

- 최종 BUILD SUCCESSFUL 32초. 단위48개/실패0/오류0/건너뜀0, Lint 오류0/경고0/정보6, ktlint 통과. 초기 기기 테스트 코드의 줄바꿈/미사용 import로 ktlint가 실패했으며 직접 수정 후 같은 전체 명령을 통과했다. ktlintFormat/검사 억제는 사용하지 않았다.
- 앱 SHA-256 `a0a6a16b40f316bd0b314d238508b223fe443c539a5c7a03616c1640224dd3cf`, 계측 `24a40372f60bf08fa8665942c0a107c784198ab8f074696becfbe33c04bad2d3`. 기기 설치본 대조는 미실행이다.
- SettingsNavigationDeviceTest에 방향 선택 후 창 유지·명시적 재생성 뒤 같은 선택/세부 창 복원·닫기/뒤로 복귀 검사1개를 추가했다. 기존 실제 회전/PDF 앵커 검사의 기대값도 상위가 아닌 방향 세부 창으로 바꿨다. ImmersiveDeviceTest는 최상위 세부 창의 포커스를 확인하고 세부/상위 두 단계를 명시적으로 닫도록 변경했다. 새 동작을 숨기거나 검사를 끄지 않았다.
- 이 기기 검사는 **컴파일만 완료/실행 안 함**이다. settings suite는4개, 전체 기본 기대 수는69→70이며 실제 전체 성공 수가 아니다. 사용자가 연결을 나중에 하기로 했으므로 ADB/설치/기기 조회도 시작하지 않았다. 실제 터치·회전 결과 확인과 새 슬라이더5개/관련 글꼴·전체 화면 회귀·개인 수용은 남아 있다.

## 기존 PR #21 리뷰/CI 확인

후속: db844c2의 CI39560887729/39560883846 모두 SUCCESS다. 실제 리뷰5596279775가 제기한 immersive 검사의 세부 창 누락 허용1건은 채택해 [후속 이력](2026-10-21-02-review-orientation-child-focus.md)에서 수정/검증한다. 앱 수정 자체와 계측 검사 보강을 구분한다.

- 기존 af0e64e의 CI39441645592/39441637281 모두 SUCCESS다. 이번 방향 수정의 새 head 검사로 확대하지 않는다.
- 봇 요약 [6293480957](https://github.com/AtraFelis/ebook_viewer/pull/21#issuecomment-6293480957)은 base f90233d→af0e64e, 17개 파일, `No actionable comments`를 명시한다. Run ID `fbf31439-cc05-439d-8c1f-97221760b570`, 응답6293540887도 Review finished다. GitHub reviews/inline 목록은 비어 있으나 이 실제 완료 요약을 읽었으며 수정할 지적은 없었다. 앞선 무료 한도 거부/접수 상태와 구분한다.
- 방향 수정은 검증과 기록을 모아 같은 PR에 일반 push한다. 새 head에 필요하면 후속 리뷰를 한 번 요청하고 실제 접수를 확인하며, 진행 중 head 변경/중복 요청은 피한다. 기기 수용 전 Draft 유지, 병합은 사용자가 한다.
- 게시 결과: db844c2 일반 push와 PR #21 제목/설명 갱신 완료. [후속 요청6310359568](https://github.com/AtraFelis/ebook_viewer/pull/21#issuecomment-6310359568)에 실제 봇6310364669의 Review triggered/PENDING을 확인했다. 새 CI39560887729/39560883846는 IN_PROGRESS, 새 inline 목록은 아직 비어 있다. 실제 후속 리뷰 완료나 원격 CI 통과로 보고하지 않는다. 진행 중 head를 유지해 이 상태 기록은 로컬에 모아 두며 추가 호출/자동 모니터/기기 작업은 시작하지 않았다.
