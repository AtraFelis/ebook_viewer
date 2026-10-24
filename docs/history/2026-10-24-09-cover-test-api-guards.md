# PR #33 표지 계측의 API 조건 보완

2026-10-24. head2d71863의 실제 재검토는 새 actionable1개(4365841915)다. CI40057904366/40057901402는 모두 SUCCESS·MERGEABLE/CLEAN이며 이전4개 스레드는 해결됐다. 새 지적은 공유 요청 테스트가 API23~29의 기본 표지 정책을 고려하지 않은 것으로 타당하다.

## 수정과 검증

- 공유 구독 테스트의 합산10개/시트 우선 단언은 API30 이상에서만 수행한다. API23~29에서는 두 구독의 요청 뒤 요청 목록 비어 있음·기본 그림(null bitmap)·진행 중 렌더 없음도 단언한다. 단순 skip이나 검증 없는 조기 반환을 추가하지 않았다.
- 같은 원인이 있는 실제 격리 표지 테스트도 낮은 API에서는 정상/잘못된 합성 PDF가 기본 그림을 유지하고 격리 렌더를 시작하지 않음을 확인한다. API30 이상은 기존 비트맵 크기/실패 대체 검사를 유지한다. 앱 코드·저장 형식·의존성은 변경하지 않았다.
- 변경한 계측 파일만 ktlint CLI1.5.0 --format을 명시 실행했다. `. ./scripts/android-env.ps1` 후 `:app:assembleDebugAndroidTest :app:lintDebug ktlintCheck --max-workers=2 --dependency-verification strict --console=plain`8초 성공(`local-artifacts/pr33-api-test-build.log`).

API23~29 기기/에뮬레이터는 이번에 실행하지 않았다. 낮은 API 분기는 컴파일·정적 검증이며 실제 하위 API 통과로 보고하지 않는다. 앱 코드가 같으므로 단위84개/unsigned release/전체96개·장시간/성능/OOM은 반복하지 않는다. 연결된 API33의 관련 두 메서드 결과와 게시 상태는 완료 뒤 아래에서 기록한다. 사용자 디자인/버전·기존 stash·정식 앱/개인용 키를 보존한다.

기존 승인 연결이 유지된 API33에서 계측 APK만 -r 갱신하고 공유 구독/실제 격리 표지 두 메서드를 각각 선택 실행해 각 P0 tests=1, failures=0을 확인했다(`local-artifacts/pr33-api-sharedCoverSubscriptionsPrioritizeSheetsAndBoundTheCombinedVisibleSet.log`, `pr33-api-isolatedCoverProducesBoundedPixelsAndInvalidPdfFallsBack.log`). 앱 본체/정식판은 재설치하지 않았고 finally에서 앱/계측 force-stop·adb kill-server를 완료했다. 사용자 두 파일 해시와 기존 stash20fbf558도 동일 보존했다.

## 게시와 리뷰 확인

2369ce0df593b00e2bc02f38a8fc6bd83f2a864a을 같은 PR 브랜치에 일반 push하고 원격 head 일치를 확인했다. 답변4365851483에 실제 검증/미실행을 구분했다. 봇4365852658이2369ce0의 두 테스트 소스를 직접 정적으로 확인해 수정 충족·finally 정리·낮은 API 실행 미검증을 확인했고 현재 스레드 미해결0이다. 봇은 빌드/기기 검사를 별도 재실행하지 않았다고 명시했다.

최신 Android checks40058707310/40058705374 두 건은 실행 중이다. CodeRabbit SUCCESS는 새 push의 자동 제외 체크이며 전체 재리뷰 완료로 세지 않는다. 작은 테스트 API 조건 보완은 개별 답변/소스 확인으로 대응했고 전체 수동 재검토를 다시 호출하지 않았다. 상태 기록만의 추가 push도 하지 않으며 다음은 최신 CI 결과 확인/사용자 병합이다.

## 최종 상태 재확인

사용자의 다음 작업 질문에 GitHub를 조회해 PR #33 MERGED, 최종2369ce0의 CI40058707310/40058705374 모두 SUCCESS, 미해결 스레드0을 확인했다. 이번 조회에서는 추가 앱 코드/테스트/기기·Git push/서명 빌드를 실행하지 않았다. 로컬 브랜치는 아직 codex/bookshelf-library-redesign이며 사용자 파일·상태 기록을 보존했다. 다음 실행 작업은 안전한 main 동기화와 개인용 정식 APK 업데이트이고 후속 기능은 사용 피드백에서 정한다.
