package io.maestro.runner.compile;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 클래스 출력을 메모리로 가로채는 파일 매니저. 그 외 조회는 표준 매니저에 위임(SDK 등 클래스패스 해석). */
final class InMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    private final Map<String, InMemoryClassFile> outputs = new HashMap<>();

    InMemoryFileManager(JavaFileManager delegate) {
        super(delegate);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className,
                                               JavaFileObject.Kind kind, FileObject sibling) {
        InMemoryClassFile file = new InMemoryClassFile(className);
        outputs.put(className, file);
        return file;
    }

    /** 생성된 모든 클래스(주 클래스 + 중첩/익명 클래스 포함)의 바이트코드. */
    Map<String, byte[]> classBytes() {
        Map<String, byte[]> result = new LinkedHashMap<>();
        outputs.forEach((name, file) -> result.put(name, file.toByteArray()));
        return result;
    }
}
