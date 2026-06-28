# Maestro — Phase 4 백엔드 오케스트레이터

> 상태: **구현·검증 완료, 게이트 대기** · 작성일 2026-06-28
> 모듈: `backend` (+ `runner` 연결 모드, `protocol`)
> 다음: 승인 시 **Phase 5 — 데스크탑(Monaco + JDT LS)**

백엔드가 러너를 **독립 프로세스로 기동**하고 gRPC로 텔레메트리를 수신하며, **프로세스 사망을 감지·재시작**하고 REST/WS로 노출한다. 완료기준(한 프로세스 강제종료해도 나머지 정상 + 재시작 정책)을 통합 테스트로 입증.

## 아키텍처 흐름

```
REST POST /api/runs ─▶ Supervisor.startRun ─▶ ProcessManager.spawn
                                                   │ java -cp … RunnerMain --connect host:port --run-id … --token …
                                                   ▼
                                          [러너 JVM] ── gRPC Session ──▶ Hello(토큰)
   StartCommand(source,period,limits) ◀── 검증 후 송신 (RunnerGatewayService→Supervisor)
   StatusReport / LogRecord / MetricSample ──▶ RunRegistry · TelemetryStore(링버퍼) ──▶ WS 푸시
   StopCommand ◀── stopRun (graceful → grace 후 강제 종료)
   [프로세스 사망] ──▶ Supervisor 워치독 감지 ──▶ exponential backoff 재시작(한도 초과 시 ERROR 격리)
```

핵심 설계: **러너가 백엔드로 아웃바운드 접속**(인바운드 포트 불필요) — 수백 프로세스 확장 용이(설계 §2).

## 구현 산출물

### 러너 연결 모드 (`runner/.../grpc`)
- `RunnerClient` — Session 스트림 개시·Hello·StartCommand로 `LifecycleEngine` 구동·StopCommand 처리.
- `GrpcContext` — emit/onMessage/param/state(인메모리) + `GrpcLogger`(LogRecord 스트리밍).
- `GrpcTelemetryListener` — 엔진 이벤트 → StatusReport/LogRecord, 통계 갱신.
- `MetricSampler` — heap/CPU(OperatingSystemMXBean)·tick·error 주기 보고.
- `RunnerMain --connect …` 모드 추가(단독 모드와 공존).

### 백엔드 (`backend`)
- **gRPC**: `GrpcServer`(io.grpc 서버 라이프사이클) + `RunnerGatewayService`(bidi Session: Hello 토큰검증·텔레메트리 반영).
- **프로세스**: `ProcessManager`(ProcessBuilder로 러너 JVM 기동, `-Xmx`·`-cp`·토큰) + `RunRegistry`(인메모리) + `RunInfo`/`RunStatus`/`RunConfig`.
- **감시/재시작**: `Supervisor` — 워치독(`Process.isAlive`)·exponential backoff 재시작(O-6)·crash loop 격리·graceful stop.
- **텔레메트리**: `TelemetryStore` + `RingBuffer`(프로세스당 메트릭/로그 링버퍼, D3) + Spring 이벤트.
- **REST**: `ScriptController`(H2 JPA CRUD-lite) + `RunController`(run/stop/list/get/metrics/logs).
- **WS**: `TelemetrySocketHandler` — `/ws/runs/{runId}/logs|metrics` 실시간 푸시.
- **영속화**: H2 + JPA(ddl-auto) `ScriptEntity`. (Flyway/소유권 FK는 Phase 5 인증과 함께 정렬.)

## 검증 (완료기준 증명)

### 통합 테스트 `OrchestrationIntegrationTest` (실측, 통과)
`@SpringBootTest`로 컨텍스트 기동(gRPC 포트 0) → `supervisor.startRunWithSource`로 **무한 tick 스크립트 3개를 독립 프로세스로 기동**:

| 단계 | 검증 |
|---|---|
| 기동 | 3개 러너 프로세스 spawn → gRPC 접속 → 전부 **RUNNING** + 메트릭 수신 |
| **격리** | 한 프로세스 `destroyForcibly()`(kill -9 시뮬) 후 나머지 2개 **tick 계속 증가 + RUNNING 유지 + 프로세스 생존** |
| **재시작** | 감시자가 사망 감지 → **재시작(restartCount≥1) → RUNNING 복귀** |

실측 로그 발췌:
```
gRPC RunnerGateway 서버 시작 — 포트 56069
러너 기동 × 3  →  러너 접속 × 3        (3개 독립 프로세스 RUNNING)
프로세스 사망 감지 runId=686e…          (kill 감지)
러너 기동 runId=686e…  →  러너 접속 runId=686e…   (같은 runId 재시작·재접속)
```
→ **격리·재시작 정책 동작 입증.** (러너 12개 단위 테스트 + 백엔드 통합 1개, `./gradlew build` 그린)

## REST/WS 요약
- `POST /api/scripts` `{name, source}` → 저장 · `GET /api/scripts[/{id}]`
- `POST /api/runs` `{scriptId, tickPeriodMs, params, stopOnError, maxHeapBytes, tickTimeoutMs, errorThreshold}` → 기동
- `GET /api/runs[/{id}]` · `POST /api/runs/{id}/stop` · `GET /api/runs/{id}/metrics|logs`
- WS `/ws/runs/{id}/logs` · `/ws/runs/{id}/metrics`

## 한계 / 이월 (문서화)
- **인증(JWT/소유권)**: Phase 4 미포함 → **Phase 5**에서 desktop 로그인과 함께 구현, 그때 Flyway 스키마(owner FK)로 전환(O-1/O-8).
- **플로우 라우팅(EMIT)**: 수신만, 라우팅은 **Phase 6**.
- **러너 클래스패스**: 개발/테스트는 `java.class.path`. 운영 패키징(러너 배포본 경로 주입)은 **Phase 10**.
- **자기완료 vs 재시작 레이스**: 연결 모드 스크립트는 무한 tick(중지/정책으로만 종료)이라 미해당. 자기완료 신호는 후속 정교화.
- **OSHI 교차검증**: 현재 러너 자기보고(`OperatingSystemMXBean`)만. OS 레벨 보강은 후속.

## Phase 4 게이트 체크리스트
- [x] 러너 독립 프로세스 기동(process-per-script) + gRPC 텔레메트리
- [x] 프로세스 감시 + exponential backoff 재시작 + crash loop 격리
- [x] REST(scripts/runs) + WS(logs/metrics) API
- [x] 프로세스별 CPU/메모리 메트릭(링버퍼) + 로그 스트리밍
- [x] H2/JPA 영속화(스크립트)
- [x] **통합 테스트로 격리+재시작 증명**, `./gradlew build` 그린
- [ ] **사용자 승인(게이트)** → Phase 5 착수
