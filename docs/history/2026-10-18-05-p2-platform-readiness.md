# P2 착수 — 기기 SDK 확장과 합성 기능/이미지 렌더 검증

2026-10-18 (Asia/Seoul). 경로 `C:\Users\cotjs\workspace\ebook_viewer`.

후속 범위 변경: 사용자가 목차·비밀번호 PDF 읽기를 P2 필수에서 제외했다. 아래는 실행/검토 당시의 결과를 보존한 이력이며 최신 착수/완료 조건은 [범위 조정](2026-10-18-06-p2-scope-reduction.md)을 따른다. 기존 목차 미결을 현재 P2의 차단 조건으로 삼지 않는다.

## 기준·보존

사용자가 P2 진행을 요청했다. AGENTS→SESSION→CHECKLIST→P2 계획을 읽었다. main `0aea06e`에서 `git fetch origin`/좌우 차이 `0 0`을 확인하고 현재 체크아웃에서 `codex/p2-readiness`를 만들었다. 기존 디자인 SESSION/CHECKLIST·history 01/02와 앞선 P2 계획/관찰 문서는 보존했다. reset/clean/강제 push/worktree/의존성 추가 없음. 새 코드는 자체 점검 도구이며 앱 소스/기록/설정은 바꾸지 않았다.

## 기기 공개 API와 실제 기능

승인 기기 한 대/API 33 확인. getprop의 s/t 확장 값은 비어 있어 지원 없음으로 판단하지 않고 자체 Java probe를 만들었다. `./scripts/probe-pdf-capabilities.ps1`은 설치 JDK 17/SDK 35의 javac→Build Tools 34.0.0 D8→기기 shell app_process로 실행한다. 기기/문서 식별자 출력 없이 SDK 공개 메서드만 인벤토리한다. 실제 S/T 확장은 각각 **23**, 기본 PdfRenderer와 달리 PreV에는 텍스트/검색/선택/비밀번호 API가 있었다. [ADR 0009](../adr/0009-p2-platform-pdf-capabilities.md) 참조.

기존 bundled Python `C:\Users\cotjs\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`로 `scripts/generate-p2-fixtures.py` 실행: 합성 문서 7개 생성. Pillow 12.3.0/pypdf 6.10.0/reportlab 4.4.9/cryptography 50.0.2이며 앱 런타임 의존성이 아니다. 개인 PDF/첨부 화면은 사용하지 않았고 기본 텍스트+계층 목차, AES-128/256, 동일 패턴의 그레이/RGB/품질·픽셀 대조 자료를 생성했다.

`./scripts/probe-pdf-capabilities.ps1 -FeatureChecks` **통과**: 텍스트 추출·검색 2건/없음·선택 내용·렌더, AES-128/256에서 암호 없음/오류 거부와 올바른 암호 검색/렌더. 클래스 존재만으로 판단하지 않았으며 합성 샘플의 실제 실행이다. 단일 shell worker이고 설치 리더/개인 자료는 열지 않았다. 앱 UI/FD/설정 수명·한글·다중 페이지/검색 핸들 수용 완료는 아니다.

## 이미지 실험과 한계

`./scripts/probe-pdf-capabilities.ps1 -ImageRenderChecks`를 실행해 24개 표본/각 4단계 렌더와 비어 있지 않은 픽셀을 확인했다. 기본 renderer의 median preview/firstTile(ms)는 그레이 q85 **19.58/13.03**, RGB q85 **46.89/37.15**, RGB q50 **47.21/35.77**, 픽셀 절반 그레이 **7.80/5.55**였다. 확장 renderer의 firstTile 중앙값은 같은 순서로 15.15/35.98/36.24/8.12ms다. 표본 수 3의 중앙값이며 p95가 아니다.

같은 패턴·픽셀 수의 RGB/그레이 차이가 나타났고 RGB 압축 용량을 줄여도 native render 시간이 비슷했다. 이 합성 조건에서 색상 처리/디코딩 경로를 추가 조사할 근거일 뿐 사용자 PDF의 병목·재디코딩 여부나 용량과 지연의 보편적 관계를 확정하지 않는다. 기본→확장 고정 순서, OS/decoder 캐시·온도 미통제 한계를 기록했다. 앱 worker 큐/프레임/실제 draw/선명화/캐시/PSS를 측정한 기준선으로 보고하지 않는다.

보고서와 샘플/DEX/raw log는 `local-artifacts/p2-platform-probe`, `local-artifacts/p2-fixtures`에 남기고 Git에는 넣지 않는다. 기기에는 고유 임시 DEX/합성 샘플만 전송 후 개별 정리했다. 설치/앱 시작/force-stop/개인 기록 접근은 하지 않았다.

## 외부 후보 조사와 다음 단계

