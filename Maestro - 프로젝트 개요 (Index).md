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
- [ ] **Phase 2 스캐폴딩 착수 승인 대기** → 모노레포·Gradle·pnpm·CI·`CLAUDE.md`
- 상태: **설계 완료 · Phase 2 대기** (코드: SDK/프로토콜 스텁만) 

#project #maestro #java #platform #index
