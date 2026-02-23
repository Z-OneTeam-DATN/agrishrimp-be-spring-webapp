# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Technology Stack

- **Framework**: Spring Boot 3.5.5 with Java 21
- **Build Tool**: Maven (use `./mvnw` wrapper)
- **Database**: MySQL with JPA/Hibernate
- **Cache**: Redis (for JWT token blacklist on logout)
- **Authentication**: JWT with Spring Security
- **Storage**: AWS S3 for file/image uploads
- **Documentation**: SpringDoc OpenAPI (Swagger UI)

## Essential Commands

### Build and Run
```bash
# Clean and install dependencies
./mvnw clean install

# Run application with dev profile
./mvnw spring-boot:run -Dspring-profiles.active=dev

# Run application with staging profile
./mvnw spring-boot:run -Dspring-profiles.active=stg

# Run tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=FileControlApiImplTest
```

### Required Environment Variables
```bash
export SECURITY_JWT_SECRET_KEY="your_jwt_secret"
export DOMAIN="localhost"
```

Optional database/Redis configuration via environment variables (defaults in application-dev.yml):
- `DBMS_CONNECTION` - Database JDBC URL
- `DBMS_USERNAME` - Database username
- `DBMS_PASSWORD` - Database password
- `SPRING_DATA_REDIS_HOST` - Redis host

## Architecture Overview

### Package Structure
```
com.zone.agri/
├── api/              # API interfaces (e.g., FileControlApi, ImageControlApi)
├── common/           # Shared utilities (AuthUtils, S3Service, Constants)
├── config/           # Configuration classes (Security, JWT, Redis, S3, DataSeeder)
├── controller/       # REST controllers implementing API interfaces
├── dto/              # Data Transfer Objects organized by domain
│   ├── admin/
│   ├── auth/
│   ├── branch/
│   ├── common/
│   ├── customer/
│   ├── file/
│   ├── supplier/
│   └── user/
├── entity/           # JPA entities and enums
│   └── enums/
├── exception/        # Custom exceptions and global handler
├── logging/          # Logging interceptors
├── repository/       # JPA repositories
├── security/         # Security components (JWTFilter, CustomUserDetails)
├── service/          # Business logic layer
└── utils/            # Utility classes (JwtUtils, CookieUtils, MessagesUtils)
```

### Hierarchical Permission System

The application implements a **tree-structured permission system** with parent-child relationships:

**Permission Types** (src/main/java/com/zone/agri/entity/enums/PermissionType.java):
- `MODULE`: Parent-level permission representing a feature module (e.g., "Quản lý xuất hàng")
- `ACTION`: Child-level permission for specific operations (e.g., "Duyệt lệnh xuất")

**Permission Groups** (src/main/java/com/zone/agri/entity/enums/PermissionGroup.java):
- `PRODUCT_CATALOG` - Hàng hóa (Products, Categories, Attributes)
- `INVENTORY_TRANSACTION` - Giao dịch kho (Import, Export, Transfer, Inventory Check)
- `SALES_MANAGEMENT` - Bán hàng (Orders, Vouchers, Promotions)
- `PARTNER_MANAGEMENT` - Đối tác (Customers, Suppliers)
- `POND_MANAGEMENT` - Quản lý ao nuôi (Ponds, Feeding logs)
- `SYSTEM_REPORT` - Báo cáo (Reports)
- `SYSTEM_SETTING` - Hệ thống (Users, Roles, Settings)

**Permission Entity Fields**:
- `code`: Unique identifier (e.g., "IMPORT_APPROVE", "EXPORT_FORCE_EDIT")
- `name`: Display name in Vietnamese
- `groupName`: Category from PermissionGroup enum
- `type`: MODULE or ACTION
- `parentId`: References parent permission ID for hierarchical structure

Example hierarchy:
```
EXPORT_MANAGE (MODULE, parentId=null)
  ├── EXPORT_APPROVE (ACTION, parentId=EXPORT_MANAGE.id)
  └── EXPORT_FORCE_EDIT (ACTION, parentId=EXPORT_MANAGE.id)
```

### Data Seeding

The `DataSeeder` (src/main/java/com/zone/agri/config/DataSeeder.java) runs on application startup and initializes:
1. **Permissions** with tree structure (parent-child relationships)
2. **Roles** with assigned permissions:
   - `ADMIN`: Full system access
   - `STAFF_KHO` (Warehouse Staff): Can view all inventory pages but cannot approve transactions
   - `FARMER`: Access to products, farms, and orders
   - `CUSTOMER`: Access to products and orders
