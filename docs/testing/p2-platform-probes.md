# P2 플랫폼 점검과 합성 렌더 프로브

API 인벤토리 및 플랫폼의 작은 기능/렌더 실험이다. 앱의 전체 계측·사용자 수용·스크롤 성능 검증을 대체하지 않는다. 기존 앱을 설치/시작/force-stop하거나 기록을 읽지 않는다. 고유한 `/data/local/tmp` 경로에 자체 DEX와 합성 샘플만 복사하고 종료 시 개별 파일/빈 폴더를 정리한다. 개인 PDF/암호/URI·기기 식별자는 보고서에 넣지 않는다.

## 실행

PowerShell, 저장소 루트에서 `. ./scripts/android-env.ps1` 후 실행한다. standalone probe는 API 30 이상에서만 실행하며 앱의 min 23 선언은 변경하지 않는다. 승인된 기기 한 대를 연결하거나 `-Serial`로 고르되 식별자를 공유하지 않는다.

```powershell
./scripts/probe-pdf-capabilities.ps1
```

기능/이미지 probe는 API 30 이상·S Extensions 13 이상과 `PdfRendererPreV`가 필요하다. 현재 API 33/S 확장 23에서 실행했다. 샘플은 저장소의 `scripts/generate-p2-fixtures.py`로 만든다. 로컬에 이미 제공된 desktop Python 환경의 Pillow 12.3.0/pypdf 6.10.0/reportlab 4.4.9/cryptography 50.0.2를 사용했으며 Android 앱 의존성이 아니다. 별도 `pip install`이나 앱 Gradle 의존성 추가는 하지 않는다. 생성에 쓴 Python 경로/버전은 [이력](../history/2026-10-18-05-p2-platform-readiness.md)에 기록했다.

```powershell
# 위 패키지가 제공된 desktop Python 실행 파일로 실행한다.
python scripts/generate-p2-fixtures.py
./scripts/probe-pdf-capabilities.ps1 -FeatureChecks
./scripts/probe-pdf-capabilities.ps1 -ImageRenderChecks
```

두 실행 옵션은 동시에 쓸 수 없다. 각 샘플 전송 전에 생성 manifest의 SHA-256을 비교한다. public synthetic fixture의 암호는 실제 사용자 자격 증명이 아닌 공개 테스트 값이다. 이 도구에 개인 문서를 넣는 옵션은 없다.

JSON은 마지막 성공 결과이며 probeId/완료 UTC를 포함한다. 새 실행이 실패했을 때 남아 있는 이전 JSON을 새 성공으로 사용하지 않는다. AES 샘플은 생성 때 무작위 암호화 요소가 바뀔 수 있으므로 매번 생성된 manifest 해시와 함께 비교한다.

## 산출물과 판정

- `local-artifacts/p2-fixtures/manifest.json`: 합성 7개 샘플의 해시/크기·픽셀/색상·예상 결과와 생성 도구 버전. PDF/manifest는 Git에 넣지 않는다.
- `local-artifacts/p2-platform-probe/capabilities.json`: API와 S/T 확장, 공개 클래스/메서드 존재. 존재 확인만으로 기능 실행 통과를 뜻하지 않는다.
- `features.json`: 텍스트 추출/검색 2건·결과 없음/선택/렌더, AES-128/256 암호 없음·오류 거부와 올바른 암호 검색/렌더. 실패/예외는 실행 실패이며 성공 보고서를 남기지 않는다. 한글·다중 페이지·UI 선택 핸들·과정 복원은 이 작은 probe의 범위 밖이다.
- `image-render.json`: 합성 이미지 4종×2렌더러×3회=24개 표본, 각 4개 render 단계. 새 renderer마다 preview→firstTile→adjacentTile→repeatFirstTile 순서이며 페이지는 매번 열고 닫는다. 첫/반복 타일은 512px, 렌더 배율 4 px/point, 미리보기는 긴 변 512px이다. `renderMs`는 native 호출 구간이며 bitmap 생성/픽셀 검사는 포함하지 않는다. `pageOpenMs`/renderer open도 분리한다.

이미지는 같은 기하/패턴의 그레이 q85·RGB q85·RGB q50·픽셀 절반 그레이 q85다. 반복 1에서 자료 순서를 반전하되 엔진은 기본→확장 고정 순서다. 새 renderer 생성은 OS/decoder 캐시 cold를 뜻하지 않는다. 마지막 단계의 타일은 첫 타일과 같지만 실제 내부 이미지 재디코딩 유무는 측정하지 않는다. 이미지 화소 검사는 빈 결과를 검출하기 위한 것이며 PDF 픽셀 완전 일치/화질 검증은 아니다.

미리보기/타일의 UI 게시와 draw, 앱 worker 대기·취소, 체감 선명화, 프레임과 메모리/장시간 안정성은 별도 계측 대상이다. P2 성능 합격이나 앱 기능 완료를 이 보고서만으로 선언하지 않는다.
