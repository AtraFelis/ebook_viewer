# PR #33 CodeRabbit 지적 대조·수정

2026-10-24. head3536da9의 실제 완료 리뷰5632427685(4개 actionable)를 읽고 현재 코드/ADR0020과 대조했다. 이전 CI39997863960/39997828449는 SUCCESS이며 수정 이후 CI와 구분한다. 기존 체크아웃·사용자 디자인/버전·stash·정식 앱/키를 보존한다.

## 판단과 변경

- 4362673821:32개 요청/8MiB 캐시의 불일치는 타당하다. 최대384×512 ARGB10장이 들어가도록 MAX_VISIBLE_COVERS=10으로 제한했다. 초과 표지는 기본 그림을 유지한다.
- 4362673838: 셀/스크롤마다 catalog.visible이 전체 기록 맵과 필터를 반복하는 지적은 타당하다. 문서 저장 순서와 다중 소속을 유지하는 documentsByShelf 인덱스를 스냅샷별 한 번 만들고 홈/시트의 표지·개수에 재사용한다. 빈 실제 책장/미분류 의미는 유지한다.
- 4362673848: 홈/시트별 로더가 독립 캐시·큐·작업을 소유해 동시1개/8MiB 정책이 중복되는 지적을 채택했다. Application 소유 BookCoverStore 하나와 화면별 BookCoverLoader 구독으로 분리했다. 앞 시트 우선·합산10개/중복 제거·단일 IO/렌더, 화면 취소/종료 시 자기 구독만 제거, 마지막 구독 종료 시 메모리 해제다. 캐시 초기화도 같은 IO 큐와 clearing 상태로 직렬화한다. 요청별 격리 서비스/FD·취소/시간/메모리 감시는 유지한다. 신규 의존성은 없다.
- 4362673862: 책장 편집/이동에서 문서용 빈 문구가 나오는 지적은 타당하다. items의 빈 안내를 매개변수로 받고 편집/이동은 문맥별 책장 리소스, 등록 문서 추가는 기존 문구를 사용한다.

## 검증

변경한 Kotlin 파일만 ktlint CLI1.5.0 --format을 명시 실행했다. `. ./scripts/android-env.ps1` 후 strict debug/계측 빌드·단위·Lint/ktlint `--max-workers=2 --dependency-verification strict --console=plain`1분2초 성공. 단위84개/실패·오류·건너뜀0이며 인덱스의 다중 소속/순서·이동/삭제 뒤 갱신을 추가 확인했다. Android Lint 오류/경고는0이다. 기존 계측의 deprecated API 컴파일 권고는 보존한다.

공유 구독의 동일 store/합산10개·시트 우선/홈 재그리기·한 구독 종료 후 나머지 유지, 기존 실제 격리 표지·잘못된 PDF 대체, 빈 편집/이동 안내의 관련 기기3개를 준비했다. 실제 실행/unsigned release 결과·게시 head는 아래에서 완료 뒤 구분한다. 이번 변경은 기본96개 전체/장시간/성능/OOM/다른 API 검증이 아니다.

사용자 연결 후 승인된 API33에 개발용/계측 APK만 -r 설치했다. 위 관련3개를 각각 메서드 선택 실행해 각 P0 tests=1, failures=0을 확인했다(`local-artifacts/pr33-sharedCoverSubscriptionsPrioritizeSheetsAndBoundTheCombinedVisibleSet.log`, `pr33-isolatedCoverProducesBoundedPixelsAndInvalidPdfFallsBack.log`, `pr33-emptyShelfEditorAndMoveUseShelfSpecificMessages.log`). 앱/계측 force-stop·ADB 종료를 마쳤다. 사용자 개인 화면/문서/기록 추출 없이 공개 합성 데이터만 사용했다.

`:app:assembleRelease :app:lintRelease -PreleaseSigning=false`와 동일 strict 옵션도22초 성공했다(`local-artifacts/pr33-review-fixes-release.log`), release Lint0/0이다. 실제 개인용 서명/정식 앱 설치는 하지 않았다. 사용자 디자인/버전 두 SHA-256 및 기존 stash20fbf558은 동일 보존했다. 새 APK의 기본96개 전체/장시간/성능/OOM/다른 API 검사는 미실행이다.
