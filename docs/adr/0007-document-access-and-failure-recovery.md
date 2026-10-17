# ADR 0007 — 취소 가능한 파일 접근과 오류 복구

2026-10-17, P1. 기존 Android 플랫폼·Activity/View·PdfRenderer·AtomicFile을 유지하며 새 런타임 의존성/저장 스키마 변경 없음. 검증 현황은 [실행 이력](../history/2026-10-17-09-document-access.md)을 따른다.

## 파일 접근과 수명

읽기 기록 I/O 큐에서는 기존 기록을 조회하고, provider 권한·표시 이름 조회·파일 열기는 PDF worker에서 실행한다. 조회/열기에 CancellationSignal을 전달한다. 문서 전환/목록 복귀/Activity 종료는 즉시 세대를 바꾸고 취소 플래그를 세운다. provider 취소 Binder 호출은 별도 직렬 취소 executor에서 수행해 UI나 취소 대상 PDF worker를 막지 않는다. PDF 엔진과 모든 descriptor/page 소유권은 계속 PDF worker에 있다.

읽기 전용 `openFileDescriptor`는 pipe를 반환할 수 있다. `Os.lseek(fd, 0, SEEK_CUR)`가 ESPIPE일 때만 앱 내부 캐시의 임시 inode로 복사한다. 탐색 가능한 파일은 복제하지 않는다. 쓰기/읽기 descriptor를 연 직후 이름을 unlink하고 **PDF 바이트는 그 뒤에 쓴다**. 따라서 성공·실패·취소·프로세스 종료 후 이름이 있는 PDF 복사본을 남기지 않는다. 생성과 unlink 사이의 강제 종료에는 비어 있는 임시 파일이 남을 수 있다. 원본 URI만 기록하고 임시 경로는 저장하지 않는다.

임시 복사 상한은 256MiB이며 상한 초과는 기기에 내려받은 파일을 다시 선택하도록 안내한다. 디스크 여유를 추측해 다른 앱의 캐시를 정리하지 않고 실제 생성/쓰기 실패를 공간 오류로 처리한다. `Os.poll`의 100ms 대기마다 취소를 확인하며 데이터가 30초간 오지 않으면 시간 초과로 처리한다. reliable pipe의 EOF 뒤 오류도 확인한다. 상한/idle timeout은 테스트에서 작은 값으로도 검증하며 실제 256MiB 전송 성능 측정을 뜻하지 않는다.

반환 descriptor의 소유권은 PdfRenderer에 한 번 넘기고 엔진 생성 실패 시 닫는다. pipe 원본과 실패한 임시 출력은 worker에서 닫는다. 이미 시작한 native render/엔진 생성과 취소를 무시하는 외부 provider Binder 호출을 강제 중단할 수 있다는 보장은 하지 않는다. 세대가 지난 결과/오류는 UI에 게시하지 않는다.

## 오류와 UI

권한 거부, 파일/제공 앱 접근 불가, 빈 파일, 손상/미지원 PDF, 암호/미지원 보안 방식, 복사 상한, 저장 공간, 무응답, 읽기, 페이지 준비/렌더 실패를 안전한 enum으로 전달한다. provider 예외 메시지·URI·PDF 내용을 로그/화면에 그대로 출력하지 않는다. 권한 획득 단계의 SecurityException과 PdfRenderer 생성 단계의 보안 예외를 구분한다. P1에 비밀번호 입력/복호화 기능을 추가하지 않는다.

실패하면 파일 목록으로 복귀해 이유와 ‘다시 시도/파일 다시 선택’을 제공한다. 취소한 파일 선택은 기존 목록/오류 복구 화면을 유지한다. 실패한 열기로 기존 기록/앵커/북마크를 덮어쓰지 않는다. 재시도는 해당 URI/앵커와 재연결 대상을 유지하며 실제 PDF를 연 뒤에만 기존 기록/북마크 재연결을 확정한다. 로딩 중에는 상태와 목록 복귀 도구를 표시하고 첫 페이지 배치를 받으면 읽기 도구를 숨긴다. 열람 중 상시 UI는 추가하지 않는다.

## 검증 범위와 근거

테스트 APK에만 별도 UID의 read-permission/grant 기반 provider를 둔다. 테스트 target UID만 fixture를 준비/회수할 수 있으며 provider는 자체 합성 PDF만 만든다. Java fixture는 별도 프로세스가 target APK의 Kotlin runtime/클래스에 의존하지 않기 위한 검사 전용 코드다. ActivityMonitor로 파일 선택 결과를 전달하므로 제조사/외부 DocumentsUI의 실제 선택 조작 검증과 구분한다. URI 권한의 지속 저장·Activity 재실행·회수와 원본 기록 보존을 확인하되 재부팅/실제 개인 PDF/모든 provider 수용을 선언하지 않는다.

- [PdfRenderer API](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer): seekable FD/엔진 소유권·예외, 기존 생성자 API 21.
- [ContentResolver API](https://developer.android.com/reference/android/content/ContentResolver): read-only pipe와 CancellationSignal 지원 openFileDescriptor(API 19).
- [Os API](https://developer.android.com/reference/android/system/Os): lseek/poll/read(API 21), 설치 API 35 android.jar 서명 대조. min 23 선언과 다른 API 기기 검증은 별도다.

Context7의 `/websites/developer_android_reference`도 조회했지만 이번 결과는 AssetFileDescriptor 중심으로 필요한 조항이 부족해 공식 API와 설치 SDK로 교차 확인했다.
