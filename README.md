# 🎼 Maestro — 동적 자바 스크립트 플랫폼

순수 **자바 코드를 동적 스크립트**로 작성·컴파일·실행하고, 각 스크립트를 **격리된 병렬 JVM 프로세스**로 돌리며, **노드레드식 플로우**로 연결·분산하고, **대시보드**로 상태·리소스를 관측하는 멀티플랫폼 플랫폼.

> 백엔드 오케스트레이터가 수많은 스크립트 프로세스를 *지휘자처럼* 병렬로 조율한다. 각 스크립트는 제 박자(`onTick`)로 연주하고, 플로우로 합주한다.

## ✨ 핵심 기능
- **동적 자바 스크립트** — 순수 자바 작성 → 실행 시 인메모리 동적 컴파일(`javax.tools.JavaCompiler`)
- **라이프사이클** — `onStart`(1회) · `onTick`(주기) · `onEnd`(종료 1회) **보장**
- **격리 병렬 실행** — 스크립트별 독립 JVM 프로세스. 한 프로세스의 예외·OOM·무한루프·크래시가 타 프로세스에 무영향
- **감시·재시작** — 프로세스 사망 감지 → exponential backoff 재시작
- **대시보드** — 프로세스별 상태·CPU·메모리·로그 실시간
- **플로우(node-RED식)** — 노드(스크립트/모듈)를 선으로 연결해 메시지 라우팅·처리 분산(DAG)
- **모듈** — 스크립트/서브플로우를 재사용 모듈로 패키징·버전관리

## 🧱 스택
- **백엔드**: Java 21 + Spring Boot (오케스트레이터·gRPC·REST/WS·메트릭)
- **런타임**: 스크립트별 독립 JVM 프로세스 + 동적 컴파일 + 격리 ClassLoader
- **IPC**: gRPC (러너↔백엔드 단일 양방향 스트림)
- **데스크탑**: Electron + React + Monaco + Eclipse JDT LS + React Flow
- **저장소**: H2(시작) → Postgres 전환경로 · 메트릭 인메모리 링버퍼
- **빌드**: Gradle 멀티모듈 + pnpm · **CI**: GitHub Actions

## 📁 구조 (모노레포)
```
maestro/
├─ sdk/        스크립트 SDK(라이프사이클 + Context API)
├─ protocol/   backend↔runner gRPC 스키마(.proto)
├─ runner/     스크립트별 독립 JVM(동적 컴파일 + 라이프사이클 + IPC)
├─ backend/    Spring Boot 오케스트레이터
├─ desktop/    Electron + React 데스크탑 앱
├─ deploy/     Docker · electron-builder · CI/CD
└─ docs/       분석·설계·ADR·테스트·시뮬레이션·배포 문서
```

## 🚀 빠른 시작 (로컬)
```bash
# 백엔드 (러너 클래스패스 제공 필요)
./gradlew :runner:installDist
MAESTRO_RUNNER_CLASSPATH="$PWD/runner/build/install/runner/lib/*" ./gradlew :backend:bootRun
#   REST/WS: http://localhost:8080 · gRPC: 9090 · health: /actuator/health

# 데스크탑
cd desktop && pnpm install && pnpm dev
#   (선택) pnpm fetch:jdtls 로 JDT LS(풀 시맨틱) 활성화
```

### 컨테이너로 백엔드 실행
```bash
docker compose -f deploy/docker-compose.yml up --build
# 또는
docker build -f deploy/backend.Dockerfile -t maestro-backend .
docker run -p 8080:8080 -p 9090:9090 maestro-backend
```

## 🧪 테스트 / 빌드
```bash
./gradlew build      # 전 모듈 빌드 + 44개 테스트 + JaCoCo 커버리지
cd desktop && pnpm build && pnpm typecheck
```

## 📦 릴리스
`v*` 태그 푸시 → `.github/workflows/release.yml`이 백엔드 이미지(GHCR) + 데스크탑 설치본(Win/Mac/Linux) + GitHub Release 생성. 자세한 내용: [docs/10-deployment.md](docs/10-deployment.md).

## 📚 문서
- [프로젝트 개요(Index)](Maestro%20-%20프로젝트%20개요%20(Index).md) · [CLAUDE.md](CLAUDE.md)(빌드·규칙)
- [SDK 레퍼런스](docs/sdk-reference.md) · [사용자 가이드](docs/user-guide.md)
- 단계별: [분석](docs/00-analysis.md) · [설계](docs/01-architecture.md) · [엔진](docs/03-script-engine.md) · [오케스트레이터](docs/04-orchestrator.md) · [데스크탑](docs/05-desktop.md) · [플로우](docs/06-flow.md) · [대시보드](docs/07-dashboard.md) · [테스트](docs/08-testing.md) · [시뮬레이션](docs/09-simulation-report.md) · [배포](docs/10-deployment.md)
- 결정: [ADR](docs/adr/)

## 📊 상태
**Phase 0–10 완료** — 분석·설계·엔진·오케스트레이터·데스크탑·플로우·대시보드·테스트·시뮬레이션·배포. 44개 테스트 그린, 백엔드 컨테이너 end-to-end 검증.
