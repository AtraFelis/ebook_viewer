# ADR 0015 — 현재 페이지 본문 읽기와 접근성

2026-10-19 (Asia/Seoul). 상태: 구현/검증 중, P2 완료 아님.

## 범위와 근거

사용자가 선택/복사 UI를 후속으로 미루고 페이지 단위 텍스트 안전성·접근성·최종 통합 검증을 요청했다. 검색·목차·비밀번호 PDF 읽기·OCR·Figma 전면 적용은 제외한다. API 33/S 확장 23의 앞선 shell probe는 앱 통합 안전성을 증명하지 않는다. 검색 수용 실패/LOW_MEMORY의 원인은 미확정이며 재현/해결로 보고하지 않는다.

플랫폼 기본 렌더러와 현재 스택을 유지하고, 본문 읽기에만 API 35 PdfRenderer 또는 API 30 이상/S 확장 13 이상 PdfRendererPreV를 사용한다. Context7은 버전 혼재/서명 불일치가 있어 공식 메서드 도입 버전·설치 SDK 35 컴파일과 대상 기기의 실제 실행으로 대조한다. 새 런타임 라이브러리·권한·SDK/도구 버전은 도입하지 않는다.

## 소유권과 중단

본문은 사용자가 요청한 현재 페이지 한 장만 읽고 자동 선읽기·전체 문서 검색·OCR·인덱스 저장은 하지 않는다. 렌더 worker에서 현재 FD를 복제해 비공개/권한 없는 isolatedProcess 서비스에 전달한다. 이 FD는 기존 익명 임시 파일/SAF 준비 결과이므로 새 복사나 URI/권한 전달이 필요 없다. 렌더 worker와 격리 서비스는 각각 자신의 FD와 renderer/page 수명을 소유한다.

서비스는 한 요청만 처리하고 6초 감시, 100ms마다 자체 RSS 192MiB/시스템 lowMemory·여유 메모리(max(512MiB, 플랫폼 threshold×2))를 확인한다. 관측 실패·초과·취소/마지막 unbind는 서비스 자신의 isolated UID 프로세스만 종료한다. native 호출 도중 다른 스레드에서 page/renderer를 close하지 않는다. 클라이언트도 7초 deadline/프로세스 사망을 처리하고 자동 재시도하지 않는다. 늦은 FD/결과는 닫거나 버리며 닫기·문서 교체·pause/destroy 때 취소하고 본문 TextView를 비운다.

결과는 16,000 UTF-16 코드 단위 이하로 제한하고 초과 페이지는 잘라 성공 처리하지 않고 중단 안내한다. 이 제한은 native getTextContents가 반환하기 전의 할당량을 제한하지 않는다. RSS 감시는 주기 관측으로 순간 peak/기기 전체 OOM을 보장하는 hard limit가 아니다. 작은 430페이지 이미지 합성 자료는 페이지 수만 같은 제한 검증이며 사용자 고해상도 문서의 재현이나 안전 보장이 아니다.

본문/URI/파일명은 로그·파일·Git·외부 서비스·클립보드에 쓰지 않는다. 사용자가 요청한 본문은 로컬 TextView/사용자가 사용하는 접근성 서비스에서 읽을 수 있으며 의미 구조·정확한 읽기 순서·OCR은 보장하지 않는다. 기능 미지원/본문 없음/메모리 부족·실패를 구분하고 기존 PDF 읽기는 유지한다.

## 접근성과 검증

본문 창의 페이지 번호 선택으로 읽을 페이지를 명시할 수 있다. 짧은 문서가 뷰포트에 전부 들어와 기하학적 스크롤로 특정 페이지를 중앙에 둘 수 없어도 한 페이지 본문에 접근하며 PDF 줌/위치는 바꾸지 않는다. 선택/복사 핸들 UI가 아니다. 번호 선택 역시 한 요청/한 페이지만 읽고 결과/커서를 저장하지 않는다. dismiss 소유권을 동기로 비우고 이전 창 콜백은 새 요청을 취소하지 못하게 한다.

클라이언트는 지원 경로(API 30 이상)에서 API 29의 bindIsolatedService를 요청별 고유 instanceName으로 호출한다. 이전 인스턴스 취소가 새 요청을 종료시키는 경합을 피하며 공유 인스턴스 플래그는 쓰지 않는다. 취소 직후 이전 프로세스의 종료와 새 인스턴스 시작이 잠깐 겹칠 수 있으며 192MiB는 프로세스별 관측 상한이다. 같은 클라이언트의 활성 요청/결과는 하나이고 각 프로세스의 deadline은 독립적이다.

Canvas에 현재 페이지 설명·도구 클릭·현재 페이지 본문 읽기·앞/뒤 페이지 접근성 action과 PageUp/PageDown 키를 제공한다. 페이지 이동은 기존 줌/앵커 경로를 사용한다. 상태 변경은 polite live region이며 본문은 스크롤 가능한 일반 TextView로 노출한다. 홈/도구/페이지 배치 설정도 스크롤 가능해 큰 글꼴에서 동작이 가려지는 문제를 줄인다.

기기 계측은 서로 다른 UID, 요청 페이지 내용/다른 페이지 부재, 빈 이미지 430페이지의 마지막 페이지, 잘못된 PDF/페이지·복구, 취소 뒤 늦은 FD 해제, 무응답 deadline, 실제 서비스 Binder 사망과 Canvas 접근성 동작을 확인한다. 합성 자동 검사만으로 실제 TalkBack/100·150·200% 글꼴의 개인 사용 확인을 대체하지 않는다. 실제 명령/실패·수정·미실행은 [이력](../history/2026-10-19-03-page-text-accessibility.md)을 따른다.

## 공식 근거

- [PdfRendererPreV 및 isolated process 권고](https://developer.android.com/reference/android/graphics/pdf/PdfRendererPreV)
- [PreV Page 본문 API](https://developer.android.com/reference/android/graphics/pdf/PdfRendererPreV.Page#getTextContents())
- [Service 수명](https://developer.android.com/reference/android/app/Service)
- [AccessibilityNodeInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)
- [요청별 격리 인스턴스 바인딩](https://developer.android.com/reference/android/content/Context#bindIsolatedService(android.content.Intent,%20int,%20java.lang.String,%20java.util.concurrent.Executor,%20android.content.ServiceConnection))
