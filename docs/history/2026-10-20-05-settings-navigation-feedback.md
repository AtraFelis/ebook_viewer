# 설정 왕복·전환·글자 잘림 수정 — 2026-10-20

## 요청과 원인

사용자가 Draft PR #20 앱에서3건을 보고했다. 배경색/밝기 등을 바꾼 후 닫기/적용이 본문으로 곧장 돌아가 반복 진입이 필요했고, 상위 설정에서 세부 설정으로 갈 때 PDF/홈이 잠깐 노출됐으며, 방향 목록의 글자 하단이 잘렸다. 사용자 수용 완료가 아닌 수정 요청으로 기록한다. 현재 `codex/design-reader-tools`/1214f64와 게시 후 로컬 상태 문서3개를 보존하고 같은 브랜치에서 수정한다.

MainActivity는 상위 설정을 먼저 dismiss한 뒤 비동기로 설정을 읽고 세부 Dialog를 띄웠다. 모든 세부 onDismiss도 설정 소유자를 비우고 도구를 숨겼다. 방향의 시스템 single-choice 행은 고정 preferred height를 사용하지만 추가한 위아래16dp padding/사용자 서체에 맞춰 높이를 늘리지 않았다.

## 변경

- 상위 설정은 독립 소유자로 유지하고 비동기 로드 중에도 opaque Window를 닫지 않는다. 세부 폼은 그 위에 보여 주고 닫기/뒤로/적용 후 기존 상위 Dialog로 돌아간다. 상위의 스크롤/페이지 번호 설정 상태도 그대로다.
- 중복 클릭/뒤늦은 비동기 결과는 현재 부모 소유자와 로드 상태로 구분한다. 상위 닫기·문서 오류·Activity 파괴 시 부모/자식/로드 소유자를 함께 정리하고 예전 onDismiss가 새 소유자를 지우지 않게 한다.
- 설정 Window의 전환 애니메이션을 없애고 부모를 유지한다. 방향/테마 변경에 따른 Activity 재생성에는 `settingsOpen`을 instance state로 전달해 상위 설정을 복원한다. 새로운 영속 설정 형식은 없다. 문서 준비 후 문서별 배치 버튼 활성 상태도 갱신한다.
- 목록 행은 WRAP_CONTENT+최소64dp·font padding으로 내용 크기에 맞춰 늘어난다. 사용자 서체와 큰 글꼴을 축소하지 않는다.
- 새 settings suite2개는 홈의 자식 왕복에서 부모 attach 유지/Activity focus 노출 없음, PDF의 배경색/표시/배치 적용 후 동일 부모 복귀·줌/위치 보존·회전 후 설정 복원을 검증한다. 기본 예상 수61→63을 갱신했다. 기존 방향/테마 검사도 상위 설정 복귀를 확인하고 읽기 창 검증 시 명시적으로 닫도록 바꿨다. 큰 글꼴 검사에는 방향 목록과 실제 텍스트 영역 높이 검증을 추가했다.

새 의존성/엔진/캐시/저장 포맷/디자인 원문 변경은 없다. 전환 중 부모 유지 검사와 사용자 체감의 깜빡임 확인을 구분하며, 모든 기기의 모든 프레임에서 무깜빡임을 보장했다고 주장하지 않는다.

## 실제 검사

`. ./scripts/android-env.ps1` 후 아래 strict 명령은 최초10초/exit0, 복원 후 설정 값 binding까지 보강한 최종 앱에서11초/exit0이다. 단위 XML44개/실패0/오류0/건너뜀0, Lint 오류/경고0·버전 정보6개, ktlint 통과다. 초기 새 검사 코드의 줄 길이/unused import는 수동 수정했고 formatter/검사 억제는 없다.

```powershell
./gradlew.bat --dependency-verification strict :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck
```

API33 기기1개 승인·잠금 해제·화면 켜짐을 확인하고 앱/계측을 데이터 보존 재설치했다. 관련 suite를 한 invocation씩 실행하고 종료를 확인하며 진행 중인 결과는 통과로 세지 않는다. 전체63개/렌더 성능 검사는 반복하지 않는다. 검사 종료 후 앱/계측·ADB를 중지한다.

