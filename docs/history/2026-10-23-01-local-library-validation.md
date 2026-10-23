# 2026-10-23 — 클라우드 PR #32 로컬 인계·컨벤션·기기 검증

## 가져오기와 사용자 변경 보존

사용자가 `feat/pdf-inversion-and-bookshelves`를 현재 Windows 체크아웃으로 가져와 연결 기기에서 검사하고 기존 컨벤션 차이도 수정하도록 요청했다. origin fetch 후 PR #32 OPEN/ready, head7f4426a132ecaaff9e4d5171d32ee5ed63a4cbdb·원격 CI SUCCESS를 확인했다. 새 worktree는 만들지 않았다.

기존 로컬은 codex/reader-background-orientation-crop18516ba와 수정5파일이었다. 사용자 디자인128줄/SHA25628ef0cdc82d5c8fddb5bb6f1a9693e6b09299892224983522e5ae0fa9e759681, gradle.properties의0.1.1/code3/SHA25624ec8ab0a0d168d101cc7152a98bb3cd817c7d807539586f3846f1ef3ffb38da를 보존했다. 상태 문서3개를 포함한 원래5파일은 stash20fbf558655599c0e7af93c1855c30789dafdb91와 무시 경로 local-artifacts/cloud-handoff-2026-10-23/에 중복 보존했다. 브랜치 전환 뒤 `git pull --ff-only origin feat/pdf-inversion-and-bookshelves`는 Already up to date였으며 사용자 디자인/버전 파일을 바이트 그대로 복원·해시 재확인했다. 사용자 변경은 이번 커밋에 포함하지 않는다. 최신 클라우드 SESSION/CHECKLIST를 사용하고 이전 로컬 상태 문서를 통째로 덮어쓰지 않았다.

## 컨벤션 정리

- 책장 선택·이동·삭제 확인은 native Builder 전용 창 대신 기존 readerFormDialog를 사용한다. 이름 입력에는 ReaderStyle.styleInput을 적용했다. 기존 취소/실패·요청 세대/대화상자 소유권 검사는 유지한다.
- MainActivity의 기존 목록 어댑터를 Context.readerListAdapter로 추출해 방향/북마크와 책장 목록이 같은 테마·64dp 최소 높이·큰 글꼴 여유를 공유한다. 저장/렌더링 계층은 변경하지 않는다.
- 한 줄로 충분한 `uris.size == 1`, `shelf == null` 조건을 정리했다. ktlintFormat은 실행하지 않았다.
- 색반전 설정의 Activity 재생성 복원, 실제 공통 책장창의 빈 이름 오류·추가·이름 변경·이동·삭제/원본 보존 검사2개를 보완했다. 기존 클라우드 계측3개를 포함해 LibraryFeatures5개, 기본 suite 기대96개다. 전체96개를 실행했다는 뜻이 아니다.

## PC 검증과 중간 실패

먼저 `. ./scripts/android-env.ps1`을 실행했다. Wrapper8.9/기존 JDK17·SDK·strict verification을 유지하고 개인 키/서명/정식판은 사용하지 않았다. 새 런타임 의존성·잠금 파일·검증 체크섬 변경은 없다.

```
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:lintRelease ktlintCheck --dependency-verification strict --console=plain
```

첫 빌드는 기존 top-level 함수까지 release 증분 컴파일 참조 오류로26초 실패했다. 캐시/작업 파일을 삭제하지 않고 `'-Pkotlin.incremental=false'`로 전체 컴파일했을 때 참조 오류는 재발하지 않았다. 증분 상태 영향으로 추정하며 원인을 확정하지 않는다. 따옴표 없는 첫 옵션은 PowerShell이 `.incremental=false`를 task로 나눠1초 실패했으므로 따옴표를 사용했다. 전체 컴파일 확인 도중 목록 어댑터 추출이 시작돼 순수 원본 baseline 성공으로 보고하지 않는다.

중간 새 공유 어댑터/계측 코드의 줄바꿈 ktlint 오류는 수동 수정했다. 최종 위 명령은17초/BUILD SUCCESSFUL, 해당 명령에서 단위69개 실제 실행/실패·오류·건너뜀0, debug/release Lint 오류·경고0, ktlint 통과다.

## 기기 검증

승인된 연결1/API33/잠금 해제/font_scale1.1을 확인했다. 별도 개발용.p0 앱과 계측만 업데이트하고 합성 파일·임시 별도 catalog만 검사한다. 개인 PDF/키/정식판 패키지·데이터는 수집·설치·삭제하지 않는다.

