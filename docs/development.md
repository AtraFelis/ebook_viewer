# 개발 환경

## 현재 상태

기획/문서 단계로 앱·Gradle Wrapper·CI가 없습니다. 현재 클라우드에서 Java 21 런타임은 확인했지만 javac/Gradle/sdkmanager/adb는 PATH에서 발견되지 않았습니다. Windows 로컬 도구·SDK·기기는 별도 확인이 필요합니다. 빌드 가능한 Android 환경 완료를 의미하지 않습니다.

## P0 초기화 절차

1. 기존 체크아웃 사용. 사용자 요청 없이 worktree 생성 금지.
2. SDK·빌드 도구·가상화·기기 연결 확인. 변수 이름과 존재만 확인하고 비밀 값은 출력하지 않음.
3. 공식 호환성 자료로 Kotlin/AGP/Gradle/JDK의 정확한 버전 확정.
4. 렌더러 매트릭스로 최소 API를 결정하고 compile/target SDK는 공식 배포 요건 확인.
5. 승인된 구성으로 app, Wrapper, 버전 카탈로그, 검증 메타데이터, 최소 테스트, Lint/ktlint, CI 생성. Wrapper 출처/배포 체크섬 검증, SDK 라이선스 조건 확인.
6. 빈 앱 빌드·기기 실행 후 PDF 프로토타입 추가.
7. 실제 검증된 설치/시작 절차를 클라우드 설정에 저장. 저장·게시·새 환경 복원은 서로 다른 결과로 보고.

예정 명령은 AGENTS.md 참조. 현재 Wrapper/app이 없어 실행 불가입니다. 실제 생성 후 명령과 결과를 갱신합니다.

기본 앱에는 계정/API 키가 필요하지 않습니다. Gradle/Google/Maven 다운로드 목적지는 선정된 버전에 맞춰 확인하며 알 수 없는 allowlist를 덮어쓰지 않습니다. SDK 경로는 local.properties 또는 환경 변수, 서명 키는 안전한 설정으로 관리합니다.

## 클라우드 및 PC 동기화

작업은 기본 클라우드에서 합니다. PC 작업 요청 시 `C:\Users\cotjs\workspace\ebook_viewer`를 해당 PC의 Codex에서 열고 원격·브랜치·미커밋 변경을 확인합니다. 깨끗한 추적 브랜치에서 `git pull --ff-only`를 우선 사용합니다. divergence나 변경 충돌이면 사용자 변경을 보존하고 진단합니다. 클라우드에서 PC pull을 실행했다고 주장하지 않습니다.

## 이번 로컬 전환

사용자가 Windows 로컬 전환을 요청했습니다. [동기화 절차와 재개 프롬프트](local-handoff.md)를 따릅니다. Context7 추가 조사와 공식 호환 표/SDK 대조 후 P0를 이어갑니다. 도구 구성은 [ADR 제안](adr/0001-p0-toolchain-and-renderer.md)이며 아직 의존성 승인 전입니다.
