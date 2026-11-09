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
Business controllers, authentication and transactional application services are not implemented yet.
A request to / returns 404 until controllers are added.
Secrets must be supplied through environment variables, never committed.

Build configuration follows the official Kotlin Spring Boot example:
https://kotlinlang.org/docs/jvm-create-project-with-spring-boot.html


## PostgreSQL persistence

See [persistence setup and service contracts](docs/persistence.md) and the
[English entity/property dictionary](docs/erd-entity-implementation-plan.md#8-quy-ước-tên-tiếng-anh-và-danh-mục-thuộc-tính).
Set DB_PASSWORD before running Compose and the backend. Tests require Docker and
use a separate PostgreSQL Testcontainer, not the application database.
