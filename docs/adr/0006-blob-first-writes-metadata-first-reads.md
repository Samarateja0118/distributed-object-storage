# 6. Blob-first writes, metadata-first reads

Date: 2026-07-30
Status: Accepted

## Context

An object is two halves: bytes on the blob node, and a metadata record naming the
digest of those bytes (ADR 3). Creating one means writing to two services, and
reading one means reading from both.

There is no transaction spanning the two. Either write can fail after the other
succeeds, and neither can be rolled back reliably — a compensating delete is
itself a call that can fail, and on the blob node it is not even available
(ADR 4). So the ordering does not determine *whether* a partial failure can
happen; it determines *which* partial state is left behind.

## Decision

**Writes store the blob first, then register the metadata.**

**Reads resolve the metadata first, then fetch the blob.**

The two orderings are deliberately opposite, for unrelated reasons.

## Consequences

### Why writes go blob-first

The choice is between two inconsistencies:

| Order | State after a partial failure |
|---|---|
| Metadata first | An object that lists and appears to exist, but cannot be downloaded |
| Blob first | Bytes that nothing references |

The first is a system misreporting what it holds. A client sees the object,
requests it, and gets an error for content that was never stored. Nothing can
detect it except a job that walks every record and checks the blob node.

The second is invisible to every client. Nothing references the bytes, so no API
can return them, and reclaiming them is exactly the job GC already has to do for
deleted objects (ADR 4) — no new mechanism.

Content addressing further limits the damage: a client retrying a failed upload
computes the same digest and lands on the same blob, so retries converge on one
orphan rather than accumulating one per attempt.

### Why reads go metadata-first

Tenancy is enforced by the metadata service. The blob node cannot enforce it — a
digest has no owner, by design.

Fetching the blob before the metadata check would mean serving content to anyone
who could name a digest. Digests are not secret; they appear in API responses and
ETags. Consulting metadata first means the tenancy check happens before any
content is read.

### Enforcement

This is the kind of invariant that a well-meaning refactor reverses, so it is
pinned by integration tests rather than convention alone: `ObjectTransferFailureIT`
asserts that a failed blob write registers no metadata, and that a failed metadata
write leaves the blob stored but nothing readable at the key.

### Accepted cost

Uploads pay two sequential round trips; they cannot be issued in parallel without
giving up the ordering guarantee. Orphaned blobs accumulate until Phase 3 GC.
