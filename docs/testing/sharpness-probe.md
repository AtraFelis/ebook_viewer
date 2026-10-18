# 선명도 기준선/페이지 재사용 비교

[ADR 0012](../adr/0012-bounded-page-reuse-and-sharpness.md)의 범위/한계를 따른다. 검색/PreV/사용자 PDF를 호출하지 않는다. 계측 observer는 기본 꺼짐이며 `suite=sharpness`에서 실제 View/worker를 연결해 합성 자료만 연다. 사용자 기록/설정을 지우지 않는다.

## 준비와 실행

Pillow/pypdf/reportlab은 기존 bundled PC 도구이며 앱 런타임/CI 의존성을 추가하지 않는다. Python 경로가 전역에 없으면 bundled Python 실행 파일을 명시한다. 읽기 APK에는 PDF fixture가 포함되지 않고 별도 계측 APK에만 지정 폴더의 합성 자료를 담는다.

```powershell
. ./scripts/android-env.ps1
python scripts/generate-sharpness-fixtures.py
./gradlew.bat :app:assembleProfile :app:assembleProfileAndroidTest -Pp0TestBuildType=profile --dependency-verification strict --console=plain
python scripts/run-sharpness-probe.py --runs 3 --label baseline
# 페이지 재사용 변경을 적용/검사한 뒤 같은 profile 명령으로 빌드
python scripts/run-sharpness-probe.py --runs 3 --label page-reuse
python scripts/summarize-sharpness-probe.py
# 같은 계측 APK로 보관한 기준/개선 APK의 30초×3회 FrameMetrics 비교
python scripts/run-sharpness-frames.py --label baseline
python scripts/run-sharpness-frames.py --label page-reuse
```

처음 계측 자체를 확인하는 `--runs 1`은 비교 합격 근거가 아니다. 기준/개선 비교는 30표본×3회×같은 문서·방향·화면/줌/주사율로 한다. 비교기는 표본/회수/자료 해시·조건이 다르면 거부한다. null/2초 timeout을 결과에서 버리지 않고 p95 censor/timeout으로 남긴다. 전체 P2 완료를 자동 판정하지 않는다.

runner는 승인된 기기 1대·잠금 해제와 설치 전/후 2GiB MemAvailable을 확인한다. 프로필 APK는 동일 서명 업데이트 설치이며 데이터 삭제/재부팅/다른 앱 조작을 하지 않는다. 호스트 기한 초과에는 이 합성 계측 대상 프로세스만 중지한다. native 호출 중단/RAM hard cap 보장은 아니다. 고정된 합성 입력 외에 개인 파일/검색어/경로 인자를 받지 않는다.

프레임 runner는 실제 설치 APK의 SHA-256을 대조하고 맞으면 중복 설치하지 않는다. 설치 ACK가 timeout이어도 성공/실패로 추정하지 않고 실제 APK 해시를 다시 확인해 다르면 실행을 거부한다. 기존 `DebugFrameProbe`는 자체 합성 URI의 읽기 기록만 검사 전 값으로 복원한다. 다른 문서 기록을 스냅샷 덮어쓰기하거나 삭제하지 않는다. 프레임 검사는 기존의 다른 플링/핀치 시나리오이므로 보통 스크롤 540개 표본과 동일 workload로 혼동하지 않는다.

별도 `am instrument -w -e suite sharpnessPixels dev.example.ebookviewer.p0.test/dev.example.ebookviewer.P0Instrumentation`은 벡터/그레이/RGB 미리보기 이후 3/8/32 배율의 타일을 새 페이지 reference와 직접 대조한다. 자료를 생성한 뒤 최신 debug/profile 계측 APK가 필요하며 기본 41개와 별도 opt-in이다. 사용자 PDF/기록을 열지 않는다. 계측 중 APK를 재설치하거나 다른 기기 작업을 병행하지 않는다.

## 해석

벡터 100페이지는 자체 `SyntheticDocument`이며 동일 생성 bytes를 앱 cache에 유지해 APK 업데이트 전후 SHA를 맞춘다. 그레이/RGB는 같은 1440×2240 패턴·JPEG q85·페이지 347×537pt이며 동일 이미지 XObject를 48페이지가 공유한다. 고유 고해상도 스캔 430페이지/원래 사용자 PDF의 재현이 아니다. 각 run은 새 View의 빈 bitmap cache/새 renderer로 시작하지만 OS/font/image cache까지 cold로 만드는 검사는 아니다. 역방향 재방문도 64MiB 퇴출 후에는 일부 cache miss를 포함한다.

onDraw 제출 시점은 물리 디스플레이 표시 완료가 아니다. 일반 스크롤은 화면 높이 70% 이동·6단계·16ms 간격·플링 억제 후 정지이며, 마지막 viewport 변경부터 현재 배율 가시 면적 95%를 그린 시간을 기록한다. 미리보기/다른 배율 fallback은 제외한다. 타일 시간의 queue는 해당 요청의 앞선 타일 처리도 포함하며 native 구간은 open/render/close를 합친 값이다. 취소/폐기 native 비용의 완전한 trace가 아니다.

원시 합성 계측 결과·APK SHA/보관본·집계/조건 표본은 ignored `local-artifacts/sharpness/`에 저장한다. 개인 파일/URI/본문/기기 식별자를 저장하지 않는다. 온도/충전·thermal/주사율 차이를 확인하고 다르면 유효한 전후 합격으로 확대하지 않는다. 빠른 플링/높은 배율·완전 cold/warm 분리·다른 원본 해상도/압축·프레임·사용자 수용과 최종 통합 검증은 별도다.
