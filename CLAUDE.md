# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project philosophy

- **Clean Architecture boundaries are not negotiable.** `domain` → `application` → `infrastructure`/`api`. The domain layer must not import Spring, JPA, or any transport type.
- **No business logic in controllers.** A controller translates HTTP to a use-case call and back. Orchestration belongs in an application service, rules belong in the domain.
- **Constructor injection only.** No field injection, no `@Autowired` on fields; it makes dependencies invisible and objects untestable without a container.
- **Services stay independently deployable.** No service reaches into another's database. Cross-service communication goes over its public API.
- **Shared contracts live in `dfs-common`.** Anything two services must agree on byte for byte — the token contract, the digest encoding — has exactly one definition there rather than a copy per service.

## Module responsibilities

| Module | Responsibility |
|---|---|
| `dfs-bom` | Dependency version alignment |
| `dfs-proto` | gRPC / Protobuf contracts (Phase 2 onward) |
| `dfs-common` | Shared kernel: token contract, digest, common infrastructure config |
| `dfs-auth-service` | Authentication only — tenants, users, JWT issuance, API keys |
| `dfs-metadata-service` | Buckets, objects, versions, object metadata |
| `dfs-blob-node` | Blob storage on local disk; SHA-256 content-addressable |
| `dfs-upload-orchestrator` | Coordinates uploads and downloads across blob + metadata |
| `dfs-gateway-service` | Edge routing, rate limiting |
| `dfs-replication-service` | Async replication |
| `dfs-gc-service` | Garbage collection, blob refcounting |
| `dfs-admin-service` | Cluster and tenant operations |

The last four are still scaffolds that boot and expose health endpoints only.

## Architecture invariants

Two contracts cut across services. Both are the kind of thing that breaks quietly rather than loudly, so treat them as fixed.

### Tenancy comes from the token

Every HTTP service is a JWT resource server. The tenant is read from the validated token's `tenant_id` claim — **never from a request header, path, or body**. This is the entire tenancy boundary: a service that accepted a tenant id from the request would let any caller address any tenant's data.

The pieces live in `dfs-common` so issuer and validators cannot drift apart:

- `dfs.security.jwt` — issuer and signing secret, one property block shared by the service that mints tokens and every service that validates them.
- `DfsResourceServerConfiguration` — the single `JwtDecoder` bean, pinning the algorithm and the expected issuer.
- `DfsTokenClaims` — reads tenant and user from a validated `Jwt`.
- `DfsTestTokens` — signs tokens in tests the way the auth service does, published as a test-jar (`<type>test-jar</type><scope>test</scope>`, version managed in `dfs-bom`).

New services reuse these rather than configuring a decoder locally.

### An object is two halves joined by a digest

Content and metadata live in different services, linked only by a SHA-256 hash:

- `dfs-blob-node` holds content, addressed purely by digest. No tenant appears in its paths — identical bytes from two tenants are one blob. It has **no DELETE endpoint**: a deduplicated blob has no single owner to authorize against, so reclamation requires refcounting and belongs to GC.
- `dfs-metadata-service` holds the pointer — bucket, key, version, and the `contentHash` naming the bytes.
- `dfs-upload-orchestrator` is the only component that joins them.

`com.dfs.common.hash.Sha256` is the single definition of a digest (lowercase hex). If two services disagreed on the encoding, identical bytes would be filed under two names and deduplication would silently stop working.

## Design decisions

**SHA-256 content-addressable storage.** Naming a blob by its own hash makes storage deduplicating and writes idempotent for free — re-sending the same bytes is indistinguishable from sending them once, so retries are safe without a transaction log. It also makes corruption detectable: the node re-hashes on write and rejects a body that does not match the digest it was filed under.

**Writes are blob-first, metadata second.** Nothing spans both services transactionally, so the only real choice is which inconsistency a partial failure leaves behind. Metadata-first leaves objects that list but cannot be downloaded — the system misreporting what it holds, detectable only by a repair job. Blob-first leaves bytes nothing refers to: invisible to every client, and reclaimable by GC. Content addressing keeps retries from accumulating orphans, since the same bytes always produce the same digest.

