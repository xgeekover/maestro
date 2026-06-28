---
title: "Maestro - Claude Code 빌드 프롬프트"
aliases:
  - Maestro 빌드 프롬프트
  - Maestro Claude Code Prompt
tags:
  - project
  - maestro
  - claude-code
  - prompt
  - reference
created: 2026-06-28
updated: 2026-06-28
status: 사용 준비 완료
---

# 🧩 Maestro — Claude Code 빌드 프롬프트 (분석~배포)

> [!tip] 사용법
> 새 프로젝트 폴더에서 Claude Code를 열고 **아래 `---` 사이 전체를 붙여넣은 뒤 Plan 모드로 시작**한다. 프로젝트 개요는 [[Maestro - 프로젝트 개요 (Index)]].

---

당신은 이 프로젝트의 리드 엔지니어입니다. 아래 시스템 **Maestro**를 **분석 → 설계 → 개발 → 테스트 → 시뮬레이션 → 배포** 순서로 구축합니다. 각 단계 끝에는 **게이트(사용자 승인)** 가 있고, 승인 없이는 다음 단계로 넘어가지 않습니다. 먼저 **Plan 모드**로 Phase 0 계획을 제시하세요.

## 0. 미션 한 줄
순수 자바 코드를 **동적 스크립트**로 작성·컴파일·실행하고, 각 스크립트를 **격리된 병렬 프로세스**로 돌리며, **노드레드식 플로우**로 연결·분산하고, **대시보드**로 상태·리소스를 관측하는 멀티플랫폼 플랫폼.

## 1. 확정된 기술 스택 (변경 시 ADR로 근거 기록)
- **백엔드 오케스트레이터**: Java 21 + Spring Boot (프로세스 관리·스케줄·REST/WebSocket API·메트릭·플로우 라우팅)
- **스크립트 런타임**: **스크립트별 독립 JVM 프로세스**(process-per-script). `javax.tools.JavaCompiler` 인메모리 동적 컴파일 + 커스텀 `ClassLoader`
- **격리**: OS 프로세스 단위 — 크래시·OOM·무한루프가 **다른 프로세스에 영향 없음**
- **데스크탑 앱**: **Electron + React + Monaco Editor + Eclipse JDT Language Server(LSP)** — VSCode급 자바 문법/시맨틱 체크·자동완성·진단. Win/Mac/Linux 멀티플랫폼
- **플로우 에디터**: React Flow (노드=스크립트/모듈, 엣지=메시지 경로)
- **빌드**: Gradle 멀티모듈(JVM) + pnpm(Electron). CI: GitHub Actions
- **저장소 구조(모노레포)**:
  ```
  maestro/
  ├─ sdk/        # 스크립트 SDK: 라이프사이클 인터페이스 + Context API (순수 자바 lib)
  ├─ runner/     # 스크립트 러너(스크립트별 JVM): 동적 컴파일 + 라이프사이클 + IPC
  ├─ backend/    # Spring Boot 오케스트레이터
  ├─ protocol/   # backend↔runner, backend↔desktop 공용 메시지 스키마
  ├─ desktop/    # Electron + React + Monaco + JDT LS
  ├─ docs/       # 분석·설계·ADR·시뮬레이션 리포트
  └─ deploy/     # Docker, electron-builder, CI/CD
  ```

## 2. 요구사항

### 기능 요구사항 (FR)
1. **FR-1 스크립트 작성**: 순수 자바로 스크립트 작성. 작성 시점에 **문법·시맨틱 체크**(Monaco+JDT LS), 컴파일 시점에 **컴파일러 진단**을 UI에 표시.
2. **FR-2 동적 컴파일**: 실행 시 소스를 인메모리 컴파일 → 클래스 로드 → 인스턴스화.
3. **FR-3 라이프사이클**:
   - `onStart()` — 최초 실행 시 **정확히 1회**
   - `onTick()` — 지정한 **실행 주기**마다 반복
   - `onEnd()` — 종료/중지 시 **정확히 1회**(정상 종료·사용자 중지·에러 종료 모두 보장; best-effort 명시)
4. **FR-4 병렬 실행**: 여러 스크립트를 동시에 독립 프로세스로 실행.
5. **FR-5 결함 격리**: 한 프로세스의 예외/크래시/OOM/행이 다른 프로세스에 영향 없음. tick 단위 예외는 격리(계속 vs 중지 정책 설정 가능), 프로세스 사망은 감시 후 **재시작 정책** 적용.
6. **FR-6 대시보드**: 프로세스별 상태(실행/중지/에러), 가동시간, tick 수·에러 수, **CPU·메모리 점유율**, 최근 로그를 실시간 표시.
7. **FR-7 플로우(노드레드식)**: 노드(스크립트/모듈)를 선으로 연결 → 노드가 emit한 메시지를 하류 노드로 라우팅하여 **처리 분산**.
8. **FR-8 모듈**: 스크립트(또는 서브플로우)를 입출력 포트를 가진 **재사용 모듈**로 패키징·버전관리·다중 인스턴스화.

