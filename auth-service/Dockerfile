# ShareRide Auth Service - Container Image

FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="ShareRide Team"
LABEL service="auth-service"
LABEL description="Authentication & User Identity Microservice"

# Create a dedicated non-root system group and user
RUN addgroup -S shareride && adduser -S shareride -G shareride

WORKDIR /app

# Copy the pre-packaged Spring Boot executable JAR from build runner
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# Enforce non-root ownership
RUN chown -R shareride:shareride /app

# Run as non-root user for container security
USER shareride

# Default service HTTP & gRPC ports
EXPOSE 8081 9091

# Container-optimized JVM flags (CGroup-aware memory limits)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

