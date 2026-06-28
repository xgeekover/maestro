package io.maestro.runner.compile;

import java.util.Map;

/**
 * 동적 컴파일된 스크립트 클래스를 정의하는 격리 ClassLoader.
 *
 * <p>부모를 SDK를 로드한 ClassLoader로 두어 {@code io.maestro.sdk.Script} 등 SDK 타입을
 * 엔진과 <b>동일 Class</b>로 공유한다(ClassCastException 방지). 스크립트가 생성한 클래스만
 * 메모리 바이트코드에서 정의하고, 나머지는 부모에 위임한다. 프로세스 종료 시 메타스페이스 회수.</p>
 */
public final class IsolatedClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

    public IsolatedClassLoader(Map<String, byte[]> classes, ClassLoader parent) {
        super(parent);
        this.classes = classes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytecode = classes.get(name);
        if (bytecode == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytecode, 0, bytecode.length);
    }
}
