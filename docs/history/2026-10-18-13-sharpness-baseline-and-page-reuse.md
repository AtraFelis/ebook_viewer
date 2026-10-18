# P2 선명도 — 독립 브랜치/기준선과 페이지 재사용

2026-10-18 (Asia/Seoul). 작업 경로 `C:\Users\cotjs\workspace\ebook_viewer`.

## 분리와 범위

fetch 후 `origin/main` 5d74c68에서 `codex/p2-sharpness-recovery`를 만들었다. 사용자 Figma 문서 블록을 ignored `local-artifacts/sharpness-handoff/`에 백업하고 해당 두 파일만 stash한 뒤 새 브랜치에 재적용했다. 사용자 블록 일치와 두 history 파일 SHA 일치를 확인했고 복구용 stash는 보존했다. 사용자 변경은 이번 커밋에 포함하지 않는다.

검색 코드는 가져오지 않았다. 검색 보류/목차·비밀번호 읽기 필수 제외라는 최신 계획만 반영하며 이전 검색 진단 근거는 기존 PR의 고정 커밋 링크로 연결했다. 기존 PR #12의 실제 리뷰 3건은 기존 브랜치 f94ffc4에서 정리했고 재검토 수동 요청은 한도로 실제 미실행이었다. 새로운 검색/기기 진단을 재개하지 않는다.

## 구현과 기준

계측 기준 소스 ede4e83은 P1 렌더 스케줄을 유지하고 opt-in onDraw 면적/큐·native·전달 시간과 합성 fixture/runner를 추가한다. 미리보기/다른 배율 fallback을 선명한 타일로 세지 않는다. 95%는 타일 개수가 아니라 페이지/화면으로 잘린 면적 기준이다. 기본 앱 읽기는 observer null이며 메모리/문서 로그를 만들지 않는다. [ADR 0012](../adr/0012-bounded-page-reuse-and-sharpness.md), [재현/한계](../testing/sharpness-probe.md).

다음 변경은 같은 worker 배치(최대 4개) 안의 연속된 페이지를 재사용한다. 입력/우선순위·yield·세대/epoch/문서 취소는 유지하며 페이지 변경/배치 마지막/예외·취소 시 자원을 닫는다. 동시에 열린 페이지 최대 1개이며 전체 고해상도 페이지 선로딩이나 cache 확대가 아니다. native render 중단/RAM hard cap를 보장하지 않는다.

최적화 전에 기존 제안 목표를 이번 보통 스크롤 비교에 적용한다: p95 500ms 이하 또는 기준 대비 30% 감소, 기존 500ms 이하인 조건은 500ms 이하 유지, timeout 증가 금지. 자료 SHA·화면/줌/주사율과 profile 조건을 대조한다. 빠른 플링/높은 배율·원본 구조/압축별 비교·프레임/최종 통합 수용은 이 실험만으로 통과 처리하지 않는다.

## 현재 검증

strict debug 앱/계측 빌드·단위 29개(기존 22 + 면적 3 + 페이지 소유권 4)·Lint 오류/경고 0·ktlint 통과. 첫 스타일/DrawAllocation 오류는 직접 수정했으며 ktlintFormat은 실행하지 않았다. draw snapshot은 재사용한다. AGENTS의 ‘버전 권고는 정보’ 정책에 맞춰 Kotlin 최신판 권고 GradleDependency를 informational로 표시하며 strict/체크섬·다른 Lint 검사는 유지했다. 버전/의존성 변경 없음.

비디버그 prototype 1회는 3개 합성 자료/각 방향 30개를 끝냈으나 이는 반복 비교 합격 근거가 아니다. 이후 같은 bytes를 보존하고 조건 기록을 보완한 기준 APK로 3회 반복 측정을 진행한다. 개인 PDF를 열거나 기록/설정 스키마·앱 데이터를 지우지 않았다. 결과/수용/원격 상태는 아래 실제 완료 결과로 갱신한다.

## 보통 스크롤 전후 — 3회/조건별 90표본

