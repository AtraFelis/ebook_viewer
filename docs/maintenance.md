# 프로젝트 파일 정리 기준

현재 체크아웃에서 사용자 변경과 재현 근거를 보존하면서 재생성 가능한 파일만 정리한다. 저장소 전체 `git clean`이나 출처가 불분명한 파일의 일괄 삭제는 사용하지 않는다.

## 재생성 가능한 항목

빌드/검사/설치 작업이 끝나 관련 프로세스가 쓰지 않을 때 다음 경로만 확인해 지울 수 있다. 삭제 전 절대 경로가 프로젝트 안인지와 링크/재분석 지점이 없는지 확인한다.

- `.gradle/`, `.kotlin/`: 이 체크아웃의 빌드 캐시/세션. 사용자 전역 Gradle 캐시는 별개다.
- `app/build/intermediates/`, `app/build/generated/`, `app/build/kotlin/`, `app/build/kotlinToolingMetadata/`, `app/build/tmp/`: 컴파일 중간 산출물. 다음 빌드는 다시 생성하므로 더 오래 걸릴 수 있다.
- `scripts/__pycache__/`: Python 바이트코드 캐시.
- 더 이상 Gradle에 포함되지 않고 추적 소스도 없는 `searchprobe/build/`: 과거 진단 모듈의 로컬 빌드 잔재. 과거 검색 진단 소스/의도는 Draft PR #12와 이력에 보존한다.

삭제 전 목록/개수/용량을 정하고 삭제 후 해당 경로만 사라졌는지 확인한다. 잠금/권한 오류는 실패로 기록하며 다른 폴더/프로세스를 강제로 정리하지 않는다.

## 남겨 둘 항목

- 사용자 수정 `docs/design.md`, `gradle.properties`, `local.properties`와 Git 변경/stash/백업.
- 서명 키/비밀번호, `local-artifacts/releases/`의 정식 APK와 해시. 기존 APK를 지워 동일 버전 충돌 검사를 우회하지 않는다.
- `local-artifacts/`의 SDK/JDK·도구 다운로드/체크섬·합성 자료·측정/빌드/리뷰 로그·스크린샷/백업. 오래됐다는 이유만으로 전체 폴더를 삭제하지 않는다.
- `app/build/outputs/`, `app/build/reports/`, `app/build/test-results/`, 루트 `build/`의 기존 검사 근거. 이들은 빌드로 갱신될 수 있으므로 장기 보존할 결과는 별도 산출물로 관리한다.
- Gradle Wrapper JAR·버전 카탈로그·lockfile/verification-metadata, 소스/테스트·스크립트, 선택 아이콘 원본/고지·라이선스 자산, ADR/실행 이력.

문서는 현재 안내(README/SESSION/기획), 계획의 당시 수용 기준(plan), 실제 결과(history)로 역할을 나눈다. 현재 상태 요약을 중복 덧붙이지 않고 기존 내용을 갱신한다. 중복 상태 문단을 줄여도 실패/미실행·검증 한계는 history에서 보존한다. 사용자 소유 디자인은 별도 요청 없이 덮어쓰지 않는다.

이번 실제 정리의 경로·용량·검사 결과는 [2026-10-29 이력](history/2026-10-29-01-documentation-and-cleanup.md)에 기록한다.