최초 settings2개는 적용/회전1개 통과·시스템 뒤로1개 실패였다. 새 검사가 child.isShowing만 확인하고 실제 입력 포커스 이전에 Back을 보낸 것이므로 child Window 포커스를 기다리도록 고쳤다. 같은 앱/교체 계측의 재검사2개는 모두 통과했다. 실패 기록은 로컬 `settings-navigation-initial-failure.txt`에 보존한다. 앱의 동작을 검사에 맞춰 우회하거나 뒤로가기 검사를 제거하지 않았다.

복원 창이 초기 설정 파일 로드보다 먼저 나타날 수 있어 페이지 번호 Switch의 비동기 로드 값도 부모 폼에 binding하도록 보강했다. listener 억제 구간으로 표시 갱신이 새 저장 요청이 되지 않게 했고 부모 정리 시 참조도 해제한다. 최종 앱 SHA-256은 `6f32ef0370febbbbe8c43889e6ff33b69d2a51e0c915678107d21f712cd273aa`다. 최종 앱/계측을 다시 설치한 settings2개도 통과했고 회전 후 저장된 번호 표시 값 복원을 포함한다. 이후 다른 관련 검사는 아직 진행 중이다.

같은 최종 앱의 font1개는100/150/200%×가로/세로에서 방향 행의 실제 글자 영역과 레이아웃 높이, 스크롤 접근, 세부 폼→부모 복귀·닫기와 본문 창을 통과했다. display4개는 밝기/화면 유지·취소·저장, 밝게/어둡게/시스템 테마 재생성 뒤 상위 설정 복귀와 PDF 픽셀/줌/위치 보존을 통과했다. 나머지 immersive7개는 진행 중이며 이전 앱의 통과를 대신 쓰지 않는다.

PR #20의 이전 head1214f64 원격 CI39428703554/39428695825는 모두 SUCCESS로 확정했다. 이번 수정 head의 CI 성공은 아직 아니다. 사용자 피드백은 수정 검증 뒤 같은 Draft PR에 묶어 일반 push하고 새 head 검토를 요청한다.

### 최종 기기 결과와 원인 대조

같은 최종 앱/계측에서 `settings`2개 → `font`1개 → `display`4개 → `immersive`7개가 모두 통과해 **14개/실패0**이다. 마지막7개는 방향5종 관련 복원과 도구 열기/닫기·스크롤/핀치 전달·전체 화면·줌/위치 보존을 포함한다. 각 명령은 `./local-artifacts/design-device.ps1 -Mode suite -Suite <suite>`로 기기180초/host210초 상한과 finally force-stop을 적용했다. 최초 실패/예비 앱 결과를 최종 표본에 섞지 않았다. 전체63개/장시간/성능 재측정은 미실행이다.

글자 잘림 원인을 보편 Android와 혼동하지 않도록 공식 API35 SDK의 `simple_list_item_single_choice.xml`도 대조했다. API35는 height=wrap_content/minHeight 속성이지만, 현재 API33 기기의 `/system/framework/framework-res.apk`를 로컬로 읽어 aapt2 xmltree로 확인한 실제 동일 레이아웃은 **layout_height=?0x01010387**이었다. aapt2 resources에서 그 속성이 **attr/listPreferredItemHeightSmall**임을 확인했다. 따라서 고정 높이 설명은 현재 기기 리소스에 해당하며 모든 Android 버전의 보편 속성으로 확대하지 않는다. 개인 앱/PDF/화면을 수집한 것이 아니고 읽은 시스템 APK는 ignored local-artifacts에만 있으며 Git/외부 서비스에 올리지 않는다.

종료 시 설치 APK와 로컬 최종 해시 일치, font_scale1.1·앱/계측 force-stop·ADB kill-server·ADB 프로세스0을 확인했다. 사용자에게 수정 후 상위 복귀/전환/방향 글자를 재확인하도록 요청했다. 현재 직접 수용은 대기 중이며 자동 검사와 분리한다. 수정·검증을 묶어 같은 Draft PR에 일반 push하며 원격 CI/실제 리뷰는 새 head에서 확인한다.