**Reads are metadata-first, blob second.** The metadata service is what enforces tenancy; a digest has no owner. Consulting the blob node first would serve any blob whose digest a caller could guess or had once seen.

**The orchestrator forwards the caller's token and holds no service account.** A service account would replace the caller's tenant claim with its own downstream, and the metadata service could no longer distinguish one tenant's writes from another's. The token is taken from the validated `Jwt` rather than re-read from the header, so what is forwarded is exactly what was verified.

**PostgreSQL for metadata.** Buckets, objects, and versions are relational and carry invariants worth enforcing in the schema; the services use JPA over Flyway-managed migrations with `ddl-auto: validate`, so the schema is owned by migrations rather than inferred from entities.

**One database per schema-owning service.** `dfs-auth-service`, `dfs-metadata-service`, and `dfs-admin-service` default to `dfs_auth`, `dfs_metadata`, and `dfs_admin`, created by `deploy/postgres/init-databases.sql` on first initialization of the Postgres volume. This is the "no service reaches into another's database" boundary made literal, and it is also load-bearing: each service ships its own `V1__` migration and its own default `flyway_schema_history`, so pointing two of them at one database means the second to start fails with a checksum mismatch on version 1 rather than applying its own schema. After editing that init script, recreate the volume with `docker compose -f deploy/docker-compose.yml down -v` — Postgres runs `docker-entrypoint-initdb.d` only on first init.

**Clean Architecture.** The services are meant to be extracted and scaled independently in later phases. Keeping transport and persistence out of the domain is what makes that a refactor rather than a rewrite.

## Coding conventions

- **Java 21, Spring Boot 3.3**, hexagonal layout per service.
- **Flyway owns the schema.** Never change a table via JPA; add a migration. `ddl-auto` stays `validate`.
- **RFC 7807 problem details for API errors.** Each service has a `@RestControllerAdvice` translating domain exceptions to `ProblemDetail`. Because `spring.mvc.problemdetails.enabled` registers Spring's own handler at order 0, these advices must be `@Order(Ordered.HIGHEST_PRECEDENCE)` or custom bodies are silently replaced.
- **Integration tests over mocks at service boundaries.** Tests named `*IT` run under failsafe at `verify`. Prefer a real dependency — a container, or an in-process HTTP stand-in — over a mocked client: a mock is configured with the same assumptions as the code under test and will agree with them whether or not they are correct.
- **Comments explain why, not what.** The reasoning behind a non-obvious choice is worth recording; a paraphrase of the next line is not.

### Testing constraints worth knowing

- A Testcontainers `@Container` field shared across test classes is stopped after the first class finishes. Start the container in a `static` block and leave it running; Ryuk reaps it at JVM exit.
- Modules using Spring Boot's `repackage` must point failsafe at `${project.build.outputDirectory}`, or tests run against the fat jar where classes sit under `BOOT-INF/` and component scanning fails.
- `@ConditionalOnClass` on a JDK type guards nothing, since every service component-scans `com.dfs.common`. Guard shared configuration on a class from the optional starter itself.
- MockMvc routes HEAD to the GET handler and retains the body, so a real server's empty HEAD response cannot be asserted in a MockMvc test.

## Build and test

Java 21 and Maven 3.9+. Confirm `java -version` matches before building; a machine's default JDK on `PATH` is often older, in which case set `JAVA_HOME` for the session.

```bash
mvn clean verify                                  # full build, unit + integration
mvn -pl dfs-metadata-service -am verify           # single module
mvn -pl dfs-metadata-service -am spring-boot:run  # run a service
```

**Always pass `-am`.** Without it a single-module build resolves `dfs-common` from the local repository rather than the reactor, so edits to shared code compile against a stale jar and fail with "package does not exist".

Integration tests are `*IT` and bind to `verify`; `mvn test` alone runs almost nothing. To narrow:

