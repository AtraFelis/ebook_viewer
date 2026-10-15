# SESSION.md — 세션 인수인계

최종 갱신: 2026-10-16 (Asia/Seoul)

## 현재 상태

Windows 로컬 전환 및 승인에 의존하지 않는 P0 환경·문서 검증 완료. P0 전체 완료는 아니다. 앱·Wrapper·SDK 초기화, 빌드와 기기 성능 검증은 미실행이다. [ADR 0001](docs/adr/0001-p0-toolchain-and-renderer.md)은 여전히 제안/승인 전이다.

지정 폴더가 비어 있어 원격 저장소를 같은 위치에 clone했다. 보존할 기존 파일/체크아웃은 없었다. main에서 fetch 및 pull --ff-only 성공, 양쪽 SHA `d850b158d26d8d47005cd1721252552d6824bc1a` 일치. reset/clean/강제 push 없음.

## 로컬 점검과 검증

- PATH Java/javac: Oracle 21.0.7. 별도 설치 Temurin 17.0.15+6의 java/javac 실행 확인. 이것은 Android 빌드 성공을 뜻하지 않는다.
- Gradle/kotlinc/sdkmanager/adb는 PATH에서 미발견. Android SDK/Studio는 기본 설치 경로·설치 목록·프로세스에서 미발견. 임의 설치 경로까지 부재라고 단정하지 않는다. adb devices는 도구 부재로 미실행, 기기 연결 상태는 미확인.
- Context7 사용 가능. AGP 요청은 최신판 자료를 반환해 8.7.2 근거로 사용하지 않음. PDF 조회는 일부 API 35 자료와 다른 클래스 자료가 섞여 공식 SDK 정의로 대조.
- 공식 AGP 8.7 표: Gradle 8.9/JDK 17/최대 API 35, Build Tools 최소·기본 34.0.0. Kotlin 2.1.20 호환 범위와 Gradle 8.9의 Java 17 실행 지원 재확인.
- 공식 AOSP API 35 SDK 텍스트 정의에서 LoadParams와 PdfRenderer 텍스트/검색/선택 서명 확인. 설치한 android.jar 대조·컴파일은 미실행.
- 사용자 지침: 로컬 설치 버전에 개발 구성을 맞추지 않고, 검증·승인된 개발 구성에 로컬 환경을 맞춘다. 기존 JDK 패치를 자동 채택하지 않는다.

## 다음 행동과 승인 경계

1. ADR 구성안 승인: Kotlin/stdlib 2.1.20, AGP 8.7.2, Gradle 8.9, JDK 17, compile/target 35/min 23, Build Tools 34.0.0, P0 PdfRenderer+View/Canvas. Compose 셸 최종 선택은 별도.
2. 승인 후 JDK 배포판/패치/해시 및 SDK 패키지 리비전 고정, 로컬 설치, Wrapper 검증, 앱 초기화. 전이 의존성·라이선스·검증 메타데이터 확인.
3. 빌드/단위 테스트/Lint/ktlint/CI 실제 실행. API 23/35 기기 기능 및 대표 실기기 성능 측정.

작업 위치는 `C:\Users\cotjs\workspace\ebook_viewer`. 이번 문서 변경은 승인된 GitHub 보존 대상이며 런타임 구성 승인은 별개다. 커밋·push의 최종 결과는 채팅에서 원격 SHA 대조 후 보고한다. 상세 명령·제약은 [로컬 P0 이력](docs/history/2026-10-16-05-windows-p0-verification.md)에 기록했다.
