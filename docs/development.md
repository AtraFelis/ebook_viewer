# 개발 환경

현재 Windows에서 승인된 P0 도구 구성으로 앱 debug 빌드·단위 테스트·Lint·ktlint가 통과했다. API 33 기기 설치·실행·계측 테스트 11개도 통과했다. 현재 기기 합성 자료의 로컬 기술 검증은 완료했고 새 Windows CI의 도구 설치·빌드/검사도 통과했다. 실제 새 PC의 드라이버/Studio/기기 연결 복원은 미검증이다.

[Windows 설치·시작 절차](windows-development.md)를 따른다. [ADR 0001](adr/0001-p0-toolchain-and-renderer.md)은 승인 구성과 과거 조사 근거를, [SESSION](../SESSION.md)은 현재 상태와 다음 행동을 기록한다.

현재 체크아웃을 사용하고 동기화 전 사용자 변경·브랜치·원격을 확인한다. fast-forward가 불가능하면 보존 후 진단하며 reset/clean/강제 push로 해결하지 않는다. 과거 클라우드/로컬 전환 경위는 [전환 문서](local-handoff.md)와 history에 남긴다.

SDK·JDK·build·local.properties는 Git에서 제외한다. 검증된 Wrapper JAR, 의존성 잠금/검증 메타데이터, 도구 배포 URL/해시는 추적한다. 비밀번호·PDF 본문·기기 식별자는 로그/Git에 남기지 않는다. 새 런타임 의존성과 승인 스택 변경은 AGENTS 규칙을 따른다.