```bash
mvn -pl dfs-metadata-service -am verify -Dit.test=BucketApiIT
mvn -pl dfs-metadata-service -am verify -Dit.test=BucketApiIT#createsBucket
```

### Container runtime

Most integration tests use Testcontainers and need a running container runtime. `deploy/docker-compose.yml` provides PostgreSQL, Redis, and Kafka for running services by hand — tests do not use it and start their own containers.

With Docker Desktop, no extra configuration is needed. **With Colima**, export the following, or Testcontainers fails to connect:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
mvn clean verify -DargLine="-Dapi.version=1.44"
```

`api.version` is required because Colima's daemon expects a newer Docker API than the bundled docker-java defaults to, and it must be a **system property** — the `DOCKER_API_VERSION` environment variable is ignored. The socket override is needed because Ryuk bind-mounts the Docker socket by host path, which does not exist inside the Colima VM.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| **1** | Auth, buckets, single-node object upload/download | ✅ Complete |
| **2** | gRPC, chunking, multipart upload, multi-node distribution | In progress — gRPC byte path, chunking and placement done |
| **3** | Replication, GC, blob refcounting, presigned URLs, encryption | Planned |
| **4** | Observability, benchmarking, admin ops, multi-tenant scale | Planned |

Phase 1 delivered the auth service, metadata service, blob node, and upload orchestrator, all covered by integration tests.

Two scoping decisions hold unless the repository owner changes them:

1. gRPC carries the **byte path only** — the orchestrator's calls to the blob node. The metadata service and the client-facing API stay REST ([ADR 10](docs/adr/0010-grpc-for-the-byte-path.md)), and `metadataGrpcTarget` remains unused scaffolding. The blob node's REST endpoints also stay until the gRPC path has proven itself; `ObjectLifecycleE2EIT.refusesUnauthenticatedBlobReads` is coupled to them and needs a gRPC equivalent when they go.
2. Auth is **login, JWT, and API keys — not full RBAC**. A `role` claim exists (OWNER/MEMBER) but nothing enforces per-bucket permissions.

3. The cluster writes **one copy** of every chunk. "Multi-node" here means distributed, not redundant — losing a node's disk loses data until replication lands in Phase 3 ([ADR 11](docs/adr/0011-chunk-placement-is-recorded-not-recomputed.md)).

Chunk placement is rendezvous hashing over `dfs.upload.blob-nodes`, a statically configured set — adding a node is a redeploy. The ranking is a *preference*: a write that cannot reach its preferred node walks down the list, and the node that accepted the chunk is what gets recorded. Reads follow that record and never recompute the ranking, which is what makes a fallback write indistinguishable from a normal one ([ADR 11](docs/adr/0011-chunk-placement-is-recorded-not-recomputed.md)).

Only an *unreachable* node is walked past. A node that answers and refuses will refuse identically everywhere else, so failing over would turn one clear error into several. There is no health checker: a write discovers a node is down by failing to reach it.

**A node id that has ever held a chunk must stay in `dfs.upload.blob-nodes`,** with its `grpc.client.<id>.address` block, whether or not it still takes new writes. Chunks are recorded against it by name and reads ask for it by name; dropping the id makes those chunks unreachable while the bytes sit intact on disk. `blob-node-0` is the id everything written before placement carries.

An object is an ordered list of chunks, `dfs.upload.chunk-size` each. The orchestrator holds one chunk at a time in both directions, so `dfs.upload.max-object-size` is now a policy ceiling rather than a bound on heap. It is enforced against bytes as they are read — a `Content-Length` can be understated or omitted — so an oversized upload stores the chunks that fit before it is rejected. Nothing is registered, so the object never becomes visible, and the stray chunks are unreferenced bytes for GC.

**An object's `contentHash` is the composite digest of its chunk list, not the SHA-256 of its bytes** ([ADR 9](docs/adr/0009-composite-digest-for-chunked-objects.md)). A client that hashes a file locally and compares it against the ETag will not match. Objects written before Phase 2 keep their true whole-object hash; nothing in the row distinguishes the two but age.