### 비기능 요구사항 (NFR)
- **NFR-1 멀티플랫폼**: 데스크탑 Win/Mac/Linux, 백엔드 컨테이너.
- **NFR-2 관측성**: 구조적 로그, 프로세스/플로우 메트릭, 헬스체크.
- **NFR-3 보안/샌드박싱**: 스크립트는 임의 자바 실행 → **프로세스당 리소스 제한**(`-Xmx`, ulimit/cgroup), 최소 권한, 네트워크/파일 접근 정책(허용 목록), 비밀정보 비주입. *임의 코드 실행 위험을 Phase 1에서 위협 모델링.*
- **NFR-4 복원력**: 러너 사망 감지·재시작, tick 워치독(행 감지), 백프레셔.
- **NFR-5 성능**: 동시 N개(목표치는 Phase 0에서 합의) 프로세스 안정 운용, 플로우 메시지 처리량 측정.

## 3. 핵심 기술 계약 (설계 단계에서 확정·정교화)

### 3-1. 스크립트 SDK — 라이프사이클 & Context
```java
// sdk: 사용자는 이 베이스를 상속해 스크립트를 작성
public abstract class Script {
    protected ScriptContext ctx;
    public void onStart() {}              // 최초 1회
    public void onTick()  {}              // 주기마다
    public void onEnd()   {}              // 종료/중지 시 1회
}

public interface ScriptContext {
    Logger log();
    <T> T param(String key, Class<T> type);          // 실행 파라미터
    void emit(String port, Object message);          // 하류 노드로 전송(플로우)
    void onMessage(String port, Consumer<Object> h); // 상류 메시지 수신
    KeyValueStore state();                            // (선택) 재시작 간 상태
}
```
- onTick은 예외를 잡아 격리(정책: continue/stop). onStart 실패 시 프로세스는 ERROR 상태로 전이 후 onEnd 호출.

### 3-2. 동적 컴파일 (runner)
- `javax.tools.JavaCompiler` + `JavaFileManager`(인메모리) + `DiagnosticCollector`(진단을 백엔드/UI로 전달) + 격리 `ClassLoader`.
- 컴파일 산출물 캐시, 소스 해시 기반 무효화.

### 3-3. 프로세스 & IPC
- 백엔드가 `ProcessBuilder`로 러너 JVM 기동. **제어·텔레메트리 채널**은 로컬 gRPC 또는 WebSocket/길이구분 JSON(설계에서 택1).
- 러너는 상태/로그/메트릭(heap·CPU via `OperatingSystemMXBean`)을 주기 보고. 백엔드는 OS 레벨 메트릭(OSHI)으로 보강.
- 종료 프로토콜: graceful stop(onEnd 보장) → 타임아웃 시 강제 종료.

### 3-4. 플로우 & 모듈
- 플로우 그래프(노드/엣지) 데이터 모델. 런타임은 노드 emit → 백엔드 라우팅 → 하류 노드 수신(초기엔 백엔드 릴레이, 확장 시 브로커). 사이클·백프레셔 정책 정의.
- 모듈 = 입출력 포트·버전·파라미터 스키마를 가진 패키지. 인스턴스화 시 독립 프로세스.

## 4. 단계별 실행 계획 (각 단계: 산출물 + 게이트 + 완료기준)

> 각 Phase는 **Plan 모드로 계획 제시 → 승인 → 구현 → 테스트 → `/docs`에 결과 기록 → 게이트**. 핵심 결정은 ADR(`docs/adr/NNN-*.md`)로 남깁니다.

