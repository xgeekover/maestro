// backend — Spring Boot 오케스트레이터 (Supervisor·스케줄·REST/WS·인증·Flow Router·메트릭). Phase 4~.
plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

dependencies {
    implementation(project(":protocol"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // 영속화: H2 시작 + Postgres 전환경로 (ADR-0002 O-1). Flyway 마이그레이션.
    runtimeOnly("com.h2database:h2")
    implementation("org.flywaydb:flyway-core")

    // gRPC 서버 스택(러너 텔레메트리 수신). 본격 배선은 Phase 4.
    implementation("io.grpc:grpc-netty-shaded:1.66.0")
    implementation("io.grpc:grpc-protobuf:1.66.0")
    implementation("io.grpc:grpc-stub:1.66.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 통합 테스트가 러너 프로세스를 기동할 수 있도록 러너를 테스트 클래스패스에 포함
    // (코드 의존이 아니라, java.class.path에 RunnerMain을 올리기 위함).
    // slf4j-simple은 Spring Logback과 충돌하므로 제외(자식 러너는 Logback 바인딩 사용).
    testImplementation(project(":runner")) {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
}

// 부트 가능한 단일 jar만 생성 (Docker COPY 모호성 방지). plain jar 비활성화.
tasks.named<Jar>("jar") {
    enabled = false
}
