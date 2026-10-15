# SESSION.md — 세션 인수인계

최종 갱신: 2026-10-16 (Asia/Seoul)

## 현재 상태

문서 기반 준비 후 P0 기술 조사에 착수했습니다. 앱·Wrapper·SDK 초기화와 기기 실행은 아직 미착수입니다. 조사 시작 기준 HEAD는 `3a68f76`이며 작업 시작 시 Git 변경은 없었습니다. 기존 AGENTS 계약을 보존했습니다.

[ADR 0001](docs/adr/0001-p0-toolchain-and-renderer.md)에 도구 후보, PDF 엔진 매트릭스, 수명/취소/타일 검증 설계와 출처를 기록했습니다. 상태는 제안이며 승인·호환 빌드·실기기 검증 완료가 아닙니다.

## 확인한 환경과 제약

- Java 런타임 21.0.12.1 존재. javac/Gradle/sdkmanager/adb/kotlinc는 PATH에서 확인되지 않았습니다.
- `/dev/kvm` 없음. 기기 연결 확인은 adb 부재로 미실행이며, 실기기 성능 판정 불가입니다.
- 일부 공식 문서 호스트는 프록시 403. 공식 Maven 메타데이터, Kotlin GitHub 호환 표, Gradle 체크섬, PDF 공개 소스는 읽었습니다. 네트워크 정책 우회 없음.
- 새 런타임 의존성·스택 확정은 AGENTS 승인 규칙 적용. 현재 추가한 런타임 의존성은 없습니다.

## 로컬 전환 요청

사용자가 GitHub push 후 Windows 로컬로 전환하기로 했습니다. [전환 절차와 로컬 채팅 프롬프트](docs/local-handoff.md)를 사용합니다. Context7 MCP는 이번 조사에서 사용하지 않았으며, 로컬에서 가용성 확인 후 추가 검증할 항목입니다. push 승인은 의존성 구성 승인과 별개입니다.

## 다음 행동

1. ADR 구성안 승인: Kotlin 2.1.20 stdlib, AGP 8.7.2/Gradle 8.9/JDK 17, SDK 35/min 23 후보, P0 PdfRenderer+View/Canvas.
2. AGP 공식 호환 표 잔여 확인, JDK/SDK 패치·배포 해시 고정, 검증된 Wrapper 및 앱 초기화. Compose 셸 최종 선택은 별도.
3. 빌드·단위 테스트·Android Lint·ktlint와 CI 실제 실행.
4. 합성 PDF 자료와 타일/공통 줌 프로토타입 검증. 기기 확보 후 기능·성능 측정.
5. 검증된 환경 설치/시작 절차 저장 및 새 환경 복원 별도 확인.

## 작업 위치와 보존

클라우드 작업 위치 `/workspace/ebook_viewer`. PC 경로 `C:\Users\cotjs\workspace\ebook_viewer`; 사용자가 PC 작업을 시작한다고 할 때 해당 환경에서 변경을 확인하고 pull합니다. 현재 PC 연결은 검증하지 않았습니다.

이번 조사·전환 문서는 사용자 요청에 따라 GitHub main에 커밋·push하는 보존 대상입니다. 원격 보존 여부는 push 결과와 원격 SHA 일치로 확인하며, 로컬 PC의 pull은 별도입니다. 이전 문서 보존 이력은 docs/history에 유지했습니다. 실제 명령과 결과는 [P0 조사 이력](docs/history/2026-10-16-03-p0-investigation.md)에 기록합니다.
