import io.maestro.sdk.Script;

/**
 * 파라미터 사용 예제. 실행 시 {@code name}·{@code lang} 파라미터를 읽어 인사를 로그한다.
 * 실행: ./gradlew :runner:run --args="examples/GreeterScript.java --ticks 3 --period 1000 --param name=세계 --param lang=ko"
 */
public class GreeterScript extends Script {

    private String greeting;

    @Override
    public void onStart() {
        String name = ctx.param("name", String.class);
        if (name == null) {
            name = "world";
        }
        String lang = ctx.param("lang", String.class);
        String hello = "en".equals(lang) ? "Hello" : "안녕하세요";
        greeting = hello + ", " + name + "!";
        ctx.log().info("준비됨: {}", greeting);
    }

    @Override
    public void onTick() {
        ctx.log().info(greeting);
    }
}
