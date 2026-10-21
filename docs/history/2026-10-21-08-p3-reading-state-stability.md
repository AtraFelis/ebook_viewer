# 2026-10-21 — P3 읽기 상태·수명주기 첫 안정화

## 범위와 기준

사용자의 다음 작업 승인으로 [첫 묶음 계획](../plan/p3-reading-stability.md)을 작성했다. 현재 체크아웃 `C:\Users\cotjs\workspace\ebook_viewer`에서 `codex/p3-reading-stability`를 만들었다. PR #24의 최종 CI39605955671 성공·CodeRabbit 수정 확인/스레드 해결·사용자 병합 `eb923b090c2e81f1916fb94559694cec2ebdafeb`를 확인했다. fetch 뒤 HEAD/main 트리 `5f37444d0e7bc8514b1903828e7074360bc2a77e` 일치와 조상 관계를 확인하고 `git merge --ff-only origin/main`으로 동기화했다. 작업 중 변경과 사용자 `docs/design.md`의 별도86줄 변경을 보존했다. 사용자 디자인은 커밋에서 제외한다.

이번 묶음은 읽기 위치와 설정 저장의 경합·문서 전환·복귀/캐시·자원 해제다. 아이콘의 최종안은 아직 없어 디자인 탐색을 선택으로 간주하지 않는다. 긴 세션/실제 OOM·시스템 메모리 압박·다른 API/기기·보류 기능·출시는 후속이다.

## 재현 — 앱을 고치기 전

MainActivity는 pendingLayoutWrites가 있으면 saveReading 전체를 건너뛰었다. 설정 저장 중 계속 읽다가 문서를 바꾸거나 나가면 설정 제출 시의 이전 위치만 남을 가능성이 있어 추정부터 고치지 않고 실제 Activity 검사를 추가했다.

```powershell
. ./scripts/android-env.ps1
./gradlew.bat --dependency-verification strict :app:assembleDebugAndroidTest :app:lintDebug ktlintCheck
```

새 검사 줄바꿈을 수동 보정한 뒤 계측 빌드/Lint/ktlint **5초 통과**. 앱 APK는 기존 `83d11fc42ee967dbf6ebcc31afdecdbf843b2bc84e341ab2750bdc60f8bd8d2b` 그대로 두고 계측만 데이터 보존 설치했다. 승인/잠금 해제된 API33 한 대에서 합성100페이지 문서와 테스트 전용 라이브러리 큐 장벽으로 저장을 잠시 지연했다. 개인 문서/URI/본문/기기 식별자나 실제 저장소 권한은 수집/변경하지 않았다.

stateBoundary **3개/실패3**: 문서 교체, 목록 복귀, Activity 종료 모두 최신 페이지 **인덱스60 대신8**이 남았다(표시 번호61 대신9). 잠금 저장 자체는 성공했지만 위치가 누락됐다. 원래 결과는 ignored `local-artifacts/p3-state-boundary-original-failures.txt`에 보존했다. 합성 IO 지연 재현이지 사용자 기기에서 같은 일이 발생했다는 관측/실제 저장장치 고장이나 OOM은 아니다.

## 최소 수정과 PC 검증

설정 저장 중에도 최신 위치만 같은 IO 큐에 넣는다. 실제 작업 실행 때 저장소의 현재 기록에 위치만 병합하므로 먼저 큐에 있던 설정 저장의 성공/실패 복구 결과를 이전 UI 값으로 덮지 않는다. 문서 교체/목록/종료 전에 큐에 넣어 다음 문서 읽기보다 먼저 반영하며, 제거된 기록은 재생성하지 않는다. 파일 형식v3/의존성/렌더러/캐시/스택은 그대로다.

```powershell
. ./scripts/android-env.ps1
./gradlew.bat --dependency-verification strict :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck
```

**BUILD SUCCESSFUL12초**, 단위53개/실패·오류·건너뜀0, Lint 오류0/경고0/정보6, ktlint 통과. ktlintFormat/새 baseline/검사 완화 없음. 새 네 번째 계측은 테스트 전용 AtomicFile의 코덱 쓰기 실패 복구 뒤 양 잠금 값/크기·간격을 보존한 위치 저장과 제거된 기록의 재생성 방지를 검증한다. 검사 준비를 통과로 보고하지 않는다. 새 전체 기대91개는 전체 실행 결과가 아니다.

## 기기 검증과 미결

수정 앱을 데이터 보존 설치해 stateBoundary **4개/실패0**을 확인했다. 원래 실패3개는 같은 기대값으로 재통과했고 새 위치로 실제 기록을 다시 읽고 문서 재열기까지 검증했다. 네 번째 검사는 테스트 전용 AtomicFile에서70,000자 합성 제목의 코덱 실패로 rollback을 유발해 양 잠금 값·크기/간격을 보존한 위치 저장과 제거된 기록을 다시 만들지 않는 것을 확인했다. 이는 실제 저장장치 고장이나 사용자 저장소에 오류를 만든 검사가 아니다.

같은 앱에서 lifecycle **4개/실패0**, navigation **4개/실패0**, panLock **5개/실패0**, guidance **9개/실패0**도 통과해 관련 **총26개/실패0**이다. 확대/페이지 이동·북마크/독립 복원, 두 손가락/잠금 전환, 저장 재시도/재생성, 캐시64MiB 상한·정지 중 비움/복귀·취소 타일 무시·문서 닫기의 descriptor 해제를 확인했다. 메모리 알림 주입은 실제 시스템 메모리 압박/OOM으로 보고하지 않는다. 전체91개/다른 API/긴 세션/새 성능 실험은 미실행이다.

최종 app SHA-256 `9da3aa5e6a96472ec2e26c6aca7536a60402913022ba676b0fa073e93f8826f8`, 계측 `22f80ae62f2ad61387fed10f75da65c5e9db2a7da52001ba5fadc5f2d519e674`. 설치본 해시 일치·원래 font_scale1.1·앱/계측 force-stop·ADB kill-server·프로세스0을 확인했다. UI가 그대로인 합성 경합 수정이라 사용자에게 실제 오류를 만들거나 같은 외관 수용을 반복하도록 요구하지 않았다. 아이콘은 사용자 답변 **“아직 선택하지 않았어요”**에 따라 별도 후속으로 둔다. PR/CI/실제 리뷰·사용자 병합은 게시 후 별도로 확인한다.
