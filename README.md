# 🦐 AgriShrimp Backend API

![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)
![Spring Boot 3.5](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Cache-DC382D?logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)

AgriShrimp Backend is a Spring Boot API for a shrimp farming operations and agri-commerce platform. This repository powers the core backend for the storefront, admin dashboard, multi-branch inventory workflows, payment processing, shipping integration, and AI-enabled mini app features.

## 📌 Project Snapshot

| Item | Details |
| --- | --- |
| Role | Central backend API for storefront, admin, and mini app |
| Domain | Agri-commerce / shrimp farming operations |
| Core stack | Java 25, Spring Boot 3.5, Spring Security, Spring Data JPA, MySQL, Redis |
| External integrations | Cloudinary, PayOS, GHN, Google Login, Zalo Mini App, TrackAsia, OpenRouteService |
| API docs | `/swagger-ui/index.html` |
| Health check | `/actuator/health` |
| Deployment | Docker Compose, GitHub Actions, GHCR, VPS, Cloudflare Tunnel |

## ✨ Technical Highlights

- Built for more than CRUD: products, orders, inventory, stock transfers, handovers, vouchers, suppliers, debt tracking, and operational dashboards.
- Supports a multi-branch business model with order splitting based on stock availability and branch proximity.
- Implements authentication and authorization with JWT, refresh tokens, Google login, Zalo auth, and permission-based access control.
- Integrates with real-world services for payments, shipping, media storage, geocoding, routing, and AI image search.
- Includes production-minded engineering practices such as Swagger/OpenAPI, Actuator health endpoints, Dockerization, health check scripts, and automated deployment workflows.

## 🧩 Core Modules

- `Authentication & Authorization`: signup, login, refresh token, current-user endpoints, roles/permissions, custom security filters, and AOP-based permission checks.
- `Product Catalog`: products, variants, categories, brands, attributes, image upload, and public catalog APIs.
- `Order Management`: cart, order preparation, checkout, order confirmation, user/admin/branch order views, payment link creation, and payment webhook handling.
- `Inventory & Warehouse`: stock receipts, export flows, stock checks, transfers, handovers, and branch-level inventory tracking.
- `Branch & Logistics`: nearest branch lookup, address suggestions, real-distance calculation, shipping provider integration, and GHN data endpoints.
- `AI Features`: image-based product search and shrimp disease diagnosis flows for the mini app.
- `Finance & Dashboard`: business stats, top products, daily performance, pending orders, profit/loss reporting, and supplier debt visibility.

## 🏗️ Architecture & Code Organization

The codebase follows a clear REST API + service layer structure:

```text
src/main/java/com/zone/agri
|-- controller/     # REST endpoints
|-- service/        # Business logic
|-- repository/     # Spring Data JPA repositories
|-- entity/         # Domain model
|-- dto/            # Request/response models
|-- security/       # JWT, user details, filters
|-- config/         # OpenAPI, security, Redis, JPA, app beans
|-- exception/      # Global exception handling
`-- utils/          # Utility helpers

infra/
|-- docker-compose.prod.yml
`-- scripts/
```

## 🧭 API Groups

Swagger is grouped for easier review:

- `Public APIs`: `/api/auth/**`, `/api/public/**`, `/api/external/**`
- `Core System`: `/api/users/**`, `/api/roles/**`, `/api/branches/**`, `/api/files/**`
- `Business Operations`: `/api/products/**`, `/api/categories/**`, `/api/suppliers/**`, `/api/customers/**`, `/api/attributes/**`
- `AI Doctor`: `/api/ai-doctor/**`

Full endpoint documentation is available at `http://localhost:8004/swagger-ui/index.html` after the application starts.

## 🛠️ Tech Stack

- Language: Java 25
- Framework: Spring Boot 3.5.5
- Security: Spring Security, JWT
- Persistence: Spring Data JPA, Hibernate, MySQL
- Cache / token support: Redis
- API documentation: springdoc-openapi
- Build tool: Maven Wrapper (`mvnw`, `mvnw.cmd`)
- Media / storage: Cloudinary, with placeholders for AWS S3
- Deployment: Docker, GitHub Container Registry, self-hosted VPS deployment

