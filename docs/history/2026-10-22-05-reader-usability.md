# 2026-10-22 — 개인 사용 피드백 세 가지

## 가로 이동 잠금 화살표 사전 시안

사용자가 “ㄱㄱ”로 승인해 시안의 path/22dp/선1.8/round cap·join을 locked/unlocked 리소스에 적용했다. 두 상태 tint를 기존 theme accent로 맞췄으며 버튼48dp·선택/잠금·두 손가락 제스처·저장은 변경하지 않았다. `. ./scripts/android-env.ps1` 뒤 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain` **11초 성공**이다(local-artifacts/reader-pan-arrows-build.txt). 작은 아이콘 교체에 기존 panLock5개를 다시 시작한 것은 범위가 과했다. 사용자 지적 직후 전용 호스트를 종료하고 개발용 앱/계측 force-stop·font_scale1.1·ADB 종료를 확인했다. 검사가 완료되지 않았으므로 통과로 기록하지 않는다. 새 테스트/성능 측정/전체 suite는 추가하지 않으며 향후 같은 변경은 리소스/빌드 확인만 한다. 정식판·개인 키·사용자 디자인 변경은 보존했다.

사용자는 자물쇠 아래 좌우 양방향 화살표를 추가하되 아이콘을 먼저 보여 달라고 했다. 현재 locked/unlocked22dp 벡터·48dp 버튼과 테마 accent를 확인했고, 두 상태의 별도 SVG/PNG·Android XML 시안을 local-artifacts/reader-pan-*-proposal.*에 생성했다. 자물쇠와 화살표 사이를 띄우고 같은1.8 선/round cap·join·기존 파란색#285BC5를 사용했다. PNG의 확대 모양과 버튼 안 크기를 육안 확인했다. 앱 리소스/코드/설치·원격 PR은 변경하지 않았으며 시안 확인 대기다. 빌드/단위/기기 검사는 앱 변경이 없어 실행하지 않았다. 사용자 design.md128줄과 이전 미커밋 게시 기록을 보존했다.

## 방향 상태 아이콘과 실제 리뷰 보완

게시 확인: b1fcbcaded6266ebc3fe62ff637d6fd066994398 일반 push와 PR #31 OPEN/isDraft=false/원격 head 일치. 설명은 최종 상태 아이콘·실제 리뷰 수정과5개 검사 범위로 갱신했다. 지적2개에 답변4349117220/4349117496을 게시했고 추가 allowance0인 리뷰를 반복·유료 요청하지 않았다. 새 CI두 건은 IN_PROGRESS이며 원격 완료/추가 실제 리뷰·사용자 병합을 완료로 세지 않는다. 이 게시 확인 문서만으로 추가 push하지 않는다.

최종: 요청 아이콘/리뷰2개 보완 앱의 관련5개를 분할해 모두 성공했다. device-4에서 quickOrientation(아이콘 픽셀 완전 일치/tint/선택/재생성/복귀)·actualPreview 기존2개 성공 후 신규 취소 검사가 일반512px preview를 crop 전용 요청으로 오인해 실패했다. normal preview가0.5배이고 crop은0.512배가 되도록400px 화면 조건을 명시해 둘을 구분했고 assertion은 그대로 유지했다. device-final에서 신규1·큰 글꼴1(6조합)·일반 방향1, **3개/실패0·54.8초**를 완료했다. 최종 앱은 전체91개/장시간·프레임 성능/다른 API를 반복하지 않았다.

초기 아이콘 검사의48px target은 이미 표시된 native vector cache를 축소해 fresh resource와 AA가 달랐다. 독립 drawable만으로도 차이가 남았다(device-2/3 실패). 15% 허용 오차 시도는 자동 승인 검토가 테스트 약화 위험으로 거절했으며 해당 검사 실행은 하지 않았다. 그 변경을 완전히 제거하고 두 벡터를 버튼보다 큰 동일 크기로 그려 캐시 크기를 맞췄다. **Bitmap.sameAs 완전 일치 조건과 실제 ImageButton tint 검사로 통과**했고 조건 완화/오류 억제는 최종 코드에 없다. test-build-2의 진단 문자열 줄바꿈 ktlint 실패도 수동 수정했다.

최종 앱 f23ee0cf40cea40f42f05253379b1d4cf33e479b4a34f8be268f9b16c6b78b12, 계측 ae177ec8386fb796b48616c2333a658e4abd9904036db4e0882b3c010c45d6a0이다. 마지막 test-build-5 strict 계측/Lint/ktlint6초 성공·양 Lint 오류/경고0, JVM59개/실패·오류·건너뜀0이다. API33 개발용 앱만 사용했고 원래 글꼴1.1·앱/계측 중지·ADB 종료를 확인했다. 사용자 디자인128줄/해시28ef0cdc 보존, 새 의존성/개인 키/정식판 변경은 없다. CodeRabbit 완료 리뷰의 포함 allowance는0으로 표시돼 이번에 유료 리뷰나 반복 수동 호출을 추가하지 않는다. 같은 PR에 수정 push/스레드 답변 후 새 CI와 추가 실제 리뷰·사용자 병합은 구분한다.

사용자 첨부의 자동 회전(두 화살표/기울어진 기기)·가로/세로 자물쇠를 네이티브 벡터로 구현했다. toolbar와 두 방향 목록은 기존 테마 accent(밝은 테마#285BC5/어두운 테마#88ACFF)를 사용한다. 저장 성공/초기 로드/재생성에 선택 상태를 반영하고 실패 때 확정 아이콘을 유지한다. 역방향은 같은 기기 모양과 기존 문구로 구분한다. 버튼48dp/드롭다운 유지·동작은 그대로다. 원본 첨부 이미지는 Git/외부에 올리지 않았다.

CodeRabbit 실제 리뷰5614954864는 f803154 대상으로 지적2개를 게시했다. inline4348886932의 크기 준비 전 preview 구독 후 타일 생성 누락은 타당하다. 계산을 분리하고 constrain에서 원본 anchor 복원/재투영/경계 제한 뒤 생성·요청·캐시 알림을 처리했다. suggested fix의 layout 생성 직후 위치는 복원 전 페이지를 고를 수 있어 그대로 복사하지 않았다. 신규 회귀는 최초 pendingPages와 기존 layout의 크기 재준비, 복원된 뒤쪽 페이지 선택/비트맵 전달·취소/해제·reset을 검사한다. inline4348886951도 수용해 여백 전체 공통 적용과 원본 보존이 동시에 성립한다고 명시했다. 직전0b32355 원격 CI두 건 SUCCESS, 새 head의 원격 검증은 별도다.

PC 첫 두 시도는 새 인자/함수 표현식 들여쓰기 ktlint 실패로 수동 수정했고, 세 번째는 벡터 fillType의 API24+ Lint 오류2개로 실패했다. min23을 올리거나 경고를 억제하지 않고 반대 winding 경로로 같은 구멍 모양을 구현했다. 네 번째 strict debug/계측/JVM/Lint/ktlint7초 통과, JVM59개는 세 번째 빌드에서 실제 실행 후 재사용이다. unsigned release/Lint/ktlint11초 통과, 양 Lint 오류/경고0이다. 개인 키·정식판은 사용하지 않았다.

기기 첫 방향 검사1개는 새 픽셀 비교 assertion에서 실패했다. 기존 ImageButton drawable의 bounds/cache를 직접 바꾸지 않고 constantState의 독립 인스턴스로 기하를 비교하며 버튼의 실제 tint는 별도로 확인하도록 검사를 수정했다. 실패 로그는 보존한다. 검사의 후속 실제 결과/범위는 아래에 추가한다. 로그: orientation-reference-build[-2~-4].txt, orientation-reference-release.txt, orientation-reference-test-build.txt, orientation-reference-device[-2].txt (local-artifacts/무시 경로).

아이콘 게시 확인: 0b32355e1db00d27acfb291396d47404f80699a9 일반 push·PR #31 OPEN/isDraft=false/원격head 일치로 ready for review 전환했다. 새 CI39786680886은 QUEUED, 실제 review/inline API는 아직 빈 배열이었다. f803154의 앞선 수동 요청이 진행 중이므로 아이콘 수정에 추가 수동 요청은 하지 않았다. 사용자 기능 수용/아이콘 승인은 완료했고 원격 CI·실제 리뷰 대응/사용자 병합은 남겨 둔다. 상태 기록만의 추가 push는 하지 않는다.

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

승인 시안과 실제 벡터 path/stroke 일치를 확인했고 개발용 APK install Success 뒤 앱/ADB를 종료했다. 추가 동작 검사는 수행하지 않았다.
