# ADR 0001 — P0 도구와 PDF 엔진 제안

날짜: 2026-10-16 (Asia/Seoul). 상태: **제안 / 승인 전 / 빌드·기기 검증 전**.

## 문제와 제안

연속 문서 좌표와 공통 줌을 직접 제어하며, 기본 읽기에 외부 서비스나 PDF 네이티브 라이브러리를 추가하지 않는 P0를 제안한다. Android 내장 PdfRenderer와 사용자 정의 View/Canvas로 타일 프로토타입을 만든다. 앱 셸의 Compose 우선 검토는 유지하며, 이 제안은 P0 읽기 영역 검증에 한정한다. Compose와의 통합 비교·최종 선택은 후속 ADR로 남긴다.

새 런타임 의존성은 Kotlin stdlib 2.1.20(Apache-2.0)만 제안한다. Kotlin 컴파일 결과 실행을 위한 것으로 Java만 사용하는 대안은 승인된 Kotlin 스택과 맞지 않는다. 초기 P0는 플랫폼 Activity/SAF/Executor로 구성하고 Compose, coroutines, Room, PDF 래퍼는 아직 도입하지 않는다. Executor 선택은 의존성 최소화를 위한 P0 선택이며 UI 스레드 렌더링을 허용하지 않는다. stdlib의 실제 전이 의존성·라이선스는 초기화 시 잠금/검증 메타데이터로 확인한다.

## 도구 후보와 확인 수준

최신 버전 추천이 아니라 범위가 명확한 P0 재현 기준 후보다. 스토어 출시용 target API 정책은 별도 검토한다.

| 항목 | 제안 | 근거와 남은 검증 |
| --- | --- | --- |
| JDK | 17, Java/Kotlin target 17 | AGP 요구사항 재확인 및 설치 배포판·패치·해시 고정 필요. 현재는 JRE 21만 확인 |
| Gradle | 8.9 | 배포 SHA-256 수신. ZIP 다운로드/실행/Wrapper JAR 검증 미실행 |
| AGP | 8.7.2 | Google Maven POM 존재·Apache-2.0 확인. Gradle 8.9/JDK 17/API 35 호환은 공식 릴리스 문서 재확인 및 실제 빌드 필요 |
| Kotlin/KGP | 2.1.20 | 공식 표: Gradle 7.6.3–8.12.1, AGP 7.3.1–8.7.2 범위. Maven POM 확인 |
| SDK | compile/target 35, min 23 | 후보. API 23–34 기본 렌더, 35 확장 경로 분리. SDK·Build Tools 35.0.0 설치/빌드 검증 필요 |
| 검사 | Android Lint(AGP 포함), JUnit 4.13.2, ktlint CLI 1.5.0 | JUnit EPL-1.0, ktlint MIT 후보. 테스트/개발 전용, 배포물·체크섬·전이 라이선스 확인 전 |

Gradle 8.9 bin ZIP의 공식 SHA-256 응답:
`d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`.
이 값 수신은 ZIP/Wrapper 검증 완료를 뜻하지 않는다. Wrapper JAR는 별도 공식 체크섬과 비교한다.

## PDF 지원 매트릭스

| 후보 | API·기본 렌더 | 검색·선택·암호화·목차 | 수명·취소·타일 | 라이선스·비용·미결 |
| --- | --- | --- | --- | --- |
| PdfRenderer 기본 API | API 21+에 존재, 제안 앱 min 23. affine Matrix와 clip으로 부분 렌더 | API 23–34 공개 기본 API에는 검색/선택/비밀번호 입력 없음. 암호화 문서 오류 안내. 목차는 이번 검증에서 지원 확인 못 함 | seek 가능한 FD 필요. Page 닫기 후 renderer 닫기. 구형 구현은 thread-safe 아님. 동기 render 즉시 중단 API 없음 | 플랫폼 제공, 앱에 별도 엔진 ABI 번들 없음. OS 업데이트에 엔진 수정 의존 |
| PdfRenderer API 35 확장 | 기본 렌더와 분리해 런타임 API 가드 필요 | LoadParams 비밀번호, getTextContents/searchText/selectContent 확인. 스캔 OCR 아님. 실제 SDK 서명·기기 검증 전 | API 35 소스는 호출 직렬화 설명. 전체 지원 API에서 단일 worker로 소유권 통일 | 확장 확인 자료는 제3자 AOSP 소스 미러이므로 공식 SDK 대조 필요. 하위 API 역이식 보장하지 않음 |
| PDFium 직접 번들/래퍼 | 네이티브 C API의 matrix 렌더 확인. min API는 선택할 래퍼·빌드에 달림 | 텍스트/검색/비밀번호/북마크 C API 계열 후보. 래퍼 노출·정확성 검증 필요 | 공식 헤더는 thread-safe 아님을 명시. 진행형 렌더와 취소 가능 여부는 별도 API/래퍼 조사 필요 | BSD 계열 및 third-party 고지. JNI·ABI·16KB 페이지 크기·보안 업데이트·APK 크기 부담. 래퍼 미선정 |
| MuPDF | Android 통합 후보, min API/ABI는 배포 구성별 검증 필요 | 텍스트/검색/암호화/목차 후보, 선택 UI는 앱 책임. 이번 세션 기능 실행 검증 없음 | 타일·취소·스레드 계약은 선택 버전 API 조사 필요 | 공식 README: AGPL-3.0-or-later 또는 상용 라이선스. 배포 조건·비용 결정 필요 |

