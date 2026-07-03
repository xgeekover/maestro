# ADR-0004 — JVM 타깃을 Java 21 → 17로 하향

- 상태: **승인(Accepted)**
- 날짜: 2026-07-04
- 맥락: [ADR-0001](0001-phase0-foundational-decisions.md) C-1(스택 확정)의 "Java 21" 결정 중 **JVM 버전 부분을 대체**한다.

## 맥락 (Context)

초기 스택은 Java **21**로 확정됐다([ADR-0001]). 그러나 실제 배포·개발 환경에는 **OpenJDK 17**(LTS)이 이미 설치된 경우가 많고, 다음 제약이 확인됐다.

- 데스크탑 설치본(Electron)은 백엔드를 번들/자동기동하지 않으므로, 사용자는 **호스트의 JDK로 백엔드를 직접 실행**하게 된다. 이때 아티팩트가 Java 21 바이트코드(class major 65)면 JDK 17 런타임에서 `UnsupportedClassVersionError`로 기동 불가.
- Gradle 툴체인이 JDK 21을 자동 프로비저닝하긴 하나(foojay), 오프라인·폐쇄망에서는 21 다운로드가 불가능할 수 있다.
- 코드베이스가 Java 21 **전용 API를 사용하지 않음**을 확인했다(가상 스레드·`SequencedCollection`·`StructuredTaskScope`·record pattern 등 미검출).

## 결정 (Decision)

**Gradle 툴체인 languageVersion을 21 → 17로 하향**하고, 파생 지점(CI·컨테이너 베이스 이미지·문서)을 17로 정렬한다.

- `build.gradle.kts` — `JavaLanguageVersion.of(17)`
- `.github/workflows/ci.yml` — `setup-java` `java-version: '17'`
- `deploy/backend.Dockerfile` — build/runtime 스테이지 `eclipse-temurin:17-jdk`
- README·CLAUDE.md 스택 표기 17

## 근거 (Rationale)

- **17은 LTS**로 사설/사내 환경 보급률이 높아 "Docker 없이 호스트 JDK로 바로 실행" 시나리오가 크게 쉬워진다.
- Java 21 전용 기능 미사용 → **기능 손실 없이** 하향 가능.
- 러너는 백엔드의 `java.home`으로 spawn되므로(ProcessManager), 백엔드가 17이면 동적 컴파일·러너 JVM 전부 17로 일관.

## 검증 (Verification)

- `./gradlew clean build` (host **OpenJDK 17.0.19**) → **BUILD SUCCESSFUL**, JVM 테스트 **91개 전부 통과**(실패 0).
- 컴파일 산출물 바이트코드 **major version 61 = Java 17** 확인.
- `backend` bootJar를 `java -jar`(호스트 17)로 기동 → `Starting MaestroApplication ... using Java 17.0.19`, health `UP`.
- 스크립트 생성→실행 → `RUNNING`, 러너(17)가 동적 컴파일된 스크립트의 `onTick`을 정상 실행(로그 확인).

## 결과 / 트레이드오프 (Consequences)

- **긍정**: JDK 17 단독 환경에서 소스 빌드·`bootRun`·`java -jar` 모두 동작. 폐쇄망 친화적.
- **부정/유의**: Java 18–21 신기능(가상 스레드 등)을 쓰려면 다시 상향 ADR 필요. 향후 그 기능이 필요해지면 21로 재상향을 검토한다.
- 데스크탑(pnpm/Electron)은 JVM과 무관하여 영향 없음.
