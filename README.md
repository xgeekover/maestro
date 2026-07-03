# 🎼 Maestro — 동적 자바 스크립트 플랫폼

순수 **자바 코드를 동적 스크립트**로 작성·컴파일·실행하고, 각 스크립트를 **격리된 병렬 JVM 프로세스**로 돌리며, **node-RED식 플로우**로 연결·분산하고, **대시보드**로 상태·리소스를 실시간 관측하는 멀티플랫폼 플랫폼.

> 백엔드 오케스트레이터가 수많은 스크립트 프로세스를 *지휘자처럼* 병렬로 조율한다. 각 스크립트는 제 박자(`onTick`)로 연주하고, 플로우로 합주한다.

---

## ✨ 핵심 기능

- **동적 자바 스크립트** — 순수 자바로 작성 → 실행 시 인메모리 동적 컴파일(`javax.tools.JavaCompiler`)
- **라이프사이클 보장** — `onStart`(1회) · `onTick`(주기) · `onEnd`(종료 1회). tick 예외는 격리
- **격리 병렬 실행** — 스크립트별 **독립 JVM 프로세스**. 한 프로세스의 예외·OOM·무한루프·크래시가 타 프로세스·백엔드에 무영향
- **감시·재시작** — 프로세스 사망 감지 → 지수 백오프 재시작(자기종료는 재시작 안 함)
- **실행 설정** — 주기·파라미터(`params`)·최대 힙·tick 타임아웃·에러 임계치 지정, **실행 중 주기 동적 변경**
- **관측성** — 프로세스별 상태·CPU·메모리·tick 지연 **추세 차트** + 실시간 로그(레벨 필터·검색·일시정지)
- **실행 이력** — 종료된 실행을 영속 기록·조회
- **플로우(node-RED식)** — 노드(스크립트/모듈)를 이름 있는 **포트**로 연결해 메시지 라우팅·처리 분산(**DAG** 강제·백프레셔)
- **모듈** — 로직을 **이름·버전(semver)·포트 스펙**으로 패키징한 재사용 단위. 다중 인스턴스화·다중 포트 배선(그래프 포트 검증)

---

## 🧱 기술 스택

| 레이어 | 기술 · 버전 |
|---|---|
| JVM | **Java 21** (Gradle 멀티모듈, foojay 툴체인 자동 프로비저닝) |
| 백엔드 | **Spring Boot 3.3.4** · Spring Data JPA · Bean Validation · WebSocket |
| 러너 | process-per-script + `javax.tools.JavaCompiler` + 격리 ClassLoader |
| IPC | **gRPC** (grpc-netty-shaded 1.66.0) 단일 양방향 스트림, 루프백 바인드 |
| 영속성 | **H2** 파일 모드 + **Flyway** 마이그레이션 (→ Postgres 전환 경로) |
| 관측성 | Micrometer + **Prometheus** 엔드포인트, 인메모리 링버퍼 메트릭 |
| 데스크탑 | **Electron + React + Vite + Monaco + React Flow** (pnpm), Eclipse JDT LS(선택) |
| 프런트 테스트 | **Vitest** + Testing Library + jsdom |
| CI/CD | **GitHub Actions** (jvm · desktop 잡) + electron-builder |

---

## 📁 구조 (모노레포)

```
maestro/
├─ sdk/        스크립트 SDK — Script 라이프사이클 + ScriptContext(emit/onMessage/log/param/state)
├─ protocol/   backend↔runner gRPC 스키마(maestro.proto) → 코드 생성
├─ runner/     스크립트별 독립 JVM — 동적 컴파일 + 라이프사이클 엔진 + gRPC 클라이언트
├─ backend/    Spring Boot 오케스트레이터 — Supervisor·REST/WS·메트릭·플로우·모듈
├─ desktop/    Electron + React 데스크탑 앱 (Monaco 에디터 + React Flow 캔버스)
├─ deploy/     백엔드 Docker · electron-builder · CI/CD
└─ docs/       분석·설계·ADR·테스트·시뮬레이션·배포 문서
```

---

## 🚀 설치 & 실행 (로컬)

### 사전 요구
- **JDK 21** (없으면 Gradle 툴체인이 자동 프로비저닝) · **Node 20+** · **pnpm 11+**

### 1) 백엔드 (러너 클래스패스 제공 필요)
```bash
./gradlew :runner:installDist                                    # 러너 실행본 생성(최초 1회)
MAESTRO_RUNNER_CLASSPATH="$PWD/runner/build/install/runner/lib/*" \
  ./gradlew :backend:bootRun
#   REST/WS: http://localhost:8080 · gRPC: 9090 · health: /actuator/health
#   데이터: ./data/maestro (H2 파일, Flyway 마이그레이션)
```
기본 포트(8080)가 점유돼 있으면 다른 포트로 띄우고 데스크탑이 그곳을 보게 한다:
```bash
… ./gradlew :backend:bootRun --args='--server.port=8081'
# 데스크탑: VITE_BACKEND_URL=http://localhost:8081 pnpm dev
```

### 2) 데스크탑
```bash
cd desktop && pnpm install && pnpm dev        # Vite 개발 서버: http://localhost:5173
#   (선택) pnpm fetch:jdtls 로 JDT LS(풀 시맨틱 완성) 활성화 (≈100MB+, JDK 필요)
```
> Electron 창을 띄우려면 `pnpm dev` 후 Electron 셸을 실행하거나, 개발 중에는 브라우저로 `localhost:5173`에 접속해도 된다.

### 컨테이너로 백엔드 실행
```bash
docker build -f deploy/backend.Dockerfile -t maestro-backend .
docker run -p 8080:8080 -p 9090:9090 maestro-backend
```

---

## 📖 사용 매뉴얼

