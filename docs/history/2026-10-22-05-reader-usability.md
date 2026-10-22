# 2026-10-22 — 개인 사용 피드백 세 가지

최신 아이콘 승인/적용: 사용자가 “좋아 이걸로 해줘”로 사전 시안을 승인했다. reader_orientation.xml을 같은 경로·strokeWidth2/round cap/round join의 회전 화살표로 교체하고 버튼48dp/위치/색상 tint/동작은 보존했다. 저장해 둔 SVG와 Android path를 토큰 비교해 기하/선 일치를 확인했다. 새 의존성/다른 사용자 변경은 없다.

`. ./scripts/android-env.ps1` 후 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain` 7초 성공·Lint 오류/경고0/정보6. 개발용 앱 -r 업데이트 후 기존 quickOrientationRestoresItsOwnRouteWithoutOpeningSettingsHome **1개/실패0·44.3초**를 확인했다. 단위/이전9개/전체91개/장시간·성능은 자원만 바꾼 이번에 반복하지 않았다. 로그 reader-orientation-icon-build.txt·reader-orientation-icon-device.txt(무시 경로), 로컬 앱6ef11855a6535516e27462da3f794014a4e0714ded4644e27b69ea9508d43a29/계측e6acd237e640a11eadd5568c558e98f3cadd6a705dbb3ac2e6015557c23d49db다. 원래 글꼴1.1 유지·앱/계측 force-stop/ADB 종료 확인. 정식판과 개인 키는 사용하지 않았다.

최신 게시/사용자 수용: f803154248045772ef59c37488446d49dce9b66c 일반 push·PR #31 OPEN/Draft·원격 head 일치. CI39785514412/39785508866 진행 중, 수동 요청6341732725 한 번에 봇6341735307 Review triggered/요약6341184734 실제 처리 중을 확인했다. 실제 review/inline API는 아직 빈 배열이다. 사용자는 드롭다운/여백 실제 미리보기/숫자 키패드를 수용했다. 추가 요청은 제목 오른쪽 방향 아이콘을 회전 화살표로 바꾸되 적용 전 시안을 먼저 보여 달라는 것이다. 무시 경로 local-artifacts/orientation-rotate-arrow-proposal.svg/png에 직접 작성한 벡터 시안/48dp 버튼 미리보기만 생성해 보여 주며 앱 리소스는 변경하지 않는다. 승인·아이콘 반영/검증·CI/실제 리뷰는 남겨 둔다. 상태 기록만의 push·중복 리뷰 요청·사용자 디자인 문서 변경은 하지 않았다.

## 후속 사용자 피드백과 같은 PR의 보완

초기 구현b785042를 일반 push하고 [Draft PR #31](https://github.com/AtraFelis/ebook_viewer/pull/31)을 생성/채팅 첨부했다. 리뷰는 아직 요청하지 않았다. 사용자는 자동 색에 대해 “이제 깜빡임 없어”로 수용했으며 방향 버튼 위치는 유지하되 드롭다운을, 여백은 실제 PDF를 보며 조절하도록 요청했다. 이후 페이지 크기·간격에서 키패드가 안 나온다는 오류를 추가했다. 아래 초기24개/도식 미리보기 결과는 초기 앱의 이력이며 이 보완 앱으로 합산하지 않는다.

빠른 방향은 PDF 위 PopupWindow의 스크롤 가능한 단일 선택 목록으로 변경했다. 선택 후 펼침을 유지하고 바깥 탭/뒤로로 닫으며 회전 재생성 상태를 보존한다. 일반 설정의 방향 세부창은 유지한다. 여백은 실제 현재 원본 페이지의 최대512px 미리보기를 기존 worker/캐시에 요청/재사용하며 상단 고정·숨길 부분의 반투명 마스크로 표시한다. 슬라이더/초기화만으로 읽기 배치/저장을 바꾸지 않는다. observer 해제/reset/늦은 결과, 캐시64MiB·별도 빌린 이미지≤1MiB 한도를 적용한다.

키패드는 ReaderFormDialog의 직접 setContentView로 설치한 편집기를 AlertController가 인식하지 못해 남긴 ALT_FOCUSABLE_IM 차단을, 실제 편집기가 있는 Window에서만 해제했다. Context7 resolve/query는 사용 가능했으나 질의 결과가 주변 Jetpack API여서 근거로 확대하지 않았다. [공식 Android IME 문서](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility#ShowDialog)의 플래그/포커스/resize 설명을 대조했다. 새 라이브러리/SHOW_FORCED는 도입하지 않았다.

보완 앱의 첫 strict debug/계측·단위59개/Lint/ktlint는12초 성공이다. 새7개 중 첫2개는 통과했고, 첫 Activity 시작은 focus=false로 실패했다. 같은 앱 재시도도 동일했고 검사 시작 실패를 보존했다. OS 진단에서 기기는 Awake/deviceLocked=0이지만 NotificationShade가 포커스를 점유했음을 확인했다. `cmd statusbar collapse` 후 readerFocus=True를 확인하고 이미 통과한 두 검사는 반복하지 않은 채 미완료 검사를 재개했다. 잠금 해제 실패나 앱 기능 실패로 단정하지 않는다. 최종 보완 결과·사용자 재수용·원격 상태는 아래에 추가한다.

## 시작과 범위

최종 보완 결과: 알림 패널을 닫은 뒤 새7개·background6/layout3/guidance9에서25개 성공을 확인했다(첫 두 성공을 합산한 고유 수). 이후 font1의200% 가로에서 조절 항목이 가려져 실패했으며 일반 방향1개는 아직 실행 전이었다. 실제 원인은 고정 미리보기 높이를 footer/scroll 가용 높이에 빼지 않은 계산이었다. preview 높이를 heading 뒤 최소96dp 조절 영역을 남기도록 제한하고 footer 적응 계산에도 preview 높이를 반영했다. 테스트 조건은 유지하고 실패 메뉴 이름 진단만 보완했다.

최종 앱은 큰 글꼴1개(6조합)를 먼저 재검증하고 새7개/일반 설정 방향1개까지 **9개/실패0·116.4초** 완료했다. 이전25개를 최종 앱 전체 재실행으로 세지 않는다. debug/계측/단위59개/Lint/ktlint12초→높이 보완10초, 마지막 unsigned release/단위/Lint/ktlint9초 통과, debug Lint0/0/info6·release0/0/info4다. 그 사이 unsigned release 첫 보완10초도 통과했다. 추가 코드 스타일 실패와 초기 알림 패널/실제 높이 실패는 원래 로그를 보존한다.

최종 app-debug.apk SHA256beb69c01d6582cb2bdfd4eec2bb3f5d9fba77b495a3e9153ae53674a2cfdb114, 계측e6acd237e640a11eadd5568c558e98f3cadd6a705dbb3ac2e6015557c23d49db. API33 설치된 두 base.apk의 sha256sum과 로컬 해시가 일치했다. 두 패키지 pid 부재·글꼴1.1·ADB 프로세스0 확인. 개인 정식판/키/개인 PDF는 사용하지 않았다. 사용자에게 개발용 PDF Viewer의 드롭다운/실제 미리보기/키패드 재수용을 요청했고 응답 대기다.

후속 로그: reader-usability-feedback-build-1~3.txt·feedback-release.txt·preview-layout-build[-2].txt·feedback-final-build.txt, feedback-device-first.txt/feedback-device-rest.txt(포커스 실패), feedback-device-after-shade.txt(25개 성공/큰 글꼴 실패), feedback-device-final.txt(최종9개) 및 usability-feedback-*.txt. helper는 각180초 device/200초 host deadline과 실패 즉시 중단·finally 정리를 유지한다. 최종 앱의 전체91개/장시간/프레임 성능/다른 API 수용을 주장하지 않는다.

사용자 요청: 자동 배경색 빠른 스크롤 때 수동색이 잠깐 보이는 현상, PDF에서 방향 설정 빠른 접근, 문서 페이지 여백 숨기기. 추가 질문에서 문서 전체 공통 적용을 선택했다. [계획](../plan/reader-usability-follow-up.md)/[ADR0018](../adr/0018-document-crop-and-reading-records-v4.md)을 작성했다.

시작은 codex/release-preparation5b90164와 SESSION/CHECKLIST/이력04 각2줄·사용자 docs/design.md128줄 변경이었다. fetch 뒤 PR #30 MERGED b3acea0c701618287ebd59fa728fbc287883d903·CI39735181505/39735168394 SUCCESS를 확인했다. review/inline API는 빈 배열이므로 CodeRabbit 체크 성공을 실제 리뷰 완료로 바꾸지 않는다. 현재 체크아웃만 ff-only 연결 후 codex/reader-background-orientation-crop을 만들었다. 사용자 디자인 SHA25628ef0cdc82d5c8fddb5bb6f1a9693e6b09299892224983522e5ae0fa9e759681을 보존/커밋 제외한다.

실제 개인용 키/비밀번호/백업 파일은 읽지 않았다. 앞선 절차 안내의 조건문을 키/설치 성공 확인으로 간주하지 않는다. 정식판 패키지는 설치·삭제/데이터 변경하지 않고 별도 개발용.p0 앱만 검사한다. 신규 의존성/스택 변경/원본 PDF 수정은 없다.

## 변경과 검증

대표 페이지 commit이 아직 없는 색을 customColor로 대체하는 경로를 제거했다. 직전 색을 유지하고 실제 분석 결과가 대체색일 때만 적용한다. 원래16색/120ms/24dp·취소/세대/캐시 정책은 유지한다.

읽기 도구에48dp 방향 아이콘을 추가하고 기존 다섯 방향 목록을 재사용했다. 선택 후 창을 유지하고 빠른 진입은 읽기 도구로, 일반 진입은 설정 부모로 복귀한다. 빠른 진입의 재생성 경로를 별도 보존하고 불필요한 부모 Window는 생성하지 않는다.

여백은 네 방향0~45%/1% 단위다. 그림/초기화/적용/취소·저장 실패 재시도를 제공한다. 남은 페이지 기하와 원본 타일/미리보기 이동·clip을 분리했다. 원본 좌표 앵커/북마크·줌은 유지하며 제거된 영역만 경계 제한한다. 기록v4 네 바이트와 v1~v3 기본0 호환, Bundle/저장 중 재생성·URI 재연결의 기존 배치 보존을 반영했다. PDF 원본/본문 읽기는 그대로라는 안내가 있다.

PC 명령(PowerShell에서 먼저 `. ./scripts/android-env.ps1`):

```
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain
```

JVM59개/실패·오류·건너뜀0(새6개 포함)을 실제 실행했다. 여백/혼합·너비 통일/0.1~32배율 가시 타일 면적45조건·원본 앵커 왕복/제거 영역 북마크·v1/v2/v3 호환과v4 손상/잔여 바이트를 확인했다. 최종 strict 명령8초 통과(이때 단위는 앞선 실행의 UP-TO-DATE 재사용), Lint 오류/경고0·정보6, ktlint 통과다. 초기 세 번의 스타일 실패와 방향 버튼 이름의 LinearLayout.orientation 충돌·계측 checkedItemId 컴파일 실패는 로그를 보존하고 수정했다. 무단 ktlintFormat/검사 억제는 하지 않았다.

기기는 처음 미연결이라 ADB를 종료했다. 사용자 연결 답변 후 승인1/잠금 해제 상태를 확인하고 API33 개발용 앱·계측만 -r 설치했다. 합성 파일만 사용하며 원래 글꼴1.1을 각 묶음 끝에서 확인/복원했다. 새 회귀는 명시적 `methodClass=usability`로만 실행하며 기존 기본91개 수는 바꾸지 않았다.

첫 묶음: 새 배경색1·여백 픽셀/앵커/회전1·실패/입력 복원1·문서별 저장/취소/재실행/다른 문서/원본 바이트 불변1 통과. 방향 빠른 접근1은 창 닫기 같은 UI turn 안에서 isShown을 검사해 실패했다. Dialog의 dismiss 콜백 처리 뒤 실제 창 포커스/도구 복귀를 기다리도록 **검사만** 보완했고 앱 수정 없이 재통과했다. 첫 실패를 성공으로 덮어쓰지 않는다.

후속 묶음은 방향 빠른 접근1·background6·layout3·panLock5·chrome3·font1(기존6조합)·일반 설정 방향1 =20개/실패0·143.5초였다. 첫 성공4개를 더해 고유24개의 최신 성공이며 최초 실패는 별도다. 이후 큰 글꼴 검사에 새 방향 아이콘과 여백 네 슬라이더/설정 메뉴를 명시적으로 추가하고 **해당 기존1개만**6조합 재검증(70.3초/실패0)했다. 고유 검사를25개로 부풀리지 않는다. 최종 명령은 위 PC 명령에 `:app:assembleRelease :app:lintRelease`를 추가해16초 성공했다. 단위는 앞선59개 실행 재사용, debug Lint0/0/info6·release0/0/info4, ktlint 통과다.

로컬 최종 app-debug.apk SHA2568489f5a1195fc01392a2d52e6d6f2892d9d543772a4402395cfe08ec9e833509, 최종 계측3ffdc418ff3a417977ecafd9b3749167074b636eb1ba1d0b47dce6bfe4fd163a. 마지막 호스트가 두 APK install Success/검사 실행·글꼴1.1 유지·앱/계측 force-stop/ADB 종료를 확인했다. 이후 설치본 sha256 재대조를 시도했을 때는 기기가 분리되어 `no devices/emulators found`였으므로 설치본 해시 일치로 보고하지 않는다. 정식판은 변경하지 않았다.

로그(무시 경로): local-artifacts/reader-usability-first-build.txt·build-2~5.txt·build-final.txt·final-all-build.txt, reader-usability-device-first.txt(첫4성공/방향 실패), reader-usability-device-rest.txt(후속20개), reader-usability-font-final.txt(확장font1개)와 개별 usability-*.txt. 테스트 helper는 각 호출 device180초/host200초 상한, 실패 시 중단/finally 글꼴 복원·앱/계측/ADB 종료다. 최종 보완 font 외 기존 성공 검사는 반복하지 않았다.

사용자에게 개발용 PDF Viewer에서 자동색 빠른 스크롤·방향 아이콘·문서 공통 여백/확대·이어 읽기 수용을 요청했다. 응답/PR/CI/실제 리뷰는 대기다. 현재 결과를 전체91개/20분/성능·모든 PDF/다른 API 검증이나 정식판 배포 완료로 세지 않는다. release 버전/개인 키 서명·설치는 사용자 수용/병합 뒤 별도 업데이트 절차이며 이번에 서명하지 않았다.
