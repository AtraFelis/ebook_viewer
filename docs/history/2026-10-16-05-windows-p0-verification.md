# Windows 로컬 전환 및 P0 추가 검증

날짜: 2026-10-16 (Asia/Seoul). 시작 원격 HEAD: `d850b158d26d8d47005cd1721252552d6824bc1a`.

## 수행과 결과

- 지정 폴더에서 Get-ChildItem -Force 결과 비어 있음. git status/branch/remote는 not a git repository. 사용자 파일 없음 확인 후 같은 폴더에 git clone 수행. 최초 네트워크 getaddrinfo 실패 후 승인된 네트워크 실행으로 성공.
- AGENTS → SESSION → CHECKLIST → ADR 0001 순으로 읽고 local-handoff, roadmap, 개발/Git 규칙, 기획/구조/검증 전략 및 전체 history 확인.
- git status --short: 변경 없음. main 및 지정 GitHub origin 확인. git fetch origin 성공. git pull --ff-only origin main: Already up to date. HEAD와 origin/main 모두 위 SHA. divergence/충돌 없음. reset/clean/강제 push 없음.
- 기본 PowerShell 프로필의 PSReadLine/Terminal-Icons 오류 후 모든 후속 명령을 login=false로 실행.
- Get-Command java,javac,gradle,kotlinc,adb,sdkmanager: java/javac만 발견. java -version 및 javac -version: Oracle 21.0.7.
- JAVA_HOME은 Oracle JDK 21, Android SDK 환경 변수는 미설정. 기본 Program Files/LocalAppData Android 디렉터리, C:\Android, Scoop/Chocolatey 후보 경로, 사용자 .jdks, Windows 설치 목록과 Studio/adb/emulator 프로세스를 조회했다. 샌드박스 LocalAppData가 다른 경로여서 실제 사용자 기본 위치도 별도 조회했다.
- .jdks에서 Temurin 17.0.15 및 OpenJDK 26.0.1/26.0.2 디렉터리 발견. Temurin bin/java.exe -version 및 bin/javac.exe -version: 17.0.15+6/17.0.15 성공. JDK 26은 실행/채택하지 않음.
- SDK/Android Studio/adb는 조사 범위에서 미발견. .NET Android Manifest 설치 항목은 SDK 설치 증거로 취급하지 않음. adb devices -l은 도구 부재로 미실행; 기기 연결/권한/실기기 여부 미확인. 전체 디스크에 설치가 없다고 단정하지 않는다.
- Context7 resolve_library_id(Android) 성공. query_docs 2회: AGP 8.7.2는 최신판 자료로 치환되어 후보 버전 확인에 불충분; PDF는 API 35 검색과 PreV 자료가 혼재. ADR에 정확한 ID·공식 교차 출처 기록.
- 공식 AGP 8.7 호환 표와 Kotlin 2.1.20 행을 웹으로 확인. Build Tools 후보를 공식 기본값 34.0.0으로 수정; compile/target 35와 구분.
- Invoke-WebRequest로 공식 AOSP prebuilts/sdk android15-release/35/public/api/android.txt의 base64를 메모리에서 디코딩, android.graphics.pdf 패키지 서명을 확인. SDK 설치·android.jar 다운로드/컴파일은 수행하지 않음.
- Gradle 8.9 웹 문서는 도구 접근 실패. 최초 GitHub 경로는 404. 정확한 v8.9.0 userguide/releases/compatibility.adoc 경로로 Java 17/21 행 수신 성공. 실패한 조회 후 남은 PowerShell 검색 프로세스는 중단했다.
- 앱/Wrapper/SDK 설치, 빌드/단위 테스트/Lint/ktlint/CI 및 실기기 성능: 모두 미실행. 문서와 도구 버전 확인을 앱 성공으로 보고하지 않음.

## 결정과 다음 행동

새 런타임 의존성은 추가하지 않았다. 사용자의 추가 지침에 따라 로컬 설치에 개발 구성을 맞추지 않고 개발 구성에 로컬을 맞춘다. Temurin 17.0.15는 발견/실행 증거일 뿐 최종 패치 선정이 아니다.

ADR의 목적·대안·라이선스·영향이 포함된 구성안 승인 후 JDK 배포판/패치/해시 및 SDK 리비전 고정, Wrapper/앱 초기화, 실제 검증을 진행한다. Compose 셸 최종 선택과 P0 성능은 미결이다. GitHub 보존 승인은 의존성 승인과 구분한다.

SESSION, CHECKLIST, ADR, development를 갱신했다. 기존 AGENTS 및 과거 이력은 보존했다. 아래 문서 검증 후 이번 문서 결과를 커밋·push한다.

검증: Markdown 상대 링크 검사 통과. 최초 diff --check에서 CHECKLIST 끝 빈 줄 1개를 발견해 제거한 뒤 재검사한다. 문서 외 생성 파일 없음.
