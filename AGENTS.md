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
| PDF | P0 PdfRenderer 승인. API 33 합성 문서·test provider 계측 완료, 다른 기기 미검증. 범위·제한은 ADR 0001/0007. 비탐색 파일은 취소/256MiB 상한의 익명 임시 복사 |
| 파일·데이터 | 로컬 우선, SAF. 플랫폼 AtomicFile. 읽기 기록 v2는 문서별 크기/간격을 추가하고 v1을 읽음(ADR 0006). 북마크/방향/배경색/번호 표시는 별도 v1(ADR 0002~0005). 추가 저장 라이브러리 없음 |
| 백엔드 | 기본 읽기에 계정·API 키·외부 서비스 불필요 |
| SDK·도구 | JDK 17(Temurin 17.0.20.1+1), compile/target 35, min 23, Build Tools 34.0.0. 로컬 빌드 통과 |

새 런타임 의존성 도입이나 승인된 스택 변경 전 목적·대안·라이선스·영향을 제시하고 사용자 승인을 받습니다. 제안과 확정 사항을 구분하고 결정은 ADR과 이 표에 반영합니다.

## 빌드·테스트

작업 디렉터리: `C:\Users\cotjs\workspace\ebook_viewer`. PowerShell에서 먼저 `. ./scripts/android-env.ps1` 실행.

| 목적 | Windows 명령 | 현재 상태 |
| --- | --- | --- |
| 빌드 | `./gradlew.bat :app:assembleDebug` | 로컬 통과 |
| 단위 테스트 | `./gradlew.bat :app:testDebugUnitTest` | 좌표·타일·기록 코덱 테스트 22개 통과 |
| Android Lint | `./gradlew.bat :app:lintDebug` | 오류/경고 0, 버전 권고는 정보 |
| 기기 테스트 | `./scripts/device-smoke.ps1` | API 33 계측 41개·앱 실행 통과. 프로세스 복원 2단계는 별도 이력, 실제 OOM 검증 아님 |

ktlint CLI 1.5.0: `./gradlew.bat ktlintCheck` 통과. `ktlintFormat`은 명시적으로만 실행합니다. CI도 같은 검사 명령을 사용하지만 원격 CI 실행은 별도 확인합니다. 의존성은 lockfile 및 SHA-256 verification-metadata로 고정하며 검사 시 `--dependency-verification strict`를 사용합니다.

현재 P2 검색 실험은 개인 대용량 PDF의 LOW_MEMORY 수용 실패로 철회했다. 앱/계측/기기 스크립트는 병합 main과 동일하며 검색은 제공하지 않는다. 복구 strict 검사·수명 계측 4개 통과와 재개 조건은 [장애 이력](docs/history/2026-10-18-08-p2-search-low-memory-withdrawal.md)을 따른다. 과거 실험의 합성/CI 통과를 현재 검색 안정성으로 취급하지 않는다.

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

PR 생성마다 CodeRabbit에 `@coderabbitai review`를 수동 요청하고 실제 접수/리뷰 상태를 확인합니다. 건너뜀 체크의 성공을 리뷰 완료로 보고하지 않습니다. 이후 리뷰는 코드·계약·현재 범위와 대조해 타당한 지적을 수정하고 필요한 검증 후 같은 PR 브랜치에 일반 push합니다. 수정 push 뒤 필요하면 새 head의 재검토를 한 번 요청하되 진행 중/동일 head 요청은 중복하지 않습니다. 채택하지 않은 지적은 근거와 미결을 기록합니다. 새 의존성 승인·사용자 변경 보존 규칙을 유지하며 PR 병합은 사용자가 합니다. 세부 절차는 [Git 규칙](docs/rule/git.md)을 따릅니다.

필요한 검증을 실행하고 통과·실패·건너뜀·미실행을 구분합니다. 변경의 위험을 확인하는 테스트를 추가하되 문서 변경에 의미 없는 앱 테스트를 만들지 않습니다. SESSION과 CHECKLIST를 갱신하고 실제 명령·결과·미결 문제는 docs/history에 기록합니다.
