# Architecture Decision Records

Each record captures one decision, the context that forced it, and what it costs.
The point is the reasoning: a decision whose rationale is lost gets reversed by
someone who only sees the inconvenience.

Records are immutable once accepted. A decision that changes gets a new record
that supersedes the old one, rather than an edit — the history of what was
believed and when is the useful part.

| # | Decision | Status |
|---|---|---|
| [1](0001-clean-architecture.md) | Clean Architecture per service | Accepted |
| [2](0002-shared-token-contract.md) | One shared token contract in `dfs-common` | Accepted |
| [3](0003-content-addressable-storage.md) | Content-addressable blob storage with SHA-256 | Accepted |
| [4](0004-no-delete-on-blob-node.md) | The blob node has no DELETE endpoint | Accepted |
| [5](0005-rest-before-grpc.md) | REST between orchestrator and blob node in Phase 1 | Superseded by [10](0010-grpc-for-the-byte-path.md) |
| [6](0006-blob-first-writes-metadata-first-reads.md) | Blob-first writes, metadata-first reads | Accepted |
| [7](0007-forward-the-callers-token.md) | The orchestrator forwards the caller's token | Accepted |
| [8](0008-objects-are-ordered-lists-of-chunks.md) | An object is an ordered list of chunks, owned by metadata | Accepted |
| [9](0009-composite-digest-for-chunked-objects.md) | A chunked object's `contentHash` is a composite digest | Accepted |
| [10](0010-grpc-for-the-byte-path.md) | gRPC for the byte path, REST everywhere else | Accepted |
| [11](0011-chunk-placement-is-recorded-not-recomputed.md) | Chunk placement is recorded, not recomputed | Accepted |
| [12](0012-s3-style-multipart-upload.md) | S3-style multipart upload with server-side sessions | Accepted |

## Format

```markdown
# N. Title

Date: YYYY-MM-DD
Status: Proposed | Accepted | Superseded by ADR M

## Context
The forces at play. What made a decision necessary.

## Decision
What was decided, in the active voice.

## Consequences
What follows — including the costs. A record listing only benefits is
advocacy, not a decision record.
```