## ▶️ Local Setup

### Requirements

- JDK 25
- Docker + Docker Compose
- Maven Wrapper already included in the repository

### Option 1: Run the backend locally and use Docker for MySQL + Redis

Start dependencies:

```bash
docker compose up -d db redis
```

Run the application with the `dev` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Default local values from `application-dev.yml`:

- API port: `8004`
- MySQL: `localhost:3307`
- Redis: `localhost:6379`

After startup:

- Swagger UI: `http://localhost:8004/swagger-ui/index.html`
- Health check: `http://localhost:8004/actuator/health`

### Option 1A: Run the backend locally and test against the real server database

This project already auto-loads `.env` via Spring config import, so after a one-time setup you can start the backend with a normal Spring Boot command.

Recommended flow:

1. Open an SSH tunnel from your machine to the server database:

```bash
ssh -L 3307:127.0.0.1:3306 your-user@your-server
```

2. Copy `.env.example` to `.env` and fill in the real secrets (tunnel-based DB/Redis values are already the defaults).
3. Keep the tunnel open.
4. Start local Redis if needed:

```bash
docker compose up -d redis
```

5. Run the backend normally:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,live-local
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,live-local"
```

Because `.env` is loaded automatically, no manual `source .env` or extra wrapper script is required for this workflow. The `live-local` profile keeps local runs safer against the real database by disabling startup schema patches and seed data.

Important:

- Your machine must be allowed to reach the remote MySQL host. Firewall, security groups, bind-address, and MySQL user host rules can still block the connection.
- The local web app should point to `http://localhost:8004/api` if you want the browser to use your local backend instead of the deployed API.

### Option 2: Run in a more production-like container setup

- Use `.env.example` as the template for your `.env` file.
- The repository `docker-compose.yml` includes `ai-visual-search`, which points to an external sibling repository at `../agrishrimp-ai-visual-search`, so that part is optional if you only need the core backend running.

## 🔐 Environment & Configuration

The project mainly uses two configuration profiles:

- `src/main/resources/application-dev.yml`: local development defaults
- `src/main/resources/application-prod.yml`: production profile driven by environment variables

Important environment variables include:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`
- `SECURITY_JWT_SECRET_KEY`
- `APP_CORS_ALLOWED_ORIGINS`, `APP_WEB_BASE_URL`, `APP_SERVER_URL`
- `RESEND_API_KEY`, `RESEND_FROM_EMAIL`, `RESEND_FROM_NAME`
- `CLOUDINARY_*`
- `PAYOS_*`
- `GHN_*`
- `TRACKASIA_*`, `ORS_API_KEY`

Notes:

- Do not commit real secrets to Git.
- `EmailService` currently sends mail through Resend, so make sure the `RESEND_*` variables exist in `.env`.
- External integrations require their matching environment variables to be fully functional.

## ✅ Testing & Operations

- Representative unit tests already exist in `src/test/java`, for example `InventoryAllocationServiceTest` for stock-based order allocation logic.
- Spring Actuator is enabled for health monitoring and deployment verification.
- `infra/scripts/healthcheck.sh` checks the webapp, API, AI service, and container health.
- `.github/workflows/deploy.yml` builds the image, pushes it to GHCR, and deploys it to a self-hosted server with a post-restart health check.

## 💼 What This Repository Demonstrates

- The ability to design backend systems for real business workflows with multiple modules and external dependencies.
- Experience beyond a basic CRUD application, especially around inventory logic, order allocation, and multi-branch operations.
- Production-ready thinking through API documentation, containerization, health checks, deployment automation, and environment separation.

## 📎 Useful Commands

```bash
./mvnw test
./mvnw clean package
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

If you are reviewing this repository as a recruiter or hiring manager, the strongest signals are the multi-module business workflow design, the number of real service integrations, and the fact that the project is structured like a deployable production system rather than a classroom demo.
