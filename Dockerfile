# Stage 1: Build the application
FROM maven:3.8.5-openjdk-17-slim AS build

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build the Spring Boot JAR
RUN mvn clean package -DskipTests -B


# Stage 2: Runtime image
FROM openjdk:17-slim

WORKDIR /app

# Install native libraries required by OpenCV
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Copy the generated JAR
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]