# Maestro — Phase 3 스크립트 엔진 (핵심)

> 상태: **구현·검증 완료, 게이트 대기** · 작성일 2026-06-28
> 모듈: `runner` (+ `sdk`) · 선행: [설계](01-architecture.md) §4·§7
> 다음: 승인 시 **Phase 4 — 백엔드 오케스트레이터**(러너를 프로세스로 기동/감시/재시작 + gRPC + REST/WS)

프롬프트가 **"핵심·최우선 검증"** 으로 지정한 단계. 동적 컴파일과 라이프사이클 보장을 단독 실행 + 단위 테스트로 증명한다.

## 구현 산출물

### 동적 컴파일 (`runner/.../compile`, FR-2)
- `InMemoryCompiler` — `javax.tools.JavaCompiler` + 인메모리 `JavaFileManager`(`InMemoryFileManager`) + `DiagnosticCollector`. 소스에서 public 클래스 FQN 추론, 런타임 클래스패스로 SDK 해석.
- `IsolatedClassLoader` — 부모를 SDK 로더로 두어 `Script` 타입 공유(ClassCastException 방지), 스크립트 클래스만 메모리 바이트코드에서 정의.
- `CompilationResult` / `Diag` — 성공 여부 + 진단(에러/경고/노트) + 로드된 클래스.

### 라이프사이클 엔진 (`runner/.../engine`, FR-3·FR-5)
- `LifecycleEngine` — 상태기계 `COMPILING→STARTING→RUNNING→STOPPING→STOPPED`(+`ERROR`) 구동.
  - **onStart 정확히 1회**(성공 시), 실패 시 ERROR 분류 후 onEnd 호출.
  - **onTick 주기 반복**, 예외 격리(`TickPolicy.CONTINUE`/`STOP` + 누적 에러 임계 `errorThreshold`).
  - **onEnd 정확히 1회**(인스턴스 생성 후 어떤 경로로 끝나든; best-effort).
  - 모든 스크립트 메서드는 단일 "스크립트 스레드"에서 실행, **타임아웃 워치독**으로 행(hang) 감지(인프로세스 best-effort; 진짜 행/OOM은 Phase 4 OS 레벨 종료).
- `EngineConfig`(주기·maxTicks·정책·임계·타임아웃), `EngineResult`(검증용 카운트), `LifecycleListener`(관측 훅).
- `StandaloneContext` / `SimpleLogger` / `InMemoryKeyValueStore` — 백엔드 없이 동작하는 `ScriptContext` 구현(emit 버퍼·onMessage 시뮬레이션·KV 상태).

### CLI 단독 실행
- `RunnerMain` — `runner <script.java> [--period ms] [--ticks n] [--policy continue|stop] [--tick-timeout ms] [--error-threshold n] [--param k=v]`. Ctrl+C → graceful 중지(onEnd 보장). 종료코드: 정상 0 / 에러 1.
- 예제: [`runner/examples/HeartbeatScript.java`](../runner/examples/HeartbeatScript.java).

## 검증 (완료기준 증명)

### 단위 테스트 — 12개 통과 (`./gradlew :runner:test`)
| 테스트 | 증명 내용 |
|---|---|
| `onStartOnce_onTickPeriodic_onEndOnce` | **OnStart 1회·OnTick 주기 N회·OnEnd 1회** (엔진 카운트 + 컨텍스트 상태 양쪽으로) |
| `onTickRespectsPeriod` | 주기 실행의 시간 하한 |
| `tickExceptionIsolation_continueKeepsTicking` | 매 tick 예외에도 5회 지속 + onEnd 1회 (격리=STOPPED) |
| `tickExceptionPolicy_stopHaltsOnFirstError` | STOP 정책: 첫 예외에서 중지 + onEnd 1회 |
| `errorThreshold_stopsAfterThreshold` | CONTINUE 누적 임계 도달 시 중지 |
| `onStartFailure_transitionsToErrorThenOnEnd` | onStart 실패 → ERROR → onEnd 1회, tick 미진입 |
| `compileFailure_errorWithDiagnosticsNoOnEnd` | 컴파일 실패 → ERROR + 진단, onEnd 없음(인스턴스 부재) |
| `userStop_endsGracefullyWithOnEndOnce` | 사용자 중지 → STOPPED + onEnd 1회 |
| `emitAndOnMessageWork` | emit 버퍼 + onMessage 핸들러 동작 |
| (`InMemoryCompilerTest` ×3) | 유효 컴파일·진단 수집·FQN 추론 |

### CLI 스모크 (실측)
```
$ ./gradlew :runner:run --args="examples/HeartbeatScript.java --ticks 3 --period 200"
INFO [HeartbeatScript] 하트비트 시작
INFO [HeartbeatScript] beat #1 / #2 / #3   (각 tick emit)
INFO [HeartbeatScript] 종료 — 총 3 beats
=== 결과 === state=STOPPED onStart=1 ticks=3 tickErrors=0 onEnd=1
```

## 한계 / Phase 4 이월
- **인프로세스 행/OOM 한계**: 단일 JVM 단독 실행에서는 진짜 무한루프/OOM을 강제 회수 불가 → Phase 4에서 **process-per-script + OS 레벨 감독/kill**로 격리(FR-5 완성).
- **SDK 클래스패스 제공**: 현재 `java.class.path` 의존 → Phase 4에서 백엔드가 러너 기동 시 `-cp`로 SDK jar 명시.
- **텔레메트리**: 현재 콘솔 리스너 → Phase 4에서 gRPC `StatusReport`/`LogRecord`/`MetricSample`로 스트리밍.
- **컴파일 캐시**: 소스 해시 기반 캐시는 미적용(설계만) → 필요 시 Phase 4/성능 단계에서 추가.

## Phase 3 게이트 체크리스트
- [x] 동적 컴파일(JavaCompiler 인메모리 + 격리 ClassLoader) + 진단
- [x] 라이프사이클 엔진(onStart 1 / onTick 주기 / onEnd 1) + 상태기계
- [x] tick 예외 격리(CONTINUE/STOP/임계) + 행 워치독
- [x] CLI 단독 실행 + 예제 스크립트
- [x] 단위 테스트로 보장 증명(12개 그린), `./gradlew build` 그린
- [ ] **사용자 승인(게이트)** → Phase 4 착수
