# syntax=docker/dockerfile:1

# ---- Build: dependencies first (cached layer and cache mount), then the code.
# Tests are not run here: the integration tests need Docker (Testcontainers);
# run them with ./mvnw verify.
FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/embedding-explorer-*.jar /build/app.jar

# ---- Runtime: JRE only, non-root user, no write access to the image.
FROM eclipse-temurin:21.0.13_9-jre-alpine-3.24
RUN addgroup -S -g 10001 viz && adduser -S -u 10001 -G viz -H -s /sbin/nologin viz
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.io.tmpdir=/tmp", "-jar", "/app/app.jar"]
