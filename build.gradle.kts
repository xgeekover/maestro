// Maestro 루트 빌드 — JVM 멀티모듈 공통 설정 (Phase 2 스캐폴딩)
// 실험 브랜치(experiment/jdk17-target): JDK 17 런타임 지원 검증용으로 툴체인을 17로 낮춤.

allprojects {
    group = "io.maestro"
    version = "0.1.1"
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin> {
        apply(plugin = "jacoco")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            finalizedBy(tasks.named("jacocoTestReport"))
        }
        tasks.withType<org.gradle.testing.jacoco.tasks.JacocoReport>().configureEach {
            dependsOn(tasks.named("test"))
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
    }
}
