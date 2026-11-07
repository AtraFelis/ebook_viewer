# PR #37 잔여 파일 정리 리뷰 보완

2026-11-07, Asia/Seoul. 현재 체크아웃/브랜치 codex/managed-pdf-storage, 기준 head9b029e8. 사용자 디자인·로컬 버전과 미게시 상태 기록5개를 ignored local-artifacts/managed-storage-review-20261107에 백업하고 원래 파일 해시/기존 stash7개를 보존했다. 새 의존성·원본 PDF/키·정식판 변경은 없다.

## 리뷰 판단과 수정

실제 완료 리뷰5810562680(9b029e8)의 인라인4500486556과 본문 nitpick을 코드와 대조해 모두 채택했다. 이전 무료 한도 거부 기록과 이후 실제 완료 리뷰를 구분한다.

- recover에서 고아 파일 삭제 실패를 던지면 DocumentLibrary 생성이 매번 실패해 서재 전체 접근이 막힌다. 자동 잔여물 정리는 삭제 실패/보안 거부를 남겨 다음 시작 때 재시도하고 다른 항목 회수를 계속하도록 바꿨다.
- copy의 finally에서 정리 실패를 던지면 기존 IO/취소 예외가 바뀐다. 동일한 최선 노력 정리를 사용해 기존 예외를 보존한다. 남은 pending 파일은 이후 recover 대상이다.
- 사용자가 요청한 명시적 문서 삭제인 remove의 실패 보고는 그대로 유지한다. 카탈로그 쓰기/원본 보호/UUID 경계도 변경하지 않았다. 일괄 함수 주석 비율 권고는 기존 계약의 이유·제약 주석 원칙에 따라 채택하지 않았다.

## 실제 검증

android-env.ps1 이후 CI 동일 assembleDebug/assembleDebugAndroidTest/testDebugUnitTest/lintDebug/assembleRelease/lintRelease/ktlintCheck에 --dependency-verification strict --console=plain --continue를 사용했다. checks.log는 57초 성공(124작업:32실행/92재사용)이다. 단위95개(실패/오류/건너뜀0) 실제 실행, 두 Lint 오류/경고0·ktlint와 APK ID/로컬0.1.2/code4·debuggable 구분 검사 성공이다. unsigned release이며 키를 사용하지 않았다.

추가 단위2개는 Windows 실제 임시 파일에서 삭제가 실패하도록 구성했다. 출력 닫힘 뒤 pending 경로를 비어 있지 않은 디렉터리로 바꾸고 취소 대용 InterruptedException을 던져 같은 예외 객체가 보존되는지 확인한다. recover가 실패 항목을 남겨도 다른 고아 파일을 지우고 참조 중인 복사본을 보존하며, 방해 파일을 제거한 다음 회수에 성공한다. 별도 검사는 명시적 remove 실패가 계속 ManagedStorageException으로 전달되는지 확인한다. 실제 Android OperationCanceledException/기기 파일 권한 상황의 새 계측 결과로 확대하지 않는다.

이 수정은 파일 정리 실패 경로이며 UI/제스처/렌더링은 변경하지 않았다. 이번에는 휴대폰 설치·기기/성능/장시간 검사를 실행하지 않았고 이전11개 결과를 새 앱의 기기 통과로 재보고하지 않는다. 로그는 위 ignored 폴더에 보존했다. 같은 PR에 검증한 변경과 상태 기록을 함께 일반 push하고 인라인에 두 지적/검증을 답변한다. 작은 두 정리 수정의 전체 수동 리뷰는 중복 호출하지 않는다. 원격 CI·봇 후속 확인과 사용자 병합은 별도로 기록한다.
