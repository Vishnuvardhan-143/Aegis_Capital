# 🛡️ Admin Service

Administrative oversight and auditing microservice for Aegis Capital Bank. This service provides a centralized dashboard for managing users and performing deep-drill transaction audits.

## Tech Stack

| Layer     | Technology              |
|-----------|-------------------------|
| Framework | Spring Boot 3           |
| Language  | Java 17+                |
| Security  | Spring Security + JWT   |
| API       | REST (Internal & Proxy) |
| Frontend  | Vanilla HTML / CSS / JS |

## Features

- **User Directory**: Centralized view of all registered bank users (excluding other administrators).
- **Account Discovery**: Quickly view all accounts associated with a specific user.
- **Transaction Ledger**: Deep-drill auditing for any account, showing full inbound and outbound transaction history.
- **Role Isolation**: Strict enforcement that only users with the `ADMIN` role can access this dashboard.

## Project Structure

```
admin_service/
├── Backend/
│   ├── src/main/java/com/admin/
│   │   ├── controller/
│   │   │   └── AdminController.java      # User & Transaction auditing API
│   │   ├── security/
│   │   │   ├── JwtAuthFilter.java       # Admin role validation
│   │   │   └── SecurityConfig.java      # Restricted access config
│   │   └── AdminApplication.java
│   └── src/main/resources/
│       └── application.yml              # Service URLs & JWT Secret
├── Frontend/
│   ├── index.html                       # Admin Dashboard
│   ├── admin.js                         # Audit logic & API calls
│   └── index.css                        # Premium Admin UI
└── README.md
```

## Running Locally

### Prerequisites
- Java 17+
- Maven
- Auth, Account, and Transaction services must be running.

### Steps

```bash
# 1. Start the backend (default port: 5065)
cd Backend
mvn spring-boot:run

# 2. Start the frontend dev server
cd Frontend
npm install
npm start
```

## Inter-Service Communication

- **Auth Service**: Fetches user lists and roles.
- **Account Service**: Fetches account portfolios for specific users.
- **Transaction Service**: Fetches detailed transaction ledgers for auditing.

The Admin Service acts as a secure proxy, validating the current user's `ADMIN` role before aggregating data from the internal microservices.