기준/개선 각각 벡터·그레이·RGB × 첫 방향/역방향 × 30표본 × 3회 = **540개씩** 수집했다. API 33/SM-N981N, 가로 2400×1080, 60.000004Hz이며 문서 SHA/줌/화면 조건과 계측 APK 해시가 동일하다. profile debuggable=false. 충전 중이고 외부 표본의 thermal status는 전후 모두 2였다(기준 중 배터리 38.4~39.6℃, 개선 중 38.4℃). 주파수/스케줄을 완전 고정한 실험은 아니다.

- 벡터 첫 방향 p95 **1713.86→649.23ms**, 역방향 **1714.13→625.94ms**.
- 그레이 첫 방향 **132.16→63.61ms**, 역방향 **164.91→64.31ms**.
- RGB 첫 방향 **464.21→165.79ms**, 역방향 **513.99→214.53ms**.

전후 모든 조건에서 2초 timeout/미완료 0개. 사전에 정한 보통 스크롤 목표(500ms 이하 또는 30% 감소, 이미 빠른 조건은 500ms 이하 유지)를 모두 충족한다. 벡터의 절대 지연은 여전히 500ms를 넘으며 ‘항상 무지연’이나 선명도/P2 전체 완료가 아니다.

예를 들어 벡터의 전달 타일 queue p95는 기준 1318~1350ms에서 개선 551~558ms, RGB는 362~369ms에서 147~152ms로 줄었다. open/render/close 전체의 반복 비용을 줄인 결과이며 원래 사용자 PDF의 JPEG decode나 특정 native 함수가 단독 원인이라고 확정하지 않는다. 타일별 native p95 하나만으로 전체 처리량을 설명하지 않는다.

캐시 최대 관측 67,106,304 bytes로 64MiB 이하를 유지했다. run 종료 PSS 최대는 기준 290,452KiB/개선 275,807KiB이나 실제 순간 peak가 아니다. 그레이 최대는 245,589→262,762KiB, RGB 최대는 268,824→275,807KiB로 일부 조건이 증가했다. 즉 모든 메모리 값이 감소했다고 보고하지 않는다. 추가 고해상도/복잡한 페이지의 native working set/장시간 검증은 남아 있다.

기준 app APK SHA-256 `06423be61af1ea5f52fccd2831fa2921f4ce8a45a5690230a5ea59cc03e0665c`, 개선 `f7a03cfc702396b4d5f23b6cf158fb995771c2c94f331b8656a7963afac80019`. 공통 선명도 계측 APK `00257bba04a6a1cc577572fb3d5458506c3f41d30f49f580a9043ebbd28d3bcf`. 집계/샘플/APK 보관본은 ignored `local-artifacts/sharpness/`에 남겼다. 비교기는 표본/조건이 다르거나 목표 미달이면 거부하며 censored 표본도 유지한다.

## 프레임 검사와 설치 timeout 구분

프레임 비교를 처음 시도할 때 APK 업데이트 단계의 60초 ADB timeout이 발생해 계측은 시작하지 않았다. 이후 shell 응답/승인 1대·여유 메모리/앱 프로세스 없음과 설치된 app/test의 정확한 SHA를 확인했다. 실제 설치는 완료돼 있었지만 timeout 원인/정확한 완료 시각은 미확정이다. 이를 앱 native 충돌이나 기기 회복 완료로 바꾸지 않는다. 새 runner는 timeout 후 실제 hash를 검증하고 일치하면 중복 설치 없이 진행한다.

프레임 계측 APK는 합성 기록 보존 보완이 있는 `bf1e39f84f59856d7ad46ad848eb73ef1aaa607ad9c64389204fa6848db440b9`이며 보통 스크롤 계측 APK와 구분한다. 기준 app의 30초×3회 p95 6.177/6.063/6.015ms, p99 7.813/7.320/7.216ms를 확인했다. 같은 frame fixture/계측으로 개선 APK 결과와 기존 41개 기기 회귀는 후속 실행 결과로 기록한다.

