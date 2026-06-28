package io.maestro.runner.compile;

import io.maestro.sdk.Script;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 순수 자바 소스를 인메모리로 동적 컴파일하고, 격리 ClassLoader로 로드한다 (FR-2).
 *
 * <p>{@code javax.tools.JavaCompiler} + 인메모리 {@code JavaFileManager} + {@code DiagnosticCollector}.
 * SDK 타입 해석을 위해 현재 런타임 클래스패스를 컴파일 옵션으로 전달한다.</p>
 */
public final class InMemoryCompiler {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)\\s*;");
    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("(?m)\\bpublic\\s+(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_]\\w*)");

    private final ClassLoader parentLoader;
    private final String classpath;

    public InMemoryCompiler() {
        this(Script.class.getClassLoader(), System.getProperty("java.class.path"));
    }

    public InMemoryCompiler(ClassLoader parentLoader, String classpath) {
        this.parentLoader = parentLoader;
        this.classpath = classpath;
    }

    /** 소스에서 public 클래스의 정규화 이름(FQN)을 추론한다. */
    public static String inferClassName(String source) {
        Matcher cls = PUBLIC_CLASS.matcher(source);
        if (!cls.find()) {
            throw new IllegalArgumentException("소스에서 public class 선언을 찾을 수 없습니다.");
        }
        String simpleName = cls.group(1);
        Matcher pkg = PACKAGE.matcher(source);
        String packageName = pkg.find() ? pkg.group(1) : "";
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    public CompilationResult compile(String source) {
        return compile(inferClassName(source), source);
    }

    public CompilationResult compile(String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("시스템 자바 컴파일러를 찾을 수 없습니다 (JRE가 아닌 JDK로 실행되어야 함).");
        }

        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(collector, null, StandardCharsets.UTF_8);

        try (InMemoryFileManager fileManager = new InMemoryFileManager(standard)) {
            List<String> options = new ArrayList<>();
            if (classpath != null && !classpath.isBlank()) {
                options.add("-classpath");
                options.add(classpath);
            }

            List<JavaFileObject> units = List.of(new StringSourceFile(className, source));
            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fileManager, collector, options, null, units);

            boolean ok = task.call();
            List<Diag> diagnostics = collector.getDiagnostics().stream().map(Diag::from).toList();

            if (!ok) {
                return CompilationResult.failure(className, diagnostics);
            }

            Map<String, byte[]> classes = fileManager.classBytes();
            IsolatedClassLoader loader = new IsolatedClassLoader(classes, parentLoader);
            try {
                Class<?> loaded = loader.loadClass(className);
                return CompilationResult.success(className, diagnostics, loaded);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("컴파일은 성공했으나 클래스 로드 실패: " + className, e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
