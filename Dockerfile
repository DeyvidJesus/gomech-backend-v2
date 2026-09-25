# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy the pom.xml and download dependencies (for layer caching)
COPY pom.xml .
# Download dependencies
RUN mvn dependency:go-offline -B

# Copy the source code
COPY src ./src
# Build the application (skipping tests for faster build)
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Needed for Docker health checks in local development.
RUN apk add --no-cache curl \
    && addgroup -S gomech \
    && adduser -S -G gomech -H -s /sbin/nologin gomech

# Copy the built jar from the builder stage
COPY --from=builder --chown=gomech:gomech /app/target/*.jar app.jar

# Run as an unprivileged user; port 8080 does not need root.
USER gomech

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
