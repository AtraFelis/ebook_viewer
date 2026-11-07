# 최근 목록 삭제 분리와 일반 설정 방향

2026-11-07, Asia/Seoul. 저장소 C:\Users\cotjs\workspace\ebook_viewer. PR #37 사용자 병합4e707f9bdc1db234ff9e9ed76fed98eb98131add 확인 후 현재 체크아웃에서 codex/recent-history-removal을 만들었다. 시작 변경5개는 ignored local-artifacts/recent-history-20261107에 백업했다. docs/design.md와 gradle.properties는 사용자 변경이며 수정/게시에서 제외한다. stash7개도 보존한다.

## 최종 동작

최근 항목 길게 누르기는 전용 중앙 확인 후 최근 목록에서만 숨긴다. 문서 등록·앱 PDF 복사본·원본 앵커/줌·북마크·책장 소속은 유지한다. 일반 위치 저장은 숨김을 해제하지 않고 실제 열기에 성공하면 다시 표시한다. 책장/전체 문서와 오류 복구의 명시적 문서 삭제는 유지한다. v8은 숨긴 URI 집합을 저장하고 v1~v7을 읽으며 첫 쓰기 전 before-v8에 원본 바이트를 백업한다. 기존 before-v6/v7과 복사본 토큰은 보존한다. [ADR0023](../adr/0023-recent-history-visibility.md).

일반 설정·하위 설정은 메인과 같은 기기 방향 정책을 사용한다. 본문 저장 방향은 바꾸지 않으며 설정 종료 때 본문 방향·위치·확대로 복귀한다. Activity 생성 초반에 설정 복원 여부를 적용해 재생성 반복을 막는다. 작은 보기 설정과 빠른 방향 드롭다운은 기존 읽기 도구 정책이다. [ADR0024](../adr/0024-settings-system-orientation.md). 새 의존성이나 도구 버전 변경은 없다.

## PC 검증

먼저 `. ./scripts/android-env.ps1` 실행. 모든 Gradle 명령에 `--dependency-verification strict --console=plain`을 사용했다.

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:lintRelease ktlintCheck`: 빌드/단위/Lint 성공, 초기 ktlint는 새 검사 파일의 긴 줄로 실패. 수동 서식 보완 뒤 최종 PC 검사와 계측 빌드/ktlint 성공. ktlintFormat은 실행하지 않았다.
- 최종 단위 XML17개 합계100개, 실패0/오류0/건너뜀0. 기존95개에 최근 필터/데이터 보존·늦은 저장/재열기·재연결/삭제 관계·v8 유효성·고정 v7 자료 읽기5개 추가.
- debug/release Lint 오류0/경고0. APK ID 검사에서 dev.example.ebookviewer.p0/debuggable과 dev.example.ebookviewer/non-debuggable 확인. 사용자 로컬 버전0.1.3/code5는 보존했다. release 빌드는 unsigned이며 개인 키/정식판 설치는 하지 않았다.

로그는 ignored local-artifacts/recent-history-20261107에 보존한다. 빌드 성공을 기기 성능 검증으로 취급하지 않는다.

최종 CI 동일 한 번 실행은 final-all-checks.log에서7초 성공/124작업(5실행/119재사용)이다. 단위100개 결과는 앞선 실제 실행 XML이며 최종 실행은 재사용했다. 사용자 파일2개 SHA와 stash7개 SHA 일치, Markdown138개/상대 링크364개 누락0·공백 검사를 확인했다.

## API33 기기 검증

USB 승인·화면 켜짐/잠금 해제 상태를 확인하고 개발용 앱/계측 APK만 설치했다. 합성 PDF와 고유 테스트 기록만 사용하고 개인 PDF는 읽거나 로그에 넣지 않았다. 다음6개를 개별120초 제한으로 실행해 모두 tests=1/failures=0을 확인했다.

1. recentHistory / homeLongPressOnlyForgetsRecentsAndRegisteredDocumentsStillResume: 주 카드/작은 최근 항목 길게 누르기·취소/삭제·재생성 후 숨김, 등록/복사본·북마크/소속 유지·전체 문서에서 다시 열어 페이지3/줌2 복원·최근 목록 복귀.
2. recentHistory / hidingRecentsBacksUpVersionSevenAndFailedWritesKeepThemVisible: 고정 v7 원본 백업·쓰기 실패 시 최근/문서 보존·늦은 위치 저장/재로드에도 숨김 유지·실제 등록 삭제 시 복사본 정리/원본 보존.
3. recentHistory / readerSettingsUseTheSystemPolicyAndClosingRestoresTheReaderAnchor: 기기와 반대 본문 방향, 일반/방향 세부 설정의 기기 정책·재생성 복원·종료 후 본문 방향/앵커·줌 유지.
4. immersive / orientationSettingsPreserveAnchorAndSurviveRelaunch.
5. immersive / reverseDirectionsRotateTheDisplayAndKeepAnchorAndSavedChoice.
6. settings / orientationChoiceKeepsChildUntilExplicitCloseAndRestoresAfterRecreation.

첫1번은 마지막 최근 복귀 확인에서 실패했다. 본문을 나가면 이전 전체 문서 화면으로 돌아오는데, 검사는 바로 메인 최근 항목을 찾았다. 이전 단계의 복사본/기록 보존은 통과했다. 실패 원문을 first-device-failure.log로 보존하고 전체 문서의 실제 뒤로 아이콘을 눌러 메인에 복귀하는 단계 및 숨김 해제 직접 확인을 추가했다. 계측 APK 재빌드/설치 후 위6개가 성공했다. 실패 결과를 숨기거나 검사를 제거하지 않았다.

기존 방향 검사는 새 계약에 맞춰 세부 설정에서 방향 선택 후 상위 설정을 닫고 본문의 실제 회전을 확인하도록 갱신했다. 새3개는 opt-in recentHistory이며 기본96개에 자동 합산하지 않는다. 최종 기본96개 전체 실행·다른 API/기기·장시간/성능/실제 OOM 검사는 하지 않았다.

각 검사 finally에서 원래 본문 방향/테스트 자료를 복원·정리했다. 호스트 finally에서 앱/계측 force-stop·원래 글꼴 복원·ADB kill-server를 실행했고 종료코드0이다. 사용자 두 기능 수동 확인 응답은 ‘좋아. 괜찮은 것 같아’로 수용됐다. 원격 CI/리뷰와 사용자 병합은 별도 확인한다.
