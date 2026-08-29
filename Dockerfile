# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B


# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install native dependencies required for OpenCV
RUN apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Copy generated JAR
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Run the application with optimized memory settings for 512MB container
CMD ["java", "-XX:MaxRAMPercentage=50.0", "-XX:+UseSerialGC", "-Xss256k", "-jar", "app.jar"]