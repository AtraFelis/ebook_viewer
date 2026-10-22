# AGENTS.md — canonical contract

## 적용과 읽기 순서

사용자의 명시적 지시가 우선입니다. 저장소 내 기술·스타일·하드 제약의 기준은 이 문서입니다. docs/rule은 세부 규칙, SESSION.md는 상태, CHECKLIST.md는 검증 기준입니다. 문서가 충돌하면 이 계약에 맞춰 함께 수정합니다. 문서가 충돌 시, 이 계약의 내용을 우선합니다.

작업 시작 시 AGENTS → SESSION → CHECKLIST → 해당 계획을 읽고 Git 상태를 확인합니다. 기존 사용자 변경을 보존합니다. 클라우드 작업은 이미 격리되어 있으므로 기존 체크아웃을 사용하고 사용자 요청 없이 worktree를 만들지 않습니다.

## 기술 스택

| 영역 | 계약 및 상태 |
| --- | --- |
| 플랫폼·언어 | Android 네이티브 / Kotlin |
| 빌드 | Gradle Kotlin DSL / 검증된 Wrapper 8.9, AGP 8.7.2, Kotlin 2.1.20 |
| 앱 UI | 플랫폼 Activity + View/Canvas, P1 전체 화면/중앙 탭 도구/방향 설정. Compose 셸 최종 선택은 후속 비교 |
| PDF | P0 PdfRenderer 승인. API 33 합성 문서·test provider 계측 완료, 다른 기기 미검증. 본문 읽기는 플랫폼 PdfRenderer/PreV의 요청별 isolated process(ADR 0015). 범위·제한은 ADR 0001/0007/0015. 비탐색 파일은 취소/256MiB 상한의 익명 임시 복사 |
| 파일·데이터 | 로컬 우선, SAF. 플랫폼 AtomicFile. 읽기 기록 v3는 문서별 크기/간격·가로 이동 잠금을 저장하고 v1/v2를 읽음(ADR 0006/0016). 배경색 v2는 자동/수동 선택을 추가하고 v1 수동색을 읽음(ADR 0004/0013). 테마/읽기 밝기/화면 유지는 별도 v1(ADR 0014). 북마크/방향/번호 표시는 별도 v1(ADR 0002/0003/0005). 추가 저장 라이브러리 없음 |
| 백엔드 | 기본 읽기에 계정·API 키·외부 서비스 불필요 |
| SDK·도구 | JDK 17(Temurin 17.0.20.1+1), compile/target 35, min 23, Build Tools 34.0.0. 로컬 빌드 통과 |
| 개인용 배포 준비 | ADR0017: debug/profile ID dev.example.ebookviewer.p0 유지, release dev.example.ebookviewer 별도 설치·새 기록. versionCode/Name은 gradle.properties. 명시적 로컬 서명만 사용하고 실제 개인용 키/설치·프로젝트 코드 LICENSE는 아직 미완료/보류 |

새 런타임 의존성 도입이나 승인된 스택 변경 전 목적·대안·라이선스·영향을 제시하고 사용자 승인을 받습니다. 제안과 확정 사항을 구분하고 결정은 ADR과 이 표에 반영합니다.

## 빌드·테스트

작업 디렉터리: `C:\Users\cotjs\workspace\ebook_viewer`. PowerShell에서 먼저 `. ./scripts/android-env.ps1` 실행.

| 목적 | Windows 명령 | 현재 상태 |
| --- | --- | --- |
| 빌드 | `./gradlew.bat :app:assembleDebug` | 로컬 통과 |
| 단위 테스트 | `./gradlew.bat :app:testDebugUnitTest` | 좌표·타일·기록v1/v2→v3·가시 면적·페이지 수명·자동 배경색/표시 설정·본문 예산·호환·슬라이더·첫 안내 상태 53개 통과 |
| Android Lint | `./gradlew.bat :app:lintDebug` | 오류/경고 0, 버전 권고는 정보 |
| 기기 테스트 | `./scripts/device-smoke.ps1` | API33 기본91개 고유 검사를 분할 보완한 뒤 번호 실제 배치 수정 앱의 관련11개/실패0 확인. 수정 앱의 전체91개 재실행 아님. 최초81통과/3실패/7미완료와 미재현 회전·포커스 이력은 [P3 최종 기록](docs/history/2026-10-22-01-p3-final-verification.md)에 보존. 전체 제한900초/개별 선택 지원, 실제 OOM/다른 API 검증 아님 |

