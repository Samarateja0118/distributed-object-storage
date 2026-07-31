# Distributed File Storage (DFS)

A multi-tenant, S3-style object storage platform built with **Java 21** and **Spring Boot 3.3** — content-addressable blob storage, a metadata control plane, JWT-based tenant isolation, and an orchestration layer that keeps bytes and metadata consistent without a distributed transaction.

<!-- TODO: add a CI build-status badge once a workflow is wired up. -->

## Why I built this

I built this project to deepen my understanding of distributed storage systems by
implementing the core building blocks of an object storage platform. Rather than
focusing on a single application, I wanted to explore how production systems
separate metadata from data storage, coordinate uploads across services, enforce
consistency boundaries, and evolve toward features such as chunking, replication,
and garbage collection.

The project emphasizes clean architecture, strong testing practices, and
incremental evolution from a monolithic upload flow to a distributed object
store.

## Architecture

```
                    ┌───────────────┐
   Clients ────────►│    Gateway    │
                    └───────┬───────┘
                            │
        ┌───────────────────┼────────────────────┐
        ▼                   ▼                    ▼
  ┌───────────┐     ┌──────────────┐     ┌───────────────┐
  │   Auth    │     │   Metadata   │◄────┤    Upload     │
  │  service  │     │   service    │     │ orchestrator  │
  └─────┬─────┘     └──────┬───────┘     └───────┬───────┘
        │                  │                     │
        ▼                  ▼                     ▼
  ┌───────────┐     ┌──────────────┐     ┌───────────────┐
  │   Redis   │     │  PostgreSQL  │     │   Blob node   │
  │ (API keys)│     │  (metadata)  │     │ (content, CAS)│
  └───────────┘     └──────────────┘     └───────────────┘
```

An object is stored as **two halves**. Its bytes live on the blob node, addressed
by the SHA-256 of their own content. Its metadata lives in PostgreSQL, holding the
bucket, key, version, and the digest naming those bytes. The upload orchestrator
is the only component that joins them.

That split is what makes deduplication and idempotent writes possible, and it is
also the system's most interesting problem: no transaction spans the two
services, so a partial failure is always possible. Which inconsistency the system
chooses to leave behind is documented in
[ADR 6](docs/adr/0006-blob-first-writes-metadata-first-reads.md).

## Features

**Multi-tenant authentication**
- Tenant and user registration with bcrypt password hashing
- HS256 JWT issuance; tenancy travels in a `tenant_id` claim, never a header
- API keys stored as prefix + hash, revealed once, Redis-cached with revocation evicting the entry

**Metadata control plane**
- Per-tenant buckets and objects, with object versioning and version history
- User-defined object metadata
- RFC 7807 problem details, with per-field validation errors

**Content-addressable blob storage**
- Blobs named by their SHA-256 digest and deduplicated across tenants
- Content hashed while streaming; a body that does not match its digest is rejected before publication
- Crash-safe writes — staged, fsynced, then atomically renamed, so a reader never observes a partial blob
- Storage sharded two levels deep by digest prefix

**Upload orchestration**
- Whole-object upload and download, joining content to metadata
- The caller's own JWT is forwarded to both downstream services; there is no service account anywhere
- Write ordering chosen so a partial failure never leaves an object that lists but cannot be downloaded

## Tech stack

| Area | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3, Spring Security (OAuth2 resource server) |
| Persistence | PostgreSQL, Spring Data JPA, Flyway migrations |
| Caching | Redis |
| Messaging | Kafka (Phase 3 onward) |
| RPC | REST today; gRPC / Protobuf scaffolded for Phase 2 |
| Testing | JUnit 5, Testcontainers, MockMvc, AssertJ |
| Build | Maven multi-module |

## Getting started

**Prerequisites:** JDK 21, Maven 3.9+, Docker (or Colima).

```bash
# 1. Start infrastructure
docker compose -f deploy/docker-compose.yml up -d

# 2. Build and run the full test suite
mvn clean verify

# 3. Run a service
mvn -pl dfs-metadata-service -am spring-boot:run
```