### 데스크탑 화면 구성 (상단 탭)
| 탭 | 용도 |
|---|---|
| **스크립트** | 스크립트 CRUD + Monaco 편집 + 실행 + 우측 실행/메트릭/로그 패널 |
| **플로우** | React Flow 캔버스에서 노드를 포트로 연결·배포 |
| **모듈** | 재사용 모듈 저작(이름·버전·포트 스펙·소스) + 수정/삭제 |
| **대시보드** | 실행 중 프로세스 그리드 + CPU/Heap 스파크라인 + 실시간 로그 |
| **이력** | 종료된 실행 기록(최신순·페이지네이션·에러 펼치기) |

### 첫 스크립트 작성·실행
1. **스크립트** 탭 → `+ 새로` → 이름 입력 → 에디터에 작성 → **생성**
2. **▶ 실행** (옆 **⚙** 로 주기·파라미터·최대 힙·타임아웃·에러 임계치 설정)
3. 우측 **실행** 목록에서 선택 → **메트릭**(tick/heap/cpu + 추세 차트) · **로그**(레벨 필터·검색·⏸ 일시정지·복사) 확인
4. 실행 중 **주기(ms)** 입력 후 *주기 적용* → tick 주기 즉시 변경
- **단축키**: `⌘/Ctrl+S` 저장 · `⌘/Ctrl+Enter` 실행. 미저장 상태로 이동하면 확인 모달, 실행 시 자동 저장 후 실행

```java
import io.maestro.sdk.Script;

public class Hello extends Script {
    private int n = 0;
    @Override public void onStart() { ctx.log().info("start"); }
    @Override public void onTick()  { ctx.log().info("tick " + (++n)); }
    @Override public void onEnd()   { ctx.log().info("end (" + n + " ticks)"); }
}
```

### SDK — `ScriptContext` API
| 메서드 | 설명 |
|---|---|
| `log()` | 구조적 로깅(INFO/WARN/ERROR) → 텔레메트리로 스트리밍 |
| `param(key, type)` | 실행 파라미터 조회 |
| `emit(port, msg)` | 지정 **출력 포트**로 메시지 송신(플로우 라우팅) |
| `onMessage(port, handler)` | 지정 **입력 포트** 수신 핸들러 등록(보통 `onStart`에서) |
| `state()` | 재시작 간 유지되는 키-값 저장소 |

### 플로우로 데이터 연동 (script ↔ script / script ↔ module)
엣지 하나가 **상류 `emit(포트)` → 하류 `onMessage(포트)`** 를 잇는다.
1. **플로우** 탭 → 노드 선택에서 스크립트/모듈 추가(`+ 노드`)
2. 상류 노드의 **`out` 핸들**(오른쪽) → 하류 노드의 **`in` 핸들**(왼쪽)로 드래그
3. 노드 클릭 → 우측 패널에서 노드별 **주기·파라미터** 설정
4. **▶ 배포** → 각 노드가 독립 JVM으로 기동, 매 메시지가 하류로 전달(노드에 RUNNING/ERROR 상태 색 반영)
5. **■ 중지** 로 플로우 종료. `Del`/선택삭제로 노드·엣지 제거
- 사이클은 금지(DAG). 모듈은 **선언한 포트로만** 연결 가능(미선언 포트로 배포 시 422)

### 모듈 저작
**모듈** 탭 → 이름·**버전(semver)**·**포트 스펙(specJson)** `{"in":["in"],"out":["out"]}` 입력 + Monaco로 소스 작성 → **생성**. 왼쪽에서 선택해 **저장(수정)/삭제**. 플로우 캔버스의 노드 선택 목록에 `이름@버전` 으로 나타나며, 선언 포트가 곧 배선 핸들이 된다.

---

## 🧪 테스트 / 빌드

```bash
./gradlew build                       # 전 모듈 빌드 + JVM 테스트 91개 + JaCoCo 커버리지
cd desktop && pnpm test               # 데스크탑 Vitest 83개
cd desktop && pnpm typecheck && pnpm build
```
**총 174개 테스트** — sdk 4 · protocol 5 · runner 16 · backend 66 · desktop 83. 계약·단위·통합·결함주입 시뮬레이션 포함.

---

## 📦 릴리스
`v*` 태그 푸시 → `.github/workflows/release.yml` 이 백엔드 이미지(GHCR) + 데스크탑 설치본(Win/Mac/Linux) + GitHub Release 생성. 자세한 내용: [docs/10-deployment.md](docs/10-deployment.md).

---

## 📚 문서
- [CLAUDE.md](CLAUDE.md) — 빌드·실행·작업 규칙
- [SDK 레퍼런스](docs/sdk-reference.md) · [사용자 가이드](docs/user-guide.md)
- 단계별: [분석](docs/00-analysis.md) · [설계](docs/01-architecture.md) · [엔진](docs/03-script-engine.md) · [오케스트레이터](docs/04-orchestrator.md) · [데스크탑](docs/05-desktop.md) · [플로우](docs/06-flow.md) · [대시보드](docs/07-dashboard.md) · [테스트](docs/08-testing.md) · [시뮬레이션](docs/09-simulation-report.md) · [배포](docs/10-deployment.md)
- 품질: [QA 리포트](docs/qa-report.md) · 결정: [ADR](docs/adr/)

---

## 📊 상태
**Phase 0–10 완료** + **QA 강화 · UX/기능 개선 5단계 · 모듈 저작(T1~T3a)**. 총 174개 테스트 그린, 백엔드 컨테이너 end-to-end 검증.
로컬 전용 전제로 인증·샌드박스(C-1)는 보류(외부 노출 시 필수).

## 📄 라이선스
[MIT](LICENSE) © 2026 xgeekover
