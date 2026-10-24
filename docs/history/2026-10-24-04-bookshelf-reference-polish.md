# 참고 사진 기반 책장 편집·하단 시트 보완

2026-10-24. 사용자는 최종 수용 대신 메인 편집 테두리 제거, 하단 시트의 배경 구분/과한 아래 여백, 참고 사진에 맞춘 편집 화면과 잘못 보이는 휴지통을 요청했다. 기존 미커밋 브랜치를 이어 사용하며 새 의존성·저장 형식·원본 PDF·정식 앱은 변경하지 않는다.

## 변경과 근거

- 메인 편집의 회색 테두리를 제거했다. 기본 투명 아이콘+글자 버튼이며 기존 눌림 반응과 터치 영역은 유지한다.
- 하단 시트는 고정 비율 높이 대신 내용에 맞춰 측정하고 긴 목록만 화면의 최대78%에서 스크롤한다. 뒤 창에 DIM_BEHIND/0.45의 어두운 덮개를 적용하고 시트 표면색·위쪽 둥근 모서리로 구분한다. 픽셀 블러/추가 이미지 라이브러리를 도입하지 않았다. 아래쪽은8dp와 필요한 시스템 안전 여백만 둔다.
- 편집은 중앙 제목/왼쪽 닫기, 간격 있는 회색 두 구역 탭과 선택된 표면, 왼쪽 원형 선택/전체 선택 개수, 표지 묶음·이름·문서 수로 배치했다. 책장 생성은 편집 제목 오른쪽 +로 유지한다. 실제로 저장하지 않는 생성/수정 날짜를 꾸며 넣지 않는다.
- 삭제는 참고처럼 하단 중앙64dp 원형 버튼으로 고정한다. 선택0개에서는 비활성 회색, 선택 후에는 빨간 휴지통이다. `enable(root)`가 미선택 비활성 상태를 덮어쓰지 않게 했다. 이 편집 화면의 상시 비활성 삭제 버튼은 문서 선택0개에서 하단을 숨기는 기존 규칙과 구분한다.
- 기존 아이콘 Drawable에 고유 크기가 없어 세로 컨테이너의 가로 전체 버튼에서 늘어날 수 있었다. 20dp 고유 크기와 ImageView.CENTER를 적용해 버튼/터치 영역과 도형 크기를 분리했다. 휴지통 도형·삭제 메뉴 아이콘 색도 정리했다.
- 시각 대조에서 원형 체크와 글자가 붙어 보이는 점을 보완했다. CompoundButton은 compoundDrawablePadding을 buttonDrawable에 적용하지 않으므로20dp 원형 그림 뒤8dp inset을 명시했다.

## 실행과 검증

`. ./scripts/android-env.ps1` 후 strict `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug ktlintCheck --max-workers=2 --dependency-verification strict --console=plain` 실행: 최초17초, 추가 계측 포함13초 성공. Lint 오류/경고0. 변경 파일에만 ktlint CLI1.5.0 --format을 명시 실행했다. 서식 보정 과정의 긴 줄 진단은 로그에 보존하고 최종 ktlintCheck 실제 성공으로 판정한다.

기기는 연결/잠금 해제 상태를 확인하고 개발용 `.p0`/계측만 -r 설치했다. 다음 고유4개 분할이 각각1개/실패0이다.

- `referenceEditorAndSheetsKeepControlsDistinctAndContentSized`: 합성 책장 전용 저장소·공개 배경에서 편집의 비활성/활성 삭제·아이콘 실제20×20dp/버튼64dp·구분된 두 탭/왼쪽 선택 배치, 시트의 실제 높이/하단 간격과 dim flag/강도를 확인했다. 별도 창의 공개 테스트 View만 PNG로 내보냈다.
- `moveAndRegisteredDocumentPickerPreserveOtherShelvesAndDrafts`: 내용 높이로 바뀐 이동 시트의 토글 복원/현재 소속만 제거·기존 등록 추가.
- `shelfDragCancelAndFailedSaveKeepDraftAndOriginalRecords`: 바뀐 편집 헤더/탭에서도 실제 드래그·취소·저장 실패 후 초안 유지/재시도.
- `uncategorizedCardAndEditingFollowTheHomePresentationRules`: 메인 편집의 투명 배경/미분류 조건/별도 편집 진입.

합성 PNG를 참고 사진과 시각 대조했다. 마지막 체크/글자 간격 보완 뒤 strict debug/계측/Lint0/0·ktlint10초와 첫 표시 검사1개/실패0 재확인도 성공했다. 최종 PNG에서 선택 그림/글자 간격을 대조했다. 앞선4개 전체를 마지막 간격 보완 APK에서 반복한 것으로 세지 않는다. 개발용만 갱신했고 앱/계측 force-stop·ADB kill-server로 종료했다. 사용자 최종 시각 확인을 요청했다.

단위83개는 이전 로직 검증이며 이번 표시 보완에서 반복하지 않았다. 기본96개 전체·읽기 성능·장시간/OOM·다른 API·정식 서명/설치를 실행한 것은 아니다. 개인 PDF/기록·키를 PC/로그/외부로 추출하지 않았다. 사용자 디자인/버전과 stash를 보존하며 commit/push/PR/CI·CodeRabbit은 아직 미실행이다. 최종 사용자 화면 수용 후 관련 변경을 묶어 게시한다.

후속 수용: 사용자가 ‘좋아. 1, 2, 3번 다 괜찮아’로 이번 시각 보완을 수용했다. 추가 개선사항을 사진과 함께 다음 메시지에 보낼 예정이라고 알려 수용 기록만 갱신했다. 앱 코드/빌드·기기/ADB·원격 작업은 이번 수용 기록에서 실행하지 않았다. 후속 사진 수신 뒤 요구를 정리하며 게시/CI·리뷰와 구분한다.
