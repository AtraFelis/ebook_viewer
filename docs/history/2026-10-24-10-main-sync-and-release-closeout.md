# main 동기화와 개인용 정식판 완료 기록

2026-10-24. 사용자가 로컬 저장소 정리와 완료 문서 최신화를 요청했다. 기존 체크아웃과 사용자 변경을 보존하며 앱 코드/의존성·개인용 키·원본 PDF를 바꾸지 않았다.

## Git 동기화와 보존

- git fetch origin 뒤 PR #33 MERGED/병합69931e98eb5c9cf481f25d4536e93c1a96c4e483을 확인했다. 당시 로컬 main은46eaa41이며 원격까지50커밋의 fast-forward 관계, 작업 head2369ce0도 원격 main의 조상이다. divergence는 없었다.
- 변경5개(CHECKLIST/SESSION/사용자 docs/design.md·gradle.properties/마지막 리뷰 이력)를 ignored local-artifacts/main-sync-backup-20261024-143003와 stash168afee63f68ca9a7795ffcf35c291ff5aaea8ad에 보존했다. git switch main, git pull --ff-only origin main, git stash apply 뒤5개 모두 바이트/SHA-256 동일 복원했고 원격 비교0/0을 확인했다.
- 기존 stash20fbf558655599c0e7af93c1855c30789dafdb91과 새 보존 stash를 모두 남겼다. reset/clean/강제 push/브랜치 삭제는 없다. 사용자 디자인/로컬0.1.2-code4 속성은 이후 문서 수정에서도 건드리지 않고 커밋에서 제외한다.

## 정식 APK의 실제 확인

- 사용자 로컬 ebook-viewer-0.1.2-vc4.apk에 Build Tools34.0.0의 apksigner verify --verbose를 실행해 종료0/Verifies와 v1/v2 성공을 확인했다. aapt2 dump badging은 ID dev.example.ebookviewer, versionName0.1.2, versionCode4이며 debuggable 표시가 없다.
- 이전 ebook-viewer-0.1.1-vc3.apk와 각각 apksigner verify --print-certs로 공개 인증서 SHA-256만 비교해 같은 인증서를 확인했다. 개인용 .p12/비밀번호는 읽지 않았고 빌드/재서명·설치를 실행하지 않았다.
- 최신 APK SHA-256은30144AB5D382C76724D59B4A58EB3CBC83F72DC76CCCB85D0D424E102A6C121E이다. APK/서명 키는 Git에 포함하지 않는다.
- 도구의 META-INF/app-metadata.properties 및 version-control-info.textproto 관련 경고2개는 그대로 관측했다. 서명 검증은 성공했고 이번 문서 범위에서 리소스 제거/검증 우회를 하지 않았다.
- 사용자가 ‘설치했고 키 백업도 해놨어’로 현재 정식판 설치·키 백업 완료를 확인했다. 이는 사용자 확인이며 에이전트의 기기/백업 파일 직접 검사나 전체 기능 재검증은 아니다. 코드 LICENSE/스토어 공개 배포는 계속 보류다.

## 문서와 검증 범위

AGENTS/SESSION/CHECKLIST·배포 준비/개인용 안내·계획·ADR0017·문서 목차/로드맵에 현재 완료 상태와 출처를 반영했다. 사용자 로컬 버전과 저장소 기본값을 구분하고 다음 버전은code를 증가/기존 키 재사용, 같은 버전 출력 충돌 보호와 SHA-256 보관 의미를 안내했다. 과거 검사/실패 이력은 보존하며 당시 대기 상태를 현재 미완료로 해석하지 않도록 최신 요약을 앞에 둔다.

첫 시도에서는 후속 도구가 응답하지 않아 APK/문서 작업을 완료로 세지 않았다. 재시도에서는 기존 작업 적용 여부를 읽어 미적용을 확인한 뒤 진행했다. 이번 변경은 문서뿐이므로 앱 빌드/단위/기기·장시간/성능/OOM·다른 API 검사를 반복하지 않는다. 실제 최종 검증 명령/결과는 아래에 구분한다.

## 최종 문서 검증 — PR #34 리뷰 보완

CodeRabbit 지적4366261550을 채택했다. 작업 위치는 `C:\Users\cotjs\workspace\ebook_viewer`, PowerShell에서 다음을 실행해 문서11개를 재확인했다. 사용자 디자인 파일은 검사/커밋 범위에서 제외한다.

**통과 — 공백 검사:** 다음 명령 종료0, 공백 오류 없음.

```powershell
git diff origin/main --check -- '*.md' ':(exclude)docs/design.md'
```

**통과 — 로컬 문서 링크:** 다음 스크립트의 `DOCUMENT_LINKS_OK (11 files)` 출력, 존재하지 않는 로컬 링크0개. 외부 URL 접속 검사는 이번 범위에서 미실행이다.

