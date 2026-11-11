# ShareRide Gateway Service - Container Image

FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="ShareRide Team"
LABEL service="gateway-service"
LABEL description="Reactive API Gateway for ShareRide Ecosystem"

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

# Default gateway HTTP port
EXPOSE 8080

# Container-optimized JVM flags (CGroup-aware memory limits)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

