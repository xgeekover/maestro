package io.maestro.runner.compile;

import io.maestro.sdk.Script;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCompilerTest {

    @Test
    void compilesValidScriptAndLoadsAsScript() throws Exception {
        String src = """
                package demo;
                import io.maestro.sdk.Script;
                public class Ok extends Script {
                    @Override public void onTick() { ctx.log().info("tick"); }
                }
                """;

        CompilationResult result = new InMemoryCompiler().compile(src);

        assertTrue(result.success(), "유효한 소스는 컴파일 성공해야 한다");
        assertEquals("demo.Ok", result.className());
        Object instance = result.scriptClass().getDeclaredConstructor().newInstance();
        assertInstanceOf(Script.class, instance, "로드된 클래스는 SDK Script 하위여야 한다");
    }

    @Test
    void reportsDiagnosticsOnInvalidSource() {
        String src = """
                public class Bad extends io.maestro.sdk.Script {
                    @Override public void onTick() { int x = ; }
                }
                """;

        CompilationResult result = new InMemoryCompiler().compile(src);

        assertFalse(result.success(), "문법 오류는 컴파일 실패여야 한다");
        assertTrue(result.hasErrors());
        assertFalse(result.diagnostics().isEmpty(), "진단이 수집되어야 한다");
    }

    @Test
    void infersClassNameFromSource() {
        assertEquals("a.b.Foo", InMemoryCompiler.inferClassName("package a.b; public class Foo {}"));
        assertEquals("Foo", InMemoryCompiler.inferClassName("public final class Foo {}"));
    }
}
