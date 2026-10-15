# P0 기술 조사와 구성 제안

날짜: 2026-10-16 (Asia/Seoul). 기준 HEAD: `3a68f76`.

## 목적·변경

AGENTS → SESSION → CHECKLIST → roadmap 및 관련 규칙/기획/구조/검증 문서를 읽고 P0를 재개했다. ADR 0001, SESSION, CHECKLIST, 문서 목차/roadmap을 갱신했다. AGENTS와 기존 이력은 보존했다. 런타임 의존성 도입·앱 초기화·커밋·push는 수행하지 않았다.

## 실제 명령과 결과

| 명령/작업 | 결과 |
| --- | --- |
| `git status --short`, `git log -3 --oneline` | 시작 시 변경 없음, HEAD 3a68f76 |
| `java -version` | OpenJDK Runtime 21.0.12.1 |
| `command -v java javac gradle sdkmanager adb kotlinc` | java만 PATH에서 발견. 다른 도구의 모든 디스크 경로 부재까지 증명한 것은 아님 |
| `ls /usr/lib/jvm`, `test -e /dev/kvm` | JRE 21 경로 확인, KVM 경로 검사 exit 1 |
| cloud environment status 및 `/etc/codex/network-policy.json` 읽기 | running, configured secret/identity 없음, restricted 정책. status의 정책 state는 unknown이므로 enforced로 단정하지 않음 |
| Python urllib.request로 ADR 출처 URL GET, timeout 20–25초 | 기본 샌드박스 네트워크 EPERM 후 승인된 network 실행으로 재시도. 허용된 소스/메타데이터 수신 |
| developer.android.com, kotlinlang.org GET | 프록시 403. 우회하지 않음. Kotlin은 허용된 공식 GitHub 소스 사용 |
| Android 15 frameworks_base 경로 및 Kotlin 이전 경로 | 404. API 35는 제3자 미러 참고로만 명시; Kotlin은 공식 이동 경로 확인 |
| AGP 8.9.2/8.7.2 및 KGP 2.1.20 POM | 수신·라이선스 확인. Kotlin 공식 호환 표 상한에 맞춰 제안 AGP를 8.7.2로 선택 |
| Gradle 8.11.1/8.9 SHA 응답 | 수신. 제안은 8.9. ZIP/Wrapper 검증 완료 아님 |
| 앱 빌드/단위 테스트/Lint/ktlint/기기/성능 | 미실행: 프로젝트·도구 초기화 전, 의존성 승인 전 |

네트워크 원본은 임시 `/tmp/pdf-p0`에 저장했다. 재현 근거 URL과 핵심 결과는 ADR에 보존했으며 임시 파일 영속성에 의존하지 않는다. 다운로드한 소스를 실행하거나 SDK 라이선스를 수락하지 않았다.

## 판정·미결

조사 및 구체적 구성 제안까지 진행. P0 완료 아님. AGP 호환 표 추가 확인, 승인 후 도구 설치/체크섬·전이 의존성 검증과 실제 빌드 필요. API 35 확장은 공식 SDK 대조 필요. PdfRenderer 취소 지연·실기기 성능은 미측정.

문서 검증: `git diff --check` 및 전체 Markdown 상대 링크/필수 파일/후행 공백 검사 실행 결과는 아래에 기록한다.

- 통과: `git diff --check` (exit 0).
- 통과: Python pathlib/re 기반 전체 Markdown 상대 링크·필수 파일·후행 공백 검사 (exit 0).
- 문서 검토: 승인 전 제안과 실측 완료를 구분했으며 P0 완료 체크는 유지하지 않고 미완료로 남김.
- 최종 보존 상태: 로컬 변경, 미커밋·미푸시.