첫 색반전 검사는 RGB/외부 배경 확인 뒤 Bundle의 IntArray를 assertEquals로 객체 비교해 실패했다. `pageCrop`는 assertArrayEquals로 전체 원소를 비교하고 Bundle 키 집합도 동일해야 하도록 고쳤다. 비교 항목/허용 오차를 줄이지 않았다. 수정 계측 strict 빌드/ktlint4초 통과 후 같은 개발 앱을 재사용해 계측만 재설치했다.

최종 선택 묶음은 **LibraryFeatures5개·background6개·큰 글꼴1개(기존6조합), 총12개/실패0·93.9초**다. 색반전의 페이지 RGB/외부 배경·줌/캐시/요청 불변, 설정 저장/Activity 재생성 복원, ClipData 중복/일괄 저장 재읽기·원본 보존, 관리 선택/복원, 공통 창의 추가/빈 이름 오류/이름 변경/문서 이동/책장 삭제/등록 삭제를 확인했다. 큰 글꼴은 기존 홈/읽기 도구/설정의100/150/200%×가로/세로6조합이다. 책장 전용 모든 창을 이6조합 전부에서 별도로 조작했다는 뜻은 아니다.

마지막 앱/계측 force-stop·원래font_scale1.1·ADB 종료를 helper가 확인했다. 개발용 app-debug.apk SHA256c0324e3a1a4fec05acf7070af251cafa4c6f5fbcb6f7171eddb420b01d05703a, 최종 계측9e8543db0fb383945aa908d6c77aab4314e837df5f0335d13477818996a8d8f6다. 전체96개/장시간·성능/다른 API·기기의 검증으로 확대하지 않는다. 시스템 SAF 선택 UI에서 실제 여러 파일을 선택하는 사용자 수용은 ClipData 합성 검사와 별도다. 개발용 앱에서 실제 다중 추가·책장/순서·색반전 재시작 유지의 사용자 확인을 요청했으며 아직 응답 대기다.

로그는 local-artifacts/pr32-local-baseline-build.txt, baseline-fresh[-2].txt, conventions-build[-2].txt, test-build-3.txt, pr32-local-device[-2].txt와 개별 pr32-local-*.txt에 보존한다. 준비용 Python 내용을 PowerShell로 실행한 파서 오류1회는 실제 검사 시작 전이며 Python 실행으로 바로잡았다.

## 원격과 인수인계

### 사용자 기능 확인·UI 후속 피드백

실제 사용 확인 요청에 사용자가 “일단 전체적으로 기능은 돌아가는 것 같은데, 전체적인 UI가 너무 난잡해. 일단 개선사항은 추후에 정리해서 보내줄게.”라고 답했다. 전반적인 기능 동작 확인을 받았지만 개별 기능의 모든 세부 동작/최종 UI 인수를 완료한 것으로 확대하지 않는다. UI 구조 개선은 구체적인 후속 요구를 받은 뒤 우선순위와 화면 구성안을 정리한다. 이 응답에서는 기록만 갱신했고 앱 코드/설치/빌드/기기 검사·원격 게시/리뷰 요청은 하지 않았다. 아래 사용자 수용 응답 대기는 이 피드백 전 조회 상태다.

로컬 수정69ddd6e92cd487a7eb1b1fd5bb225778d226f2d4를 같은 브랜치에 일반 push했다. 직후 첫 PR 조회는7f4426a 이전 값을 반환해 완료로 단정하지 않았고, 다음 조회에서 PR/head와 ls-remote가69ddd6e로 일치함을 확인했다. PR 설명은 로컬 컨벤션/검사12개·단위69개·실제 한계로 갱신했다. 사용자 디자인/gradle.properties는 커밋 제외·해시 동일이며 원래5파일 stash도 유지한다.

검증한 최신 head에 수동6357203397(`@coderabbitai review`)을 한 번 요청해 봇6357208149 Review triggered를 확인했다. 아직 완료 리뷰로 세지 않는다. 새 CI두 건 IN_PROGRESS이며 이전 head SUCCESS와 구분한다. 리뷰 중 이 게시 결과만의 추가 push/중복 호출/자동 예약/병합은 하지 않는다. 실제 시스템 SAF·개인 사용 수용은 사용자 응답 대기다.

클라우드의 CodeRabbit 초기 선택 패널 지적은493cb90으로 수정·스레드 해결/봇 확인4351896400이 있다. 이번 수정은 해당 문제를 되돌리지 않는다. 같은 PR의 일반 push/실제 CI·리뷰와 사용자 수용은 실행한 결과만 후속 기록한다. 사용자 병합/정식판 서명·배포는 별도다.
