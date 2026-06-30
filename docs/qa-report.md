# Maestro — QA 품질 점검 리포트

> 작성: 2026-06-30 · 방식: **라이브 블랙박스 테스트(42케이스) + 코드 정적 감사(7개 차원) + 표적 재현**
> 대상: 백엔드 8081 라이브 인스턴스 + 전 모듈 소스 · 증거표기 🔴=라이브 재현 / 📋=코드 감사(file:line)

## 0. 총평 (Senior QA Verdict)

**핵심 기능은 견고하게 동작한다.** 스크립트 CRUD·라이프사이클·격리·재시작·플로우 라우팅·대시보드·메트릭/로그가 라이브 e2e로 모두 검증됐고(기능 26케이스 전부 PASS), 잘못된 입력·동시 부하·결함 주입에도 백엔드가 무중단 유지된다.

**그러나 "현재 상태는 프로덕션-레디가 아니다."** 본 프로젝트는 의도적으로 *Phase 4 스캐폴드 + 신뢰모델 D2(신뢰된 사용자/내부도구)* 로 설계됐고, **인증·러너 샌드박스·영속성·입력검증**이 설계만 있고 미구현이다. 이 갭들은 문서에 "이월"로 명시돼 있으나, **현재 기본값(와일드카드 CORS, 전 인터페이스 gRPC 평문 바인딩, Docker 포트 노출, 한도 OFF)이 "내부 도구" 가정보다 위험을 넓힌다**. 내부망 전용이라도 **즉시 조일 quick-win**들이 존재한다.

| 심각도 | 건수 | 대표 |
|---|---|---|
| 🟥 Critical | 2 | 무인증 임의코드 실행 · 기본 리소스 한도 OFF |
| 🟧 High | 8 | gRPC 신뢰경계 · 입력검증 부재 · 인메모리 무한증가 · 영속성 없음 · 자가종료 레이스 등 |
| 🟨 Medium | 6 | 관측성 · 페이지네이션 · 플로우 배포 누수 등 |
| 🟦 Low | 3 | 내부메시지 누출 · 스레드 누수 등 |

---

## 1. 기능 검증 — 작동 확인됨 (라이브 e2e)

42케이스 중 **정상기능 26개 전부 PASS**:

| 영역 | 검증 | 결과 |
|---|---|---|
| 헬스/기동 | `/actuator/health` UP | 🔴 PASS |
| 스크립트 CRUD | 생성(201)·목록·조회·수정(PUT)·삭제 | 🔴 PASS |
| 실행 라이프사이클 | run(202)→**RUNNING**→메트릭/로그 수신→stop→**STOPPED** | 🔴 PASS |
| 대시보드 | `/api/dashboard` 상태+최신 메트릭 노출 | 🔴 PASS |
| 플로우 | 생성(201)·배포(202)·**라우팅(소비자 수신)**·중지 | 🔴 PASS |
| 모듈 | 생성·목록 | 🔴 PASS |
| 격리/복원력 | 잘못된 자바→ERROR(백엔드 무중단)·동시 10건 수용·**tickTimeout 지정 시 무한루프 7s 바운드** | 🔴 PASS |
| 검증 | 사이클/댕글링 플로우 422·없는 리소스 404·깨진 JSON 400 | 🔴 PASS |

→ **시스템의 핵심 가치(동적 컴파일·프로세스 격리·플로우 분산·관측)는 실제로 동작한다.**

---

## 2. 결함 — 심각도별

### 🟥 Critical

**C-1. 무인증 임의 코드 실행 / 인증·인가 전무**
- 🔴 모든 `/api`가 무인증 접근(N18). 📋 Spring Security 의존성·`SecurityFilterChain`·auth 컨트롤러 없음. `ScriptController.java:30` + `InMemoryCompiler.java:59-97` → 네트워크 클라이언트가 **임의 자바를 백엔드 사용자 권한으로 컴파일·실행**(파일·네트워크·`Runtime.exec`·`System.exit`).
- 설계상 JWT+소유권은 `openapi.yaml:8-12`·`V1__init.sql:6-31`에 있으나 **엔티티에 owner_id 없음**, Flyway 비활성.
- **맥락**: D2(신뢰된 사용자) 모델로 의도적 이월. 단, 와일드카드 CORS+포트 노출이 신뢰경계를 넓힘.
- **영향**: 내부망 밖 노출 시 host 장악. **권고**: 최소한 토큰/베이식 인증 + 오리진 제한 + gRPC 루프백 바인딩을 auth 본구현 전 선조치.

