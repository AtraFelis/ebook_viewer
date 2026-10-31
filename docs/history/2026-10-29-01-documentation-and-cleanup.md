# 프로젝트 문서 최신화와 재생성 파일 정리

2026-10-29 (Asia/Seoul). 사용자 요청: 현재 프로젝트 문서를 최신화하고 불필요한 파일을 정리한다. 앱 기능·의존성·서명 설정을 바꾸거나 기기 검사를 반복하는 작업은 아니다.

## 시작 상태와 Git

AGENTS → SESSION → CHECKLIST와 로드맵/책장·개인용 계획, Git 규칙을 읽고 현재 체크아웃을 확인했다. 시작 브랜치는 `codex/documentation-closeout`, HEAD `9c4df30`이며 변경은 SESSION/CHECKLIST/마지막 완료 이력과 사용자 디자인·버전의 5개다.

GitHub 연결 도구로 PR #34의 MERGED와 병합 `674c8d890a8b07ec2173dc2f8d75675fdf70955c`을 확인했다. `git fetch origin`은 최초 `.git/FETCH_HEAD` 쓰기 권한으로 실패했고 승인된 재실행은 성공했다. HEAD와 origin/main의 파일 차이가 없음을 확인한 뒤 `git switch -c codex/project-docs-cleanup origin/main`으로 같은 체크아웃에서 작업 브랜치를 만들었다. 기존 변경 5개를 유지했고 main 자체 이동·reset/clean/강제 push는 하지 않았다.

작업 전 tracked 295개 해시와 기존 dirty 5개의 바이트 사본/상태, stash 목록을 ignored `local-artifacts/project-maintenance-20261029`에 보관했다. 배포 APK·앱 APK/보고서/테스트 결과·루트 build의 보호 파일 85개도 해시를 기록했다. 키·비밀번호·개인 PDF는 열거나 복사하지 않았다. 사용자 `docs/design.md`, `gradle.properties`는 수정/커밋 대상에서 제외한다.

## 문서 변경

- README·문서 목차·SESSION/CHECKLIST·로드맵을 현재 완료/보류/검증 제한 중심으로 정리했다. 누적 중복 진행 문단 대신 기존 history의 실제 근거로 연결한다. 이전 내용은 Git 이력과 작업 전 로컬 사본에 보존된다.
- 제품/구조/개발 안내에 이미 구현한 여백·가로 잠금·색반전·책장 v6/표지·작은 도구를 반영했다. P2 완료와 TalkBack 후속 제외, 개인용 설치/키 백업 사용자 확인을 구분했다.
- 과거 계획의 맥락/수용 기준은 남기고 상단에 현재 완료 범위와 미검증 한계를 연결했다. 초기 클라우드 전환 문서를 현재 초기화 안내로 읽지 않도록 했다. 사용자 소유 디자인은 그대로 보존했다.
- 기기 검사 기본 기대 수 96개와 P3 당시 91개를 구분했다. 단위 84개·관련 기기 검사/CI 성공은 당시 실행이며 최종 전체 96개/다른 API/OOM 검증으로 확대하지 않았다.
- 개발/배포 버전의 저장소 기본과 사용자 로컬 값을 구분하고 아이콘 선택 대기 같은 낡은 문구를 정리했다. 파일 정리 기준을 `docs/maintenance.md`에 기록했다.

## 삭제한 항목과 보존

실행: `& ./local-artifacts/project-maintenance-20261029/clean-generated.ps1`.

진행 중 java/javaw/adb 프로세스가 없음을 확인했다. 고정된 대상들의 절대 경로가 이 프로젝트 안인지, 경로/하위에 재분석 지점이 없는지, 추적 파일이나 서명 키가 없는지 먼저 검사했다. 사전 목록을 `cleanup-inventory.json`에 기록하고 PowerShell `Remove-Item -LiteralPath`로 해당 항목만 삭제했다.

삭제 대상 9개: `.gradle`, `.kotlin`, `app/build/generated`, `app/build/intermediates`, `app/build/kotlin`, `app/build/kotlinToolingMetadata`, `app/build/tmp`, `scripts/__pycache__`, `searchprobe/build`. 추적 소스/Gradle 포함이 없고 빌드 잔재만 있던 searchprobe의 빈 상위 디렉터리도 제거했다.

