pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Java 21 툴체인 자동 프로비저닝 (호스트에 JDK 21 미설치 시 Gradle이 자동 다운로드)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "maestro"

include("sdk", "protocol", "runner", "backend")
