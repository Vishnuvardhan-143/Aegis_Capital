# AI Agent Guidelines for Aegis Capital Banking Microservices

## Architecture Overview
This is a microservices-based banking system with three independent services: Auth, Account, and Transaction. Each service has its own MySQL database and Spring Boot backend, plus a vanilla HTML/CSS/JS frontend served via static file servers.

- **Auth Service** (Backend: port 5052, Frontend: 5173): Handles user registration, login with TOTP MFA, and JWT token issuance.
- **Account Service** (Backend: port 5050, Frontend: 5501): Manages bank accounts; validates JWTs for user endpoints and exposes internal endpoints for balance/PIN operations.
- **Transaction Service** (Backend: port 5005, Frontend: 5502): Processes deposits, withdrawals, transfers; calls Account Service internals for validation and updates.

Services communicate via REST: Auth issues JWTs consumed by Account; Transaction calls Account's `/internal/*` endpoints (no auth required).

## Key Files & Directories
- `auth_service_post_integration/auth_service/Backend/src/main/resources/application.yml`: JWT secret and DB config.
- `account_service_post_integration/account_service/Backend/src/main/resources/application.yml`: Shared JWT secret for validation.
- `transaction_service_post_integration/transactionservice/src/main/resources/application.yml`: No JWT; calls Account Service at `http://localhost:5050`.
- Each service's `pom.xml`: Spring Boot 3.x with JPA, Security (Auth/Account), MySQL connector.

## Developer Workflows
- **Build & Run Backends**: `cd <service>/Backend; mvn spring-boot:run`. Databases auto-create with `ddl-auto: update`.
- **Run Frontends**: `cd <service>/Frontend; npm start` (uses `http-server` or `serve` on specified ports).
- **Full System**: Start services in order: Auth → Account → Transaction. Frontends run independently.
- **Debugging**: Check MySQL logs; backends log SQL with `show-sql: true`. No integrated tests; manual testing via frontends.

## Project-Specific Patterns
- **Error Handling**: Controllers wrap service calls in try-catch, return `ResponseEntity.badRequest()` with error messages (e.g., `AuthController.java`).
- **Security**: JWT HS256 with shared hex-encoded secret across Auth/Account. Transaction Service has no security; relies on internal calls.
- **Inter-Service Calls**: Use `RestTemplate` (configured in `AppConfig.java`); Transaction Service calls Account internals for PIN verify (`POST /internal/accounts/{id}/verify-pin`) and balance updates (`PUT /internal/accounts/{id}/balance`).
- **Database Naming**: Each service uses a dedicated DB (e.g., `auth_Service`, `account_Service`) with auto-creation.
- **Frontend Integration**: Vanilla JS fetches from backends; assumes services run on localhost with fixed ports. No CORS issues due to same-origin or config.
- **MFA**: TOTP-based; Auth Service generates secrets on registration, verifies on login.

## Integration Points
- JWT tokens from Auth must match Account's secret for `/api/*` endpoints.
- Transaction Service requires Account Service running for operations.
- Frontends hardcode backend URLs (e.g., `http://localhost:5050/api/accounts`).

## Conventions
- Package structure: `com.example.<service>/controller`, `service`, `repository`, `entity`, `security`.
- Use Lombok `@RequiredArgsConstructor` for dependency injection.
- DTOs use builders (e.g., `AuthResponse.builder()`).
- Hibernate: `ddl-auto: update` for schema evolution; format SQL in logs.</content>
<parameter name="filePath">D:\SpringBoot Apps\Aegis_Capital_Bank-main\AGENTS.md