- **Phase 0 — 분석**: 유스케이스, 용어집, 제약, 동시성 목표치, 위협 모델 초안, **미해결 결정 목록(+권장안)**. 산출물 `docs/00-analysis.md`. **게이트.**
- **Phase 1 — 설계**: 시스템 아키텍처 다이어그램, 라이프사이클 상태기계, 프로세스 감시/재시작 모델, IPC 프로토콜, 플로우/모듈 데이터 모델, OpenAPI, DB 스키마, ADR(프로세스격리·Electron·JDT 확정), `sdk` 인터페이스 스텁. 산출물 `docs/01-architecture.md`. **게이트.**
- **Phase 2 — 스캐폴딩**: 모노레포·Gradle 멀티모듈·pnpm·린트·CI 스켈레톤·`CLAUDE.md`(빌드·규칙). 완료: `./gradlew build`·`pnpm build` 통과.
- **Phase 3 — 스크립트 엔진(핵심·최우선 검증)**: `sdk` + 동적 컴파일 + 러너(컴파일→onStart→스케줄 onTick→onEnd) + tick 예외 격리 + CLI 단독 실행. 완료: 샘플 스크립트가 OnStart 1회·OnTick 주기·OnEnd 1회 **단위 테스트로 증명**.
- **Phase 4 — 백엔드 오케스트레이터**: 프로세스 기동/감시/재시작, 스케줄, REST+WS API, 영속화, **프로세스별 CPU·메모리 메트릭**, 로그 스트리밍. 완료: 한 프로세스 강제 종료해도 나머지 정상 + 재시작 정책 동작.
- **Phase 5 — 데스크탑 앱**: Electron+React+Monaco, **JDT LS 연동**(SDK를 클래스패스로 제공해 onStart/onTick/onEnd·Context 자동완성·진단), 스크립트 CRUD·실행/중지·로그/상태 뷰. 완료: 멀티플랫폼 빌드 + 작성→실행→상태 확인 왕복.
- **Phase 6 — 플로우 & 모듈**: React Flow 캔버스, 와이어링 모델, 프로세스 간 메시지 라우팅, 모듈 패키징·인스턴스화. 완료: 2+ 노드를 선으로 이어 메시지 분산 처리 시연.
- **Phase 7 — 대시보드**: 프로세스 상태 + CPU/메모리 차트 + 로그 실시간(WS). 완료: 부하 변화가 대시보드에 정확 반영.
- **Phase 8 — 테스트**: 단위(엔진·컴파일러·라이프사이클), 통합(backend↔runner·API), e2e(desktop→backend→runner), SDK/프로토콜 계약 테스트. 완료: 커버리지·CI 그린.
- **Phase 9 — 시뮬레이션**: 시나리오 하니스 — 병렬 N개 기동, **결함 주입**(onTick 예외, OOM, 무한루프, 러너 `kill -9`) 후 **격리·대시보드 정확성·재시작 검증**, 부하/소크·플로우 처리량 측정. 산출물 `docs/09-simulation-report.md`.
- **Phase 10 — 배포**: 백엔드 Docker화, 데스크탑 설치본(electron-builder, Win/Mac/Linux), GitHub Actions CI/CD, 릴리스 절차, 설정/비밀 관리, 문서(README·사용자 가이드·SDK 레퍼런스).

## 5. 작업 방식 (반드시 준수)
- 각 Phase **Plan 모드 → 승인 → 구현**. 큰 결정·스택 변경·스키마 변경은 먼저 물어보고 ADR 기록.
- **작게, 자주** 커밋. 각 기능에 테스트 동반, 변경 후 빌드/테스트로 검증.
- `CLAUDE.md`에 빌드·실행·규칙을 유지. 보안(NFR-3)은 코드 작성 내내 고려.
- 불확실하면 추측하지 말고 질문. 가정은 명시.

## 6. Phase 0에서 사용자에게 확인할 결정들
- 동시 실행 프로세스 **목표 규모**(예: 수십/수백)와 호스트 사양
- 스크립트 **신뢰 모델**(신뢰된 사용자만 vs 완전 샌드박스 필요) → 보안 강도 결정
- 영속 저장소(Postgres vs 임베디드 H2), 메트릭 보관 방식(인메모리 링버퍼 vs 시계열 DB)
- 플로우 메시지 버스(백엔드 릴레이로 충분 vs Redis/Kafka 필요)
- 인증(로컬 단독 vs 다중 사용자/계정)

## 7. 전체 인수 기준 (Definition of Done)
- 데스크탑에서 자바 스크립트 작성 시 실시간 문법/시맨틱 진단 → 실행 시 동적 컴파일.
- OnStart 1회 / OnTick 주기 / OnEnd 1회가 **보장**되고 테스트로 증명.
- 다수 스크립트가 병렬 독립 프로세스로 실행되고, 하나의 실패가 다른 것에 영향 없음(시뮬레이션으로 입증).
- 대시보드에서 프로세스별 상태·CPU·메모리·로그 실시간 확인.
- 노드레드식 플로우로 노드를 연결해 처리 분산, 모듈로 재사용.
- Win/Mac/Linux 설치본 + 백엔드 컨테이너 배포, CI/CD 동작, 문서 완비.

**지금 할 일**: 이 요구사항을 검토하고 **Phase 0(분석) 계획**을 Plan 모드로 제시하세요. 불명확한 점은 §6 기준으로 먼저 질문하세요.

---

## 🔗 관련
- [[Maestro - 프로젝트 개요 (Index)]]

#project #maestro #claude-code #prompt #reference