**C-2. 러너 샌드박스 부재 + 기본 리소스 한도 OFF**
- 🔴 **기본 설정 무한루프가 12초+ ERROR 없이 영구 RUNNING**(재현). `RunController.java:44-45`가 `tickTimeoutMs=0`·`maxHeapBytes=0` 기본 → 워치독·heap 캡 모두 없음. 📋 유일한 한도는 `maxHeapBytes>0`일 때의 `-Xmx`(`ProcessManager.java:49-51`). CPU·파일·네트워크·스레드·벽시계 한도 전무.
- **영향**: 사소한 스크립트가 코어를 영구 점유(호스트 DoS). **권고**: tickTimeout·maxHeap **기본값 강제**(예: 30s/512MB), 서버측 상한 클램프.

### 🟧 High

**H-1. gRPC 신뢰경계 취약** — 🔴 `java *:9090` 전 인터페이스 평문 바인딩(재현). 📋 `usePlaintext()`(`RunnerClient.java:46`), 토큰을 **명령행 인자**로 전달(`ProcessManager.java:55-62`, `ps`로 노출), Docker가 9090 호스트 매핑. → 9090 도달 가능한 누구나 Session 개시 가능. **권고**: 루프백 바인딩 + mTLS/UDS, 토큰을 env/stdin로.

**H-2. 입력 검증 전무** — 🔴 `name=null`→**500**(N9, DataIntegrityViolation), 빈 소스 201(N3), 음수 tickPeriod 202(N11), **1.2MB 소스 201**(N13, 크기 제한 없음). 📋 `spring-boot-starter-validation` 없음, `Dtos.java`의 모든 record 무제약. **권고**: Bean Validation(@NotBlank/@Positive/@Size) + 소스 크기 상한.

**H-3. 예외 매핑 부족 → 잘못된 HTTP 의미** — 🔴 미매핑 예외 500 누출(N9). 📋 `GlobalExceptionHandler`가 IllegalArgument→422, IllegalState→409 둘만 처리. **"없는 리소스"가 422**(should 404, `Supervisor.java:78`·`FlowRuntime.java:56`), **그래프 손상이 409**(should 5xx). **권고**: 표준 에러 엔벨로프(code/timestamp/traceId) + 404/400/409/500 정합.

**H-4. 인메모리 무한 증가 (eviction 없음)** — 🔴 종료 런 16건 누적(N19). 📋 `RunRegistry`에 remove 없음(`RunRegistry.java:12-41`), `TelemetryStore` 링버퍼가 죽은 run도 영구 보유(`TelemetryStore.java:18-19`). → **느린 OOM**. **권고**: 종료 후 TTL 회수 + 상한.

**H-5. 영속성 없음 / 재시작 시 데이터 소실** — 📋 `jdbc:h2:mem`(`application.yaml:6`) + `ddl-auto:update` + Flyway off. 스크립트·플로우·모듈이 재시작 시 소멸, run 이력 테이블 미구현. **권고**: H2 파일/서버모드 또는 Postgres + Flyway, Run 엔티티 영속.

**H-6. hung-but-alive 러너 미감지** — 🔴 기본설정 hang이 영구 RUNNING(C-2와 동일 증거). 📋 `RunInfo.lastTelemetryNanos`는 갱신되나(`RunInfo.java:81`) **읽히지 않음**(dead code), 워치독은 `process.isAlive()`만 검사. CPU 스핀/데드락(프로세스 살아있음)은 영원히 미감지. **권고**: telemetry-silence 워치독 활성화 + Heartbeat.

**H-7. 자가종료 vs 재시작 레이스** — 📋 STOP정책/임계/행으로 스크립트가 스스로 종료하면 `userStopped` 미설정 → 워치독이 종료 상태 적용 전에 `isAlive()`를 보면 **의도적 종료를 재시작**(`Supervisor.java:200-228` vs `RunnerClient.java:99-107`). **권고**: 종료 사유를 백엔드에 명시 전달(CommandAck/완료 신호) 후 재시작 판정.

**H-8. WS 동기 브로드캐스트가 ingestion 스레드 블록** — 📋 `TelemetryStore`가 동기 이벤트 발행(`:28,33`) → `@EventListener`가 gRPC 수신 스레드에서 per-session `synchronized sendMessage`(`TelemetrySocketHandler.java:79-81`). **느린 WS 구독자 하나가 전체 텔레메트리 경로를 백프레셔**(무인증 DoS). **권고**: 비동기 브로드캐스트 + 구독자별 바운디드 큐/드롭.

