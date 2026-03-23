# 🏦 Aegis Capital Banking Microservices Application

A full-stack banking application built with a **microservices architecture** using **Spring Boot** (Backend) and **Vanilla HTML/CSS/JS** (Frontend). The system provides user authentication with MFA, bank account management, and transaction processing across three independent services.

---

## 📋 Table of Contents
- [Architecture Overview](#architecture-overview)
- [Services](#services)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [API Endpoints](#api-endpoints)
- [Project Structure](#project-structure)

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client (Browser)                        │
│  Auth Frontend (:5173)  Account Frontend (:5501)  Txn Frontend (:5502)│
└────────┬────────────────────┬──────────────────────┬────────────┘
         │                    │                      │
         ▼                    ▼                      ▼
┌──────────────┐     ┌──────────────┐      ┌──────────────────┐
│ Auth Service │     │Account Service│      │Transaction Service│
│  (Port 5052) │     │  (Port 5050) │◄────►│   (Port 5005)    │
└──────┬───────┘     └──────┬───────┘      └───────┬──────────┘
       │                    │                      │
       ▼                    ▼                      ▼
┌──────────────────────────────────────────────────────────────┐
│                     MySQL Database                           │
└──────────────────────────────────────────────────────────────┘
```

- **Auth Service** issues JWT tokens; other services validate them.
- **Account Service** manages bank accounts (create, list, lookup).
- **Transaction Service** handles deposits, withdrawals, and transfers (both own-account and external).

---

## 🔧 Services

### 1. Auth Service (`auth_service_post_integration/`)
| Layer | Details |
|-------|---------|
| **Backend** | Spring Boot 3.2.4, Spring Security, JPA, JWT (jjwt 0.11.5) |
| **Frontend** | Login, Register, MFA (TOTP) pages |
| **Port** | Backend: `5052` · Frontend: `5173` |
| **Features** | User registration, login, TOTP-based MFA, JWT token issuance |

### 2. Account Service (`account_service_post_integration/`)
| Layer | Details |
|-------|---------|
| **Backend** | Spring Boot 3.2.5, Spring Security, JPA, JWT validation |
| **Frontend** | Account dashboard with create-account form |
| **Port** | Backend: `5050` · Frontend: `5501` |
| **Features** | Create bank account, list user accounts, account lookup by number |

### 3. Transaction Service (`transaction_service_post_integration/`)
| Layer | Details |
|-------|---------|
| **Backend** | Spring Boot 3.3.0, JPA |
| **Frontend** | Tabbed UI — Deposit, Withdraw, Transfer (own / external), History |
| **Port** | Backend: `5005` · Frontend: `5502` |
| **Features** | Deposit, withdraw, transfer between accounts, transaction history |

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.x |
| **Security** | Spring Security, JWT (jjwt 0.11.5) |
| **MFA** | TOTP (dev.samstevens.totp) |
| **ORM** | Spring Data JPA |
| **Database** | MySQL |
| **Build Tool** | Maven |
| **Frontend** | Vanilla HTML, CSS, JavaScript |
| **Other** | Lombok, Jackson |

---

## ✅ Prerequisites

- **Java 17** or higher
- **Maven 3.8+**
- **MySQL 8.0+**
- **Node.js** (for running frontend dev servers such as `http-server` or `live-server`)

---

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/Vishnuvardhan-143/BankingSystem-PostIntegration.git
cd BankingSystem-PostIntegration
```

### 2. Configure MySQL
Create the required databases and update each service's `application.properties` (or `application.yml`) with your MySQL credentials.

### 3. Run the Backend Services
```bash
# Auth Service
cd auth_service_post_integration/auth_service/Backend
mvn spring-boot:run

# Account Service (in a new terminal)
cd account_service_post_integration/account_service/Backend
mvn spring-boot:run

# Transaction Service (in a new terminal)
cd transaction_service_post_integration/transactionservice
mvn spring-boot:run
```

### 4. Run the Frontend Servers
Each frontend can be served using any static file server (e.g., `npx http-server` or VS Code Live Server):
```bash
# Auth Frontend  → http://localhost:5173
cd auth_service_post_integration/auth_service/Frontend
npx http-server -p 5173

# Account Frontend → http://localhost:5501
cd account_service_post_integration/account_service/Frontend
npx http-server -p 5501

# Transaction Frontend → http://localhost:5502
cd transaction_service_post_integration/transactionservice/Frontend
npx http-server -p 5502
```

---

## 📡 API Endpoints

### Auth Service (`:5052`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive JWT |
| POST | `/api/auth/verify-mfa` | Verify TOTP MFA code |

### Account Service (`:5050`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/accounts` | List accounts for authenticated user |
| POST | `/api/accounts` | Create a new bank account |
| GET | `/internal/accounts/by-accno/{accno}` | Lookup account by account number (internal) |

### Transaction Service (`:5005`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/transactions/deposit` | Deposit to an account |
| POST | `/transactions/withdraw` | Withdraw from an account |
| POST | `/transactions/transfer` | Transfer between accounts |
| GET | `/transactions/history/{accountId}` | Transaction history for an account |

---

## 📁 Project Structure

```
post-integration/
├── auth_service_post_integration/
│   └── auth_service/
│       ├── Backend/          # Spring Boot auth service
│       └── Frontend/         # Login, Register, MFA pages
│
├── account_service_post_integration/
│   └── account_service/
│       ├── Backend/          # Spring Boot account service
│       └── Frontend/         # Account dashboard
│
├── transaction_service_post_integration/
│   └── transactionservice/
│       ├── src/              # Spring Boot transaction service
│       └── Frontend/         # Transaction UI (Deposit, Withdraw, Transfer, History)
│
├── .gitignore
└── README.md
```

---

## 📄 License

This project is for educational and demonstration purposes.
