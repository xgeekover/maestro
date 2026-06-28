# ADR-0003 — 프로세스 격리 · Electron · Eclipse JDT LS 확정

- 상태: **승인(Accepted)** — Phase 1 설계 (2026-06-28)
- 맥락: [docs/01-architecture.md](../01-architecture.md) · 빌드 프롬프트 확정 스택(C-1)
- 선행: [ADR-0001](0001-phase0-foundational-decisions.md), [ADR-0002](0002-design-decisions-ipc-flow-auth-storage.md)

## 맥락
빌드 프롬프트가 확정한 핵심 스택 3종을 설계 단계에서 근거와 함께 못박는다(변경 시 새 ADR 필요).

## 결정

### 1. 격리 = **프로세스 단위(process-per-script)**
- **결정**: 스크립트마다 독립 JVM 프로세스(러너)로 실행. 스레드/클래스로더만의 격리는 채택하지 않음.
- **근거**: FR-5(결함 격리) 충족 — 예외/크래시/**OOM/무한루프/행**이 다른 프로세스에 영향 없음. 스레드 격리로는 OOM·무한루프·`System.exit`·네이티브 크래시를 막을 수 없다. process-per-script는 메타스페이스/클래스로더 누수도 프로세스 종료로 자연 회수(T-10).
- **트레이드오프**: 프로세스당 JVM 메모리·기동 오버헤드(R-1) → AppCDS+경량 heap(O-3)·기동 스로틀링·Phase 9 실측으로 관리. 수백 규모(D1)에서 단일 호스트 가정.
- **대안**: 스레드풀+SecurityManager(자바 신버전 deprecated, 격리 불완전), 단일 JVM 다중 클래스로더(OOM/행 격리 불가) → 기각.

### 2. 데스크탑 = **Electron + React**
- **결정**: 멀티플랫폼 데스크탑을 Electron+React로 구축.
- **근거**: Win/Mac/Linux 단일 코드베이스(NFR-1), Monaco/React Flow 등 웹 생태계 직접 활용, electron-builder로 설치본 패키징(Phase 10).
- **트레이드오프**: 번들 크기·메모리. 내부 도구(D2) 맥락에서 수용 가능.
- **대안**: Tauri(경량이나 JDT LS/Monaco 통합·생태계 성숙도에서 리스크), 네이티브(멀티플랫폼 비용↑) → 기각.

### 3. 작성 보조 = **Monaco + Eclipse JDT Language Server(LSP)**
- **결정**: 에디터 Monaco + 자바 분석은 Eclipse JDT LS를 LSP로 연동.
- **근거**: VSCode급 자바 문법/시맨틱 진단·자동완성(FR-1). **`sdk`를 클래스패스로 제공**하면 `onStart/onTick/onEnd`·`ScriptContext` 자동완성·진단이 가능.
- **트레이드오프**: JDT LS 구동(JRE 필요)·워크스페이스 구성 복잡 → Phase 5에서 조기 스파이크 권장(R-3).
- **대안**: 경량 문법 하이라이트만(시맨틱 체크 부재로 FR-1 미충족) → 기각.

## 결과
- `runner`는 `ProcessBuilder` 기반 기동 + gRPC 클라이언트(ADR-0002).
- `desktop`은 pnpm 워크스페이스, JDT LS 번들/스폰 관리 필요(Phase 5).
- `sdk`는 순수 자바 lib로 유지해 JDT LS 클래스패스·러너 양쪽에서 재사용.
