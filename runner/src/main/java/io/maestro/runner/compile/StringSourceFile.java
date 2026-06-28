package io.maestro.runner.compile;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/** 문자열 소스를 입력으로 제공하는 JavaFileObject. URI는 public 클래스명과 일치해야 한다. */
final class StringSourceFile extends SimpleJavaFileObject {

    private final String code;

    StringSourceFile(String className, String code) {
        super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
