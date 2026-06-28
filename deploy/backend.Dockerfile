# Maestro 백엔드 오케스트레이터 컨테이너 (멀티스테이지). 본격 운영 설정은 Phase 10.
# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
