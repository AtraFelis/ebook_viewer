# 개발 환경과 작업 시작

갱신: 2026-10-29. Windows 로컬 체크아웃은 `C:\Users\cotjs\workspace\ebook_viewer`다. 현재 도구/기능 계약은 [AGENTS](../AGENTS.md), 최신 구현과 검증은 [SESSION](../SESSION.md)을 따른다.

승인 도구는 Kotlin 2.1.20 / AGP 8.7.2 / Gradle Wrapper 8.9 / Temurin JDK 17.0.20.1+1 / compile·target 35 / min 23 / Build Tools 34.0.0이다. 플랫폼 Activity + View/Canvas·PdfRenderer·SAF·AtomicFile을 사용한다. 새 런타임 의존성이나 스택 변경은 별도 승인 대상이다.

## 시작하기

AGENTS → SESSION → CHECKLIST → 해당 계획을 읽고 Git 변경·브랜치·원격을 확인한다. 현재 체크아웃을 사용하며 사용자 디자인/버전 변경을 보존한다. 원격 동기화는 관계를 확인한 뒤 ff-only로 하고 충돌/divergence를 reset/clean/강제 push로 숨기지 않는다.

최초 도구 설치는 [Windows 개발](windows-development.md)을 따른다. 이미 도구가 있으면 다시 설치하지 않고 PowerShell 7에서 아래를 실행한다.

```powershell
Set-Location 'C:\Users\cotjs\workspace\ebook_viewer'
. ./scripts/android-env.ps1
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug ktlintCheck --dependency-verification strict
```

ktlintFormat은 명시적으로 요청한 경우만 실행한다. SDK/JDK·local.properties·서명 키와 개인 산출물은 추적하지 않고 Wrapper JAR·잠금/검증 메타데이터는 보존한다. 비밀번호·PDF 본문·URI·기기 식별자는 Git/공개 로그에 남기지 않는다.

## 검증과 정리

PC 빌드·문서 작업에는 휴대폰이 필요하지 않다. 필요한 기기 검사 때만 USB 디버깅 승인/잠금 해제를 확인하고 [검증 전략](testing/strategy.md)에 맞춰 관련 검사를 선택한다. 최종 코드의 단위 84개와 기기 일부 검증은 과거 실행 결과이며 환경 복원이나 새 기기 성능을 보장하지 않는다.

문서·아이콘 표시만의 변경에서 전체 기기/장시간 검사를 반복하지 않는다. 종료 때 실제 결과·미실행·미결을 기록한다. [파일 정리 기준](maintenance.md)에 따라 재생성 캐시와 보존할 배포/증거를 구분한다. 정식 업데이트는 [개인용 빌드](personal-release.md)를 따른다.
