# Multi-stage Dockerfile for building and running the Spring Boot application

# Stage 1: Build stage - Compile and package the application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy source code
COPY ./ ./

# Build the application (skip tests for faster builds, remove -DskipTests if you want to run tests)
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage - Run the application
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the JAR from build stage
# The JAR name pattern is: tinyurl-0.0.1-SNAPSHOT.jar
COPY --from=build /app/target/*.jar app.jar

# Expose the application port (default Spring Boot port)
EXPOSE 8080

# Set JVM options for containerized environments
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
