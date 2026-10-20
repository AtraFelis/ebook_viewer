# 읽기 도구·반응형 적용 — 2026-10-20

## 작업 기반과 범위

사용자가 이어서 진행하도록 요청했다. PR #19의 수정 head63fa450은 원격 Android CI39424169568/39424163063 모두 성공했고, CodeRabbit 답변4325028852가 중복 제목 수정이 충분하다고 확인했다. 두 리뷰 스레드는 해결됐고 사용자 병합 시각은2026-10-20T12:52:30Z다. Git 상태가 깨끗함을 확인하고 fetch → 현재 체크아웃 main 전환 → `git pull --ff-only origin main`으로46eaa41 동기화 → `codex/design-reader-tools`를 생성했다. worktree/reset/clean/강제 push는 없다.

AGENTS/SESSION/CHECKLIST/적용 계획/디자인 인계와 관련 소스를 읽었다. Figma 디자인 적용 스킬을 사용하되 사용자 지시대로 MCP는 호출하지 않았다. 기존 Chrome의 Figma 프로토타입을30:190으로 이동해 렌더 완료된 상단 제목·뒤로가기/하단 페이지 수·도구 배치를 확인했다. 디자인 원문은 수정하지 않는다.

이번 범위는 계획3의 읽기 도구다. 페이지 슬라이더/첫 안내/실패 초안은 다음 단계다. 새 런타임 의존성·도구 버전·저장 모델·PDF 엔진/렌더/캐시 변경은 없다.

## 구현과 검증 기준

- `ReaderToolsView`는 상단 제목/48dp 뒤로가기(원형40dp·벡터20dp), 하단 페이지 수/기존 상태 안내/페이지 이동·북마크·설정/본문 읽기를 구성한다. 제목은 한 줄 말줄임이며 전체 접근성 이름을 유지한다. ‘읽기로 돌아가기’ 버튼은 제거했다.
- 도구 전체는 PDF와 독립된 overlay로 배치한다. 가운데 투명 공간은 터치 차단 뷰가 아니며 PDF 스크롤/핀치를 그대로 받는다. PDF의 가시 크기와 줌/앵커는 도구 열기/닫기로 바꾸지 않는다.
- 닫힌 상태는 중앙1/3 단일 탭으로 열고 열린 상태는 중앙 밖 단일 탭도 닫는다. 기존 PDF의 다중 포인터/플링 중지 탭 억제를 재사용한다. 버튼이 소비한 이벤트는 닫기로 보내지 않으며 도구 자체 여백도 GestureDetector의 단일 탭만 닫기로 처리한다.
- 하단은 사용 가능 너비760dp 이상/높이480dp 미만에서 좌우로 나누고, 짧은 창에서는 상단/문서 간격을 남긴 제한 높이 ScrollView로 하단 조작에 접근한다. 이 분기는 인계의 초기 제안이며 모든 조합의 시안 일치/문서 노출96dp 보장은 아니다.
- 실제 root dispatch 경로의 바깥 탭/열기 범위·줌/위치 보존·스크롤/핀치 통과·여백 닫기·홈 복귀 회귀2개를 immersive suite에 추가했다. 기본 suite 예상 수는59→61로 갱신했다. 기존 home 이동 검사는 새 아이콘 ID로, 큰 글꼴 검사는48dp 아이콘도 확인하도록 갱신했다. 기존 테스트를 제외하거나 deadline을 늘리지 않았다.

## PC 검사

`. ./scripts/android-env.ps1` 후 strict 앱/계측 빌드·단위·Lint·ktlint가 최종13초/exit0이었다. 단위 XML은44개/실패0/오류0/건너뜀0이다. 초기 검사에서 새 코드 스타일, 누락 View import, 제거된 두 버튼 문자열의 미사용, listener 밖에 있던 performClick의 Lint 추적 문제가 각각 검출됐다. 수동 스타일 수정·import/문자열 정리·클릭 검출 후 listener에서 performClick 호출로 고쳤다. formatter/검사 억제/baseline 추가는 없다. 최종 Lint는 오류/경고0·버전 권고Information6개다.

```powershell
./gradlew.bat --dependency-verification strict :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck
```

## 기기 검사

API33 기기1개 승인·잠금 해제/화면 켜짐을 확인했다. 식별자·개인 화면/파일은 수집하지 않는다. 데이터 보존 재설치와 관련 suite를 한 invocation씩 실행하고 종료를 확인한다. 현재 설치/검사 진행 중이며 통과로 기록하지 않는다. 기존 성능20그룹/장시간/전체61개 검사는 반복하지 않는다. 종료 후 앱/계측·ADB를 중지한다.