개선 app의 30초×3회도 통과했다. p95 6.582/6.680/6.337ms, p99 8.395/7.910/7.568ms로 약간 증가했지만 기존 16.7/33.3ms 목표 안이다. 보고 누락 0이며 실제 frame fixture SHA는 전후 `ec89d9379bf880bd8e5023d63223ef23e41a9e896c3bd65d08f2eb67e6bf0963`으로 같았다. 양쪽 시작 thermal status는 1이었다. 보통 스크롤 측정 중 status 2와 다른 workload/구간이므로 둘을 같은 조건 하나로 합치지 않는다. frame cache 최고도 64MiB 이하, 측정 구간 FD 전후 증가 없음. 자체 합성 URI의 읽기 기록은 repository 직렬 IO로 이전 값만 복원했고 다른 문서를 덮어쓰지 않았다.

## 기존 기능·화질·설치

`./scripts/device-smoke.ps1`: strict debug app/계측 빌드 후 API 33 기본 **41개/실패 0**, 앱 실행 확인. 이어서 `suite=sharpnessPixels`의 별도 **1개/실패 0**을 같은 app에서 실행했다. 벡터/그레이/RGB 각각 미리보기→3/8/32 배율의 native 타일을 매번 새 페이지를 여는 reference와 pixel 배열로 비교해 일치했다. 픽셀 비교/자료 IO는 UI 스레드 밖에서 수행하고 page owner/검사 executor 종료를 확인했다. 이 대조는 조건 밖 문서의 모든 native cache 버그가 없다는 보장이 아니다.

debug app SHA-256 `c945bfe123e631548c61e4a1eefb2b93f65bea792420a3c6a492d8ca6edf106d`. 후속 화질 계측 APK는 `5aa2990059a1da7d95cfa3ce8104eea6598b0ab37964938df411016173bb201a`이며 앞선 기본 41개/프레임/선명도 계측 APK와 구분한다. 하나의 42개 실행으로 합산하지 않는다. 추가 계측 코드 뒤에도 strict 빌드/Lint 오류·경고 0/ktlint 통과를 확인했고 앱 runtime은 변경하지 않았다. 기본 suite는 여전히 41개이며 화질 대조는 별도 opt-in이다.

검사 후 개선 debug 앱의 목록 화면만 실행했다. 개인 PDF는 자동으로 열지 않았고 사용자에게 직접 스크롤 체감/확대 화질을 확인하도록 요청했다. 사용자는 **“더 빨라졌고 화질도 정상”**이라고 확인했다. 파일/본문은 받지 않았으며 자기보고와 합성 자동 검증을 구분한다. 별도 프로세스 복원 seed/verify·10분 최종 통합·다른 기기·빠른 이동/고배율 선명도 분포와 완전 cold/warm·고유/복잡한 이미지 자료의 native 작업 집합 검증은 아직 미실행이다.

ignored 증거: `sharpness-build.log`, `sharpness-profile-build.log`, `sharpness-page-reuse-build.log`, `sharpness-final-checks.log`, `sharpness-device-smoke.log`, `sharpness-pixels-device.log`, `sharpness/{baseline,page-reuse}-{profile,frames}.json`과 해당 합성 계측 결과/APK 보관본. 링크/공백·사용자 문서 보존·검색 모듈/의존성/도구 버전/CI 불변을 확인한다. PR은 최신 main 기준의 별도 변경으로 등록하고 사용자만 병합한다.

## PR·원격 확인

