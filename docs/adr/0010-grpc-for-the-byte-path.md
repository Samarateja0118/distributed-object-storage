# 10. gRPC for the byte path, REST everywhere else

Date: 2026-07-30
Status: Accepted

## Context

ADR 5 deferred gRPC to Phase 2 on the grounds that its advantages are advantages
*for chunking*: streaming, framing, and flow control buy little when an upload is
one whole-object PUT. ADR 8 introduces chunking, so that argument has now expired
and the scaffolding it named — `blob.proto`, `blobGrpcTarget`,
`metadataGrpcTarget` — is due to become real.

The open question is blast radius. gRPC could replace REST on the orchestrator's
byte path only, on both of its downstream hops, or everywhere internal.

`blob.proto` as scaffolded does not survive contact with ADR 8 either. Its
`PutChunk` and `GetChunk` are unary with a `bytes data` field, which holds a whole
chunk in memory per call and reproduces Phase 1's memory bound one level down.
And it carries a `DeleteChunk` RPC.

## Decision

gRPC carries the orchestrator's calls to the blob node. The orchestrator's calls
to the metadata service stay REST, and the client-facing API stays REST.

`blob.proto` is reshaped: `PutChunk` becomes client-streaming, with a header
message carrying digest and size followed by data frames; `GetChunk` becomes
server-streaming; `ChunkExists` is added; `DeleteChunk` is removed.

The blob node's REST endpoints and their integration tests retire once the gRPC
path is proven.

## Consequences

**`DeleteChunk` should never have been there.** It contradicts ADR 4, which
records that a deduplicated blob has no owner to authorize a delete against. A
generated stub is not a decision, but it reads like permission — leaving it in
the contract invites someone to implement the thing ADR 4 exists to prevent. ADR
4 stands unchanged; reclamation is still GC's, in Phase 3.

**Authentication needs a second enforcement point.** The blob node already
authenticates: `BlobSecurityConfiguration` runs an OAuth2 resource server with
`anyRequest().authenticated()`. But a `SecurityFilterChain` is a servlet filter
chain and never sees a gRPC call, so the same policy has to be enforced again in
a `ServerInterceptor`. This is exactly the drift ADR 2 was written to prevent, so
the interceptor validates with the shared `JwtDecoder` bean rather than
configuring its own — one decoder, two entry points.

**`ChunkExists` is what makes chunk-level deduplication pay.** Without it the
orchestrator re-sends bytes the cluster already holds. It is also a probe that
reveals whether a digest exists, but only to authenticated internal callers, and
only about chunks whose bytes the orchestrator is already holding — it discloses
nothing a caller could not establish by uploading.

**Keeping metadata on REST preserves an argument from ADR 5** that is easy to
lose: the orchestrator uses the same public API as any other client, so its
authorization is exercised on the common path rather than through a privileged
internal channel. Moving it to gRPC would have bought consistency of transport
and cost that property, while rewriting a working path and its tests.

The cost is two transports to configure, secure, and test, and a blob node that
speaks one protocol to the orchestrator and another to nothing else once its REST
endpoints are gone. `BlobStorePort` absorbs the change (ADR 1); orchestration
logic names no transport and does not move.