PdfRenderer를 우선 검증하고, API 23–34에서도 검색/암호화가 필수가 되거나 성능 기준에 실패하면 대안 엔진을 재평가한다. P2 기능을 모든 지원 OS에서 제공한다고 약속하지 않는다. PDFium/MuPDF 바이너리 크기와 성능은 측정 전이므로 숫자를 추정하지 않는다.

## 프로토타입 검증 설계

- 좌표: `screen = document * zoom + translation`. 페이지별 배치 배율과 공통 zoom을 분리한다. 20페이지 왕복·회전·모드 변경에서 zoom을 재설정하지 않는다.
- 타일: 우선 512×512 ARGB_8888(픽셀 버퍼 1MiB)을 후보로 한다. PDF point→타일 px 행렬의 이동값에 타일 원점을 빼고, bitmap 범위로 clip한다. 인접 타일 이음새·회전·혼합 크기를 기기로 확인한다.
- 수명: 단일 백그라운드 worker가 FD/renderer/Page를 소유한다. 한 Page씩 열고 finally에서 닫는다. 생성 실패 시 호출자가 FD를 닫고, 성공 후 renderer가 FD를 소유한다.
- 취소: 대기 작업 제거, 타일 사이 세대 검사, 결과 게시 전 문서/세대 검사. 진행 중 native render는 즉시 중단하지 못하므로 close를 경합시키지 않는다. 장시간 단일 타일의 취소 지연을 측정한다. 요구 미달이면 격리 프로세스/다른 엔진 검토.
- 메모리: 캐시 상한 64MiB는 후보. `allocationByteCount`로 계산하고 작업 중 버퍼·미리보기·화면 참조를 별도 계상한다. 캐시 퇴거만으로 사용 중 bitmap을 recycle하지 않는다. 총 PSS와 캐시 사용량은 다르다.
- SAF: seek 불가능 provider는 용량 상한·취소·실패 정리를 갖춘 앱 전용 임시 복사 경로 검증. URI/본문/비밀번호 로그 금지.
- 자료: 합성 혼합 규격/회전, 100페이지 벡터, 500페이지 문서, 스캔, 손상, 암호화. 생성법과 SHA 기록 후 사용.
- 판정: API 23 및 35 기기 기능 검증, 중급 실기기 3회 프레임/첫 화면/10분 메모리 측정. 현재 결과 없음. 기준은 [검증 전략](../testing/strategy.md)을 따른다.

## 출처와 접근 한계

2026-10-16 확인. mutable 소스는 참고 자료이며 실제 채택 시 버전 고정·SDK 대조한다.