### 🟨 Medium
- **M-1 관측성 갭** — 🔴 `/actuator/metrics`·prometheus 미노출(N16, health/info만). 📋 micrometer-prometheus 없음, 트레이싱/상관ID 없음, 로그 비구조적. → 오케스트레이터 자체가 표준 도구로 관측 불가.
- **M-2 페이지네이션·필터 없음** — 🔴 전체 반환(N19). 📋 모든 list 엔드포인트 + dashboard가 풀 컬렉션 fan-out.
- **M-3 잘못된 응답 의미(경미)** — 🔴 없는 런 중지 202 no-op(N8, should 404), 내부 메시지 누출 "id must not be null"(N10).
- **M-4 플로우 배포 누수** — 📋 더블 배포 시 이전 deployment 미정지(누수, `FlowRuntime.java:60-61`), 부분배포 롤백 없음, `restartCount` 안정창 미리셋, 강제종료 시 `onEnd` 미보장.
- **M-5 모듈 포트-스펙 미검증** — 📋 `spec_json` 저장만, 파싱·포트 일치 검증 없음(엣지가 선언 안 된 포트 참조 가능).
- **M-6 CORS/WS 와일드카드** — 🔴 OPTIONS 200(N15). 📋 `allowedOrigins("*")`(무인증과 결합 시 위험).

### 🟦 Low
- **L-1** 에러 바디가 원시 내부 메시지(한국어 진단·내부 ID) 노출, 표준 엔벨로프 없음.
- **L-2** 인터럽트 무시 스크립트의 `maestro-script` 스레드 누수 + `safeOnEnd`가 onEnd 미실행에도 1 반환(`LifecycleEngine.java:212-222`).
- **L-3** 강제(non-graceful) 셧다운: `Supervisor.shutdown`이 StopCommand 없이 `destroyForcibly`.

---

## 3. 부족한 기능 (Quality Gap)

**설계됨 but 미구현:**
- 🔑 **인증/소유권**(openapi·V1 스키마 존재, 코드 없음) · **run 이력 영속**(run 테이블 설계, 엔티티 없음)
- 💾 **KeyValueStore 영속**(`StateOp`가 백엔드 no-op, `RunnerGatewayService.java:84-86`; SDK는 "재시작 간 유지" 약속) · **동적 주기변경**(`UpdatePeriodCommand` proto 존재, 러너 무시 `RunnerClient.java:68`) · **Heartbeat/CommandAck**(proto 정의, 미사용)
- 📦 **모듈 포트-스펙 강제** · **런타임 OpenAPI/Swagger**(정적 yaml만, 존재하지 않는 `/api/auth/login` 기술)

**완전 미존재:**
- 스크립트 **버전관리**(in-place 덮어쓰기) · **로그/메트릭 영속·시간범위 쿼리** · **백프레셔 정책 선택**(drop-oldest 하드코딩) · **rate limit·요청 크기 제한·idempotency** · **멀티 인스턴스/HA** · **데스크탑 단위 테스트(Vitest)**

---

## 4. 품질 강화 권고 (우선순위 로드맵)

### ⚡ Quick wins (각 1일 이내, 즉시 위험 감소)
1. **리소스 한도 기본값 강제** (C-2): `RunController`에서 tickTimeout·maxHeap 기본/상한 클램프(예 30s/512MB).
2. **Bean Validation 도입** (H-2): `spring-boot-starter-validation` + `@NotBlank/@Positive/@Size` + 소스 크기 캡 → 500/무검증 제거.
3. **예외 핸들러 확장** (H-3): NPE/DataAccess/Validation→400, NotFound→404, 표준 에러 엔벨로프(traceId).
4. **gRPC 루프백 바인딩 + 토큰 env 전달** (H-1): `*:9090`→`127.0.0.1:9090`, compose 포트 미노출, argv 토큰 제거.
5. **종료 런 TTL 회수** (H-4): 종료 후 N분 뒤 RunRegistry/Telemetry evict + 상한.
6. **CORS 오리진 제한 + actuator metrics/prometheus 노출** (M-1, M-6).

### 🏗️ 중기 (보안·복원력 본구현)
7. **인증/인가** (C-1): JWT + 소유권(설계 자산 활용), 최소 토큰 인증부터.
8. **러너 샌드박스** (C-2): cgroup/seccomp 또는 컨테이너-per-runner, 네트워크/파일 허용목록.
9. **영속성** (H-5): Postgres+Flyway 전환(O-1 경로), Run 이력·KV(StateOp) 영속.
10. **워치독 보강** (H-6/H-7): telemetry-silence + Heartbeat, 자가종료 신호로 재시작 레이스 제거.
11. **WS 비동기화** (H-8): 구독자별 바운디드 큐, ingestion 스레드 분리.

### 📈 장기 (운영·확장)
12. 페이지네이션/필터(M-2) · 동적 주기변경(UpdatePeriod 배선) · 백프레셔 정책 선택 · rate limit/idempotency · 멀티인스턴스 HA · 스크립트 버전관리 · 런타임 OpenAPI/Swagger · 데스크탑 Vitest.

---