결과: 파일 2,622개 / 142,740,375 bytes(136.13MiB) 삭제, 선택한 대상 모두 부재 확인. 파일 논리 크기 합계이며 디스크 실제 사용량 측정은 아니다. 다음 빌드는 중간 산출물을 다시 생성한다.

`app/build/outputs`, reports/test-results, 루트 build, SDK/JDK·Wrapper·도구/자료/로그·모든 기존 stash/백업·정식 APK를 보존했다. 소스/테스트·도구 스크립트·아이콘 원본·고지/ADR/실행 이력은 삭제하지 않았다. `local-artifacts` 전체나 전역 캐시는 정리하지 않았다.

## 검증과 미실행

문서 링크/공백, 비문서 추적 파일·사용자 두 파일·보호 산출물과 stash 보존 검사는 아래 최종 결과에서 기록한다. 로컬 문서 링크는 파일 존재만 검사하며 웹 접근이나 Markdown 제목 anchor 렌더링 성공은 별도다.

Windows `python`은 앱 실행 별칭을 시작하지 못해 사용하지 않았다. 문서 처리는 PowerShell로 진행했고 Python 설치/환경 변경은 하지 않았다. 첫 rg 검색의 Windows 와일드카드 경로 오류도 실패로 구분하며 올바른 glob 지정으로 다시 확인했다.

앱 코드 변경이 없으므로 앱 빌드/단위/Lint·기기/장시간/성능·개인용 서명 빌드는 미실행이다. 캐시 삭제를 앱 회귀 통과/새 환경 복원 성공으로 보고하지 않는다. 코드 LICENSE/공개 배포·다른 API/전체96개·검색/복사 등 기존 보류는 유지한다.

## 최종 로컬 검증 결과

실행: `& ./local-artifacts/project-maintenance-20261029/verify.ps1`.

- 문서 127개에서 상대 로컬 링크 317개 파일 존재 확인, 누락 0. 외부 URL/제목 anchor는 이번 검사 대상이 아니다.
- 시작 manifest와 비문서 추적 파일 170개 해시 동일. 사용자 디자인/로컬 버전 2개, 보호 APK/보고서 85개, 기존 stash 7개도 동일했다.
- 삭제 대상 9개 모두 부재 확인. `git diff --check -- '*.md' ':(exclude)docs/design.md'` 종료 0. 새 문서 2개도 링크 검사에 포함했다.
- 최초 공백 검사는 검사 호출에서 core.autocrlf=false를 강제해 CRLF를 차이/공백으로 오인했고 실패했다. 작업 문서의 줄 끝/실제 잔여 공백을 정리하고 저장소의 정상 Git 설정으로 다시 검사해 통과했다. 사용자 파일의 줄 끝/바이트는 바꾸지 않았다. LF→CRLF 안내는 오류와 구분했다.

앱 코드·빌드 설정·실행 스크립트·의존성·개인용 버전은 변경하지 않았다. 문서 정리와 로컬 캐시 삭제 결과만이며 앱 재빌드/기기 수용/새 서명 APK 생성 결과는 아니다. 이후 게시/리뷰 상태는 아래에 구분해 기록한다.

## 게시와 외부 검토

문서 34개를 e15629eab45c929f3839677a59703ee798521222로 커밋/일반 push하고 PR #35를 main 대상으로 게시·현재 채팅에 첨부했다. 문서 이외 파일은 포함되지 않았으며 원격 작업 브랜치 head와 일치했다. 게시 직후 보존/링크/공백 검사를 다시 실행해 같은 결과를 확인했다.

CodeRabbit 수동 요청6488610493 한 번을 보냈고 봇6488612097의 ‘Review triggered’ 접수를 확인했다. 자동 리뷰 제외 체크를 실제 완료 리뷰로 세지 않으며 현재 완료 결과는 대기다. Markdown 전용 Android checks는 워크플로 경로 필터 대상이다. 이 접수/게시 상태는 로컬 인수인계에 모아 기록하고 상태만의 추가 push는 하지 않는다. PR 병합은 사용자다.