최초 immersive7개는6개 통과/새 스크롤 검사1개 실패였다. 앱이 튕긴 것은 아니며 핀치·바깥 탭·방향·전체 화면은 통과했다. 새 검사는 이동24px 한 번과 핀치 전 위치 비교를 사용했다. 밀도를 고려한16dp×4회의 MOVE와 핀치 직후 위치 비교로 바꿔 실제 PDF 이동을 검증했고, 앱 APK 변경 없이 새 계측 APK만 설치한 재검사7개는 모두 통과했다. 실패 기록은 로컬 `design-tools-immersive-initial-failure.txt`에 보존한다. strict 계측 빌드/Lint/ktlint도5초/exit0이었다.

합성 PDF를 확실히 연 검사에서만 root를 그린 PNG를 private cache에 저장한 뒤 해당 파일 하나를 로컬 `design-tools-synthetic.png`로 읽어 시각 확인했다. 앱 라이브러리나 개인 문서·기기 전체 화면은 캡처하지 않았다. 다크 상단 제목/원형 뒤로가기·하단 페이지 수/조작/비활성 북마크·기존 본문 읽기와 가운데 문서 노출을 확인했다. 시안에 없는 본문 읽기 경로를 보존하므로 시안과 하단 높이까지 동일하다고 주장하지 않는다. 추가 가로/큰 글꼴 검사는 별도 진행한다.

### 최종 결과

관련 기기는 `immersive`7개/실패0 → `font`1개/실패0 → `navigation`4개/실패0로 총 **12개/실패0**이다. 새 계측 APK를 사용했고 앱 APK는 최초 설치본과 같았다. font 검사는100/150/200%×가로/세로와 홈/새 도구/설정/본문 창 조작 접근을 포함한다. navigation은 페이지 이동/북마크/번호 표시·기록 복원과 줌 보존을 확인했다. 전체61개/기존 성능20그룹/장시간은 미실행이며 이전17개 결과를 새 앱 결과로 옮기지 않는다.

실제 명령은 로컬 host wrapper `./local-artifacts/design-device.ps1 -Mode suite -Suite immersive`/`font`/`navigation`이다. 각각 기기 계측에 `-e suite <suite> -e deadlineSeconds 180`을 전달하고 host210초 상한과 finally 앱/계측 force-stop을 적용했다. 관련 검사마다 종료를 확인한 뒤 다음을 실행했다. 재검사 전에는 앱 재설치 없이 `install-test`로 계측만 교체했다.

종료 시 font_scale1.1, 설치 APK SHA-256과 로컬 `8fd46e16ba11647393bb8c878af791eef095d18430268f3e7798e9cb63f67da4` 일치를 확인했다. 첫 APK 확인은 Android 설치 경로의 정상 `~` 문자를 허용하지 않은 host 검증으로 중단됐다. 해당 문자 검증을 고치고 finally로 앱/계측·ADB 종료를 보장한 후 일치/ADB 프로세스0을 확인했다. 기기/검사 실패로 세지 않는다.

사용자에게 새 상단 제목/뒤로가기·하단 도구·빈 곳 닫기·스크롤/확대의 직접 사용감을 확인하도록 요청했다. 현재 답변 대기이며 새 TalkBack 음성·최소폭320dp/긴 제목 조합까지 통과했다고 선언하지 않는다. 구현/검증을 모아 Draft PR로 게시하고 필요한 실제 CodeRabbit 검토를 한 번 요청한다. 병합은 사용자가 하며 전체 디자인 완료가 아니다. 다음은 별도 페이지 슬라이더, 이후 첫 안내/실패 초안·최종 통합이다.

### 게시

공백·변경 문서의로컬 링크79개/누락0을 확인하고16개 파일을1214f64로 커밋해 origin/codex/design-reader-tools에 일반 push했다. [Draft PR #20](https://github.com/AtraFelis/ebook_viewer/pull/20)을 생성/채팅 첨부했고 head 일치를 확인했다. 원격 Android CI39428703554/39428695825는 당시 IN_PROGRESS였다. CodeRabbit 자동 체크는 Draft PR not reviewed라는 skip이었고 실제 리뷰 완료가 아니다. 구현/검증이 마무리된 head에 [수동 요청6291715178](https://github.com/AtraFelis/ebook_viewer/pull/20#issuecomment-6291715178)을 한 번 등록했다. 실제 접수/결과를 확인하고 상태만을 위한 push로 진행 중 head를 바꾸지 않는다. 이 게시 후 기록은 로컬에 모아 보존한다.

실제 응답[6291717359](https://github.com/AtraFelis/ebook_viewer/pull/20#issuecomment-6291717359)은 **Action not completed / Review rate limited**다. 요약6291707279는 다음 포함 리뷰8분 후라고 안내했다. 요청은 등록됐지만 실제16개 파일 리뷰는 미실행이며 성공 체크를 완료로 세지 않는다. 한도 중 즉시 재호출하거나 유료 결제/설정 변경을 하지 않는다. 최종 조회에서 CI 두 실행은 IN_PROGRESS였고 새 사용자 수용 답변은 아직 없다. 이 상태 기록3개 문서는 다음 결과와 모으도록 로컬에 보존하며 앱/계측·ADB를 재시작하지 않는다.
