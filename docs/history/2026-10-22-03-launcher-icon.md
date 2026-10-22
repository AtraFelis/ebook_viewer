# 2026-10-22 — 선택 아이콘 런처 적용

## 기준과 사용자 변경 보존

사용자가 다음 작업인 아이콘 적용을 승인했다. AGENTS→SESSION→CHECKLIST→현재 계획/Git 규칙을 읽고 상태/브랜치/원격을 확인했다. PR #28은 CI39729182282/39729163714 모두SUCCESS·MERGEABLE/CLEAN이었으며 실제 CodeRabbit review/inline API는 비어 있고 한도 응답만 있었다. 사용자에게 이 한계를 구분해 병합 가능하다고 안내했다. 작업 중 사용자 병합a58b0207c058e05a687594249801ca92a2bfd845(2026-10-22T05:45:34Z,14:45 KST)를 확인하고 git fetch origin·현재 체크아웃 git merge --ff-only origin/main 후 codex/launcher-icon을 사용했다. 기존 상태 문서 기록과 사용자 docs/design.md128줄 변경을 보존/후자는 커밋 제외했다. reset/clean/강제 push/worktree 생성은 없다.

## 자산 출처와 구현

선택 PNG SHA-256은8449cd87afee60376da87657d8b0797cc290f386637433dea2840ccc2957d5b1이다. 별도 디자인 작업의 기존 icon-b-parallel-flow-v2.png와 바이트가 같음을 확인해 대응 SVG를 docs/design-assets/selected-launcher-icon.svg로 보존했다(SHA-256 08be32857dd21a1edd40f80cc5f167f3969d41b26d10d9b5840ed78b1ab67a51). 새 이미지 생성/사용자 PNG 편집은 없으며 기존 SVG의 동일 곡선/캡슐/점/위치 도형을 Android vector로 옮겼다. 사용자 디자인 문서 자체는 수정하지 않았다.

- 일반 아이콘: mipmap-anydpi/ic_launcher.xml의48dp/1254viewport vector. 배경#72ACE9/종이#FFFFFF/강조#4086EC/본문#A3C8F2를 유지한다.
- API26+ adaptive:108dp 전경의 중앙72dp에 원래 전체 구도를 넣는다(scale2/3,viewport내 translate209). 종이 상하만 overscan까지 연장해 마스크/이동 여유에서 종이 끝이 드러나지 않도록 한다. 배경색은 별도 color 리소스이며 전경은 투명하다.
- 시스템 단색: 같은 종이와 본문/위치 표시를 alpha silhouette/투명 구멍으로 파생했다. 긴 compound path 대신750문자 이하 짧은 evenOdd clip 세 개를 교차 적용해 종이를 한 번 그린다. API24+ clip fillType 리소스이고 이를 참조하는 adaptive는API26+다.
- Manifest icon을 @mipmap/ic_launcher로 바꾸고 유일하게 참조되던 이전 ic_book.xml을 제거했다. Activity 코드/기능/기록/패키지명/버전/서명 정책·런타임 의존성 변경은 없다.

기준은 [Android 공식 adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)의108dp 레이어/72dp 표시 영역/66dp 안전 원·foreground/background/monochrome 설명으로 대조했다. 문서 페이지의 Compose 분류와 관계없이 플랫폼 vector/adaptive XML만 사용하며 Compose를 도입하지 않는다.

## 실제 검증과 실패

기존 번들 Python 표준 XML과 Node sharp로 원본 SVG→Android vector 도형 변환 및 자산 미리보기를 수행했다. 도구는 개발용 일회성 ignored local-artifacts 스크립트이며 앱 의존성이 아니다. 원본 SVG 대 일반/중앙 adaptive를1254px로 렌더한 평균 채널 차이0·4초과 차이 비율0을 확인했다. 본문/위치 표시 최대 반경443.61은66/72 안전 원574.75 안이다. 실제 자원 XML로부터 원형/둥근 사각형·48/32px 및 단색 테마 예시를 렌더해 시각 확인했다. 미리보기는 플랫폼 런처 화면의 직접 캡처가 아니다.

