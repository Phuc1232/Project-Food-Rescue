# FoodRescue backend

Independent Spring Boot / Kotlin Gradle project. Open this directory as the IDE project.
Requires JDK 21. Package: com.foodrescue.backend.

## Run (PowerShell)
```powershell
$env:DB_PASSWORD = "<your-local-database-password>"
docker compose up -d db
.\gradlew.bat clean build
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

On Linux/macOS use `sh ./gradlew` instead of `.\gradlew.bat`.
The default port is 8080; set PORT to override it.
For production set SPRING_PROFILES_ACTIVE=prod and optionally LOG_LEVEL.

## Docker Compose

`compose.yml` runs PostgreSQL with a persistent volume. Run the backend through Gradle as above.
The test suite starts its own isolated PostgreSQL container and requires Docker.

## Structure
- common/exception, response, util: shared application helpers.
- config, security: future configuration and authorization.
- auth, account, store, staff, catalog, surprisebox, order, payment, pickup,
  finance, complaint, notification, file, audit: feature packages.
- resources/db/migration: Flyway schema, foreign keys, indexes and history protections.
- test: PostgreSQL/Testcontainers integration tests and finance value-object tests.

Empty packages are tracked with .gitkeep.
The backend includes JPA entities, repositories, PostgreSQL/Flyway migrations and persistence tests.
Authentication API, JWT/session validation, account profile and store authorization are implemented. Business order/payment controllers are not implemented yet.
API base path: /api/v1. See docs/auth-setup.md for required key/mail configuration.
Secrets must be supplied through environment variables, never committed.

Build configuration follows the official Kotlin Spring Boot example:
https://kotlinlang.org/docs/jvm-create-project-with-spring-boot.html


## PostgreSQL persistence

See [persistence setup and service contracts](docs/persistence.md) and the
[English entity/property dictionary](docs/erd-entity-implementation-plan.md#8-quy-Æ°á»›c-tÃªn-tiáº¿ng-anh-vÃ -danh-má»¥c-thuá»™c-tÃ­nh).
Set DB_PASSWORD before running Compose and the backend. Tests require Docker and
use a separate PostgreSQL Testcontainer, not the application database.

## Authentication API

Read [authentication setup](docs/auth-setup.md), [OpenAPI](docs/api/openapi.yaml) and
[implementation control report](docs/specs/api-foundation-and-auth/03-implementation-control.txt).
The application requires RSA keys and RATE_LIMIT_KEY_SECRET before startup.
Gradle tests generate isolated keys and never send real email.

## Swagger UI

In dev/test, open `/docs` for interactive Swagger UI, then use **Try it out** and
**Authorize** with the access JWT from login (without the `Bearer` prefix).
UI assets are bundled locally. Authorization is cleared on reload; refresh is manual.
Docs, contract and assets are denied whenever `prod` is active, including mixed profiles.
See [Swagger setup](docs/auth-setup.md#swagger-ui-tương-tác-devtest) and
[implementation plan](docs/specs/swagger-ui/02-plan.md) and
[control report](docs/specs/swagger-ui/03-implementation-control.txt).