## 5. 결론
- ✅ **기능적으로 완성도 높음**: 핵심 가치(동적 컴파일·격리·플로우·관측)가 라이브로 입증.
- ⚠️ **프로덕션 강화 필요**: 보안(인증·샌드박스)·입력검증·영속성·일부 복원력/관측성 갭. 대부분 **설계 자산이 이미 존재**하므로 본구현 비용이 낮다.
- 🎯 **즉시 권고**: 위 Quick win 6종은 신뢰모델을 바꾸지 않고도 위험을 크게 낮춘다 — 우선 적용 권장.

---

## 6. 해결 현황 (Quick wins 적용 — 2026-06-30)

QA 직후 신뢰모델을 바꾸지 않는 Quick win을 3개 배치로 적용·검증 완료(테스트 동반).

| 항목 | 상태 | 조치 | 커밋 |
|---|---|---|---|
| **C-2** 기본 리소스 한도 OFF | ✅ 해결 | `RunConfigFactory`로 tickTimeout/maxHeap **기본값 강제(30s/512MB)** + 상한 클램프(REST·플로우 공통). 무한루프 기본 워치독 적용 | `b7ea21e` |
| **H-4** 인메모리 무한 증가 | ✅ 해결 | 종료 런 **TTL 회수**(RunRegistry/Telemetry evict + 스윕, 기본 10분/상한 1000) | `b7ea21e` |
| **H-2** 입력 검증 전무 | ✅ 해결 | Bean Validation(@NotBlank/@PositiveOrZero/@Size 256KB) + `@Valid`. **null name 500 제거** | `320443c` |
| **H-3** 예외 매핑 부족 | ✅ 해결 | `NotFoundException`→404, 검증→400, 도메인→422, 표준 에러 엔벨로프({status,error,details}), 내부오류 비노출 | `320443c` |
| **H-1** gRPC 신뢰경계 | ✅ 완화 | gRPC **루프백 바인딩(127.0.0.1, 설정화)** — 전 인터페이스 노출 제거. (mTLS·토큰 stdin은 후속) | `(batch3)` |
| **M-6** CORS 와일드카드 | ✅ 해결 | 허용 오리진 **설정화**(기본 localhost), `*` 제거 | `(batch3)` |
| **M-1** 메트릭 미노출 | ✅ 해결 | micrometer-prometheus + `/actuator/metrics`·`/actuator/prometheus` 노출(민감 엔드포인트는 차단 유지) | `(batch3)` |
| **H-5** 영속성 없음(재시작 소실) | ✅ 해결 | H2 **파일모드 + Flyway** 마이그레이션(엔티티 정합 V1) + ddl-auto none. **스크립트/플로우/모듈 재시작 후 보존**. Docker 데이터 볼륨. (Postgres 전환경로 유지) | `(persist)` |
| **H-7** 자가종료 vs 재시작 레이스 | ✅ 해결 | 워치독 **death-grace**(사망 후 종료신호 도착 유예) → 자가종료(STOP/에러/행) 부활 방지, kill -9만 재시작 | `(reliab)` |
| **H-6** onStart 행 미감지 | ✅ 해결 | onStart **기본 타임아웃** 강제(엔진이 바운드 → ERROR). (onTick 행은 C-2의 tickTimeout 기본값으로 이미 바운드) | `(reliab)` |
| **H-8** WS 동기 브로드캐스트 블로킹 | ✅ 해결 | 수신 스레드는 구독자별 **바운디드 큐에 논블로킹 offer**만, 전송은 별도 스레드풀. 느린 구독자는 자기 큐만 드롭, 텔레메트리 경로 무영향 | `(ws)` |
| **run 이력 영속** | ✅ 해결 | `RunHistoryEntity` + V2 마이그레이션. 종료 런을 워치독이 1회 기록 → 재시작 후에도 `GET /api/runs/history`(페이지네이션)·`/{runId}`로 조회 | `(history)` |

**검증 추가 테스트**: `RunConfigFactoryTest`(8)·`RunRegistryEvictionTest`(2)·`EvictionIntegrationTest`(1)·`ValidationContractTest`(14)·`ObservabilityCorsTest`(5)·`PersistenceRestartTest`(1)·`ReliabilityTest`(3)·`TelemetrySocketHandlerTest`(2)·`RunHistoryIntegrationTest`(1). 전체 빌드 그린.

### 남은 권고(미적용)
- 🟥 **C-1 인증/인가 + 러너 샌드박스** — 신뢰모델(D2) 변경이 필요한 본구현. **외부 노출 계획 시** 1순위(현재 로컬 전용 → 보류).
- 🟧 **KV(StateOp) 재시작 영속**(script_state 스키마, V3+).
- 🟨 (목록)페이지네이션 일반화 · 동적 주기변경(UpdatePeriod 배선) · 모듈 포트검증 · 백프레셔 정책 선택 등.