`git commit`의 구현 head는 892c44b이며 일반 `git push -u origin codex/p2-sharpness-recovery` 후 [PR #13](https://github.com/AtraFelis/ebook_viewer/pull/13)을 생성/이 채팅에 연결했다. `gh pr view 13`에서 [push CI 39136769982](https://github.com/AtraFelis/ebook_viewer/actions/runs/39136769982)와 [PR CI 39136773066](https://github.com/AtraFelis/ebook_viewer/actions/runs/39136773066)의 성공을 확인했다. 초기 CodeRabbit 성공 표시는 별 수 조건에 의한 자동 리뷰 건너뜀이었고 실제 리뷰가 아니었다.

`gh pr comment 13 --body '@coderabbitai review'`를 한 번 실행해 [수동 요청 6251196279](https://github.com/AtraFelis/ebook_viewer/pull/13#issuecomment-6251196279)을 등록했고 [접수 응답 6251197303](https://github.com/AtraFelis/ebook_viewer/pull/13#issuecomment-6251197303)의 `Review triggered`와 PENDING을 확인했다. 중복 요청/Autofix/자동 병합은 하지 않았다. 실제 리뷰 결과/지적은 완료 후 코드·계약에 대조하며 기록을 갱신한다. 사용자 Figma 두 이력의 SHA-256과 SESSION/CHECKLIST 블록은 그대로 보존하며 PR에서 제외했다.

후속 실제 리뷰 PRR_kwDOVLKYJM8AAAABS5wJ5A는 29개 파일을 검토하고 2건을 제출했다. [계측 복원 assertion이 원래 오류와 보고를 가리는 문제](https://github.com/AtraFelis/ebook_viewer/pull/13#discussion_r4307465386)는 현재 finally 흐름에서 재현 가능한 위험이라 채택했다. 계측 전용 `finishProbe`가 복원/파일 기록/상태 보고를 각각 시도하고 원래 오류에는 secondary 오류를 suppressed로 보존한다. 원래 오류가 없으면 모든 단계를 시도한 뒤 첫 실패를 그대로 던진다. [완료된 기준선을 다음 작업으로 표시한 문제](https://github.com/AtraFelis/ebook_viewer/pull/13#discussion_r4307465391)도 타당해 roadmap/문서 목차/SESSION을 갱신했다. 첫 보통 스크롤 개선과 남은 빠른 플링·고배율·복잡한 자료/최종 통합을 구분한다. 미채택 지적은 없다.

후속 검증 명령은 `./gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain`이다. 첫 수동 코드의 줄바꿈 스타일 실패를 직접 고친 뒤 재실행 통과했으며 ktlintFormat은 사용하지 않았다. `scripts/probes/ProbeCompletionCheck.java`를 JDK 17 javac/java로 실제 컴파일된 계측 helper/Kotlin stdlib 2.1.20에 연결해 성공 순서, 복원 실패 뒤 보고, 원래 오류+복원/저장 오류 동시 보존, 저장 실패 뒤 상태 전송의 **PC 4건 통과**를 확인했다. [재현](../testing/sharpness-probe.md). 후속 수정은 계측/문서뿐이며 앱 runtime/기존 실측 APK는 불변이다. 기기 재측정/실제 복원 timeout을 주입한 결과로 보고하지 않는다.

후속 수정 커밋 43c7c76을 같은 브랜치에 일반 push했다. GitHub reviewThreads로 두 지적이 변경 전 코드에 대한 outdated 상태임을 확인하고 해당 두 스레드를 resolve했으며 실제 isResolved=true를 확인했다. [새 head 수동 요청 6251306446](https://github.com/AtraFelis/ebook_viewer/pull/13#issuecomment-6251306446)을 한 번 등록했다. [응답 6251309603](https://github.com/AtraFelis/ebook_viewer/pull/13#issuecomment-6251309603)은 처음 Review triggered였다가 최종 **Review rate limited / Action not completed**로 바뀌었다. 따라서 수정 head 재리뷰는 실제 미실행이다. 현재 성공 상태만으로 재검토 완료라고 보고하거나 재요청을 반복하지 않는다. 수정 head의 push CI 39137832581/PR CI 39137835631과 문서만 추가한 후속 head를 구분한다.

`gh run watch 39137832581 --interval 15 --exit-status`가 exit 0으로 종료했고 [push CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39137832581)가 성공했다. `gh run view 39137835631 --json status,conclusion`도 completed/success로 [PR CI](https://github.com/AtraFelis/ebook_viewer/actions/runs/39137835631)를 확인했다. 실제 검사 head는 43c7c76이다. 이후 문서만 기록하며 앱/계측 소스·검증된 설치본은 그대로다. 재리뷰 한도/사용자 병합/남은 P2 작업은 통과로 바꾸지 않는다.
