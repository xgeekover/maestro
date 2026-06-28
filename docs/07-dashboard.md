# Maestro — Phase 7 대시보드

> 상태: **구현·검증 완료, 게이트 대기** · 작성일 2026-06-28
> 모듈: `backend`(집계 API) + `desktop`(시각화)
> 다음: 승인 시 **Phase 8 — 테스트**

프로세스별 상태 + CPU/메모리 차트 + 실시간 로그를 한눈에 관측(FR-6, NFR-2).

## 구현 산출물

### 백엔드 집계
- `TelemetryStore.latest(runId)` — 런별 최신 메트릭.
- `DashboardController` `GET /api/dashboard` — 모든 실행의 **상태 + 최신 메트릭(CPU·heap·tick·error·uptime)** 일괄 제공(개요용, N개 WS 연결 회피).
- (기존) 프로세스별 메트릭/로그 링버퍼 + WS `/ws/runs/{id}/logs|metrics` 재사용.

### 데스크탑 대시보드
- `components/Sparkline` — **의존성 없는 SVG 라인 차트**(번들 경량 유지).
- `components/Dashboard` — 프로세스 **그리드 카드**(상태 점·CPU%·Heap MB·tick/error/uptime) + **CPU/메모리 시계열 스파크라인**(1초 폴링 누적, 최근 60포인트) + 선택 프로세스 **실시간 로그**(WS).
- `App` — **대시보드 탭** 추가(스크립트·플로우·대시보드).

## 검증 (완료기준 증명)

### 통합 테스트 `DashboardIntegrationTest` (실측, 통과)
- 부하 스크립트를 실행 → `GET /api/dashboard`에 해당 프로세스가 **RUNNING + 최신 메트릭(heap>0, tick·heapMax 노출)** 으로 반영됨을 검증 → **부하/상태가 대시보드 데이터 소스에 정확 반영**.

### 데스크탑
- `pnpm typecheck`·`pnpm build` 그린(대시보드·스파크라인 포함).

> 헤드리스라 차트 렌더 GUI는 미검증. **데이터 소스(/api/dashboard)의 정확 반영은 통합 테스트로 증명**, 차트는 그 데이터를 폴링/WS로 시각화.

## 백엔드 테스트 현황 (누적)
`./gradlew build` 그린 — 백엔드 8개(Dashboard 1 · Flow 라우팅 2 · DAG 3 · 격리/재시작 1 · REST 왕복 1) + 러너 단위 12개.

## 한계 / 이월
- 대규모(수백) 시 개요는 폴링/집계로 처리(개별 WS 회피). 차트 상세는 선택 프로세스 위주.
- 히스토리 장기 보관·차트 줌/팬은 후속(현재 인메모리 링버퍼 최근 구간).

## Phase 7 게이트 체크리스트
- [x] 프로세스 상태 개요(/api/dashboard) + 그리드 시각화
- [x] CPU/메모리 **시계열 차트**(SVG 스파크라인)
- [x] 실시간 로그(WS) 연동
- [x] **통합 테스트로 부하 반영 검증**, `pnpm build`·`./gradlew build` 그린
- [ ] **사용자 승인(게이트)** → Phase 8 착수
