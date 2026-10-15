# Windows 로컬 전환과 재개 프롬프트

작성: 2026-10-16 (Asia/Seoul).

## 전환 범위

GitHub 저장소: https://github.com/AtraFelis/ebook_viewer

로컬 경로: `C:\Users\cotjs\workspace\ebook_viewer`.

이번 전환은 문서와 P0 조사 결과의 이동이다. 앱·Wrapper·SDK 설치나 빌드 성공 상태를 이동하는 것이 아니다. 클라우드의 `/tmp/pdf-p0` 원본 다운로드는 Git에 포함하지 않았고, 근거 URL과 결과는 ADR에 기록했다. SDK·JDK·환경 변수·MCP 설정·인증은 Git pull로 복원되지 않는다. 로컬 접근과 기기 연결은 아직 확인하지 않았다.

사용자는 조사 문서의 커밋·push와 로컬 전환을 승인했다. ADR의 런타임 의존성 구성은 아직 승인하지 않았다. Context7 MCP는 이전 조사에서 사용하지 않았다. 로컬에서 도구 가용성을 확인하고 사용할 수 있으면 추가 검증한다. 사용할 수 없으면 그 사실과 대체 출처를 명시한다.

## 로컬 동기화

PowerShell에서 기존 폴더를 열고 먼저 아래 읽기 명령으로 상태를 확인한다.

```powershell
Set-Location 'C:\Users\cotjs\workspace\ebook_viewer'
git status --short
git branch -vv
git remote -v
git fetch origin
git log -5 --oneline --all
```

원격 URL을 확인하고, 로컬 변경이 없으며 main의 fast-forward가 가능한 경우에만 다음을 실행한다.

```powershell
git switch main
git pull --ff-only origin main
git status --short
git rev-parse HEAD
git rev-parse origin/main
```

브랜치가 다르거나 로컬 변경/divergence가 있으면 reset/clean/강제 checkout을 하지 않고 보존 후 상태에 맞게 통합한다. main이 없다면 원격 브랜치를 확인한 뒤 추적 브랜치를 만든다. 기존 폴더가 없을 때만 원격 저장소를 해당 경로로 clone한다. 마지막 두 SHA의 일치와 작업 트리 상태로 동기화를 확인한다.

## 로컬 도구 점검과 다음 작업

1. AGENTS → SESSION → CHECKLIST → P0 ADR → roadmap 및 관련 규칙을 읽는다. AGENTS의 클라우드 경로 대신 실제 Windows 체크아웃에서 명령을 실행한다.
2. `java -version`, `javac -version`, PowerShell `Get-Command java,javac,adb,sdkmanager -ErrorAction SilentlyContinue`로 존재를 확인한다. Android Studio/SDK 위치와 설치 패키지는 비밀 값 출력 없이 확인한다. 클라우드 Java 상태를 로컬 상태로 간주하지 않는다.
3. adb가 있으면 `adb devices -l`로 연결/승인 상태를 확인한다. 기기 식별자는 공개 문서에 복사하지 않는다. 실기기와 에뮬레이터 측정을 구분한다.
4. Context7 도구를 검색하고 사용 가능 시 정확한 라이브러리/버전 문서를 조회한다. AGP 8.7.2·Gradle 8.9·Kotlin 2.1.20·JDK 17·SDK 35 조합을 공식 표와 교차 확인한다. API 35 PdfRenderer 확장은 공식 SDK 서명과 대조한다. 조회 결과가 최신판만 설명하면 해당 후보 버전 근거로 단정하지 않는다.
5. ADR의 미확인 항목을 해소하고 목적·대안·라이선스·영향이 담긴 구성안을 승인받은 후 의존성을 도입한다. 버전 변경 시 ADR에 반영한다.
6. 검증된 Wrapper/앱/검사/CI 초기화와 타일 프로토타입을 진행한다. 실제 실행한 결과만 SESSION/CHECKLIST/history에 기록한다.

## 로컬 Codex 채팅에 붙여 넣을 프롬프트

```text
Android PDF 뷰어 작업을 클라우드에서 이 Windows 로컬 환경으로 옮겨 이어서 진행해 줘.
저장소 경로는 C:\Users\cotjs\workspace\ebook_viewer이고 원격은 https://github.com/AtraFelis/ebook_viewer야.

먼저 로컬 변경·브랜치·원격을 확인하고 사용자 변경을 보존해 줘. git fetch origin 후 안전한 경우 main을 git pull --ff-only origin main으로 동기화해 줘. 충돌이나 divergence가 있으면 reset/clean/강제 push하지 말고 진단해 줘. 현재 체크아웃을 사용해 줘.

AGENTS.md → SESSION.md → CHECKLIST.md → docs/adr/0001-p0-toolchain-and-renderer.md 순서로 읽고, docs/local-handoff.md와 관련 계획·규칙·이력도 확인해 줘. 클라우드의 /workspace 경로 대신 실제 로컬 경로를 사용해 줘.

현재는 P0 조사 문서만 있고 앱·Gradle Wrapper·SDK 초기화와 기기 성능 검증은 아직 안 했어. Kotlin 2.1.20 / AGP 8.7.2 / Gradle 8.9 / JDK 17 / compile·target 35 / min 23 및 P0 PdfRenderer+View/Canvas는 제안이지 확정이 아니야. Compose 셸 최종 선택은 남아 있어. 이전 조사에서 Context7 MCP를 사용하지 않았고, AGP 공식 호환 표와 API 35 공식 SDK 대조도 미완료야.

로컬 JDK/컴파일러/SDK/Android Studio/adb·기기 상태를 직접 점검해 줘. Context7 MCP server를 사용할 수 있는지 도구를 검색하고, 가능하면 버전을 특정해 호환성·PdfRenderer API를 추가 검증해 줘. 사용 불가나 자료 부족이면 명시하고 공식 문서/SDK로 교차 확인해 줘. 이전 조사 결과를 빌드 성공이나 실기기 성능 검증으로 취급하지 마.

P0 기술 검증을 계속 진행하되, 새 런타임 의존성 도입 전에는 AGENTS의 승인 규칙대로 구체적인 구성안을 제시해 줘. 이번 GitHub push 승인은 의존성 구성 승인과 별개야. 승인에 의존하지 않는 환경 점검·문서 검증은 먼저 끝내 줘. 결과와 다음 행동을 SESSION.md, CHECKLIST.md, docs/history에 기록해 줘.
```
