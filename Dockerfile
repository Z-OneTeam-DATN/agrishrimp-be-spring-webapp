# --- Stage 1: Build Stage ---
FROM maven:3.9.14-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Run Stage ---
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-vie \
    && rm -rf /var/lib/apt/lists/*

ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Create a non-root user for security
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# JVM Optimization flags
# -XX:+UseContainerSupport: Ensures JVM respects container memory limits
# -Xmx: Set max heap size (can be overridden via environment)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8004

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
