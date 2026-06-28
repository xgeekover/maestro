# Maestro — Phase 8 테스트

> 상태: **구현·검증 완료, 게이트 대기** · 작성일 2026-06-28
> 다음: 승인 시 **Phase 9 — 시뮬레이션(결함 주입)**

각 Phase에서 누적한 검증에 **계약 테스트·갭 단위 테스트·커버리지(JaCoCo)** 를 더해 테스트 스위트를 완성한다.

## 테스트 인벤토리 (총 39개, 전부 그린)

### 단위 (Unit)
| 모듈 | 테스트 | 대상 |
|---|---|---|
| runner | `LifecycleEngineTest`(9) | onStart/onTick/onEnd 보장, tick 격리(CONTINUE/STOP/임계), onStart 실패, 사용자 중지, emit/onMessage |
| runner | `InMemoryCompilerTest`(3) | 동적 컴파일·진단·FQN 추론 |
| runner | `SimpleLoggerTest`(4) | `{}` 포맷 치환 |
| backend | `RingBufferTest`(3) | 링버퍼 순서·덮어쓰기 |
| backend | `FlowDeploymentBackpressureTest`(3) | **백프레셔 drop-oldest**·미라우팅 무시·팬아웃 |
| backend | `flow.FlowValidatorTest`(3) | **DAG**(사이클/무결성) |

### 계약 (Contract)
| 모듈 | 테스트 | 대상 |
|---|---|---|
| sdk | `ScriptContractTest`(4) | 라이프사이클 기본 no-op·`__bind` 주입·ScriptContext/KeyValueStore 형상 |
| protocol | `ProtocolContractTest`(5) | 메시지 직렬화 round-trip·enum/서비스명 안정성(backend↔runner 호환) |

### 통합 / e2e (실제 프로세스·Spring 컨텍스트)
| 테스트 | 증명 |
|---|---|
| `OrchestrationIntegrationTest`(1) | 다중 프로세스 **격리 + 감시 재시작**(kill→나머지 정상+재시작) |
| `RestRoundTripTest`(1) | REST **작성→실행→상태→중지**(데스크탑이 호출하는 e2e 경로) |
| `FlowRoutingIntegrationTest`(2) | **2노드 메시지 분산 처리**(producer→consumer) + 사이클 거부 |
| `DashboardIntegrationTest`(1) | 대시보드 **부하 반영**(실행→RUNNING+메트릭) |

## 커버리지 (JaCoCo, instruction)
`./gradlew build` 시 `**/build/reports/jacoco/test/`에 생성(CI 아티팩트 업로드).

| 모듈 | 커버리지 | 비고 |
|---|---|---|
| sdk | ~100% | 인터페이스/베이스 클래스 |
| backend | ~75% | 오케스트레이터·플로우·REST(통합 테스트가 인-JVM 실행) |
| runner | ~44% | 엔진/컴파일러는 높게 커버. **gRPC 클라이언트 코드는 통합 테스트에서 별도 러너 JVM(서브프로세스)으로 실행되어 본 모듈 커버리지에 미집계** |
| protocol | ~14% | 대부분 **protobuf 생성 코드**(행위는 계약 테스트로 검증) |

> 정직한 해석: runner의 실제 통합 커버리지는 측정치보다 높다(서브프로세스 실행분 미집계). protocol은 생성 코드라 라인 % 의미가 낮아 **계약 테스트**로 호환성을 보장한다.

## CI
`.github/workflows/ci.yml` — `jvm` 잡이 `./gradlew build`(테스트+JaCoCo) 실행 후 커버리지 HTML을 아티팩트로 업로드, `desktop` 잡이 typecheck+build. **테스트 게이트 = CI 그린.**

## 한계 / 이월
- **데스크탑 단위 테스트**(Vitest/RTL) 미도입 → 후속. 현재 데스크탑은 typecheck+build + REST 계약(통합 테스트)으로 검증.
- 커버리지 하드 게이트(임계 강제)는 미적용(측정·가시화 우선). 필요 시 `jacocoTestCoverageVerification`로 도입.

## Phase 8 게이트 체크리스트
- [x] 단위(엔진·컴파일러·라이프사이클·링버퍼·백프레셔·DAG)
- [x] 통합(backend↔runner·API) + e2e(REST 작성→실행→상태→중지)
- [x] **SDK/프로토콜 계약 테스트**
- [x] **커버리지(JaCoCo) 측정 + CI 아티팩트** · 39개 테스트 CI 그린
- [ ] **사용자 승인(게이트)** → Phase 9 착수
