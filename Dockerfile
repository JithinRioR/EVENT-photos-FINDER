# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the application fast with parallel threads and concise logs
RUN mvn clean package -DskipTests -B -T 4C -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install native dependencies required for OpenCV
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Copy generated JAR
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Run the application with balanced memory settings for 512MB container
CMD ["java", "-XX:MaxRAMPercentage=60.0", "-XX:+UseSerialGC", "-Xss512k", "-jar", "app.jar"]