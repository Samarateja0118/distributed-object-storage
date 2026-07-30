# 3. Content-addressable blob storage with SHA-256

Date: 2026-07-30
Status: Accepted

## Context

The blob node needs a naming scheme for stored content. The obvious candidates
are a generated id (UUID per blob) or the content's own hash.

The storage layer also has to survive partial writes, concurrent writes of the
same content, and retries from a client that never learned whether its first
attempt succeeded.

## Decision

Blobs are named by the SHA-256 of their content, rendered as lowercase hex, and
served at `PUT/GET/HEAD /internal/v1/blobs/{sha256}`. No tenant appears in the
path.

The encoding has one definition, `com.dfs.common.hash.Sha256`, shared by every
service that computes or verifies a digest.

Writes stage under `<root>/.staging`, are hashed while streaming, are rejected if
the computed digest does not match the one in the path, and are published by
fsync followed by an atomic rename. Stored files are sharded two levels deep by
digest prefix.

## Consequences

**Deduplication is free.** Identical bytes from two tenants are one file. This is
also why the path carries no tenant: ownership is the metadata service's concern,
and a blob has no single owner.

**Writes are idempotent without coordination.** Re-sending the same bytes is
indistinguishable from sending them once, so a client that times out can simply
retry. No write-ahead log or dedup table is needed to make that safe.

**Corruption is detectable.** The node re-hashes on write rather than trusting
the caller, so a truncated or altered body is rejected before it can be published
under a name that promises different content.

**Readers never see a partial blob**, because content becomes visible only at the
rename, after the fsync.

The costs are real. A digest must be known before the first byte can be stored,
so the writer must see the whole content first — which is why Phase 1's
orchestrator buffers uploads in memory and why chunking is a Phase 2 concern.
Deduplication also means deletion has no obvious owner; see ADR 4. And the two
levels of sharding are a guess at directory sizing that may need revisiting under
real volume.

Uppercase hex of the same digest would be a second name for one object, so the
shared helper validates lowercase specifically.
