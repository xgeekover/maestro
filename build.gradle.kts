// Maestro 루트 빌드 — JVM 멀티모듈 공통 설정 (Phase 2 스캐폴딩)
// 스택: Java 21 (C-1). 호스트가 17이어도 Gradle 툴체인이 21을 프로비저닝.

allprojects {
    group = "io.maestro"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
