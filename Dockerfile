# ---- Build stage ----------------------------------------------------------
# Uses a full JDK + Maven to compile and package the app into a jar.
# A separate dependency-resolution layer keeps rebuilds fast: as long as
# pom.xml is unchanged, Docker reuses the cached dependency layer.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Resolve dependencies first (cached unless pom.xml changes).
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# 2. Copy sources and build. Skip tests in the image build — CI runs them.
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Runtime stage --------------------------------------------------------
# A slim JRE (no compiler/Maven) — smaller image, smaller attack surface.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user (security best practice; K8s securityContext expects this).
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

# Container-friendly JVM flags: honour cgroup memory limits set by Docker/K8s.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

EXPOSE 8096

# Spring Boot actuator health endpoint powers the Docker/K8s health checks.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8096/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
