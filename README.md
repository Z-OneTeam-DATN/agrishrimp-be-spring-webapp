# Agri Shrimp

Nền tảng API cho dự án **agri-shrimp** sử dụng **JWT authentication**, Spring Security, JPA và cấu hình đa môi trường.

## Tính năng chính

- Xác thực và phân quyền: JWT + Spring Security
- Quản lý người dùng tối thiểu (đăng ký/đăng nhập)
- Cấu hình multi-profile (dev, staging)
- CORS configuration
- Exception handling tập trung
- Logging interceptor

## Yêu cầu hệ thống

- Java 24 (JDK)
- Maven 3.8+ (hoặc sử dụng `./mvnw`)
- MySql
- Redis (token blacklist khi logout)

## Cấu trúc thư mục chính

```
.
├─ pom.xml
├─ src
│  ├─ main
│  │  ├─ java/com/zone/agri
│  │  │  ├─ AgriShrimpApplication.java
│  │  │  ├─ common/
│  │  │  ├─ config/
│  │  │  ├─ controller/
│  │  │  ├─ dto/
│  │  │  ├─ entity/
│  │  │  ├─ exception/
│  │  │  ├─ logging/
│  │  │  ├─ repository/
│  │  │  ├─ security/
│  │  │  └─ service/
│  │  └─ resources
│  │     ├─ application.yml
│  │     ├─ application-dev.yml
│  │     └─ application-stg.yml
│  └─ test/java/com/zone/agri/AgriShrimpApplicationTests.java
└─ mvnw, mvnw.cmd
```

## Hướng dẫn nhanh (chạy được project)

1. Tạo database PostgreSQL (ví dụ `agri_shrimp_db`).
2. Thiết lập biến môi trường JWT và domain.
3. Chỉnh `application-dev.yml` cho đúng user/password.
4. Cài dependencies và chạy project.

Lệnh nhanh:

```bash
./mvnw clean install
./mvnw spring-boot:run -Dspring.profiles.active=dev
```

## Hướng dẫn hoàn chỉnh cho người mới bắt đầu

Mục tiêu: chạy được project, gọi thử API, và biết điểm bắt đầu khi phát triển tính năng mới.

### 1) Clone project

```bash
git clone <repo-url>
cd agri-shrimp-spring-webapp
```

### 2) Tạo database

```sql
CREATE DATABASE agri_shrimp_db;
```

### 3) Thiết lập biến môi trường

Bạn cần ít nhất 2 biến sau:

- `SECURITY_JWT_SECRET_KEY` (chuỗi bí mật cho JWT)
- `DOMAIN` (tên miền hoặc `localhost`)

MacOS/Linux:

```bash
export SECURITY_JWT_SECRET_KEY="your_jwt_secret"
export DOMAIN="localhost"
```

Windows PowerShell:

```powershell
$env:SECURITY_JWT_SECRET_KEY = "your_jwt_secret"
$env:DOMAIN = "localhost"
```

Windows CMD:

```cmd
set SECURITY_JWT_SECRET_KEY=your_jwt_secret
set DOMAIN=localhost
```

### 4) Cấu hình database và redis

Mở `src/main/resources/application-dev.yml` và cập nhật đúng thông tin của bạn:

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${DBMS_CONNECTION:jdbc:mysql://localhost:3306/agri_shrimp_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
    username: ${DBMS_USERNAME:root}
    password: ${DBMS_PASSWORD:your_password_here}

  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update 
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true

  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: 6379
      timeout: 60000
```

### 5) Cài dependency và chạy project

```bash
./mvnw clean install
./mvnw spring-boot:run -Dspring.profiles.active=dev
```

### 6) Gọi thử API

```bash
curl -X POST http://localhost:8004/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"student@example.com","password":"123456"}'
```

### 7) Lỗi thường gặp

- `Connection refused` to PostgreSQL: kiểm tra DB đã chạy chưa.
- `Could not resolve placeholder SECURITY_JWT_SECRET_KEY`: chưa set biến môi trường.
- `Redis connection failure`: kiểm tra Redis đã chạy chưa, port có đúng không.

## Hướng dẫn bắt đầu triển khai tính năng mới (dành cho sinh viên)

Thường bắt đầu từ các thư mục sau, theo thứ tự từ dữ liệu đến API:

1. `src/main/java/com/zone/agri/entity/`
   - Tạo hoặc cập nhật Entity.
2. `src/main/java/com/zone/agri/repository/`
   - Tạo Repository (JPA).
3. `src/main/java/com/zone/agri/service/`
   - Viết business logic.
4. `src/main/java/com/zone/agri/dto/`
   - Tạo DTO cho request/response.
5. `src/main/java/com/zone/agri/controller/`
   - Tạo API endpoint gọi xuống service.

Thư mục hỗ trợ thường dùng:

- `src/main/java/com/zone/agri/security/` khi cần phân quyền.
- `src/main/java/com/zone/agri/config/` khi cần cấu hình bean, CORS, WebSocket.
- `src/main/resources/` khi cần thêm cấu hình `application-*.yml`.
- `src/test/java/com/zone/agri/` để viết test.

## API auth cơ bản

```
POST /api/auth/signup
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```
