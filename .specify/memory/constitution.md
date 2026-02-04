<!--
Sync Impact Report:
- Version Change: 1.0.0 (Initial)
- Principles Defined:
  - Security-First
  - Layered Architecture
  - Multi-Environment Configuration
  - Standardized API Response
  - Test-Driven Quality
- Added Sections: Technology Stack, Development Workflow
- Templates Status:
  - .specify/templates/plan-template.md: ✅ Compatible (generic gates)
  - .specify/templates/spec-template.md: ✅ Compatible
  - .specify/templates/tasks-template.md: ✅ Compatible
-->

# Agri Shrimp Constitution

## Core Principles

### I. Security-First
Secrets and credentials MUST be managed via environment variables or secure vault mechanisms and never committed to version control. Authentication and authorization are enforced via JWT and Spring Security. All external input MUST be validated at the API boundary before processing.

### II. Layered Architecture
Strict separation of concerns is enforced: Controllers handle HTTP requests/responses, Services contain business logic, Repositories handle data access, and Entities define the data schema. Data Transfer Objects (DTOs) MUST be used for all API inputs and outputs; Entities MUST NEVER be exposed directly in the API contract.

### III. Multi-Environment Configuration
Configuration MUST be profile-aware (e.g., dev, stg, prod). The base `application.yml` contains shared defaults; environment-specific overrides reside in `application-{profile}.yml` or are injected via environment variables.

### IV. Standardized API Response
APIs MUST return unified response structures to ensure consistent client consumption. Exception handling is centralized (e.g., via `@ControllerAdvice` or interceptors). HTTP status codes MUST be semantically correct (e.g., 200 OK, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 500 Internal Error).

### V. Test-Driven Quality
Code changes MUST be verified with tests where feasible. The build command (`./mvnw clean install`) MUST pass before any code is merged. Unit tests should cover business logic (Services), and integration tests should verify API endpoints (Controllers).

## Technology Stack

- **Java**: 24 (JDK)
- **Framework**: Spring Boot (Spring Security, Spring Data JPA)
- **Build Tool**: Maven (Wrapper `./mvnw` preferred)
- **Database**: PostgreSQL
- **Caching/Session**: Redis
- **Security**: JWT (JSON Web Tokens)

## Development Workflow

1.  **Entity**: Define the data model in `src/main/java/com/zone/agri/entity/`.
2.  **Repository**: Create the JPA repository interface in `src/main/java/com/zone/agri/repository/`.
3.  **Service**: Implement business logic in `src/main/java/com/zone/agri/service/`.
4.  **DTO**: Define Request/Response objects in `src/main/java/com/zone/agri/dto/`.
5.  **Controller**: Expose the API endpoint in `src/main/java/com/zone/agri/controller/`.
6.  **Verify**: Run tests and build locally to ensure quality.

## Governance

This Constitution supersedes all other project practices. Amendments require a Pull Request with updated documentation and team approval.

All Pull Requests and Code Reviews MUST verify compliance with these principles. Complexity must be justified.

**Version**: 1.0.0 | **Ratified**: 2026-01-29 | **Last Amended**: 2026-01-29