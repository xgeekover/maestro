# Maestro — Phase 5 데스크탑 앱

> 상태: **구현·검증 완료(헤드리스 한계 명시), 게이트 대기** · 작성일 2026-06-28
> 모듈: `desktop` (Electron + React + Vite + Monaco) + 백엔드 REST/WS
> 다음: 승인 시 **Phase 6 — 플로우 & 모듈(React Flow)**

데스크탑에서 자바 스크립트를 작성·저장하고, 백엔드로 실행/중지하며, 상태·로그·메트릭을 실시간으로 본다.

## 구현 산출물

### UI (`desktop/src`)
- `App.tsx` — 3분할 레이아웃(스크립트 목록 · Monaco 에디터 · 실행/메트릭/로그).
- `components/ScriptList` · `ScriptEditor`(Monaco) · `RunPanel`(상태/중지) · `MetricsPanel` · `LogsPanel`.
- `api/client.ts` — REST(scripts CRUD, runs run/stop/list/get/metrics/logs).
- `api/ws.ts` — `/ws/runs/{id}/logs|metrics` 실시간 구독.
- `monaco/setup.ts` — Vite 번들 Monaco + editor worker(오프라인 Electron 호환) + Java 보조 등록.
- `monaco/javaAssist.ts` — **SDK API 기반 경량 자동완성/호버**(onStart/onTick/onEnd·ctx.* 스니펫·골격). JDT LS 미연동 시 폴백.

### CRUD 보강 (backend)
- `ScriptController`/`ScriptService`에 **PUT(수정)·DELETE(삭제)** 추가 → 데스크탑 스크립트 CRUD 완성.

### JDT LS 연동 (스파이크)
- `electron/jdtls.ts` — JDT LS를 별도 자바 프로세스(stdio LSP)로 기동하는 모듈. **SDK jar를 워크스페이스 클래스패스로 제공**해 onStart/onTick/onEnd·Context 자동완성·진단을 가능케 하는 설계. 미설치 시 `isAvailable()=false` → 경량 보조로 폴백.
- `scripts/fetch-jdtls.mjs` — JDT LS 배포본 다운로드(`pnpm fetch:jdtls`).
- `electron/preload.ts` — `backendUrl` 브리지(환경변수 오버라이드).

## 검증

### 빌드/타입 (헤드리스, 실측)
| 명령 | 결과 |
|---|---|
| `pnpm typecheck` (tsc) | ✅ 오류 0 |
| `pnpm build` (vite) | ✅ Monaco 풀 번들(Java 언어·editor worker·codicon) 생성, 1101 모듈 |

> 메인 청크 ~3.5MB(Monaco 특성). 동적 import/단일 언어 번들로의 최적화는 후속 과제(빌드 경고, 비차단).

### 작성→실행→상태 확인 왕복 (헤드리스 대체 증명)
GUI 클릭 왕복은 디스플레이가 없어 직접 검증 불가 → **데스크탑이 호출하는 REST 흐름을 백엔드 통합 테스트로 왕복 검증**:
- `RestRoundTripTest` (`@SpringBootTest(RANDOM_PORT)`): **POST /api/scripts(작성) → POST /api/runs(실행) → GET /api/runs/{id}=RUNNING + 메트릭 수신(상태) → POST /stop → STOPPED(중지)** 를 실제 HTTP + 실제 러너 프로세스로 통과.
- 이는 UI의 `api/client.ts`가 호출하는 정확한 엔드포인트/계약을 증명한다.

## 헤드리스 환경의 검증 한계 (투명 고지)
- **Electron GUI 클릭 왕복**: 디스플레이 필요 → 본 환경에서 미검증. 코드/빌드/계약(REST)은 검증됨.
- **JDT LS 풀 시맨틱 완성**: `pnpm fetch:jdtls`(≈100MB+) + JDK + 디스플레이 + monaco-languageclient 연동이 필요 → 본 단계는 **기동 모듈·다운로드·클래스패스 제공 설계 + 경량 SDK 보조**까지. 풀 LSP 브리지(렌더러 monaco-languageclient ↔ Electron stdio)는 디스플레이 환경의 후속 스파이크.

## 실행 방법 (수동 검증용)
```bash
# 1) 백엔드 (러너 클래스패스 제공)
./gradlew :runner:installDist
MAESTRO_RUNNER_CLASSPATH="$PWD/runner/build/install/runner/lib/*" ./gradlew :backend:bootRun
# 2) 데스크탑
cd desktop && pnpm install && pnpm dev      # 개발 서버
#   (선택) pnpm fetch:jdtls 로 JDT LS 활성화 후 Electron 실행
```

## Phase 5 게이트 체크리스트
- [x] Electron + React + **Monaco**(Java 언어·SDK 보조 자동완성/호버)
- [x] 스크립트 **CRUD**(목록·작성·수정·삭제) + 백엔드 PUT/DELETE
- [x] 실행/중지 + 상태·로그·메트릭 실시간 뷰(REST/WS)
- [x] JDT LS 연동 **스파이크**(기동 모듈·다운로드·클래스패스 설계) + 경량 폴백
- [x] `pnpm typecheck`·`pnpm build` 그린 + **REST 라운드트립 테스트**로 작성→실행→상태→중지 왕복 증명
- [ ] (디스플레이 필요) GUI 클릭 왕복 · JDT LS 풀 시맨틱 — 후속
- [ ] **사용자 승인(게이트)** → Phase 6 착수
