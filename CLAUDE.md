# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Interactive test harness (formerly `swim-dnotam-mockclient`) for validating DNOTAM Provider implementations against the EUR SWIM Registry service definition. Acts as a mock ANSP consumer: authenticates via Keycloak, manages subscriptions through the provider's REST API, connects to the provider's AMQP broker, captures DNOTAM events, and runs 117 conformance test scenarios (SPEC-170).

## Build & Run

```bash
# Prerequisites: Java 21, Maven 3.9+, Podman
# Install shared deps first (one-time):
#   git clone git@github.com:swim-developer/swim-developer-validators.git && cd swim-developer-validators && ./mvnw clean install -DskipTests

# Start MariaDB (port 3308)
podman compose up -d

# Dev mode (hot-reload, port 8085)
./mvnw quarkus:dev

# Package (skip tests)
./mvnw clean package -DskipTests

# Run tests (Testcontainers auto-provisions MariaDB)
./mvnw test

# Run a single test class
./mvnw test -Dtest=SomeTest

# Run a single test method
./mvnw test -Dtest=SomeTest#methodName

# Sync all deps (pull + clone + install)
make sync
```

Dev mode requires a running `swim-dnotam-provider` instance. Configure via env vars or `application.properties` `%dev` profile:
- `SWIM_PROVIDER_API_URLS` — provider REST API
- `SWIM_PROVIDER_AMQP_HOST` / `SWIM_PROVIDER_AMQP_PORT` — provider Artemis broker
- `KEYCLOAK_URL` — Keycloak server

UI: `http://localhost:8085/ui` | Swagger: `http://localhost:8085/swagger-ui`

## Architecture

Quarkus 3 application using hexagonal (ports & adapters) architecture. Extends `swim-validator-provider` from the `swim-developer-validators` shared library.

### Package Structure

Base package: `com.github.swim_developer.validator.dnotam.provider`

```
domain/           Models + port interfaces (inbound/outbound)
application/      Use cases: conformance testing, message persistence, subscription lifecycle, SSE console
infrastructure/   REST resources, AMQP messaging (Vert.x), JPA persistence (Panache), HTTP clients, SVG map rendering
```

### Key Flows

- **mTLS Proxy**: `ProviderProxyResource` proxies browser requests to the provider REST API with client certificates via `ProviderHttpClient` (Vert.x WebClient with mTLS).
- **AMQP Message Capture**: `UserReceiverLifecycle` manages per-user AMQP connections to the provider's Artemis broker. Messages are persisted via `MessageService` → `ReceivedMessageRepository` (MariaDB).
- **Conformance Testing**: `ConformanceTestService` executes 117 SPEC-170 test scenarios via `ConformanceHttpClient`, validating provider responses against `ConformanceAssertions`.
- **Real-time Console**: `ConsoleService` streams operational events to the browser via SSE (`ConsoleResource`).
- **UI**: Server-rendered Qute templates (`src/main/resources/templates/`) with HTMX for dynamic updates and Keycloak JS for authentication.

### Inherited from swim-validator-provider

Subscription management, JWT/security services, base conformance models (`TestScenario`, `TestResult`, `AssertionResult`), and `UserConnectionState` come from the parent library. This project adds DNOTAM-specific domain logic, AMQP consumer lifecycle, and the provider proxy.

## Critical Rules

- **Naming must be unambiguous** — always qualify names (e.g., `swim-dnotam-consumer` not `swim-consumer`). Check for existing siblings before naming.
- **Consumer != Provider** — a Consumer connects to a Consumer Validator, never to the Provider of the same module. This project (the Provider Validator) connects to the actual Provider.

## Container Images

```bash
make jvm              # JVM multi-arch (amd64 + arm64)
make native           # Native full sequence
# Override: make jvm REGISTRY=quay.io/myorg TAG=v1.2.3
```

Helm chart: `src/main/helm/swim-dnotam-provider-validator/`