`. ./scripts/android-env.ps1` 후 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict --console=plain`을 실행했다. 첫15초는 MonochromeLauncherIcon 누락으로 실패했다. 동일 도형의 단색을 추가한 다음10초는 VectorPath1969문자 성능 지적으로 실패했다. 짧은 clip으로 나눈 뒤 같은 명령이 **7초 BUILD SUCCESSFUL**, Lint 오류0/경고0/정보6이었다. 검사 억제/기준 완화는 없다. 단위53개는 UP-TO-DATE 재사용이며 새 실행으로 보고하지 않는다. 신규 앱 테스트/전체91개/20분/성능 검사는 추가하지 않았다.

aapt2 dump badging으로 package dev.example.ebookviewer.p0/versionCode1/versionName0.1.0-p0/min23/target35/아이콘 연결을 확인했고 apksigner verify --verbose --print-certs가 성공했다. 알려진 app-metadata.properties 비보호 엔트리 경고는 서명 검증 실패와 구분하며 검증을 우회하지 않았다. ZIP 비교 첫 가정인 모든 DEX 불변은 generated R 전용classes2.dex 때문에 맞지 않았고, DEX class_defs를 읽어 변경된9572bytes에는 generated R 클래스9개만 있음을 확인했다. classes.dex/ classes3.dex는 바이트 동일했다. 아이콘 경로도 AAPT 정규화(-v21/anydpi-v24)를 반영해 실제 네 자원을 확인했다. 고지 assets3개는 바이트 동일하고 PNG/SVG는 APK에 없다.

API33 기기가 승인/화면 켬/잠금 해제임을 확인했다. ignored check-launcher-icon.ps1은 기존 설치본81fc9289 해시를 먼저 확인/보존하고 같은 서명의 -r 업데이트만 수행했다. 최종 앱 SHA-256 **5e918346d04f39b7e5a4a9c1430fdfe110d136be918f7249a0c21d4e7b5965d5**와 설치본 일치·am start -W Status ok·원래font_scale1.1 불변을 확인했다. 앱 삭제/데이터 초기화/기기 타임아웃 변경은 없다. finally에서 앱/계측 force-stop·ADB 중지했고 ADB프로세스0을 확인했다. 개인 PDF를 열거나 런처의 개인 화면을 캡처하지 않았다.

## 한계와 다음 행동

병합 완료 확인: gh pr view 29에서 MERGED·2026-10-22T06:12:19Z(15:12 KST)·merge973f784bf309718cf445f8ba4df9039b7d2dff52·head7566f21·CI39731267009/39731254725 모두SUCCESS를 확인했다. 실제 CodeRabbit reviews/inline API는 빈 배열이다. 사용자 수용과 아이콘 적용/설치·병합은 완료, 외부 리뷰는 한도로 미실행인 이력으로 분리한다. git fetch origin 뒤 현재 체크아웃을 origin/main973f784로 ff-only 연결했고 사용자 docs/design.md와 기존 상태 기록을 보존했다. 병합 뒤 리뷰 재호출/상태 기록 push/앱·ADB·검사 재실행은 없다. 남은 별도 결정은 자체 LICENSE·제품 버전·release 서명/업데이트 방식이다. 아래 OPEN/진행 중 표현은 당시 기록이다.

후속 사용자 수용: 홈/앱 목록 아이콘 확인 요청에 “새 아이콘이 잘 보여요”로 응답했다. 설치된 색상 아이콘 외관은 사용자 수용 완료다. 시스템 단색 테마·다른 API 기기/모든 마스크를 직접 확인한 것으로 확대하지 않는다. 수용 기록에 앱/기기 검사를 반복하지 않았다. 원격 확인 시 PR #29 OPEN/MERGEABLE·CI39731267009/39731254725 IN_PROGRESS·실제 리뷰/inline 빈 배열이며, 봇6333924577은 Action not completed/Review rate limited로 바뀌었다. 최초 접수 문구와 실제 미실행을 구분하고 한도 중 재호출/상태 기록만의 push는 하지 않았다. 아래 수용 미확인/최초 접수 표현은 당시 기록이다.

아이콘 리소스 적용과 PC 미리보기/빌드/설치 실행 확인을 완료했다. 실제 홈/앱 목록의 외관·시스템 테마 사용자 수용과 다른 API 기기는 미확인이다. 전체 기능/성능/20분 검증을 이번 APK에서 다시 한 것으로 세지 않는다. 프로젝트 자체 LICENSE·제품 버전·release 서명/배포는 별도 결정이다. PR/CI/실제 외부 리뷰·사용자 병합은 게시 뒤 상태를 구분해 기록한다.

게시: 7566f21ae6ce5b61ea2d79a59e668e5cf6c016f7 일반 push·PR #29 OPEN/base main 생성/채팅 첨부. CI39731267009/39731254725은 IN_PROGRESS다. 봇6333915186 자동 별 수 skip 뒤 수동6333923316 한 번에 공식 응답6333924577 Review triggered를 확인했다. 그러나 요약6333915186은 Review limit reached(당시3분 표시)로 바뀌었고 실제 봇 reviews API는 빈 배열이다. 접수 응답/체크SUCCESS와 실제 리뷰 완료를 구분하며 추가 중복 호출/자동 예약/상태 기록만의 재push/에이전트 병합은 하지 않는다. 설치된 홈/앱 목록 외관 확인을 사용자에게 요청했고 아직 응답 전이다. 기존 사용자 docs/design.md128줄/SHA-256 28ef0cdc82d5c8fddb5bb6f1a9693e6b09299892224983522e5ae0fa9e759681 변경은 보존/커밋 제외했다. 게시 상태는 로컬에 모으고 CI/실제 리뷰/사용자 병합을 후속 확인한다.
