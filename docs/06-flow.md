# Maestro — Phase 6 플로우 & 모듈

> 상태: **구현·검증 완료, 게이트 대기** · 작성일 2026-06-28
> 모듈: `backend`(flow/module 라우팅) + `desktop`(React Flow) + `runner`(emit/deliver)
> 다음: 승인 시 **Phase 7 — 대시보드**

노드(스크립트/모듈)를 선으로 연결해 메시지를 하류로 라우팅, 처리 분산(FR-7). 모듈로 재사용(FR-8).

## 구현 산출물

### 백엔드 플로우 라우팅 (`backend/flow`)
- `FlowModel` — 노드(`id, kind, refId, params, tickPeriodMs`)·엣지(`fromNode/fromPort → toNode/toPort`)·그래프.
- `FlowValidator` — **DAG 강제**(Kahn 위상정렬로 사이클 검출, 무결성 검사) → 위반 시 422(O-9).
- `FlowEntity`/`FlowService` — H2 영속 + 그래프 JSON 직렬화.
- `FlowDeployment` — 배포 런타임: 노드별 러너 + **라우팅 테이블** + **바운디드 큐 백프레셔**(drop-oldest, 디스패처 스레드).
- `FlowRuntime` — `deploy`(노드 기동) · `route`(emit→하류 DeliverMessage) · `stop`.
- 게이트웨이 **EMIT 배선**: 러너 emit → `FlowRuntime.route` → 라우팅 테이블 → 하류 노드 러너로 `DeliverMessage`.
- `RunInfo.sendCommand` — 스트림별 송신 직렬화(스레드 안전).

### 모듈 (`backend/module`)
- `ModuleEntity`/`ModuleService` — 이름·버전(semver)·포트 스펙(spec)·소스(artifact) 패키징. 플로우 노드 `kind=MODULE`가 모듈 소스로 인스턴스화(독립 프로세스).

### REST
- `POST/GET/DELETE /api/flows`, `POST /api/flows/{id}/deploy|stop` (DAG 위반 422).
- `POST/GET /api/modules`.
- `GlobalExceptionHandler` — IllegalArgument→422, IllegalState→409.

### 데스크탑 (React Flow)
- `components/FlowCanvas` — reactflow 캔버스: 스크립트로 노드 추가, 엣지 연결, 저장/배포, 플로우 열기.
- `App` — **스크립트/플로우 뷰 전환** 탭.

## 검증 (완료기준 증명)

### 통합 테스트 `FlowRoutingIntegrationTest` (실측, 통과)
- **`emitFromOneNodeIsRoutedToDownstreamNode`**: producer/consumer 스크립트로 2노드 플로우(`p.out → c.in`)를 배포 → producer가 독립 프로세스에서 `emit("out", n)` → 백엔드 라우팅 → consumer 프로세스가 `onMessage("in", …)`로 수신·로그(`got N`). **consumer 로그에 수신 메시지 확인 → 프로세스 간 메시지 분산 처리 시연.**
- **`cyclicFlowIsRejected`**: 사이클 플로우 생성 거부(DAG).

### 단위 테스트 `FlowValidatorTest` (3개 통과)
- DAG 수용 · 사이클 거부 · 무결성(존재하지 않는 노드 참조) 거부.

### 데스크탑
- `pnpm typecheck`·`pnpm build` 그린(React Flow 캔버스 포함).

> 헤드리스라 캔버스 드래그·배포 GUI 왕복은 미검증. **배포→라우팅 흐름은 통합 테스트로 증명**(UI가 호출하는 동일 엔드포인트).

## 한계 / 이월
- **모듈 포트 스펙 강제**: 현재 spec은 메타데이터(런타임 포트 검증 미적용) → 후속.
- **백프레셔 정책**: drop-oldest 기본. block/drop 선택·노드별 큐·메트릭 노출은 후속.
- **사이클/피드백 루프**: DAG만 허용(O-9). 필요 시 ADR로 루프가드 도입.

## Phase 6 게이트 체크리스트
- [x] 플로우 데이터 모델 + **DAG 강제**(사이클 거부)
- [x] 노드 emit → 하류 노드 라우팅(백엔드 릴레이) + 바운디드 큐 백프레셔
- [x] 모듈 패키징(이름·버전·포트 스펙) + 인스턴스화(독립 프로세스)
- [x] React Flow 캔버스(노드/엣지 편집·저장·배포) + 뷰 전환
- [x] **통합 테스트로 2노드 메시지 분산 처리 시연**, `./gradlew build`·`pnpm build` 그린
- [ ] **사용자 승인(게이트)** → Phase 7 착수
