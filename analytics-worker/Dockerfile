# syntax=docker/dockerfile:1.7
# ----------------------------------------------------------------------------
# analytics-worker
#   Build context: ./analytics-worker (this folder only). The image MUST NOT
#   see any sibling-service source.
# ----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew && \
    ./gradlew bootJar -x test --no-daemon

# ----------------------------------------------------------------------------
# Runtime — lean Temurin JRE 25, only the boot JAR copied in
# ----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/analytics-worker-*-SNAPSHOT.jar app.jar

EXPOSE 8083

HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=40s \
    CMD curl -fsS http://localhost:8083/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]