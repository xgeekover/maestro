package io.maestro.runner.compile;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

/** 컴파일 산출(바이트코드)을 메모리에 보관하는 출력 JavaFileObject. */
final class InMemoryClassFile extends SimpleJavaFileObject {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final String className;

    InMemoryClassFile(String className) {
        super(URI.create("mem:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        this.className = className;
    }

    String className() {
        return className;
    }

    byte[] toByteArray() {
        return bytes.toByteArray();
    }

    @Override
    public OutputStream openOutputStream() {
        return bytes;
    }
}