ktlint CLI 1.5.0: `./gradlew.bat ktlintCheck` 통과. `ktlintFormat`은 명시적으로만 실행합니다. CI도 같은 검사 명령을 사용하지만 원격 CI 실행은 별도 확인합니다. 의존성은 lockfile 및 SHA-256 verification-metadata로 고정하며 검사 시 `--dependency-verification strict`를 사용합니다.

현재 P2는 사용자 결정으로 문서 검색·목차·비밀번호 PDF 읽기를 필수에서 제외한다. 검색은 후속 보류/비활성화이며 진단 코드와 원인 미확정 이력은 Draft PR #12에 보존한다. 선명도 개선은 main 기반 독립 브랜치에서 기존 PdfRenderer로 진행한다. 2026-10-19 사용자 결정으로 선택/복사 UI도 후속으로 미룬다. 현재 페이지 본문 읽기·TalkBack/큰 글꼴과 해당 native 안전 검증·최종 통합 검증은 유지한다. [현재 계획](docs/plan/p2-scope-and-completion.md)을 따른다.

2026-10-20 사용자 범위 변경: 이후 개발과 디자인 완료 조건에서 TalkBack 전용 기능·음성/초점·제스처 검증을 제외한다. 페이지 슬라이더에도 TalkBack 전용 조절을 추가하지 않는다. 기존 구현과 과거 P2 검증 이력은 보존하며, 큰 글꼴·일반 터치 조작·현재 페이지 본문 읽기는 유지한다.

## 코드 스타일

- Kotlin 공식 규약, 4칸 들여쓰기, 파일 끝 개행.
- 클래스/인터페이스 PascalCase, 함수/프로퍼티 camelCase, 상수 UPPER_SNAKE_CASE, 패키지 소문자.
- applicationId와 기본 패키지는 초기화 때 결정하며 사용자 도메인을 임의로 만들지 않음.
- nullable 상태, 오류, 자원 소유권을 명시. 근거 없는 `!!`, 예외 무시, 전역 mutable 상태 지양.
- PDF point·문서 좌표·화면 px·UI dp와 페이지 인덱스/표시 번호를 구분.
- 사용자 문자열은 리소스로 분리. 주석은 이유와 제약을 설명.

## 하드 제약

1. 페이지 경계나 회전 때문에 사용자 줌을 초기화하지 않음. 기본 읽기에 페이지 스냅을 도입하지 않음.
2. UI 스레드에서 PDF 렌더링·파일 I/O를 하지 않음. 전체 페이지 고해상도 비트맵 선로딩 금지.
3. 가시 타일 우선, 취소 가능한 작업, 바이트 단위 캐시 상한, 문서 종료 시 자원 해제.
4. PDF·추출 텍스트·비밀번호·키를 로그/Git/외부 서비스에 노출하지 않음.
5. TLS·서명·체크섬 검증 우회, 테스트 무력화, 미실행 테스트 통과 보고 금지.
6. 사용자 변경 덮어쓰기, 승인 없는 reset/clean·강제 push·릴리스 금지.
7. 빌드 성공만으로 실기기 스크롤 성능이나 환경 복원 완료를 선언하지 않음.

## 완료와 인수인계

CodeRabbit은 구현과 필요한 검증이 어느 정도 마무리된 시점에 `@coderabbitai review`를 수동 요청하고 실제 접수/리뷰 상태를 확인합니다. Draft에서도 필요하면 호출할 수 있지만, PR 생성·작은 수정·상태 문서 push마다 호출하지 않습니다. 관련 변경을 모으고 검증한 최신 head에 요청하며 진행 중/동일 head의 중복 요청을 피합니다. 리뷰 중 불필요한 상태 기록 push로 head를 바꾸지 않고 결과 기록을 모아 게시합니다. 건너뜀 체크의 성공을 리뷰 완료로 보고하지 않습니다. 이후 리뷰는 코드·계약·현재 범위와 대조해 타당한 지적을 수정하고 필요한 검증 후 같은 PR 브랜치에 일반 push합니다. 수정 push 뒤 재검토도 의미 있는 수정이 모인 시점에 필요할 때만 한 번 요청합니다. 채택하지 않은 지적은 근거와 미결을 기록합니다. 새 의존성 승인·사용자 변경 보존 규칙을 유지하며 PR 병합은 사용자가 합니다. 세부 절차는 [Git 규칙](docs/rule/git.md)을 따릅니다.

필요한 검증을 실행하고 통과·실패·건너뜀·미실행을 구분합니다. 변경의 위험을 확인하는 테스트를 추가하되 문서 변경에 의미 없는 앱 테스트를 만들지 않습니다. SESSION과 CHECKLIST를 갱신하고 실제 명령·결과·미결 문제는 docs/history에 기록합니다.
