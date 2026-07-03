# Maestro 백엔드 오케스트레이터 컨테이너 (멀티스테이지).
# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
# 백엔드 bootJar + 러너 배포본(노드 프로세스 기동에 사용) 빌드
RUN ./gradlew --no-daemon :backend:bootJar :runner:installDist

# ---- runtime ----
# JDK 필요: 러너가 javax.tools.JavaCompiler 로 스크립트를 동적 컴파일한다(JRE 불가).
FROM eclipse-temurin:17-jdk
WORKDIR /app

# healthcheck용 curl + 비루트 사용자
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r maestro && useradd -r -g maestro maestro

COPY --from=build /workspace/backend/build/libs/*.jar /app/app.jar
# 러너 클래스패스(러너 jar + sdk + protocol + grpc 의존). 백엔드가 -cp 로 러너 JVM 기동.
COPY --from=build /workspace/runner/build/install/runner/lib /app/runner-lib

# H2 파일 DB 디렉터리(영속). application.yaml 기본 url=jdbc:h2:file:./data/maestro → /app/data
RUN mkdir -p /app/data && chown -R maestro:maestro /app/data

ENV MAESTRO_RUNNER_CLASSPATH="/app/runner-lib/*" \
    MAESTRO_GRPC_PORT=9090 \
    SERVER_PORT=8080 \
    JAVA_OPTS=""

VOLUME ["/app/data"]
USER maestro
EXPOSE 8080 9090

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
