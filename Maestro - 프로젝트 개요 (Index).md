---
title: "Maestro - 프로젝트 개요 (Index)"
aliases:
  - Maestro
  - 동적 자바 스크립트 플랫폼
  - Dynamic Java Script Platform
tags:
  - project
  - maestro
  - java
  - platform
  - index
created: 2026-06-28
updated: 2026-06-28
status: 기획
---

# 🎼 Maestro — 동적 자바 스크립트 플랫폼

> [!note] 이름의 뜻
> **Maestro**(거장·지휘자) — 백엔드 오케스트레이터가 수많은 스크립트 프로세스를 *지휘자처럼* 병렬로 지휘·조율한다. 각 스크립트는 제 박자(`onTick`)로 연주하고, 플로우(node-RED 와이어링)로 합주한다.

순수 **자바 코드를 동적 스크립트**로 작성·컴파일·실행하고, 각 스크립트를 **격리된 병렬 JVM 프로세스**로 돌리며, **노드레드식 플로우**로 연결·분산하고, **대시보드**로 상태·리소스를 관측하는 멀티플랫폼 플랫폼.

## ✨ 핵심 기능
| # | 기능 | 요지 |
|---|---|---|
| 1 | 동적 자바 스크립트 | 순수 자바 작성 → 실행 시 인메모리 동적 컴파일 |
| 2 | 라이프사이클 | `onStart`(1회) · `onTick`(주기) · `onEnd`(종료 시 1회) |
| 3 | 격리 병렬 실행 | 스크립트별 독립 JVM 프로세스 — 한 프로세스 장애가 타 프로세스에 영향 X |
| 4 | 작성 보조 | Monaco + Eclipse JDT LS로 실시간 문법·시맨틱 체크 |
| 5 | 대시보드 | 프로세스별 상태·CPU·메모리·로그 실시간 |
| 6 | 플로우(node-RED식) | 노드를 선으로 연결해 메시지 라우팅·처리 분산 |
| 7 | 모듈 | 스크립트/서브플로우를 재사용 모듈로 패키징 |

## 🧱 확정 스택
- **백엔드**: Java 21 + Spring Boot (오케스트레이터)
- **런타임**: 스크립트별 별도 JVM 프로세스 + `javax.tools.JavaCompiler`
- **데스크탑**: Electron + React + Monaco + Eclipse JDT LS (멀티플랫폼)
- **플로우**: React Flow · **빌드**: Gradle 멀티모듈 + pnpm · **CI**: GitHub Actions

## 🗺️ 로드맵 (게이트 단계)
분석 → 설계 → 스캐폴딩 → **스크립트 엔진(핵심)** → 백엔드 오케스트레이터 → 데스크탑 앱 → 플로우·모듈 → 대시보드 → 테스트 → **시뮬레이션(결함 주입)** → 배포

## 🔗 프로젝트 문서
- [[Maestro - Claude Code 빌드 프롬프트]] — 분석~배포 전체를 구동하는 Claude Code 프롬프트(붙여넣기용)

