# 삭제 확인을 중앙 대화상자로 변경

2026-10-24. 사용자가 삭제 경고는 하단 시트 대신 첨부 사진처럼 화면 중앙에 보이도록 방향을 정정했다. 이전 하단 요청/검증은 history05에 보존하고 현재 결정은 이 문서와 계획을 따른다. 기존 체크아웃·사용자 디자인/버전·stash·정식 앱/키를 보존한다.

## 변경

- 책장/문서 다중 삭제 및 메인 기록 단일 삭제의 확인만 중앙에 놓는다. 카드 폭은 화면 좌우24dp 여유/최대440dp이며 내용 높이·20dp 둥근 모서리와 뒤 창 dim0.45를 사용한다. 제목·안내문을 중앙 정렬하고 상단 뒤로가기 아이콘은 이 확인창에서만 제거했다.
- 사진처럼 테두리형 취소와 붉은색 삭제 버튼을 나란히 둔다. 삭제 버튼 글자는 짧은 ‘삭제’이며 기존 구체 동작 설명은 contentDescription/제목·원본 보존 안내에 유지한다. 흰 글자의 가독성을 위해 삭제 배경#CC4848을 사용했다(계산한 대비 약4.58:1).
- 책장 이동/더보기·이름 입력은 기존 하단 시트/편집 흐름을 유지한다. 삭제 대상/취소/재생성 복원·실패 재시도·원본 PDF/다른 소속 보존 의미와 저장 형식은 바꾸지 않는다. 새 런타임 의존성은 없다.

## 검증

PowerShell `. ./scripts/android-env.ps1` 후 strict debug/계측/Lint/ktlint `--max-workers=2 --dependency-verification strict --console=plain`15초 성공. 최종 색상 보완을 포함한 빌드/관련 기기 결과는 아래에 이어 기록한다. 변경 파일에만 ktlint CLI1.5.0 --format을 명시 실행했다.

기존 삭제 계측을 `warningsUseCenteredDialogsAndCancellationAndDeletionPreserveOriginals`로 갱신해 중앙 gravity·양옆 여유·실제 중심/콘텐츠 크기·dim·취소/삭제 두 버튼과 취소·재구성/원본 보존 검사를 유지한다. 이름만 바꾸고 검사를 무력화하지 않았다. 공개 합성 경고 카드만 PNG로 확인하며 개인 화면/기록은 추출하지 않는다.

최종 색상 보완 뒤 같은 strict debug/계측 빌드·Lint 오류/경고0·ktlint11초 성공(`local-artifacts/centered-delete-final-build.log`). API33에 개발용/계측 APK만 -r 설치하고 위 메서드1개/실패0을 확인했다(`local-artifacts/centered-delete-device.log`). 중앙 위치·두 버튼·취소/창 복원·실제 합성 삭제/원본3개와 다른 소속 보존을 검사했다. 공개 합성 View의 PNG만 시각 대조했으며 앱/계측 force-stop과 adb kill-server를 마쳤다.

사용자에게 직접 경고를 열었다 취소하도록 요청했고 ‘좋아’ 응답으로 최종 중앙 확인창을 수용했다. 후속 게시 준비 중 `:app:assembleRelease :app:lintRelease -PreleaseSigning=false --max-workers=2 --dependency-verification strict --console=plain`도11초 성공했다(`local-artifacts/bookshelf-final-release.log`). 개인용 키를 사용하지 않은 unsigned 빌드이며 정식 앱은 설치하지 않았다.

단위83개/기본96개 전체·읽기 성능/장시간/OOM·다른 API 검사를 이번 표시 수정에서 반복하지 않았다. commit/push/PR/원격 CI·CodeRabbit은 이후 게시 이력에서 실제 상태를 구분한다. 사용자 디자인/버전 해시와 기존 stash도 동일 보존했다.
