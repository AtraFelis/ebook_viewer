# ADR 0009 — P2 플랫폼 PDF 확장 검증과 엔진 경계

2026-10-18 (Asia/Seoul). 상태: **플랫폼 실행 검증 완료 / 앱 통합 예정 / 추가 라이브러리 미승인**.

## 문제

개인 API 33에서 P2의 검색·선택·비밀번호 PDF 읽기·계층형 목차를 제공해야 한다. OS API 숫자만으로 새 PDF 엔진이 필요하다고 판단하면 불필요한 네이티브/라이선스·전이 의존성 비용을 도입할 수 있다. 기본 PdfRenderer 공개 메서드와 SDK 확장 경로를 구분해 실제 실행부터 확인한다.

## 확인 결과와 진행 방향

현재 연결 기기는 API 33, `SdkExtensions.getExtensionVersion(31/33)` 모두 23이다. 기본 PdfRenderer는 렌더링 경로만 제공하지만 `PdfRendererPreV`와 LoadParams·텍스트·검색·선택 클래스가 있다. 기존 설치 SDK 35/JDK 17로 컴파일한 자체 shell probe에서 합성 문서의 텍스트 추출·검색 2건/없음·선택·렌더, AES-128/256의 암호 없음/오류 거부와 올바른 암호의 검색/렌더를 통과했다. 이는 설치된 앱의 UI/수명/접근성 수용 완료가 아니다.

우선 기존 플랫폼 렌더 계열 안에서 확장 경로를 통합하는 방향으로 진행한다. 새 런타임 의존성/SDK·Kotlin·AGP·Wrapper 변경은 현재 없다. 지원 확인은 `SDK_INT >= 30 && S 확장 >= 13`과 실제 공개 API/클래스 기준이며 분기를 별도 어댑터에 격리한다. 하위/확장 미지원 환경은 기존 PdfRenderer 읽기를 유지하고 지원하지 않는 추가 기능을 명확히 구분한다. 다른 기기의 기능을 검증했다고 주장하지 않는다.

렌더/페이지/FD는 계속 단일 worker가 소유하고 native 호출 도중 close하지 않는다. 기존 줌·페이지/문서 좌표·배치 배율·64MiB 비트맵 캐시·취소 epoch와 SAF 임시 파일 정책을 보존한다. 확장 기능 호출도 worker에서 처리한다. 앱 통합 시 기존 회귀와 확장 기능 계측을 실행하고 최종 앱 소스/설치 APK를 연결한다.

## 목차의 별도 미결

PdfRenderer/PreV의 공개 메서드에는 문서 전체 outline 트리를 얻는 경로를 확인하지 못했다. 페이지 goto link는 계층형 문서 목차가 아니다. **P2의 목차는 필수로 유지**하고 플랫폼만으로 모든 P2 엔진 요구가 충족됐다고 선언하지 않는다. 별도 보조 파서/엔진이 필요하면 목적·정확한 버전·전체 라이선스/고지·전이 의존성·메모리·ABI·보안 업데이트·암호/FD 수명을 담은 구성안으로 사용자 승인을 받은 뒤 도입한다. 임의 PDF 파서를 급조해 완료 처리하지 않는다.

## 비교한 외부 후보 — 채택 아님