## 📌 상태 / 다음 할 일
- [x] 빌드 프롬프트를 Claude Code에 투입
- [x] Phase 0(분석) 결정 확정: 동시 실행 **수백(~100–500)** · 신뢰모델 **신뢰된 사용자(경량 샌드박스)** · 저장소 **H2+인메모리 링버퍼** · 인증 **다중 사용자** · 버스 **백엔드 릴레이**
- [x] Phase 0 산출물 작성: `docs/00-analysis.md`, `docs/adr/0001-*.md`
- [x] 설계 결정 확정(ADR-0002): IPC **gRPC** · 플로우 **DAG 강제** · 인증 **로컬계정+JWT** · 저장소 **H2시작+Postgres 전환경로**
- [x] Phase 1 설계 산출물: `docs/01-architecture.md`(다이어그램·상태기계·감시모델·IPC) · `protocol/maestro.proto`(gRPC) · `sdk` 인터페이스 스텁 · `docs/api/openapi.yaml` · `docs/db/schema.sql` · ADR-0003(프로세스격리·Electron·JDT)
- [x] Phase 2 스캐폴딩: Gradle 멀티모듈(sdk/protocol/runner/backend, **Java 21 툴체인 자동 프로비저닝**) + pnpm 데스크탑(Electron+React+Vite) + GitHub Actions CI + `CLAUDE.md`. **검증: `./gradlew build`·`pnpm build`·`pnpm typecheck` 통과**, git 초기화·초기 커밋(`5d766d3`)
- [x] Phase 3 스크립트 엔진(핵심): 동적 컴파일(`JavaCompiler` 인메모리 + 격리 ClassLoader) + 라이프사이클 엔진(onStart 1·onTick 주기·onEnd 1) + tick 예외 격리(CONTINUE/STOP/임계) + 행 워치독 + **CLI 단독 실행**. **검증: 단위 테스트 12개 그린 + CLI 스모크**. 문서 `docs/03-script-engine.md`
- [x] Phase 4 백엔드 오케스트레이터: 러너 **독립 프로세스 기동**(ProcessBuilder) + gRPC RunnerGateway(텔레메트리) + **감시·exponential backoff 재시작** + REST(scripts/runs)/WS(logs·metrics) + 프로세스별 CPU/메모리 링버퍼 + H2/JPA. **검증: 통합 테스트로 격리+재시작 입증**(프로세스 3개 중 1개 kill → 나머지 정상 + 재시작), `./gradlew build` 그린. 문서 `docs/04-orchestrator.md`
- [x] Phase 5 데스크탑: Electron+React+**Monaco**(Java 언어·SDK 자동완성/호버) + 스크립트 **CRUD**(+백엔드 PUT/DELETE) + 실행/중지·상태·로그·메트릭 실시간(REST/WS) + **JDT LS 연동 스파이크**(기동 모듈·다운로드·클래스패스 설계). **검증: `pnpm typecheck/build` 그린 + REST 라운드트립 테스트(작성→실행→상태→중지)**. 헤드리스라 GUI 클릭/JDT 풀시맨틱은 후속. 문서 `docs/05-desktop.md`
- [x] Phase 6 플로우 & 모듈: 백엔드 **플로우 라우팅**(노드 emit→하류 DeliverMessage, **DAG 강제**·바운디드 큐 백프레셔) + **모듈**(semver·포트 스펙·인스턴스화) + REST(flows/modules) + **React Flow 캔버스**(뷰 전환). **검증: 통합 테스트로 2노드 메시지 분산 처리 시연(producer→consumer) + DAG 거부 + `pnpm build` 그린**. 문서 `docs/06-flow.md`
- [x] Phase 7 대시보드: `/api/dashboard` 집계(상태+최신 메트릭) + 데스크탑 **프로세스 그리드·CPU/메모리 SVG 스파크라인·실시간 로그**(대시보드 탭). **검증: DashboardIntegrationTest(부하 반영: 실행→대시보드 RUNNING+heap) + `pnpm build` 그린**. 문서 `docs/07-dashboard.md`
- [x] Phase 8 테스트: **계약 테스트**(SDK·프로토콜) + 갭 단위(링버퍼·백프레셔·로거) + **JaCoCo 커버리지**(빌드 시 생성, CI 아티팩트). 문서 `docs/08-testing.md`
- [x] Phase 9 시뮬레이션: 결함 주입 하니스(`SimulationTest` 5종) — **tick예외 격리·kill -9 재시작·무한루프 바운드종료·OOM 봉쇄·플로우 처리량(50 msg/s)** 실측. 러너 `System.exit` 보강. **5개 통과, 총 44개 테스트 그린**. 문서 `docs/09-simulation-report.md`
- [ ] **Phase 10(배포) 착수 승인 대기** → Docker·electron-builder·CI/CD·릴리스·문서
- 상태: **시뮬레이션 입증 · 격리/복원/처리량 실측 · Phase 10 대기** (44개 테스트 그린)

#project #maestro #java #platform #index
