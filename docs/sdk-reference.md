# Maestro SDK 레퍼런스

스크립트는 순수 자바로 작성하며 `io.maestro.sdk.Script`를 상속한다. 모듈: `sdk` (의존성 없는 순수 자바 lib).

## Script (베이스 클래스)
```java
public abstract class Script {
    protected ScriptContext ctx;   // 런타임이 onStart 전에 주입
    public void onStart() {}        // 최초 1회
    public void onTick()  {}        // 주기마다
    public void onEnd()   {}        // 종료/중지 시 1회
}
```

### 라이프사이클 보장
| 메서드 | 보장 |
|---|---|
| `onStart()` | 프로세스당 **정확히 1회**. 실패 시 ERROR 분류 후 `onEnd` 호출 |
| `onTick()` | 지정 **주기마다 반복**. tick 단위 예외는 **격리**(정책 `continue`/`stop`, 누적 임계). 짧고 논블로킹으로 작성(행 감지 워치독 존재) |
| `onEnd()` | 종료/중지 시 **정확히 1회**(best-effort). `kill -9`/OOM/전원차단은 보장 불가 |

## ScriptContext (런타임 API)
```java
public interface ScriptContext {
    Logger log();                                     // 구조적 로깅(대시보드로 스트리밍)
    <T> T param(String key, Class<T> type);           // 실행 파라미터
    void emit(String port, Object message);           // 하류 노드로 전송(플로우)
    void onMessage(String port, Consumer<Object> h);  // 상류 메시지 수신
    KeyValueStore state();                            // (선택) 재시작 간 상태
}
```
- `param` 지원 타입: `String, Integer, Long, Double, Boolean`.
- `emit`/`onMessage`: 플로우 배포 시 노드 간 메시지 라우팅(백엔드 릴레이, DAG). 단독 실행 시 emit은 버퍼/로그.

## Logger
```java
void trace/debug/info/warn/error(String msg, Object... args);  // SLF4J 스타일 {} 플레이스홀더
void error(String msg, Throwable t);
```

## KeyValueStore (선택)
```java
<T> Optional<T> get(String key, Class<T> type);
void put(String key, Object value);
void remove(String key);
boolean contains(String key);
```

## 예제

### 하트비트
```java
import io.maestro.sdk.Script;

public class Heartbeat extends Script {
    @Override public void onStart() { ctx.state().put("beats", 0); }
    @Override public void onTick() {
        int n = ctx.state().get("beats", Integer.class).orElse(0) + 1;
        ctx.state().put("beats", n);
        ctx.log().info("beat #{}", n);
        ctx.emit("heartbeat", n);
    }
    @Override public void onEnd() {
        ctx.log().info("총 {} beats", ctx.state().get("beats", Integer.class).orElse(0));
    }
}
```

### 파라미터 + 메시지 수신
```java
import io.maestro.sdk.Script;

public class Consumer extends Script {
    @Override public void onStart() {
        String name = ctx.param("name", String.class);
        ctx.onMessage("in", msg -> ctx.log().info("{} 수신: {}", name, msg));
    }
}
```

## 실행 방법
- **단독(CLI)**: `./gradlew :runner:run --args="path/Script.java --ticks 3 --period 500 [--policy continue|stop] [--param k=v]"`
- **오케스트레이터**: 데스크탑에서 작성·저장 후 실행, 또는 REST `POST /api/scripts` → `POST /api/runs`(아래 가이드 참조).

## 작성 보조(데스크탑)
- Monaco 에디터 + **SDK 기반 자동완성/호버**(`onStart/onTick/onEnd`·`ctx.*`·스크립트 골격).
- (선택) **Eclipse JDT LS** 연동 시 VSCode급 풀 시맨틱 진단·자동완성(`pnpm fetch:jdtls`).