- `io.legere:pdfiumandroid:1.0.32`: 공식 Maven POM/Gradle module과 AAR SHA-256을 확인했다. AAR manifest min 23이며 POM 설명의 API 24 문구와 다르다. classes.jar의 텍스트 페이지·목차 API를 정적 확인했다. Kotlin stdlib 2.0.21, coroutines-android 1.8.1, annotation-jvm 1.9.1을 선언한다. 현재 stdlib 2.1.20과의 실제 resolution/strict 빌드는 미실행이다. AAR 10,233,563 bytes이고 arm64-v8a의 libpdfium/libpdfiumandroid 합계는 5,203,552 bytes(압축 전)다. 최종 APK 증가량/성능은 미측정이다. POM 기본 라이선스는 Apache-2.0이지만 PDFium/번들 native third-party 고지·빌드 출처/패치/16KB 적합성은 추가 확인 대상이다.
- `io.legere:pdfiumandroid:1.0.35`: AAR manifest min 24, Guava 33.5.0-android 선언이다. 현재 min 23 계약을 자동 변경하지 않는다. AAR는 9,912,351 bytes이며 원격 공개 SHA-256과 일치했다.
- `io.legere:pdfiumandroid:2.0.3`: POM이 stdlib 2.4.10과 api/core 2.0.3·Guava 33.6.0-android를 선언해 현재 Kotlin 2.1.20에 무조건 최신판을 추가할 수 없다. 최신 Maven release 표기는 3.0.4-alpha01이므로 latest를 채택하지 않는다.
- 목차 보조 후보 `com.tom-roush:pdfbox-android:2.0.27.0`은 Bouncy Castle 1.72 세 모듈을, fork `com.dineshdev:pdfbox-android:2.0.27.1`도 1.73 세 모듈/stdlib을 POM에 선언한다. 목차 하나를 위해 도입할 전이 비용·버전/보안·암호 수명을 추가 평가해야 하므로 선정하지 않았다. 상기 선언만으로 취약/안전이나 전이 의존성 제거 가능성을 확정하지 않는다.

라이브러리 파일은 승인 전 정적 조사용으로만 `local-artifacts/p2-engine-research`에 받았고 앱에서 실행·선언·번들하지 않았다. lockfile/verification-metadata 변경 없음. 후보 POM의 기본 라이선스 표기를 전체 배포 라이선스 확인 완료로 세지 않는다.

## 성능 검증 범위

자체 합성 스캔 4종×기본/확장 렌더러×3회, 총 24개 표본을 실행했다. 각 표본은 미리보기·첫 타일·인접·반복 타일을 측정한다. 기본 렌더러의 첫 타일 중앙값은 같은 픽셀 수의 그레이 q85 약 13.03ms, RGB q85 약 37.15ms, RGB q50 약 35.77ms였다. RGB의 저장 용량 감소가 시간에 비례하지 않았다. 이 합성 조건의 결과이며 사용자의 파일 병목을 증명하거나 RGB 전반의 성능 비율로 일반화하지 않는다.

앱 큐/캐시·UI draw/스크롤 선명화·프레임·PSS 측정은 포함하지 않았다. 3회 중앙값을 p95로 보고하지 않으며 native 프로세스/OS 캐시와 기기 온도는 완전 통제하지 않았다. 그레이와 RGB 출력 타일은 모두 ARGB_8888이다. 실제 앱의 선명화 기준선/최적화 목표는 이후 확정하며 이 결과만으로 확장 렌더가 빨라졌다고 주장하지 않는다.

## 근거

- [공식 PdfRendererPreV](https://developer.android.com/reference/android/graphics/pdf/PdfRendererPreV), [확장 Page](https://developer.android.com/reference/android/graphics/pdf/PdfRendererPreV.Page), [SDK 확장](https://developer.android.com/guide/sdk-extensions).
- [버전 1.0.32 Maven 배포](https://repo.maven.apache.org/maven2/io/legere/pdfiumandroid/1.0.32/), [1.0.35 배포](https://repo.maven.apache.org/maven2/io/legere/pdfiumandroid/1.0.35/), [2.0.3 POM](https://repo.maven.apache.org/maven2/io/legere/pdfiumandroid/2.0.3/pdfiumandroid-2.0.3.pom), [상류](https://github.com/johngray1965/PdfiumAndroidKt).
- Context7 `/johngray1965/pdfiumandroidkt`는 최신/구버전 API가 혼재해 버전 고정의 단독 근거로 쓰지 않았다. 공식 배포 POM/module/manifest/클래스를 우선했다.
- 실제 명령/미결과 도구 사용은 [착수 검증 이력](../history/2026-10-18-05-p2-platform-readiness.md), [재현 절차](../testing/p2-platform-probes.md).
