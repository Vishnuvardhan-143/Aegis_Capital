# AI Agent Guidelines for Aegis Capital Banking Microservices

## Architecture Overview
This is a microservices-based banking system with four independent services: Auth, Account, Transaction, and Admin. Each service has its own MySQL database (except Admin, which acts as a secure proxy) and Spring Boot backend, plus a vanilla HTML/CSS/JS frontend.

- **Auth Service** (Backend: port 5052, Frontend: 5173): Handles user registration, login with TOTP MFA, and JWT token issuance.
- **Account Service** (Backend: port 5050, Frontend: 5501): Manages bank accounts; validates JWTs for user endpoints and exposes internal endpoints for balance/PIN operations.
- **Transaction Service** (Backend: port 5005, Frontend: 5502): Processes deposits, withdrawals, transfers; calls Account Service internals for validation and updates.
- **Admin Service** (Backend: port 5065, Frontend: 5503): Provides administrative auditing. Fetches user lists from Auth, portfolios from Account, and transaction ledgers from Transaction service.

Services communicate via REST: Auth issues JWTs consumed by Account/Admin; Transaction calls Account; Admin calls Auth/Account/Transaction.

## Project Root
The actual code lives at:
`D:\SpringBoot Apps\Aegis_Capital_Bank-main\Aegis_Capital_Bank-main\Aegis_Capital-main\BankingSystem-PostIntegration-main`

## Key Files & Directories
- `auth_service_post_integration/auth_service/Backend/src/main/resources/application.yml`: JWT secret and DB config.
- `account_service_post_integration/account_service/Backend/src/main/resources/application.yml`: Shared JWT secret for validation.
- `transaction_service_post_integration/transactionservice/src/main/resources/application.yml`: No JWT; Account Service URL is configurable via `account-service.url` property (defaults to `http://localhost:5050`, overridable via `ACCOUNT_SERVICE_URL` env var in Docker).
- `docker-compose.yml`: Orchestrates all 7 containers (MySQL, 3 backends, 3 frontends).
- `init-db.sql`: Auto-creates the 3 databases on first MySQL startup.
- Each service's `pom.xml`: Spring Boot 3.x with JPA, Security (Auth/Account), MySQL connector.

## Docker Deployment
- **Run**: `docker-compose up --build` from the project root.
- MySQL runs on port 3307 (host) → 3306 (container).
- All backends use `SERVER_PORT` env var in docker-compose to set the correct port inside containers.
- Spring datasource URLs use `mysql` (Docker service name) instead of `localhost`.
- Transaction Service uses `ACCOUNT_SERVICE_URL=http://account-backend:5050` for inter-service calls via Docker networking.
- Frontends use `serve` (auth) and `http-server` (account/transaction) — these are in `dependencies` (not `devDependencies`) so `npm install --production` installs them.

## CORS Configuration
- **Auth & Account Services**: CORS is configured ONLY in `SecurityConfig.java` via `CorsConfigurationSource` bean. The `CorsConfig.java` (WebMvcConfigurer) files were intentionally emptied to avoid conflicts with Spring Security's CORS filter.
- **Transaction & Admin Services**: CORS is configured via `WebMvcConfigurer`.
- All CORS configs allow origins: `localhost:5173`, `localhost:5501`, `localhost:5502`, `localhost:5503`, `127.0.0.1:5500`.

## Developer Workflows
- **Build & Run Backends**: `cd <service>/Backend; mvn spring-boot:run`. Databases auto-create with `ddl-auto: update`.
- **Run Frontends**: `cd <service>/Frontend; npm start` (uses `http-server` or `serve` on specified ports).
- **Full System (Local)**: Start services in order: Auth → Account → Transaction. Frontends run independently.
- **Full System (Docker)**: `docker-compose up --build`.
- **Debugging**: Check MySQL logs; backends log SQL with `show-sql: true`. No integrated tests; manual testing via frontends.

## Project-Specific Patterns
- **Error Handling**: Controllers wrap service calls in try-catch, return `ResponseEntity.badRequest()` with error messages (e.g., `AuthController.java`). Account service has `GlobalExceptionHandler.java`.
- **Security**: JWT HS256 with shared hex-encoded secret across Auth/Account. Transaction Service has no security; relies on internal calls.
- **Inter-Service Calls**: Use `RestTemplate` (configured in `AppConfig.java`); Transaction Service calls Account internals for PIN verify (`POST /internal/accounts/{id}/verify-pin`) and balance updates (`PUT /internal/accounts/{id}/balance`).
- **Database Naming**: Each service uses a dedicated DB (e.g., `auth_Service`, `account_Service`) with auto-creation.
- **Frontend Integration**: Vanilla JS fetches from backends. Auth frontend calls `localhost:5052`, Account frontend calls `localhost:5050`, Transaction frontend calls `localhost:5005`.
- **MFA**: TOTP-based; Auth Service generates secrets on registration, verifies on login.

## Integration Points
- JWT tokens from Auth must match Account's secret for `/api/*` endpoints.
- Transaction Service requires Account Service running for operations.
- Frontends hardcode backend URLs (e.g., `http://localhost:5050/api/accounts`).

## Conventions
- Package structure: `com.example.<service>/controller`, `service`, `repository`, `entity`, `security`.
- Account service uses package `com.account` (not `com.example.account`).
- Use Lombok `@RequiredArgsConstructor` for dependency injection.
- DTOs use builders (e.g., `AuthResponse.builder()`).
- Hibernate: `ddl-auto: update` for schema evolution; format SQL in logs.

## Recent Changes (April 2026)
1. **Administrative Oversight**: Added `Admin Service` for centralized auditing. This includes a User Directory and a Deep-Drill Transaction Ledger for every account.
2. **Security Hardening**: Implemented **Strict Role-Based Isolation**. Administrators are forced to use the "Login as Admin" path and are blocked from user-specific dashboards. Standard users are immediately blocked from the Admin dashboard before MFA.
3. **MFA Fixes**: Resolved a Jackson/Lombok mapping bug where boolean `isAdmin` fields were defaulting to `false`, causing administrative login failures.
4. **Development Workflow**: Enabled **Frontend Hot-Reloading** via Docker Volume mappings (`./Frontend:/app`). UI changes are now visible instantly upon browser refresh without container rebuilds.
5. **Persistence**: Fixed JPA optimistic locking bugs in PIN resets by using native `@Modifying` queries to bypass Hibernate level-1 cache.
6. **Docker Fixes**: Added `TRANSACTION_SERVICE_URL` and `AUTH_SERVICE_URL` environment variables to enable the Admin Service to communicate across the Docker network.
