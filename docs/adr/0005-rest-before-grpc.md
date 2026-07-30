# 5. REST between orchestrator and blob node in Phase 1

Date: 2026-07-30
Status: Accepted

## Context

The Phase 0 scaffold anticipated gRPC for the data plane: `dfs-proto` carries a
`blob.proto`, the blob node depends on a gRPC server starter, and the
orchestrator has `blobGrpcTarget` and `metadataGrpcTarget` properties.

The roadmap places gRPC in Phase 2, alongside chunking and multipart upload.
Phase 1's goal is a working single-node path from bytes to stored object.

## Decision

Phase 1 uses REST for both downstream calls. The gRPC scaffolding stays in place,
unused, until Phase 2.

The orchestrator holds one `RestClient` per downstream service, each pinned to
its own base URL, behind the `BlobStorePort` and `MetadataRegistryPort`
interfaces.

## Consequences

The reason gRPC belongs to Phase 2 rather than Phase 1 is that its advantages are
advantages *for chunking*. Streaming, flow control, and framing matter when an
upload is a sequence of chunks with backpressure. For a single whole-object PUT
they buy little, while costing a second transport to configure, secure, and test
before either end is doing anything a stream would help with.

Because the calls sit behind ports (ADR 1), Phase 2 adds a gRPC adapter rather
than rewriting orchestration. `ObjectTransferService` names no transport.

Using the metadata service's public REST API — the same one any client uses —
also means the orchestrator's authorization is exercised on the same path as
everyone else's, rather than through a privileged internal channel.

The unused `blobGrpcTarget` properties and `blob.proto` will read as dead code to
anyone who does not know the plan. They are annotated in configuration and named
here for that reason.
