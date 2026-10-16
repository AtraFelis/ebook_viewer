# P0 제스처 회귀와 디버그 예비 측정

날짜: 2026-10-16 (Asia/Seoul). 현재 main의 시작 HEAD `0aa65d3`; 구현은 로컬 미커밋·미푸시. 기존 변경 보존, 새 런타임 의존성 없음.

## 변경과 통과 기록

- MotionEvent를 View에 주입하는 핀치/드래그, 20페이지 왕복 중 줌 유지, requestedOrientation 변경 시 문서 중심/줌 복원 계측을 추가했다. 손가락 입력·물리 회전 센서 검증과 구분한다.
- 문서 열기가 교체되면 이전 FD가 닫히고 최신 문서만 게시되는 회귀 테스트 추가.
- 사용자 줌은 연속값 그대로 두고 렌더 배율만 반 옥타브 단계로 올림한다. 같은 배율 타일과 이전 배율의 캐시 타일을 재사용한다. 이전 타일은 키만 참조하며 별도 비트맵 캐시를 만들지 않아 공유 64MiB 상한을 유지한다.
- 최신 요청에도 필요한 진행 중 타일은 렌더 완료 후 게시한다. 다음 타일부터 최신 요청으로 전환하며 이전 문서 결과는 계속 폐기한다. native render의 즉시 중단은 지원하지 않는다.
- `. ./scripts/android-env.ps1` 후 `./gradlew.bat ktlintFormat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict`: 통과, 단위 테스트 6개, Lint 오류/경고 0.
- `./scripts/device-smoke.ps1`: API 33 SM-N981N에서 7개, 실패 0. 합성 문서 및 파일 핸들만 사용하며 기기 식별자를 기록하지 않았다.
- 수정 APK SHA-256: `a19eac50993bcfb121a437554f91bd7f990b6351d67030dd2b6efff085afbe37`.

## 측정 방법과 한계

`./scripts/device-smoke.ps1 -Probe`는 같은 합성 100페이지 파일로 30초 플링/핀치 3회를 수행한다. FrameMetrics TOTAL_DURATION, 보고 누락 수, 가시 고해상도 타일 준비 상태, PSS/FD/캐시를 기록한다. 프레임 보고 누락은 jank와 다르다. refresh 예산 초과도 실제 deadline 기반 jank와 동일하지 않다.

디버그 APK·동일 프로세스에서 새 Activity를 여는 예비 측정이다. 파일 URI로 직접 열며 SAF 선택기와 cold start를 포함하지 않는다. `firstVisibleTilesMs`는 열기 요청부터 모든 가시 타일이 캐시에 준비될 때까지이며 최초 화면 제출 시간이 아니다. `missingTileSamples`는 제스처 종료 시 고해상도 타일 하나 이상이 미완성인 횟수다. 흰 화면 면적·지속 시간이나 이전 배율 타일의 시각적 커버리지를 뜻하지 않는다. 10분 메모리/릴리스 성능 판정으로 사용할 수 없다.

자료: 합성 PDF 2,684,504 bytes, SHA-256 `ec89d9379bf880bd8e5023d63223ef23e41a9e896c3bd65d08f2eb67e6bf0963`. 생성 코드 SyntheticDocument. 비교를 위해 기기 cache에 같은 파일을 유지한다. 개인 문서 아님.

## 변경 전 완료된 3회

APK SHA-256 `171e9f76718d55485b2017f4a204b970f9d7f06852c2f1c92d15a7c5f8966888`. API 33, 약 60Hz. 각 30초.

- 1회: 1,773 frames; p95 3.925ms / p99 13.254ms; 가시 타일 준비 8,702ms; 미완성 123/123; PSS 156,179→158,437 KiB; FD 137→132.
- 2회: 1,771 frames; p95 3.787ms / p99 4.371ms; 가시 타일 준비 10,332ms; 미완성 123/123; PSS 181,984→182,051 KiB; FD 139→133.
- 3회: 1,772 frames; p95 3.864ms / p99 4.685ms; 가시 타일 준비 11,031ms; 미완성 123/123; PSS 203,919→204,919 KiB; FD 139→133.
- 모든 회차 50ms 초과/보고 누락 0, 최대 캐시 22,591,872 bytes. 회차 간 PSS 상승을 누수 없음으로 해석하지 않는다. 짧은 프레임 시간도 타일 준비/읽기 품질 통과를 의미하지 않는다.

