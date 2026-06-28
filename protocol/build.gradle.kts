// protocol — backend<->runner 공용 gRPC 스키마 (ADR-0002 O-2). 단일 출처(.proto)에서 코드 생성.
import com.google.protobuf.gradle.id

plugins {
    `java-library`
    id("com.google.protobuf") version "0.9.4"
}

val grpcVersion = "1.66.0"
val protobufVersion = "3.25.5"

dependencies {
    api("io.grpc:grpc-protobuf:$grpcVersion")
    api("io.grpc:grpc-stub:$grpcVersion")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
    // 생성 코드의 javax.annotation.Generated 용 (런타임 불필요)
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}
