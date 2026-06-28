# Maestro 예제 스크립트

각 파일은 `io.maestro.sdk.Script`를 상속한 독립 실행 가능한 예제다. SDK 레퍼런스: [docs/sdk-reference.md](../../docs/sdk-reference.md).

## 목록
| 파일 | 보여주는 것 | 포트 |
|---|---|---|
| `HeartbeatScript.java` | 라이프사이클 + 상태 + emit (기본) | out |
| `GreeterScript.java` | **파라미터**(`name`, `lang`) | — |
| `FibonacciScript.java` | **인스턴스 상태**(필드) tick 간 유지 + emit | out |
| `RandomWalkScript.java` | **ctx.state()** + 난수 + emit | out |
| `AggregatorScript.java` | **플로우 변환**: 수신→평균 집계→emit | in → out |
| `FilterScript.java` | **플로우 필터**: 임계값(`threshold`) 통과만 emit | in → out |
| `EchoScript.java` | **플로우 싱크**: 수신 로그·누적 | in |
| `FlakyScript.java` | **tick 예외 격리**(`everyN`마다 throw) | — |
| `CpuBusyScript.java` | **CPU 메트릭**(`iterations` 부하) | — |

## CLI 단독 실행
```bash
./gradlew :runner:run --args="examples/GreeterScript.java --ticks 3 --period 1000 --param name=세계"
./gradlew :runner:run --args="examples/FlakyScript.java --ticks 12 --period 300 --param everyN=4"
./gradlew :runner:run --args="examples/CpuBusyScript.java --ticks 10 --period 500 --param iterations=8000000"
```
옵션: `--period ms`, `--ticks n`, `--policy continue|stop`, `--tick-timeout ms`, `--error-threshold n`, `--param k=v`(반복).

## 데스크탑/REST로 실행
스크립트 본문을 `POST /api/scripts {name, source}`로 등록 후 `POST /api/runs {scriptId, tickPeriodMs, params}`. 데스크탑에서는 스크립트 탭에 붙여넣고 `생성`→`실행`.

## 플로우 조합 예시
파이프라인: **producer → filter → aggregate → sink**
```
RandomWalkScript(out) ──in→ FilterScript(threshold=0)(out) ──in→ AggregatorScript(out) ──in→ EchoScript(in)
```
데스크탑 **플로우 탭**에서 각 스크립트를 노드로 추가하고 `out → in`으로 이어 `배포`. 상류의 `emit("out", …)`이 하류 `onMessage("in", …)`로 라우팅된다(DAG·백프레셔).
