# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CashTrack is a Smart ATM Database & Transaction Management System built with Java 25, Spring Boot 4.0.5, and gRPC. It simulates a real-world banking ATM network handling accounts, transactions, fraud detection, and analytics.

## Build Commands

```bash
# Build all modules
./gradlew build

# Build a specific module
./gradlew :cashtrack-account-module:build
./gradlew :cashtrack-withdrawl-module:build

# Run tests for all modules
./gradlew test

# Run tests for a specific module
./gradlew :cashtrack-account-module:test

# Run a single test class
./gradlew :cashtrack-account-module:test --tests "com.cashtrack.account.service.AccountServiceTest"

# Run the application (app module)
./gradlew :app:bootRun

# Generate gRPC stubs from proto file
./gradlew :cashtrack-api-module:generateProto
```

## Architecture

### Multi-Module Gradle Structure

The project uses a Gradle multi-module architecture with 14 modules:

- **app** - Main Spring Boot application entry point (`CashTrackApplication`)
- **cashtrack-api-module** - Protobuf definitions and generated gRPC stubs (shared contracts)
- **cashtrack-account-module** - Account management (create, update, deactivate, link cards)
- **cashtrack-session-module** - ATM session management (initiate, validate PIN, authenticate)
- **cashtrack-withdrawl-module** - Cash withdrawal processing with state machine
- **cashtrack-deposit-module** - Cash deposit handling
- **cashtrack-transfer-module** - Fund transfers between accounts
- **cashtrack-balance-module** - Balance inquiries and transaction history
- **cashtrack-machine-module** - ATM machine monitoring and management
- **cashtrack-auth-module** - OAuth 2.0 authentication with JWT
- **cashtrack-fraud-module** - Fraud detection and suspicious activity monitoring
- **cashtrack-notification-module** - Alerts and notifications
- **cashtrack-reconciliation-module** - Transaction reconciliation and settlement
- **cashtrack-analytics-module** - Banking metrics and reports
- **cashtrack-forecast-module** - Cash demand prediction and ATM optimization

### gRPC Service Architecture

All inter-module communication uses gRPC with Protobuf contracts defined in `cashtrack-api-module/src/main/proto/cashtrack.proto`. Each module implements its corresponding gRPC service (e.g., `AccountServiceGrpcImpl`, `WithdrawalServiceGrpcImpl`).

After modifying the proto file, run `./gradlew :cashtrack-api-module:generateProto` to regenerate Java stubs.

### Key Technical Details

- **Java 25** with `--enable-preview` flag (required for all compilation, testing, and execution)
- **Virtual Threads** enabled (`spring.threads.virtual.enabled=true`)
- **Java 25 Features**: Records (DTOs), Sealed Classes (transaction states), Pattern Matching, Structured Concurrency, String Templates
- **Spring Boot 4.0.5** with JPA/Hibernate for MySQL persistence
- **gRPC** with `net.devh:grpc-server-spring-boot-starter` (port 9090)
- **OAuth 2.0** with JWT for authentication (roles: CUSTOMER, ATM_MACHINE, BANK_ADMIN, AUDITOR)
- **MySQL** database (url configured in `app/src/main/resources/application.yml`)

### Module Structure Pattern

Each module follows this structure:
```
cashtrack-{name}-module/
├── src/main/java/com/cashtrack/{name}/
│   ├── entity/      # JPA entities
│   ├── repository/  # Spring Data repositories
│   ├── service/     # gRPC service implementations (*GrpcImpl)
│   └── config/     # Module-specific configuration
└── src/test/        # Tests
```

### Transaction State Machine

Withdrawal and deposit modules use **sealed classes** (Java 25) for transaction state flow:
`INITIATED → AUTHORIZED → PROCESSING → COMPLETED → FAILED → REVERSED`

### Database

MySQL with strong ACID guarantees. JPA automatically creates/updates tables (`ddl-auto: update`). Connection config in `app/src/main/resources/application.yml`.

## Development Notes

- All Java compilation and execution requires `--enable-preview` (configured in root `build.gradle`)
- The app module is the Spring Boot main application that components scans all `com.cashtrack` packages
- gRPC services run on port 9090, REST (if any) on port 8080
- Lombok is used for boilerplate reduction (version 1.18.46)
- Testing uses JUnit 5 + Mockito
