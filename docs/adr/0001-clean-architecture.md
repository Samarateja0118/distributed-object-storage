# 1. Clean Architecture per service

Date: 2026-07-25
Status: Accepted

## Context

DFS is planned in phases, and later phases move services onto separate deployment
units and swap transports — REST calls become gRPC, local disk gains replication,
synchronous writes gain a Kafka path. A layout that lets framework and transport
concerns reach into business rules makes each of those a rewrite of the rules
themselves rather than a change of adapter.

The services also differ enormously in what they touch: the auth service is
database-heavy, the blob node touches no database at all, the orchestrator is
pure coordination. A structure worth adopting has to fit all three without
special cases.

## Decision

Every service uses the same hexagonal layout:

```
com.dfs.{service}/
├── domain/              # Entities, value objects, domain rules
├── application/port/    # Inbound and outbound port interfaces
├── application/service/ # Use-case implementations
├── infrastructure/      # Adapters: JPA, Redis, Kafka, HTTP clients
├── api/rest/            # REST controllers
├── api/grpc/            # gRPC service implementations
└── config/              # Spring @Configuration
```

The dependency rule is `domain` → `application` → `infrastructure`/`api`. The
domain layer imports no Spring, JPA, or transport type. Business logic does not
live in controllers; a controller translates HTTP into a use-case call.

## Consequences

Swapping a transport becomes a matter of adding an adapter behind an existing
port. The upload orchestrator's `BlobStorePort` is the first real test of this:
Phase 1 implements it over REST, and Phase 2 adds a gRPC implementation without
the orchestration logic in `ObjectTransferService` changing.

The cost is indirection that looks gratuitous in small services. A port with one
implementation and one caller reads as ceremony until the second implementation
arrives. This is accepted deliberately — the alternative is discovering at
Phase 2 that the rules and the transport cannot be separated.

Ports are also what make the integration tests possible without standing up the
whole system: a test can drive a real use case against a stand-in adapter without
mocking the logic under test.