- [Kotlin 공식 호환 표 소스](https://raw.githubusercontent.com/JetBrains/kotlin-web-site/master/docs/topics/gradle/gradle-configure-project.md): 2.1.20–2.1.21 행 확인.
- [AGP 8.7.2 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.7.2/gradle-8.7.2.pom), [KGP 2.1.20 POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.1.20/kotlin-gradle-plugin-2.1.20.pom).
- [Gradle 8.9 체크섬](https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256), [Gradle Java 호환 표 소스](https://raw.githubusercontent.com/gradle/gradle/v8.11.1/platforms/documentation/docs/src/docs/userguide/releases/compatibility.adoc): Java 17 실행은 Gradle 7.3+, 21은 8.5+.
- [AOSP Android 14 PdfRenderer](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/graphics/java/android/graphics/pdf/PdfRenderer.java).
- [API 35 제3자 소스 미러](https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-35/src/android/graphics/pdf/PdfRenderer.java): 공식 SDK 검증의 대체물이 아님.
- [PDFium API](https://raw.githubusercontent.com/chromium/pdfium/main/public/fpdfview.h), [라이선스](https://raw.githubusercontent.com/chromium/pdfium/main/LICENSE).
- [MuPDF README·라이선스](https://raw.githubusercontent.com/ArtifexSoftware/mupdf/master/README).
- developer.android.com 및 kotlinlang.org 직접 접근은 프록시 403. Kotlin은 공식 GitHub 원문으로 확인. AGP 공식 릴리스 호환 표는 미확인으로 남겼다.

## 승인 범위

이 ADR의 도구 후보로 초기화하고 Kotlin stdlib를 도입하며 P0 View/Canvas+PdfRenderer 프로토타입을 진행하는 구성안이다. 승인 후 호환 표 잔여 확인, JDK/SDK 설치, Wrapper 검증, 빌드/테스트/Lint/ktlint/CI 순으로 진행한다. 충돌하는 호환 결과가 나오면 확정으로 기록하지 않고 구성안을 수정한다. applicationId는 출시용 사용자 도메인을 가정하지 않는 개발용 식별자로 정한다. 최종 Compose/저장/PDF 대안 런타임 의존성은 이 승인 범위 밖이다.

## Windows 추가 검증 — 2026-10-16

상태는 계속 **제안 / 승인 전 / 빌드·기기 검증 전**이다. 위 조사 당시의 환경·접근 제약은 과거 기록이며 아래 결과로 보완한다. 사용자는 로컬 설치에 개발을 맞추지 않고 승인된 개발 구성에 로컬을 맞추기로 했다.

- [AGP 8.7 공식 릴리스](https://developer.android.com/build/releases/agp-8-7-0-release-notes): 8.7.2 수정 이력 포함. 최대 API 35, Gradle 최소/기본 8.9, JDK 최소/기본 17, Build Tools 최소/기본 34.0.0 확인. 따라서 기존 Build Tools 35.0.0 후보를 **34.0.0 후보로 수정**한다. compile/target 35와 Build Tools 버전은 별개이며 실제 빌드 전이다.
- [Kotlin 공식 호환 표](https://kotlinlang.org/docs/gradle-configure-project.html): KGP 2.1.20–2.1.21의 Gradle 7.6.3–8.12.1 및 AGP 7.3.1–8.7.2 확인.
- [Gradle v8.9.0 공식 표](https://raw.githubusercontent.com/gradle/gradle/v8.9.0/platforms/documentation/docs/src/docs/userguide/releases/compatibility.adoc): Java 17 실행 7.3+, Java 21 실행 8.5+. 로컬 Java 21을 이유로 JDK 17 제안을 바꾸지 않는다.
- Context7 `/websites/developer_android_studio`는 8.7.2 지정에도 최신 AGP 자료를 반환했다. `/websites/developer_android`는 API 35 searchText와 PdfRendererPreV 자료를 혼합 반환했다. 버전 고정 문서 ID는 제공되지 않았으며 단독 근거로 사용하지 않는다.
- [공식 AOSP SDK API 35 정의](https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/android15-release/35/public/api/android.txt): `PdfRenderer(ParcelFileDescriptor, LoadParams)`, `LoadParams.Builder.setPassword(String)`, `Page.getTextContents()`, `searchText(String)`, `selectContent(SelectionBoundary, SelectionBoundary)` 및 두 render 오버로드 확인. selectContent 결과는 nullable PageSelection이다. branch URL은 mutable이며 설치 SDK 패키지 리비전/해시와 android.jar 컴파일 대조는 남아 있다.
- [공식 PdfRenderer 문서](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)는 LoadParams 생성자의 API 35 추가, seek 가능한 FD, renderer의 FD 소유, worker thread 호출과 잘못된 비밀번호의 SecurityException을 확인한다. SDK Extensions/PdfRendererPreV는 별도 지원 경로이며 API 23–34 전체 지원으로 일반화하지 않는다.

승인 요청 구성은 Kotlin stdlib 2.1.20(Apache-2.0)을 앱 런타임에 추가하고 플랫폼 Activity/SAF/Executor 및 PdfRenderer+View/Canvas로 P0를 만드는 것이다. Java-only는 Kotlin 계약과 맞지 않으며 Compose는 추가 의존성·통합 검증, PDFium은 JNI/ABI·배포 크기·업데이트 부담, MuPDF는 AGPL/상용 조건이 있어 후속 비교로 둔다. stdlib 전이 의존성은 도입 전 확인하며 승인 범위를 벗어나는 항목은 재검토한다. JDK 17 배포판·패치·해시는 초기화 전에 고정한다. 로컬 Temurin 17.0.15 실행 확인은 해당 패치 채택 승인이 아니다.