## 실패와 복구

초기 측정 도구는 FrameMetrics listener를 두 번 해제해 IllegalArgumentException으로 실패했다. 연결 여부 플래그로 수정했고 위 baseline 3회는 수정된 도구로 완주했다. 실패한 부분 결과는 별도로 보존한다.

변경 후 첫 측정은 세션 종료로 완료 상태를 잃었다. 18시경 재개 시 adb unauthorized를 확인했고 사용자가 USB 디버깅을 재승인했다. 기기 파일에는 2회만 남아 있어 `local-artifacts/p0-debug-probe-recovered.json`에 보존하되 3회 비교 판정에서 제외했다. 다시 3회를 실행한다.

## 근거와 후속

Context7의 Android 문서에서 FrameMetrics 검색은 충분한 자료를 얻지 못했다. [공식 listener 문서](https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener)로 callback 객체 재사용과 dropCount 의미를 대조했다. profileable/testBuildType 추가 조사에서 Context7은 AGP 8.7 지정에도 7.4/8.0/8.10 자료를 혼합 반환하여 단독 근거로 사용하지 않는다. [공식 profileable 문서](https://developer.android.com/guide/topics/manifest/profileable-element), [Gradle 설정 문서](https://developer.android.com/build/gradle-tips)를 추가 확인했다. profileable 빌드 자체는 아직 추가하지 않았다.

P0 잔여: 첫 유효 화면/타일 준비 지연 분리, release/profileable 동일 조건 3회, 10분 왕복 메모리, 스캔·대용량·암호화 자료, 취소 지연·타일 이음새. 원격 CI/새 PC 복원도 별도 미검증이다. P0 완료를 선언하지 않는다.

## 변경 후 디버그 3회 재실행 완료

동일 합성 파일 SHA 일치. APK는 위 수정 APK와 동일. 각 30초, 보고 누락/50ms 초과/refresh 예산 초과 모두 0.

- 1회: 1,774 frames; p95 6.261ms / p99 7.252ms; 가시 타일 준비 9,345ms; 미완성 111/115; 최대 캐시 67,061,936 bytes; PSS 185,026→281,093 KiB.
- 2회: 1,780 frames; p95 6.271ms / p99 8.052ms; 가시 타일 준비 10,390ms; 미완성 111/114; 최대 캐시 67,051,520 bytes; PSS 301,254→266,028 KiB.
- 3회: 1,779 frames; p95 5.974ms / p99 7.232ms; 가시 타일 준비 10,319ms; 미완성 112/115; 최대 캐시 67,053,568 bytes; PSS 300,989→250,425 KiB.

캐시 상한 67,108,864 bytes 이내지만 총 PSS는 더 크다. 이전 배율 타일을 보존하며 p95와 메모리 비용이 늘었고, 고해상도 미완성 비율도 여전히 높다. 순수 개선이나 P0 성능 통과로 결론 내리지 않는다. 온도/백그라운드 작업을 통제한 실험이 아니므로 차이의 원인을 코드만으로 단정하지 않는다.

## 개인용 범위와 측정 빌드

사용자는 추가 기기가 없으며 현재 기기에서만 동작해도 충분하다고 명시했다. API 23·35 검증은 이번 P0 완료 조건에서 제외한다. minSdk 23을 검증된 전체 지원으로 표현하지 않는다.

profile variant는 release 설정 복사, debuggable=false, 로컬 debug 키 서명, profileable shell=true. 새 런타임 의존성 없음. 난독화는 현재 release와 동일하게 비활성. `./gradlew.bat ktlintFormat :app:assembleProfile :app:assembleProfileAndroidTest -Pp0TestBuildType=profile --write-locks --dependency-verification strict` 통과. lockfile에 기존 버전의 profile configuration을 추가했다. 기존 debug 빌드/단위6개/Lint/ktlint strict 재검사도 통과했다.

`./scripts/device-smoke.ps1 -Profile -Probe`로 비디버그 30초×3회를 시작했다. `-Seconds 600 -Runs 1` 지원 및 30초 간격 PSS 샘플을 추가했다. 보고서를 instrumentation status로 돌려주므로 비디버그 앱의 run-as 권한에 의존하지 않는다. API 29+ profileable 설정은 공식 문서와 로컬 AGP 8.7.2 빌드로 확인했다. 인프로세스 계측 자체의 오버헤드는 남으며 외부 Perfetto 기반 첫 제출 프레임과 구분한다.

## 비디버그 30초 3회 완료

실행 명령 위와 같음, 실패 0. debuggable=false 확인. APK SHA-256 `36f294dd0b0f0258b324738122fd73fc90d2747089df2d93916842cf0bf7391f`, 합성 PDF SHA 동일. 원본 요약 `local-artifacts/p0-profile-probe-30s.json`.

- 1회: 1,767 frames; p95 5.874ms / p99 7.010ms; 전체 가시 타일 8,511ms; 미완성 112/115; 최대 캐시 67,055,616 bytes; PSS 166,468→259,375 KiB; FD 137→131.
- 2회: 1,779 frames; p95 5.813ms / p99 7.272ms; 전체 가시 타일 11,028ms; 미완성 111/115; 최대 캐시 67,105,120 bytes; PSS 283,788→259,829 KiB; FD 140→134.
- 3회: 1,780 frames; p95 5.796ms / p99 6.742ms; 전체 가시 타일 11,015ms; 미완성 111/115; 최대 캐시 67,098,624 bytes; PSS 281,131→238,393 KiB; FD 139→133.

모든 회차 보고 누락/50ms 초과 0, refresh 예산 초과는 3회차 1개(나머지 0). 프레임 p95/p99 잠정 목표 범위지만 타일 지연과 표시 품질 판정은 남아 있어 P0 전체 합격으로 해석하지 않는다.

10분 검증 전 계측을 세분화하여 layoutReadyMs, firstTileCachedMs, firstVisibleTilesMs를 구분했다. 모두 폴링 기반 준비 시간이며 실제 화면 제출 프레임 시간은 아니다. `./scripts/device-smoke.ps1 -Profile -Probe -Seconds 600 -Runs 1` 실행 중이다.

후속 테스트 준비: 이미지 기반 1페이지 PDF 확대 타일 픽셀, 500페이지 혼합 규격 메타데이터/누적 좌표. 초기 컴파일은 PdfDocument가 Closeable이 아니어서 실패했고 try/finally close로 수정했다. `:app:assembleProfileAndroidTest :app:lintProfile ktlintCheck -Pp0TestBuildType=profile --dependency-verification strict` 통과, 기기 실행은 장시간 측정 후 진행한다.

## 비디버그 10분 1회 완료

동일 APK/합성 SHA, 600,219ms, 33,149 frames, p95 6.187ms/p99 7.030ms, 50ms 초과 0, refresh 예산 초과 6, 보고 누락 0. 캐시 최대 67,108,568 bytes로 64MiB 상한 이내. 제스처 종료 표본 미완성 833/2,425. 10분 반복 3회 완료는 아니다.

layoutReadyMs 9,576 / firstTileCachedMs 9,682 / 전체 가시 타일 10,918ms. 초기 지연의 대부분이 배치 게시 전 발생하므로 PdfRenderer 생성과 전체 페이지 메타데이터 순회를 분리 진단한다. 최초 화면 2초 잠정 목표 통과로 기록하지 않는다.

PSS 시작 92,472→종료 286,918 KiB. 30초 표본 최대 296,028 KiB, 5분 이후 288,164~293,225 KiB 범위로 큰 지속 상승은 관찰되지 않았다. 초기 그래픽스 할당 증가와 FD 106→137이 있어 누수 없음/완전한 자원 복구를 확정하지 않는다. OOM/계측 실패는 발생하지 않았다. 원본은 `local-artifacts/p0-profile-probe-10min.json`.

9개 기기 테스트를 실행하면서 후속 테스트 APK 패키징을 겹쳐 실행해 Windows 파일 잠금으로 패키징 1회 실패했다. 설치 중인 APK 삭제를 강제하지 않고 설치 완료 후 재시도한다. 앱 코드/데이터를 reset하지 않았다.

재시도 패키징 통과. `./scripts/device-smoke.ps1 -Profile`: API 33 비디버그 앱에서 **9개, 실패 0**. 추가한 이미지 PDF의 확대/이동 타일 픽셀과 500페이지 혼합 규격의 마지막 페이지·누적 좌표 검증도 통과했다. 이후 `-Profile -Probe -Seconds 10 -Runs 1`로 PdfRenderer 생성과 페이지 메타데이터 순회 시간을 분리 측정한다.
