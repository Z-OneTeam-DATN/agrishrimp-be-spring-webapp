# ==================== STAGE 1: BUILD ====================
FROM maven:3.9-sapmachine-21 AS build
WORKDIR /app

# Cache dependency (Kỹ thuật Layer Caching)
COPY pom.xml ./

# Tải trước toàn bộ thư viện về máy.
RUN mvn dependency:go-offline

# Copy source code & build
COPY src ./src

# Lệnh build: Xóa sạch (clean), đóng gói (package), dùng profile (prod or dev ), bỏ qua test unit (skipTests).
RUN mvn clean package -Pdev -DskipTests

# ==================== STAGE 2: RUNTIME ====================
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# Lấy file .jar đã tạo ra ở giai đoạn 'build'.
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Cấu hình bộ nhớ cho Java trong container.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Lệnh chạy cuối:
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]