```powershell
$docs = @(git diff --name-only origin/main -- '*.md' ':(exclude)docs/design.md')
$missing = @()
foreach ($doc in $docs) {
    $source = [IO.File]::ReadAllText((Join-Path $PWD $doc))
    foreach ($match in [regex]::Matches($source, '\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value
        if ($target -match '^(https?://|#|mailto:|codex:)') { continue }
        $target = ($target -split '#')[0]
        $base = Split-Path (Join-Path $PWD $doc) -Parent
        if ($target -and -not (Test-Path -LiteralPath (Join-Path $base $target))) {
            $missing += $doc + ': ' + $target
        }
    }
}
if ($missing.Count) { throw ($missing -join "`n") }
"DOCUMENT_LINKS_OK ($($docs.Count) files)"
```

**통과 — 사용자 파일 보존:** 다음 명령의 `USER_FILES_UNCHANGED (2 files)` 출력. 동기화 전 백업의 SHA-256과 사용자 디자인/속성 파일이 각각 같았다. 키/비밀번호/개인 PDF는 대상에 넣지 않았다.

```powershell
$backupRoot = Get-Content local-artifacts/main-sync-backup-path.txt
$manifest = Get-Content (Join-Path $backupRoot 'manifest.json') -Raw | ConvertFrom-Json
$userFiles = @($manifest | Where-Object { $_.path -in @('docs/design.md', 'gradle.properties') })
if ($userFiles.Count -ne 2) { throw 'Incomplete preservation manifest' }
foreach ($entry in $userFiles) {
    if ((Get-FileHash -LiteralPath $entry.path).Hash -ne $entry.sha256) {
        throw ('User file changed: ' + $entry.path)
    }
}
'USER_FILES_UNCHANGED (2 files)'
```

**통과 — Git 보존:** `git rev-list --left-right --count main...origin/main` 출력0/0. `git cat-file -t 168afee63f68ca9a7795ffcf35c291ff5aaea8ad`와 `git cat-file -t 20fbf558655599c0e7af93c1855c30789dafdb91`는 각각commit을 반환했다. 새/기존 stash를 삭제하지 않았다.

**실패/중단:** 첫 시도의 후속 도구 응답 지연으로 중단된 검증은 성공으로 세지 않았다. 재시도/이번 문서 재확인의 위 검사에는 실패가 없었다.

**건너뜀:** Markdown 전용 변경은 CI 경로 필터로 Android checks가 실행되지 않았다. **미실행:** 앱 빌드/단위/기기·장시간/성능/OOM/다른 API 재검증과 키/백업 파일 직접 검사. 기존 앱 검사 결과와 사용자 설치/백업 확인을 이 검사에서 실행한 것으로 보고하지 않는다.

**미결/범위 밖:** APK 서명 도구의 META-INF 경고2개는 보존하며 이번 문서 수정에서 해결하지 않았다. 프로젝트 코드 LICENSE/공개 배포는 사용자 결정으로 보류다. PR #34 병합은 사용자에게 남아 있다. 이번 보완은 검증 증거를 추가하는 문서 변경이며 앱 코드/설정은 바꾸지 않았다.

## 실제 게시

문서11개를2a977367341273480b7e18caf307099fc560ab03으로 같은 체크아웃의 codex/documentation-closeout에 커밋/일반 push하고 [PR #34](https://github.com/AtraFelis/ebook_viewer/pull/34)을 게시했다. 링크/공백 검사와 사용자 디자인/버전의 백업 대비 해시 동일 검사를 통과했다. 로컬 main은 origin/main69931e9와0/0이고 현재는 문서 브랜치다. 사용자 변경2개는 커밋하지 않았다.

Markdown 전용이므로 경로 필터에 따라 Android checks는 실행되지 않았고 이를 앱 검사 통과로 세지 않는다. CodeRabbit 자동 제외 후 수동6380886160 한 번에 봇6380887036의 Review triggered 접수·PENDING을 확인했다. 실제 완료 리뷰는 아직 아니며 결과 기록은 로컬에서 모으고 상태만의 추가 push는 하지 않는다. PR 병합은 사용자다.

## 리뷰 보완 게시와 최종 확인

실제 완료 리뷰의 지적4366261550을 채택해 위 최종 검증 명령/결과를 보완하고9c4df3058abb701635b804afe6a7ef19a1a2847d으로 같은 브랜치에 일반 push했다. 명시한 검사들을 재실행해 문서11개 링크/공백·사용자 파일2개 해시 일치·main 원격 비교0/0·stash2개 보존을 통과했다. 사용자 디자인/속성은 그대로 보존·커밋 제외다.

답변4367494396에 수정/검증을 설명했고 봇4367495720이9c4df30과 현재 문서를 직접 확인해 검증 기록 누락 해소를 확인했다. 이는 문서 내용 확인이며 봇의 로컬/앱 검사 재실행은 아니다. 최종 head9c4df30·미해결 스레드0·MERGEABLE/CLEAN이다. Android checks는 문서 경로 필터로 미실행이고 CodeRabbit 자동 제외 SUCCESS를 전체 재리뷰로 세지 않는다. 작은 기록 보완에 전체 수동 리뷰/앱 검사를 반복하지 않았으며 사용자 병합만 남아 있다. 이 최종 상태 기록은 로컬에 보존하고 상태만의 추가 push는 하지 않는다.