Running the tests under Colima rather than Docker Desktop needs a little extra
configuration — see [CLAUDE.md](CLAUDE.md#container-runtime).

### A round trip

The round trip crosses four services, so all four have to be running — each in
its own terminal, since `spring-boot:run` stays in the foreground:

```bash
mvn -pl dfs-auth-service        -am spring-boot:run   # :8081
mvn -pl dfs-metadata-service    -am spring-boot:run   # :8082
mvn -pl dfs-upload-orchestrator -am spring-boot:run   # :8083
mvn -pl dfs-blob-node           -am spring-boot:run   # :8090
```

Wait for each to report `Started …Application` before running the sequence
below. If a port is already taken, override it — `SERVER_PORT=18082` — and tell
the orchestrator where to look with `METADATA_SERVICE_URL` or `BLOB_NODE_URL`.

```bash
# Register a tenant
curl -sX POST localhost:8081/api/v1/tenants \
  -H 'Content-Type: application/json' \
  -d '{
        "name": "Acme Corp",
        "slug": "acme",
        "ownerEmail": "owner@acme.test",
        "ownerPassword": "correct-horse-battery"
      }'

# Log in and capture the access token
TOKEN=$(curl -sX POST localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"owner@acme.test","password":"correct-horse-battery"}' \
  | jq -r .accessToken)

# Create a bucket
curl -sX PUT localhost:8082/api/v1/buckets/photos \
  -H "Authorization: Bearer $TOKEN"
# → 201 {"id":"…","name":"photos","versioningEnabled":false,"defaultEncryption":"NONE",…}

# Upload an object — hashed, stored, then registered
curl -sX PUT localhost:8083/api/v1/buckets/photos/objects/hello.txt \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: text/plain' \
  --data-binary 'hello world'
# → 201 {"key":"hello.txt","versionId":"…","sizeBytes":11,"contentType":"text/plain",
#        "contentHash":"b94d27b9…cde9","etag":"b94d27b9…cde9","userMetadata":{}}

# Read it back — the bytes, with the digest as ETag
curl -i localhost:8083/api/v1/buckets/photos/objects/hello.txt \
  -H "Authorization: Bearer $TOKEN"
# → 200, ETag: "b94d27b9…cde9", X-Object-Version-Id: …, body: hello world
```

Uploading the same bytes under a second key stores one blob, not two — the
`contentHash` in both upload responses is the same, and only one file appears
under `blob-data/`. That holds across tenants as well as across keys: the blob
is shared, while a second tenant asking for the first tenant's key still gets a
404, because tenancy is enforced on the metadata pointer rather than on the
bytes.

## Services

| Module | Port | Purpose |
|---|---|---|
| `dfs-gateway-service` | 8080 | Edge routing, rate limiting |
| `dfs-auth-service` | 8081 | Tenants, users, JWT, API keys |
| `dfs-metadata-service` | 8082 | Buckets, objects, versions |
| `dfs-upload-orchestrator` | 8083 | Upload/download orchestration |
| `dfs-replication-service` | 8084 | Async replication |
| `dfs-gc-service` | 8085 | Garbage collection, refcounting |
| `dfs-admin-service` | 8086 | Cluster and tenant operations |
| `dfs-blob-node` | 8090 | Content-addressable blob storage |

Auth, metadata, blob node, and the orchestrator are implemented; the remaining
four are scaffolds that boot and expose health endpoints, pending their phase.
Every service uses the same hexagonal layout — see
[ADR 1](docs/adr/0001-clean-architecture.md).

## Documentation

- [Architecture](docs/architecture.md) — service map, the two cross-cutting contracts, layering, persistence
- [Upload sequence](docs/upload-sequence.md) — blob first, metadata second, and what a partial failure leaves behind
- [Download sequence](docs/download-sequence.md) — metadata first, because a digest has no owner
- [Data model](docs/erd.md) — the tables, from the Flyway migrations

## Design decisions

The reasoning behind the non-obvious choices is recorded as
[ADRs](docs/adr/README.md), costs included:

- [Clean Architecture per service](docs/adr/0001-clean-architecture.md)
- [One shared token contract](docs/adr/0002-shared-token-contract.md) — why four services share a single decoder
- [Content-addressable storage](docs/adr/0003-content-addressable-storage.md) — why blobs are named by their own hash
- [No DELETE on the blob node](docs/adr/0004-no-delete-on-blob-node.md) — why an apparent omission is deliberate
- [REST before gRPC](docs/adr/0005-rest-before-grpc.md)
- [Blob-first writes, metadata-first reads](docs/adr/0006-blob-first-writes-metadata-first-reads.md) — consistency without a distributed transaction
- [Forwarding the caller's token](docs/adr/0007-forward-the-callers-token.md) — why there is no service account

## Testing

Integration tests run against real dependencies rather than mocks — PostgreSQL
and Redis in Testcontainers, and, for the orchestrator, an in-process HTTP
stand-in for each downstream service so the assertions cover the actual wire
contract. Tests are named `*IT` and run at `verify`:

```bash
mvn clean verify                                        # everything
mvn -pl dfs-metadata-service -am verify                 # one module
mvn -pl dfs-metadata-service -am verify -Dit.test=BucketApiIT
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| **1** | Auth, buckets, single-node object upload/download | ✅ Complete |
| **2** | gRPC, chunking, multipart upload, multi-node distribution | Next |
| **3** | Replication, GC and refcounting, presigned URLs, encryption | Planned |
| **4** | Observability, benchmarking, admin ops, multi-tenant scale | Planned |

Two Phase 1 limits are deliberate rather than unfinished: the orchestrator
buffers each object in memory, which is what lets the digest be computed before
the first byte is sent (Phase 2 chunking removes it), and auth stops at login,
JWT, and API keys — a `role` claim exists, but no per-bucket RBAC is enforced.

<!-- TODO: add a Screenshots section. Options that demo well:
     - a terminal recording of the upload/download round trip above
     - Swagger UI at /swagger-ui.html for the metadata service
     - `tree` of the blob storage root, showing digest sharding
     - a green `mvn verify` with the integration-test summary -->

## License

Licensed under the Apache License, Version 2.0 — see [LICENSE](LICENSE).
