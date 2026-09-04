# DocuForge AI — backend multi-stage build (Phase 2)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21.0.6_7-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl \
    && addgroup -S docuforge \
    && adduser -S -G docuforge docuforge

COPY --from=build /workspace/target/docuforge-backend-*.jar /app/app.jar
RUN chown -R docuforge:docuforge /app

USER docuforge
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]