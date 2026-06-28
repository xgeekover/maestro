# Maestro 사용자 가이드

데스크탑 앱으로 자바 스크립트를 작성·실행하고, 플로우로 연결하며, 대시보드로 관측한다.

## 0. 준비
1. **백엔드 실행** (러너 클래스패스 제공 필요):
   ```bash
   ./gradlew :runner:installDist
   MAESTRO_RUNNER_CLASSPATH="$PWD/runner/build/install/runner/lib/*" ./gradlew :backend:bootRun
   ```
   또는 컨테이너: `docker compose -f deploy/docker-compose.yml up --build`.
2. **데스크탑 실행**: `cd desktop && pnpm install && pnpm dev` (백엔드 URL 기본 `http://localhost:8080`, `MAESTRO_BACKEND_URL`로 변경).

## 1. 스크립트 (탭: 스크립트)
- **작성**: `+ 새로` → Monaco 에디터에 자바 작성(골격 자동완성 제공). 이름 입력 후 `생성`/`저장`.
- **실행**: 스크립트 선택 → `▶ 실행`. 우측 **실행** 패널에 상태(RUNNING/STOPPED/ERROR·재시작 횟수) 표시.
- **관측**: 실행 항목 선택 → **메트릭**(tick·error·heap·cpu·uptime) + **로그** 실시간.
- **중지**: 실행 항목의 `중지`(graceful → onEnd 보장).
- **수정/삭제**: 스크립트 선택 후 편집·`저장`(PUT) / `삭제`(DELETE).

## 2. 플로우 (탭: 플로우)
- 스크립트를 **노드**로 추가(드롭다운 선택 → `+ 노드`), 노드 핸들을 드래그해 **엣지** 연결(상류 `out` → 하류 `in`).
- `저장` → 그래프 저장(**사이클은 거부**, DAG만 허용). `▶ 배포` → 각 노드를 독립 프로세스로 기동하고 메시지 라우팅 시작.
- 상류 노드의 `ctx.emit("out", msg)` → 하류 노드의 `ctx.onMessage("in", ...)`로 전달(백엔드 릴레이, 바운디드 큐 백프레셔).
- `플로우 열기`로 기존 플로우 로드.

## 3. 대시보드 (탭: 대시보드)
- 모든 실행 프로세스의 **그리드 카드**: 상태·CPU%·Heap MB·tick/error/uptime + **CPU/메모리 시계열 스파크라인**.
- 카드 선택 → 하단 **실시간 로그**.

## 4. REST API (자동화/연동)
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/scripts` `{name, source}` | 스크립트 생성 |
| GET/PUT/DELETE | `/api/scripts[/{id}]` | 조회·수정·삭제 |
| POST | `/api/runs` `{scriptId, tickPeriodMs, params, stopOnError, maxHeapBytes, tickTimeoutMs, errorThreshold}` | 실행 |
| GET | `/api/runs[/{id}]` | 실행 목록·상태 |
| POST | `/api/runs/{id}/stop` | 중지 |
| GET | `/api/runs/{id}/metrics`·`/logs` | 메트릭·로그 |
| POST/GET/DELETE | `/api/flows[/{id}]` · `/deploy` · `/stop` | 플로우 |
| POST/GET | `/api/modules` | 모듈 |
| GET | `/api/dashboard` | 전체 개요(상태+최신 메트릭) |
| WS | `/ws/runs/{id}/logs`·`/metrics` | 실시간 스트림 |

전체 스키마: [docs/api/openapi.yaml](api/openapi.yaml).

## 5. 자주 묻는 것
- **스크립트가 멈추지 않아요**: `onTick`은 짧게. 무한 루프는 tick 워치독(`tickTimeoutMs`)이 감지해 종료.
- **한 스크립트가 죽으면?**: 다른 스크립트는 영향 없음(프로세스 격리). 돌연사는 감시자가 재시작.
- **상태를 재시작 간 유지?**: `ctx.state()` 사용.
- SDK 상세: [docs/sdk-reference.md](sdk-reference.md).
