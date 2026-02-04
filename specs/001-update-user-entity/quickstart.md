# Quickstart

## Prerequisites
- Java 24
- PostgreSQL running
- Redis running

## Setup
1. **Clone/Pull** the branch `001-update-user-entity`.
2. **Build** the project:
   ```bash
   ./mvnw clean install
   ```
3. **Run** the application:
   ```bash
   ./mvnw spring-boot:run
   ```

## Testing
1. **Create User**:
   ```bash
   curl -X POST http://localhost:8080/users \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com", "password":"password", "full_name":"Test User", "status":"ACTIVE", "gender":"MALE"}'
   ```
2. **Verify Database**: Check `users` table for new columns.
