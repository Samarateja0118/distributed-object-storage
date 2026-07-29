# Distributed File Storage (DFS)

Production-grade distributed object storage platform built with **Java 21** and **Spring Boot 3.3**.

This repository contains the Maven multi-module scaffold. Business logic is intentionally deferred — each module boots, wires configuration, and exposes health endpoints only.

## Architecture Overview

```
Clients → Gateway → Auth / Metadata / Upload Orchestrator
                         ↓              ↓
                    PostgreSQL      Blob Nodes (gRPC)
                         ↓
                       Kafka → Replication / GC
```

| Module | Port (HTTP) | gRPC | Purpose |
|--------|-------------|------|---------|
| `dfs-gateway-service` | 8080 | — | Edge routing and rate limiting |
| `dfs-auth-service` | 8081 | — | Authentication and authorization |
| `dfs-metadata-service` | 8082 | 9082 | Buckets, objects, chunk metadata |
| `dfs-upload-orchestrator` | 8083 | — | Upload/download orchestration |
| `dfs-blob-node` | 8090 | 9090 | Chunk storage on local disk |
| `dfs-replication-service` | 8084 | — | Async chunk replication |
| `dfs-gc-service` | 8085 | — | Garbage collection |
| `dfs-admin-service` | 8086 | — | Cluster operations |

## Module Structure

```
distributed-file-storage/
├── dfs-bom/                    # Dependency version alignment (BOM)
├── dfs-proto/                  # gRPC / Protobuf contracts
├── dfs-common/                 # Shared kernel and infrastructure config
├── dfs-gateway-service/        # Edge API gateway
├── dfs-auth-service/           # Auth & RBAC
├── dfs-metadata-service/       # Metadata control plane
├── dfs-upload-orchestrator/    # Upload/download orchestration
├── dfs-blob-node/              # Data-plane chunk storage
├── dfs-replication-service/    # Replication coordinator
├── dfs-gc-service/             # Garbage collector
├── dfs-admin-service/          # Admin / ops API
├── dfs-integration-tests/      # Cross-service integration tests
└── deploy/
    └── docker-compose.yml      # Local infrastructure
```

## Clean Architecture Layers (per service)

Each service follows the same package layout:

```
com.dfs.{service}/
├── domain/              # Entities, value objects, domain services
├── application/port/    # Use-case interfaces (inbound/outbound ports)
├── infrastructure/      # Adapters: JPA, Kafka, gRPC, Redis
├── api/rest/            # REST controllers (public)
├── api/grpc/            # gRPC service implementations (internal)
└── config/              # Spring @Configuration classes
```

**Dependency rule:** `domain` → `application` → `infrastructure` / `api`. Domain never depends on Spring or transport.

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker & Docker Compose

## Quick Start

### 1. Start infrastructure

```bash
docker compose -f deploy/docker-compose.yml up -d
```

### 2. Build all modules

```bash
mvn clean verify
```

### 3. Run a service (example: metadata)

```bash
mvn -pl dfs-metadata-service spring-boot:run
```

### 4. Verify health

```bash
curl http://localhost:8082/actuator/health
```

## Configuration

All services read configuration from `application.yml` with environment variable overrides:

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |
| `POSTGRES_DB` | `dfs` | Database name |
| `POSTGRES_USER` | `dfs` | Database user |
| `POSTGRES_PASSWORD` | `dfs` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `DFS_ENV` | `local` | Environment label |
| `JWT_SIGNING_SECRET` | `change-me-in-production` | Auth JWT secret |

## Implementation Phases

| Phase | Focus |
|-------|-------|
| **0** (current) | Scaffold, config, Docker Compose, CI-ready build |
| **1** | Single-node MVP: auth, buckets, simple object upload/download |
| **2** | Multi blob-node distribution, multipart, gRPC |
| **3** | Replication, GC, presigned URLs, encryption, observability |
| **4** | Service extraction, admin ops, multi-tenant scale |

## Development Notes

- Virtual threads are enabled on all services (`spring.threads.virtual.enabled=true`).
- Flyway migrations use placeholder scripts until Phase 1 schema is implemented.
- gRPC proto stubs are generated at build time from `dfs-proto/src/main/proto/`.
- Security configs currently permit all requests — JWT enforcement comes in Phase 1.

## License

Private / unlicensed — update as needed.
