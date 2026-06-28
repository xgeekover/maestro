# CLAUDE.md — Maestro 빌드·실행·규칙

> 동적 자바 스크립트 플랫폼. 순수 자바를 동적 컴파일·격리 병렬 실행하고, node-RED식 플로우로 연결, 대시보드로 관측한다.
> 기획/요구사항: [`docs/`](docs/) · 분석 [`docs/00-analysis.md`](docs/00-analysis.md) · 설계 [`docs/01-architecture.md`](docs/01-architecture.md) · 결정 [`docs/adr/`](docs/adr/)

## 모노레포 구조
```
maestro/
├─ sdk/        # 스크립트 SDK: 라이프사이클(Script) + Context API (순수 자바 lib)
├─ protocol/   # backend↔runner 공용 gRPC 스키마(.proto) → 코드 생성
├─ runner/     # 스크립트별 독립 JVM: 동적 컴파일 + 라이프사이클 + IPC
├─ backend/    # Spring Boot 오케스트레이터 (Supervisor·REST/WS·메트릭·플로우)
├─ desktop/    # Electron + React + Monaco + React Flow (별도 pnpm 패키지)
├─ deploy/     # Docker, electron-builder, CI/CD
└─ docs/       # 분석·설계·ADR·(예정)시뮬레이션 리포트
```

## 기술 스택 (확정 — 변경 시 ADR)
- **JVM**: Java **21** (Gradle 멀티모듈). 호스트에 JDK 21이 없으면 Gradle 툴체인이 자동 프로비저닝(foojay).
- **러너**: process-per-script + `javax.tools.JavaCompiler` 동적 컴파일 + 격리 ClassLoader.
- **IPC**: **gRPC** 단일 양방향 스트림(`protocol/src/main/proto/maestro.proto`).
- **백엔드**: Spring Boot 3.3, H2(시작) → Postgres 전환경로, 메트릭 인메모리 링버퍼, JWT 인증.
- **데스크탑**: Electron + React + Vite + Monaco + Eclipse JDT LS + React Flow (pnpm).
- 확정 근거: [ADR-0001](docs/adr/0001-phase0-foundational-decisions.md), [ADR-0002](docs/adr/0002-design-decisions-ipc-flow-auth-storage.md), [ADR-0003](docs/adr/0003-confirm-process-isolation-electron-jdt.md).

## 빌드 & 실행

### JVM (sdk · protocol · runner · backend)
```bash
./gradlew build              # 전체 빌드(+proto 코드생성, +bootJar)
./gradlew :protocol:build    # gRPC 스키마 코드 생성/컴파일
./gradlew :backend:bootRun   # 백엔드 기동 (http://localhost:8080, /actuator/health)
./gradlew :runner:run        # 러너 스텁 실행
./gradlew test               # 단위 테스트 (Phase 8에서 본격화)
```
- 첫 빌드는 JDK 21 툴체인 + protoc/gRPC + 의존성 다운로드로 시간이 걸린다.
- 생성된 gRPC 코드: `protocol/build/generated/source/proto/...`.

### 데스크탑 (Electron + React)
```bash
cd desktop
pnpm install                 # 최초 1회 (electron/esbuild 빌드 스크립트는 pnpm-workspace.yaml allowBuilds로 허용)
pnpm dev                     # Vite 개발 서버
pnpm build                   # 렌더러 프로덕션 빌드 → dist/renderer
pnpm typecheck               # tsc --noEmit
pnpm dist                    # (Phase 10) 렌더러+electron 빌드 후 electron-builder 패키징
```

### 백엔드 컨테이너
```bash
docker build -f deploy/backend.Dockerfile -t maestro-backend .
docker run -p 8080:8080 maestro-backend
```

## 작업 방식 (반드시 준수)
- **단계(Phase)별 게이트**: Plan 제시 → 사용자 승인 → 구현 → 테스트 → `docs/`에 결과 기록 → 게이트.
- **큰 결정·스택/스키마 변경은 먼저 질문**하고 `docs/adr/NNN-*.md`로 기록.
- **작게, 자주 커밋**. 각 기능에 테스트 동반, 변경 후 `./gradlew build`·`pnpm build`로 검증.
- **보안(NFR-3) 상시 고려**: 러너 리소스 제한(-Xmx·타임아웃), 최소 권한, 비밀정보 비주입, IPC 토큰.
- 불확실하면 추측하지 말고 질문. 가정은 명시.

## 코드 규칙
- 패키지 루트: `io.maestro.*` (sdk/runner/backend), 프로토콜 생성 코드 `io.maestro.protocol.v1`.
- 들여쓰기: Java/Kotlin 4 스페이스, TS/JSON/YAML 2 스페이스 (`.editorconfig`).
- 라이프사이클 보장(onStart 1회 / onTick 주기·예외격리 / onEnd 1회)은 시스템의 핵심 계약 — 테스트로 증명.

## Phase 진행 상태
- [x] **Phase 0** 분석 — `docs/00-analysis.md`
- [x] **Phase 1** 설계 — `docs/01-architecture.md`, `maestro.proto`, sdk 스텁, OpenAPI, DB 스키마
- [x] **Phase 2** 스캐폴딩 — 모노레포·Gradle 멀티모듈·pnpm·CI·이 문서 (`./gradlew build`·`pnpm build` 통과)
- [x] **Phase 3** 스크립트 엔진(핵심): 동적 컴파일 + 라이프사이클 + tick 격리 + CLI 단독 실행 (단위 12개 그린)
- [x] **Phase 4** 백엔드 오케스트레이터: 기동/감시/재시작·gRPC·REST/WS·메트릭 (격리+재시작 통합테스트 입증)
- [x] **Phase 5** 데스크탑: Electron+React+Monaco(SDK 보조)·스크립트 CRUD·실행/상태·로그/메트릭 + JDT LS 스파이크 (REST 라운드트립 입증)
- [x] **Phase 6** 플로우 & 모듈: 라우팅(DAG·백프레셔)·모듈·React Flow 캔버스 (2노드 분산처리 통합테스트 입증)
- [ ] **Phase 7** 대시보드 · [ ] **Phase 8** 테스트 · [ ] **Phase 9** 시뮬레이션 · [ ] **Phase 10** 배포

## 데스크탑 실행
```bash
./gradlew :runner:installDist
MAESTRO_RUNNER_CLASSPATH="$PWD/runner/build/install/runner/lib/*" ./gradlew :backend:bootRun   # 백엔드(러너 클래스패스 제공)
cd desktop && pnpm install && pnpm dev                                                          # 데스크탑 개발 서버
#   pnpm fetch:jdtls  → JDT LS 활성화(선택, ≈100MB+, JDK 필요)
```

## CI
`.github/workflows/ci.yml` — `jvm`(setup-java 21 + `./gradlew build`)와 `desktop`(pnpm install + typecheck + build) 두 잡.
