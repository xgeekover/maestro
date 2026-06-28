// runner — 스크립트별 독립 JVM 프로세스. 동적 컴파일 + 라이프사이클 + gRPC 클라이언트 (Phase 3).
plugins {
    application
}

val grpcVersion = "1.66.0"

dependencies {
    implementation(project(":sdk"))
    implementation(project(":protocol"))
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("io.maestro.runner.RunnerMain")
}