3. **Default Branch**: Main branch in Cần Thơ
4. **Admin User**: Email `admin@gmail.com` / Password `123456`

**Important**: DataSeeder only runs if no roles exist in the database (safe for production).

### Base Entity Pattern

All entities extend `BaseEntity` (src/main/java/com/zone/agri/entity/BaseEntity.java) which provides automatic auditing:
- `createdAt`: Timestamp of creation
- `updatedAt`: Timestamp of last modification
- `createdByUserId`: User ID who created the record
- `updatedByUserId`: User ID who last modified the record

Uses Spring Data JPA's `@EntityListeners(AuditingEntityListener.class)` with `AuditorAwareImpl` to automatically populate user IDs from JWT authentication context.

### Authentication Flow

1. User logs in via `/api/auth/login` with email/password
2. `AuthService` validates credentials and generates JWT tokens (access + refresh)
3. JWT tokens contain user ID, role, and permissions
4. `JWTFilter` intercepts requests, validates tokens, and sets Spring Security context
5. On logout, tokens are blacklisted in Redis
6. Protected endpoints use Spring Security's `@PreAuthorize` or manual permission checks

**Security Configuration**: Currently set to permit all requests (line 58-60 in SecurityConfig.java). The commented-out configuration shows proper authentication requirements.

### Multi-Profile Configuration

- `application.yml`: Base configuration with actuator endpoints
- `application-dev.yml`: Development settings (MySQL, Redis, show-sql=true)
- `application-stg.yml`: Staging settings

Activate profiles via: `-Dspring.profiles.active=dev`

### Exception Handling

Centralized in `ApiExceptionHandler` with custom exceptions:
- `BadRequestException`: 400 errors
- `NotFoundException`: 404 errors
- `Forbidden`: 403 access denied
- `ConflictException`: 409 conflicts
- `CustomAuthenticationException`: Authentication failures
- `SignInRequiredException`: Requires login

All exceptions return consistent JSON structure via `ErrorDetail`.

### File Upload Architecture

Two API interfaces for file operations:
- `FileControlApi` / `FileControlApiImpl`: General file uploads to S3
- `ImageControlApi` / `ImageControlImpl`: Image-specific uploads

Both use `S3Service` (src/main/java/com/zone/agri/common/S3Service.java) configured via `S3Config`.

## Development Patterns

### Adding New Features

Follow this order (as documented in README):

1. **Entity** (src/main/java/com/zone/agri/entity/)
   - Extend `BaseEntity` for automatic auditing
   - Add JPA annotations and relationships

2. **Repository** (src/main/java/com/zone/agri/repository/)
   - Extend `JpaRepository<Entity, ID>`
   - Add custom query methods if needed

3. **DTOs** (src/main/java/com/zone/agri/dto/)
   - Organize by domain (admin/, auth/, customer/, etc.)
   - Use validation annotations from jakarta.validation

4. **Service** (src/main/java/com/zone/agri/service/)
   - Implement business logic
   - Use `@Transactional` for operations modifying data

5. **Controller** (src/main/java/com/zone/agri/controller/)
   - Implement REST endpoints
   - Use `@RestController` and `@RequestMapping`
   - Optionally create API interface in `api/` package first

### Permission Conventions

When adding new permissions in DataSeeder:
- Create MODULE-level permission first (parent)
- Create ACTION-level permissions as children with `parentId`
- Use descriptive codes: `{RESOURCE}_{ACTION}` (e.g., `PRODUCT_CREATE`, `IMPORT_APPROVE`)
- Group logically by PermissionGroup
- Assign to appropriate roles based on business requirements

### Controller Patterns

Controllers may implement separate API interfaces (see `api/` package) for better separation of concerns, though not all controllers follow this pattern.

## API Documentation

Swagger UI available at: `http://localhost:8080/swagger-ui.html`

API docs: `http://localhost:8080/v3/api-docs`

## Testing

- Test files in `src/test/java/com/zone/agri/`
- Uses H2 in-memory database for tests (configured in pom.xml)
- Spring Security test support available

## Notes

- The README mentions PostgreSQL in one section but the actual configuration uses MySQL
- Security is currently disabled (all endpoints permit all) - see SecurityConfig.java:58-60
- All Vietnamese comments and naming conventions should be preserved when working with permissions and roles
- DataSeeder creates default admin account - ensure this is secured in production deployments