Context7 resolve `/johngray1965/pdfiumandroidkt`/query를 사용했으나 버전 혼재로 원문/실제 배포를 대조했다. Maven metadata/POM/module/AAR를 정적 조사했고 두 AAR는 공식 SHA-256 일치: 1.0.32 `561759462d8071ad16d54f0b83ebdc4daa6e63cd8b0833a6516dcebeaa848f3a`, 1.0.35 `862ed337d6b52485fefba9ced9fe7fdb800d41fb300d8c8ebb03d8bea64d72f0`. 바이너리를 실행하거나 Gradle 의존성으로 추가하지 않았다.

1.0.32 AAR 실제 min 23(설명 문구는 24), 1.0.35 min 24/Guava, 2.0.3 stdlib 2.4.10 선언을 구분했다. 공식 `.module` 응답이 byte[]인 최초 저장은 문자 코드 문자열로 변환돼 파싱에 쓰지 않고 `Invoke-WebRequest -OutFile`로 다시 받아 실제 JSON의 runtime dependency를 확인했다. PDFBox 계열 POM의 BC 전이 비용도 확인했으며 어떤 후보도 선정/승인 처리하지 않았다. 라이선스 고지/빌드 출처·업데이트/실제 APK 크기는 ADR의 미결이다.

확장 플랫폼을 기존 worker/좌표/캐시 계약에 통합한 뒤 앱 컨텍스트 계측과 검색/선택/비밀번호 UI를 진행하는 방향이다. 목차는 여전히 필수 미결로 보조 구성안이 필요하다. 실제 앱 선명화 지표/합격선과 통합 성능 측정도 남아 있어 P2-0 전체 완료나 P2 완료를 선언하지 않는다. 새 런타임 의존성/스택 변경이 필요한 때 구체적인 구성 승인 후 진행한다.

## 로컬 검사와 PR

점검 도구의 실제 실행·문서/스크립트 검사와 원격 PR/리뷰 결과를 구분해 후속 기록한다. 문서만이 아닌 script 변경이므로 원격 Android CI 실행 여부도 확인한다. ktlintFormat/릴리스/병합은 하지 않는다.

최종 인벤토리 재실행에서 API 33/S·T 확장 23/클래스 5개와 probeId/완료 시각을 확인했다. standalone 도구는 API 30 미만을 사전 거부하고 앱 min 23을 바꾸지 않는다. PowerShell Parser 오류 0, Python AST/manifest 7개/합성 페이지·2단계 목차 확인 통과, `git diff --check`와 앱/Gradle/의존성 소스 불변 확인 통과. 별도 로컬 앱 빌드/전체 41개 계측을 반복하지 않았고 기존 통과 수치를 새 P2 통과로 재사용하지 않는다.

## PR 게시·실제 CI와 리뷰

P2 변경 16개만 `a387ad4`로 커밋/일반 push하고 main 대상 [PR #11](https://github.com/AtraFelis/ebook_viewer/pull/11)을 등록/대화에 연결했다. SESSION/CHECKLIST는 index에서 디자인 부분만 제외해 staged 버전을 만들었고 작업 파일을 덮어쓰지 않았다. 남은 작업 트리 diff는 디자인 두 문단/체크 목록과 history 01/02뿐이다. 기존 두 디자인 history의 SHA-256 불변도 확인했다. staged Markdown 내부 파일 링크 116개/누락 0, 공백/범위 검사 통과. 앱/Gradle 소스는 main과 같다.

같은 head의 [pull_request Windows CI 39099974986](https://github.com/AtraFelis/ebook_viewer/actions/runs/39099974986)와 [push CI 39099946435](https://github.com/AtraFelis/ebook_viewer/actions/runs/39099946435)가 completed/success다. 도구 설치·Wrapper 검증·strict 앱/테스트 APK 빌드·단위/Lint/ktlint의 원격 결과이며 연결 기기 probe는 위 로컬 실행과 구분한다.

[CodeRabbit 수동 요청](https://github.com/AtraFelis/ebook_viewer/pull/11#issuecomment-6246012788)을 한 번 등록했고 [Review triggered 응답](https://github.com/AtraFelis/ebook_viewer/pull/11#issuecomment-6246013509) 후 [실제 리뷰](https://github.com/AtraFelis/ebook_viewer/pull/11#pullrequestreview-5561567427)가 head a387ad4의 16개 파일을 검토했다. 지적은 [로드맵 문구 1건](https://github.com/AtraFelis/ebook_viewer/pull/11#discussion_r4305846761): ‘구현/기기 측정 미실행’이 이미 완료한 플랫폼 probe/24개 격리 표본까지 부정하므로 앱 통합/최종 화면 성능의 미완료와 구분하라는 내용이었다. 현재 결과와 대조해 타당하므로 문구를 수정했다. 코드 동작 지적은 없었다. 리뷰의 CLI/Autofix 제안은 실행하지 않았고 이후 포함 리뷰 0건 한도 안내와 단순 문서 수정 범위를 고려해 중복 요청하지 않는다.

후속 변경은 리뷰 문구/실제 결과 기록뿐이며 검증한 도구/앱 소스를 바꾸지 않는다. 최종 문서 head의 checks는 PR에서 별도 확인하고 새 앱 CI 실행으로 보고하지 않는다. PR 병합은 사용자가 한다.
