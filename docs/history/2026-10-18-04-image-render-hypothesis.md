# P2 이미지 크기·색상 형식과 렌더링 지연 가설

2026-10-18 (Asia/Seoul). 작업 경로 `C:\Users\cotjs\workspace\ebook_viewer`.

## 사용자 자료와 해석

사용자는 두 페이지 이미지 속성의 분석 화면을 제공하고 두 번째가 느렸으며 DPI보다 원본 이미지 크기와 관련 있어 보인다고 보고했다. 첨부 화면은 눈으로 확인했으며 원본 PDF는 받거나 외부에 보내지 않았다. PDF 제목/본문·화면 캡처/개인 경로를 Git에 넣지 않고 비식별 이미지 속성만 계획에 반영했다.

첫 이미지 1446×2237px/149.2KB/그레이/8bpc/DCTDecode, 두 번째 1407×2223px/284.6KB/RGB/8bpc/DCTDecode라는 표시를 비교했다. 계산상 두 번째 픽셀 수는 약 3.3% 적고 표시 용량은 약 1.9배다. 표시 DPI는 300→600이라 기존 낮은 DPI 가설을 지지하지 않는다. DISPLAY SIZE 차이도 있으나 단위 미확인이므로 화면 px/PDF point로 단정하지 않았다. 분석 도구의 용량/단위/추출 방법을 직접 검증한 결과는 아니다.

따라서 픽셀 크기가 더 커서 느리다고 확정할 수 없으며 압축 데이터/색상 처리와 문서 배치, 큐·캐시·방문 순서의 영향을 나누는 가설로 수정했다. RGB/그레이 차이로 native 디코딩/변환 비용이 달라질 가능성은 있으나 실제 병목/시간 기여도·반복 재디코딩은 미확인이다. KB 크기를 렌더 시간에 비례하는 수치로 사용하지 않는다.

## 코드·문서 확인

AGENTS→SESSION→CHECKLIST→선명도 계획과 Git 상태를 확인했다. `PdfWorker`는 최대 4개 요청 타일을 배치하고 출력에 `Bitmap.Config.ARGB_8888`를 쓴 뒤 `page.render`를 호출한다. [공식 PdfRenderer.Page.render](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page#render(android.graphics.Bitmap,%20android.graphics.Rect,%20android.graphics.Matrix,%20int))도 ARGB 목적 비트맵을 요구한다. 원본 RGB 때문에 출력 타일 캐시가 3배가 된다고 주장하지 않으며 native 내부 메모리는 별도다. 실측 없이 PdfRenderer 내부 이미지 처리 구현/캐시 동작을 확정하지 않는다.

[선명도 계획](../plan/p2-sharpness-recovery.md)에 같은 픽셀/배치 크기의 그레이/RGB·압축 대조군, 화면상 페이지 너비를 맞춘 원시 줌/타일 배율 기록, 방문 순서 반전·cold/warm 캐시의 미리보기/첫·반복 타일/큐 대기 비교를 추가했다. SESSION에는 최신 가설을, CHECKLIST에는 미실행 검증을 추가했다. 앱 코드/의존성·빌드/설치/기기 측정은 변경·실행하지 않았다. 기존 디자인/계획 미커밋 변경은 보존했고 커밋·push하지 않았다.

## 검증

변경 문서의 로컬 링크/공백과 앱 소스 불변을 확인하며 측정/앱 테스트를 통과한 것으로 보고하지 않는다.

실제 검사: 관련 Markdown 4개 내부 파일 링크 52개/누락 0, 줄 끝 공백/최종 개행 및 `git diff --check` 통과. `git diff --exit-code HEAD -- app scripts gradle .github AGENTS.md` 차이 없음. 현재 문서 변경은 로컬 미커밋 상태다.
