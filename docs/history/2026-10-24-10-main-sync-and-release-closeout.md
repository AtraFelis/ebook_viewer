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

첫 시도에서는 후속 도구가 응답하지 않아 APK/문서 작업을 완료로 세지 않았다. 재시도에서는 기존 작업 적용 여부를 읽어 미적용을 확인한 뒤 진행했다. 이번 변경은 문서뿐이므로 앱 빌드/단위/기기·장시간/성능/OOM·다른 API 검사를 반복하지 않는다. 최종 검증은 문서 공백/링크와 사용자 파일 해시 보존을 확인한다.
